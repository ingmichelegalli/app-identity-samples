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
import com.google.gdata.util.ServiceException;
import com.google.gson.JsonParser;

import net.oauth.jsontoken.crypto.AbstractSigner;
import net.oauth.jsontoken.crypto.SignatureAlgorithm;
import net.oauth.signatures.SignedJsonAssertionToken;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.SignatureException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class GoogleStorageImpl {

  public static String GOOGLE_TOKEN_ENDPOINT = "https://accounts.google.com/o/oauth2/token";

  public static String GOOGLE_AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/auth";

  public static String SANDBOX_GOOGLE_TOKEN_ENDPOINT = "https://sandbox.google.com/o/oauth2/token";

  public static String SANDBOX_GOOGLE_AUTH_ENDPOINT = "https://sandbox.google.com/o/oauth2/auth";

  public static String BIGSTORE_SCOPE = "https://www.googleapis.com/auth/devstorage.full_control";

  public static String PROJECT_ID = "72176245428";

  private final String accessToken;

  private final String serviceAccountName;

  private final String servletPath;

  public GoogleStorageImpl(String servletPath, String serviceAccountName) throws IOException {
    this.serviceAccountName = serviceAccountName;
    this.servletPath = servletPath;
    accessToken = getGsToken();
  }

  public void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String bucketName = req.getParameter("bucket_name");
    this.createBucket(resp, bucketName);

    resp.setContentType("text/html");
    resp.getWriter().println("<html><head>");
    resp.getWriter().println("<title>Google Storage API Demo</title>");
    resp.getWriter().println("</head><body>");
    resp.getWriter().println("<h1>Google Storage API demo</h1>");
    resp.getWriter().println(
        "<div>Add <b>" + this.serviceAccountName + "</b> to your project ACL" + "</div>");

    resp.getWriter().println("<div><b> OAuth2 Token: " + accessToken + "</b>" + "</div>");

    resp.getWriter().println(
        "<div id=createbox><form action=" + this.servletPath + " method=post>"
            + "<b>Bucket name: </b><input type=text name=bucket_name size=60 />"
            + "<input type=submit name=create_bucket value=\"Create A Bucket\">" + "</div>");

    resp.getWriter().println("<div><b>Buckets I own:</b>" + "</div>");

    this.listMyBuckets(resp, this.accessToken);

    resp.getWriter().println("</body>");
    resp.getWriter().println("</html>");
  }

  private void createBucket(HttpServletResponse resp, String bucketName) throws IOException {
    if (bucketName == null || bucketName.isEmpty()) {
      return;
    }
    URLFetchService service = URLFetchServiceFactory.getURLFetchService();
    URL url = new URL("https://" + bucketName + ".commondatastorage.googleapis.com");
    HTTPRequest request =
        new HTTPRequest(url, HTTPMethod.PUT, FetchOptions.Builder.doNotFollowRedirects()
            .setDeadline(10.0));
    HTTPHeader authHeader = new HTTPHeader("Authorization", "OAuth " + this.accessToken);
    HTTPHeader lengthHeader = new HTTPHeader("Content-Length", "0");
    HTTPHeader versionHeader = new HTTPHeader("x-goog-api-version", "2");
    HTTPHeader projectIdHeader = new HTTPHeader("x-goog-project-id", PROJECT_ID);

    request.setHeader(authHeader);
    request.setHeader(lengthHeader);
    request.setHeader(versionHeader);
    request.setHeader(projectIdHeader);

    HTTPResponse response = service.fetch(request);
    resp.getWriter().println(new String(response.getContent()));
  }

  private void listMyBuckets(HttpServletResponse resp, String token) throws IOException {
    URLFetchService service = URLFetchServiceFactory.getURLFetchService();
    URL url = new URL("https://commondatastorage.googleapis.com");
    HTTPRequest request =
        new HTTPRequest(url, HTTPMethod.GET, FetchOptions.Builder.doNotFollowRedirects()
            .setDeadline(10.0));
    HTTPHeader authHeader = new HTTPHeader("Authorization", "OAuth " + this.accessToken);
    HTTPHeader lengthHeader = new HTTPHeader("Content-Length", "0");
    HTTPHeader versionHeader = new HTTPHeader("x-goog-api-version", "2");
    HTTPHeader projectIdHeader = new HTTPHeader("x-goog-project-id", PROJECT_ID);

    request.setHeader(authHeader);
    request.setHeader(lengthHeader);
    request.setHeader(versionHeader);
    request.setHeader(projectIdHeader);

    HTTPResponse response = service.fetch(request);

    Document doc;
    try {
      DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
      InputStream is = new ByteArrayInputStream(response.getContent());
      doc = dBuilder.parse(is);
      doc.getDocumentElement().normalize();
      NodeList nList = doc.getElementsByTagName("Bucket");
      for (int temp = 0; temp < nList.getLength(); temp++) {
        Node nNode = nList.item(temp);  
        Element eElement = (Element) nNode;
        resp.getWriter().println("<div>" + getTagValue("Name", eElement) + "</div>");
      }
    } catch (SAXException e) {
      e.printStackTrace();
    } catch (ParserConfigurationException e) {
      e.printStackTrace();
    }
  }
  
  private static String getTagValue(String sTag, Element eElement){
    NodeList nlList= eElement.getElementsByTagName(sTag).item(0).getChildNodes();
    Node nValue = (Node) nlList.item(0); 
    return nValue.getNodeValue();    
 }

  private String getGsToken() throws IOException {
    try {
      String jwtString = "";
      jwtString = createJsonTokenForScope(BIGSTORE_SCOPE);
      String token = this.doTokenExchange(jwtString);
      return token;
    } catch (SignatureException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
    throw new IOException();
  }

  private String doTokenExchange(String jwt) throws IOException {
    URLFetchService service = URLFetchServiceFactory.getURLFetchService();
    URL url = new URL(GOOGLE_TOKEN_ENDPOINT);
    HTTPRequest request =
        new HTTPRequest(url, HTTPMethod.POST, FetchOptions.Builder.doNotFollowRedirects()
            .setDeadline(10.0));
    HTTPHeader header = new HTTPHeader("Content-Type", "application/x-www-form-urlencoded");
    request.setHeader(header);

    String payload = "grant_type=assertion&assertion_type=";
    payload += SignedJsonAssertionToken.GRANT_TYPE_VALUE;
    payload += "&assertion=";
    payload += jwt;
    request.setPayload(payload.getBytes());
    HTTPResponse response = service.fetch(request);
    JsonParser parser = new JsonParser();
    System.err.println("token response: " + new String(response.getContent()));
    String token =
        parser.parse(new String(response.getContent())).getAsJsonObject().get("access_token")
            .getAsString();
    return token;
  }

  private String createJsonTokenForScope(String scope) throws SignatureException {
    AppEngineSigner signer = new AppEngineSigner(this.serviceAccountName, "");
    SignedJsonAssertionToken jwt = new SignedJsonAssertionToken(signer);
    jwt.setAudience(GOOGLE_TOKEN_ENDPOINT);
    jwt.setScope(scope);
    return jwt.serializeAndSign();
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
