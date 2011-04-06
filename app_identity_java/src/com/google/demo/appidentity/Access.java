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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.SignatureException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class Access extends HttpServlet {
  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    resp.setContentType("text/html");
    resp.getWriter().println("<html><head>");
    resp.getWriter().println("<title>App Identity API Demo</title>");
    resp.getWriter().println("</head><body>");

    resp.getWriter().println("<div id=createbox><form action=/access method=post>"
        + "Input your resource page URL:<input type=text name=resource_url value = \"http://app-identity-java.appspot.com/resource\" size=100 />" + ""
        + "<input type=submit name=access value=\"Access\">" + "");

    String urlString = req.getParameter("resource_url");
    
    if (urlString != null) {
      URL url;
      urlString += "?jwt=";
      try {
        urlString += App_identity_javaServlet.createJsonToken();
      } catch (SignatureException e) {
        e.printStackTrace();
      }
      urlString += "&certurl=";
      urlString += URLEncoder.encode("http://app-identity-java.appspot.com/certs");
      resp.getWriter().println("<p>" + "<b>Send request to: </b>" + urlString + "</p>");
      
      try {
        url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10 * 1000);
        connection.setReadTimeout(10 * 1000);
        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
          InputStreamReader in = new InputStreamReader((InputStream) connection.getContent());
          BufferedReader buff = new BufferedReader(in);
          StringBuffer content = new StringBuffer();
          String line = "";
          do {
            content.append(line + "\n");
            line = buff.readLine();
          } while (line != null);
          
          resp.getWriter().println("<p>" + "<b>Result: </b>" + content.toString() + "</p>");
        } else if (connection.getResponseCode() == HttpURLConnection.HTTP_FORBIDDEN) {
          resp.getWriter().println(
              "<p>" + "<b>Access denied </b>" + Resource.getAccessInfo() + "</p>");
        } else {
          resp.getWriter().println("<b><p>" + "Unknown error </b>" + "</p>");
        }
      } catch (MalformedURLException e) {
        resp.getWriter().println("<p> <b>" + "Invalid URl </b>" + "</p>");
        return;
      }
    }
    
    resp.getWriter().println(
        "<p> <a href=\"" 
        + "/" 
        + "\">"
        + "Back to main page" 
        + "</a></p>");

    resp.getWriter().println("</body>");
    resp.getWriter().println("</html>");
  }
  
  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    doGet(req, resp);
  }
}
