package com.google.demo.appidentity;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;

import net.oauth.jsontoken.AudienceChecker;
import net.oauth.jsontoken.JsonTokenParser;
import net.oauth.jsontoken.crypto.SignatureAlgorithm;
import net.oauth.jsontoken.discovery.UrlBasedVerifierProvider;
import net.oauth.jsontoken.discovery.VerifierProviders;

import java.io.IOException;
import java.security.SignatureException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class Resource extends HttpServlet {
  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String jwt = req.getParameter("jwt");
    String certUrl = req.getParameter("certurl");
    boolean verified = verify(jwt, certUrl);

    resp.setContentType("text/html");
    resp.getWriter().println("<html><head>");
    resp.getWriter().println("<title>App Identity API Demo</title>");
    resp.getWriter().println("</head><body>");
    
    if (verified) {
      resp.getWriter().println("Access Succeed, http://app-identity-java.appspot.com/resource confirmed I am <b>" + certUrl + "</b>");
    } else {
      resp.getWriter().println("403 Authentication fails");
      
      resp.getWriter().println(
          "<p> <a href=\"" 
          + "http://app-identity-java.appspot.com/access"
          + "\">"
          + "Access this page from http://app-identity-java.appspot.com/access"
          + "</a></p>");
      
      resp.getWriter().println(
          "<p> <a href=\"" 
          + "http://app-identity-python.appspot.com"
          + "\">"
          + "Access this page from http://app-identity-python.appspot.com"
          + "</a></p>");
      
      resp.getWriter().println(
          "<div>" + "The resource page was been last accessed by: <b>" + getAccessInfo() + "</b></div></p>");
      
      resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
    }
    
    resp.getWriter().println("</body>");
    
    resp.getWriter().println("</html>");
  }

  public boolean verify(String jwt, String publicCertUrl) {
    if (publicCertUrl == null || publicCertUrl.isEmpty()) {
      return false;
    }
    if (jwt == null || jwt.isEmpty()) {
      return false;
    }

    VerifierProviders providers = new VerifierProviders();
    providers.setVerifierProvider(
        SignatureAlgorithm.RS256, new UrlBasedVerifierProvider(publicCertUrl));
    JsonTokenParser parser = new JsonTokenParser(providers, new MyAudienceChecker());

    try {
      parser.verifyAndDeserialize(jwt);
    } catch (SignatureException e) {
      System.err.println(e.getMessage());
      return false;
    }
    storeAccessInfo(publicCertUrl);
    return true;
  }

  static class MyAudienceChecker implements AudienceChecker {
    @Override
    public void checkAudience(String arg0) throws SignatureException {
      // pass
    }
  }

  static public String getAccessInfo() {
    Key certKey = KeyFactory.createKey("LastAccess", "LastAccess");
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    try {
      Entity entity = datastore.get(certKey);
      if (entity != null) {
        return (String) entity.getProperty("name");
      } else {
        return "";
      }
    } catch (EntityNotFoundException e) {
      return "";
    }
  }

  static public void storeAccessInfo(String certUrl) {
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    Entity entity = new Entity("LastAccess", "LastAccess");
    entity.setProperty("name", certUrl);
    datastore.put(entity);
  }
}
