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
import com.google.gdata.client.DocumentQuery;
import com.google.gdata.client.docs.DocsService;
import com.google.gdata.data.PlainTextConstruct;
import com.google.gdata.data.acl.AclEntry;
import com.google.gdata.data.acl.AclFeed;
import com.google.gdata.data.acl.AclRole;
import com.google.gdata.data.acl.AclScope;
import com.google.gdata.data.docs.DocumentEntry;
import com.google.gdata.data.docs.DocumentListEntry;
import com.google.gdata.data.docs.DocumentListFeed;
import com.google.gdata.data.docs.PresentationEntry;
import com.google.gdata.data.docs.SpreadsheetEntry;
import com.google.gdata.util.ServiceException;
import com.google.gson.JsonParser;

import net.oauth.jsontoken.crypto.AbstractSigner;
import net.oauth.jsontoken.crypto.SignatureAlgorithm;
import net.oauth.signatures.SignedJsonAssertionToken;

import java.io.IOException;
import java.net.URL;
import java.security.SignatureException;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author jcai@google.com (Jian Cai)
 *
 */
public class DocListImpl {
  
  public static String GOOGLE_TOKEN_ENDPOINT =
    "https://accounts.google.com/o/oauth2/token";
  
  public static String GOOGLE_AUTH_ENDPOINT =
    "https://accounts.google.com/o/oauth2/auth";
  
  public static String SANDBOX_GOOGLE_TOKEN_ENDPOINT =
    "https://sandbox.google.com/o/oauth2/token";
  
  public static String SANDBOX_GOOGLE_AUTH_ENDPOINT =
    "https://sandbox.google.com/o/oauth2/auth";

  public static String SPREADSHETT_SCOPE = "https://spreadsheets.google.com/feeds/";

  public static String DOCLIST_SCOPE = "https://docs.google.com/feeds/";

  public static String CONTACT_SCOPE = "https://www.google.com/m8/feeds/contacts/default/full";
  
  private final String accessToken;
  
  private final String serviceAccountName;
  
  private final String servletPath;
  
  public DocListImpl(String servletPath, String serviceAccountName) throws IOException {
    this.serviceAccountName = serviceAccountName;
    this.servletPath = servletPath;
    accessToken = getDoclistToken();
  }
  
  public void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String title = req.getParameter("title");
    String shareList = req.getParameter("sharelist");
    boolean docs = req.getParameter("cdocs") == null ? false : true;
    boolean spreadsheet = req.getParameter("cspreadsheet") == null ? false : true;
    boolean presentation = req.getParameter("cpresentation") == null ? false : true;
    try {
      this.createADocument(resp,
          title,
          shareList,
          docs,
          spreadsheet,
          presentation);
    } catch (ServiceException e) {
      e.printStackTrace();
      return;
    }

    resp.setContentType("text/html");
    resp.getWriter().println("<html><head>");
    resp.getWriter().println("<title>App Identity API Demo</title>");
    resp.getWriter().println("</head><body>");
    resp.getWriter().println("<h1>A Robot's Doclist...(only latest 10 Documents are listed)</h1>");
    resp.getWriter()
        .println(
            "<div id=createbox><form action=" + this.servletPath + " method=post>"
                + "<b>Title: </b><input type=text name=title size=60 />"
                + "<b>Share with(list of email address separated by comma): "
                    + "</b><input type=text name=sharelist size=100 />" 
                    + "<div>" + "<input type=submit name=cdocs value=\"Create A Document\">" 
                    + "<input type=submit name=cspreadsheet value=\"Create A Spreadsheet\">" 
                    + "<input type=submit name=cpresentation value=\"Create A Presentation\">" 
                    + "</div>" + "</div>");

    resp.getWriter().println("<div><b>Documents I own:</b>" + "</div>");

    try {
      this.listMyDocs(resp, this.accessToken);
    } catch (ServiceException e) {
      e.printStackTrace();
      return;
    }

