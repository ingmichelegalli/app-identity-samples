package com.google.appidentity.sample;

import com.google.appengine.api.appidentity.AppIdentityService;
import com.google.appengine.api.appidentity.AppIdentityService.SigningResult;
import com.google.appengine.api.appidentity.AppIdentityServiceFactory;
import com.google.appengine.api.urlfetch.FetchOptions;
import com.google.appengine.api.urlfetch.HTTPHeader;
import com.google.appengine.api.urlfetch.HTTPMethod;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.appengine.api.urlfetch.URLFetchService;
import com.google.appengine.api.urlfetch.URLFetchServiceFactory;
import com.google.gson.JsonParser;

import net.oauth.jsontoken.crypto.AbstractSigner;
import net.oauth.jsontoken.crypto.SignatureAlgorithm;
import net.oauth.signatures.SignedJsonAssertionToken;

import java.io.IOException;
import java.net.URL;
import java.security.SignatureException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author jcai@google.com (Jian Cai)
 */
public class WebServerFlowWithAssertion extends HttpServlet {

  public static final String clinetId = "72176245428-22khvcfdqej38hgt8lmf7obrr8vta6ou.apps.googleusercontent.com";

  public static final String clinetSecret = "iO64j3yQP5K6oAfD3KEWKAvp";

  public static final String callback = "http://assertion-flow-demo.appspot.com/oauth2callback";

  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String code = req.getParameter("code");
    if (code == null) {
      String redirectedUrl =
          DocListImpl.GOOGLE_AUTH_ENDPOINT + "?client_id=" + clinetId + "&"
              + "redirect_uri=" + callback + "&" + "scope=https://www.google.com/m8/feeds/&"
              + "response_type=code";
      resp.sendRedirect(redirectedUrl);
    } else {
      String payload;
      String assertion = getAssertion();
      payload = 
      //"client_id=" + clinetId + "&client_secret=" + clinetSecret
      "client_assertion=" + assertion
      + "&redirect_uri=" + callback
      + "&grant_type=authorization_code" + "&code=" + code;
      URLFetchService service = URLFetchServiceFactory.getURLFetchService();
      URL url = new URL(DocListImpl.GOOGLE_TOKEN_ENDPOINT);
      HTTPRequest request = new HTTPRequest(
          url, HTTPMethod.POST, FetchOptions.Builder.doNotFollowRedirects().setDeadline(20.0));
      HTTPHeader header = new HTTPHeader("Content-Type", "application/x-www-form-urlencoded");
      request.setHeader(header);
      request.setPayload(payload.getBytes());
      HTTPResponse response = service.fetch(request);
      resp.getWriter().print(new String(response.getContent()));
      // get refresh token
      JsonParser parser = new JsonParser();
      String token = parser
          .parse(new String(response.getContent()))
          .getAsJsonObject()
          .get("refresh_token")
          .getAsString();
      payload = 
        //"client_id=" + clinetId + "&client_secret=" + clinetSecret
        "client_assertion=" + assertion
        + "&grant_type=refresh_token"
        + "&refresh_token=" + token;
      request.setPayload(payload.getBytes());
      response = service.fetch(request);
      resp.getWriter().print(new String(response.getContent()));
    }
  }

  private String getAssertion() {
    AppEngineSigner signer =
        new AppEngineSigner(ThirdPartyServiceAccountDocList.EXTERNAL_ACCOUNT_NAME, "");
    SignedJsonAssertionToken jwt = new SignedJsonAssertionToken(signer);
    jwt.setAudience(DocListImpl.GOOGLE_TOKEN_ENDPOINT);
    jwt.setNonce("123456");
    // no scope
    try {
      String assertion = jwt.serializeAndSign();
      return assertion;
    } catch (SignatureException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return null;
  }

  static class AppEngineSigner extends AbstractSigner {

    protected AppEngineSigner(String issuer, String keyId) {
      super(issuer, keyId);
    }

    @Override
    public SignatureAlgorithm getSignatureAlgorithm() {
      return SignatureAlgorithm.RS256;
    }

    @Override
    public byte[] sign(byte[] source) {
      AppIdentityService service = AppIdentityServiceFactory.getAppIdentityService();
      SigningResult key = service.signForApp(source);
      this.setSigningKeyId(key.getKeyName());
      return key.getSignature();
    }
  }
}
