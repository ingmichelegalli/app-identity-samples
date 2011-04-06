package com.google.demo.appidentity;

import com.google.appengine.api.appidentity.AppIdentityService;
import com.google.appengine.api.appidentity.AppIdentityService.SigningResult;
import com.google.appengine.api.appidentity.AppIdentityServiceFactory;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.urlfetch.FetchOptions;
import com.google.appengine.api.urlfetch.HTTPHeader;
import com.google.appengine.api.urlfetch.HTTPMethod;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.appengine.api.urlfetch.URLFetchService;
import com.google.appengine.api.urlfetch.URLFetchServiceFactory;

import net.oauth.jsontoken.crypto.AbstractSigner;
import net.oauth.jsontoken.crypto.SignatureAlgorithm;
import net.oauth.signatures.SignedJsonAssertionToken;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.*;

public class App_identity_javaServlet extends HttpServlet {

  // TODO: Don't hardcode this when app service account name is exposed via API.
  // Service account name can be constructed by:
  // for Consumer app: <your app display id>@appspot.com
  // for Google apps app: <your app display id>@<domain name>.a.appspot.com
  //
  // NOTICE: format are subject to change.
  public static String APP_SERVICE_ACCOUNT_NAME = "app-identity-java@appspot.com";

  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    resp.setContentType("text/html");
    resp.getWriter().println("<html><head>");
    resp.getWriter().println("<title>App Identity API Demo</title>");
    resp.getWriter().println("</head><body>");
        
    resp.getWriter().println(
        "<p> <a href=\"" 
        + "/resource" 
        + "\">"
        + "Resource page at http://app-identity-java.appspot.com/resource" 
        + "</a>");
    
    resp.getWriter().println(
        "<p> <a href=\"" 
        + "/certs" 
        + "\">"
        + "Certificate page at http://app-identity-java.appspot.com/certs"
        + "</a></p>");
    
    resp.getWriter().println(
        "<p> <a href=\"" 
        + "/access" 
        + "\">"
        + "Access protected resource page at http://app-identity-java.appspot.com/access" 
        + "</a></p>");

    resp.getWriter().println("</body>");
    resp.getWriter().println("</html>");
  }
  
  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    doGet(req, resp);
  }

  static public String createJsonToken() throws SignatureException {
    AppEngineSigner signer = new AppEngineSigner(APP_SERVICE_ACCOUNT_NAME, "");
    SignedJsonAssertionToken jwt = new SignedJsonAssertionToken(signer);
    jwt.setNonce("don't care");
    return jwt.serializeAndSign();
  }

  public static class AppEngineSigner extends AbstractSigner {

    protected AppEngineSigner(String issuer, String keyId) {
      super(issuer, keyId);
    }

    @Override
    public SignatureAlgorithm getSignatureAlgorithm() {
      return SignatureAlgorithm.RS256;
    }

    @Override
    public byte[] sign(byte[] source) throws SignatureException {
      AppIdentityService service = AppIdentityServiceFactory.getAppIdentityService();
      SigningResult key = service.signForApp(source);
      this.setSigningKeyId(key.getKeyName());
      return key.getSignature();
    }
  }
}