    resp.getWriter().println("</body>");
    resp.getWriter().println("</html>");
  }

  private void listMyDocs(HttpServletResponse resp, String token)
      throws IOException, ServiceException {
    DocsService docsService = new DocsService("robotdoclist");
    docsService.setAuthSubToken(token);
    DocumentQuery query =
        new DocumentQuery(new URL("https://docs.google.com/feeds/default/private/full"));
    // Get Everything
    DocumentListFeed allEntries = new DocumentListFeed();
    DocumentListFeed tempFeed = docsService.getFeed(query, DocumentListFeed.class);

    while (tempFeed.getEntries().size() > 0) {
      allEntries.getEntries().addAll(tempFeed.getEntries());
      if (tempFeed.getNextLink() != null) {
        tempFeed =
            docsService.getFeed(new URL(tempFeed.getNextLink().getHref()), DocumentListFeed.class);
      } else {
        tempFeed = new DocumentListFeed();
      }
    }

    int count = 0;
    for (DocumentListEntry entry : allEntries.getEntries()) {
      if (count++ > 10) {
        break;
      }
      String acls = "";
      AclFeed aclFeed =
          docsService.getFeed(new URL(entry.getAclFeedLink().getHref()), AclFeed.class);
      List<AclEntry> aclEntries = aclFeed.getEntries();
      for (AclEntry aclEntry : aclEntries) {
        // skip the robot itself.
        if (!aclEntry.getScope().getValue().equals(this.serviceAccountName)) {
          acls = acls + aclEntry.getScope().getValue() + " ";
        }
      }

      resp.getWriter().println(
          "<div><b>Title: </b>" + entry.getTitle().getPlainText() + " <b>Type: </b>"
              + entry.getType() + " <b>Link:</b><a href=" + entry.getDocumentLink().getHref() + ">"
              + entry.getDocumentLink().getHref() + "</a>" + " <b>Share with: </b>" + acls
              + "</div>");
    }
  }

  private String getDoclistToken() throws IOException {
    try {
      String jwtString = "";
      jwtString = createJsonTokenForScope(DOCLIST_SCOPE);
      System.err.println("jwt: " + jwtString);
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
        new HTTPRequest(url, HTTPMethod.POST, FetchOptions.Builder.doNotFollowRedirects().setDeadline(10.0));
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
    String token = parser
        .parse(new String(response.getContent()))
        .getAsJsonObject()
        .get("access_token")
        .getAsString();
    return token;
  }

  private String createJsonTokenForScope(String scope)
      throws SignatureException {
    AppEngineSigner signer = new AppEngineSigner(this.serviceAccountName, "");
    SignedJsonAssertionToken jwt = new SignedJsonAssertionToken(signer);
    jwt.setAudience(GOOGLE_TOKEN_ENDPOINT);
    jwt.setScope(scope);
    return jwt.serializeAndSign();
  }

  private void createADocument(HttpServletResponse resp,
      String title,
      String sharelist,
      boolean docs,
      boolean spreadsheet,
      boolean presentation) throws IOException, ServiceException {
    DocsService docsService = new DocsService("robotdoclist");
    DocumentListEntry createdEntry;
    if (docs) {
      docsService.setAuthSubToken(this.accessToken);
      createdEntry = createNewDocument(docsService, title, "document");
    } else if (spreadsheet) {
      docsService.setAuthSubToken(this.accessToken);
      createdEntry = createNewDocument(docsService, title, "spreadsheet");
    } else if (presentation) {
      docsService.setAuthSubToken(this.accessToken);
      createdEntry = createNewDocument(docsService, title, "presentation");
    } else {
      return;
    }

    String[] emailList = sharelist.split(",");
    for (String email : emailList) {
      boolean validEmail = false;
      if (email.indexOf("@") != 0 && email.indexOf("@") != email.length()) {
        validEmail = true;
      }
      if (validEmail) {
        AclRole role = new AclRole("writer");
        AclScope scope = new AclScope(AclScope.Type.USER, email.trim());
        AclEntry aclEntry = new AclEntry();
        aclEntry.setRole(role);
        aclEntry.setScope(scope);
        docsService.insert(new URL(createdEntry.getAclFeedLink().getHref()), aclEntry);
      }
    }
  }

  private DocumentListEntry createNewDocument(DocsService client, String title, String type)
      throws IOException, ServiceException {
    DocumentListEntry newEntry = null;
    if (type.equals("document")) {
      newEntry = new DocumentEntry();
    } else if (type.equals("presentation")) {
      newEntry = new PresentationEntry();
    } else if (type.equals("spreadsheet")) {
      newEntry = new SpreadsheetEntry();
    }
    newEntry.setTitle(new PlainTextConstruct(title));
    // newEntry.setWritersCanInvite(false);
    // newEntry.setHidden(true);
    return client.insert(new URL("https://docs.google.com/feeds/default/private/full/"), newEntry);
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
