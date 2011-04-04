package com.google.demo.appidentity;

import com.google.appengine.api.appidentity.AppIdentityService;
import com.google.appengine.api.appidentity.AppIdentityServiceFactory;
import com.google.appengine.api.appidentity.PublicCertificate;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.Collection;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class Public_certs_javaServlet extends HttpServlet {
  // TODO:
  public static String APP_SERVICE_ACCOUNT_NAME = "app-identity-java@appspot.com";

  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse resp) {
    AppIdentityService service = AppIdentityServiceFactory.getAppIdentityService();
    Collection<PublicCertificate> certs = service.getPublicCertificatesForApp();
    resp.setContentType("application/json");
    JsonObject output = new JsonObject();
    for (PublicCertificate cert : certs) {
      output.addProperty(cert.getCertificateName(), cert.getX509CertificateInPemFormat());
    }
    try {
      resp.getWriter().println(new Gson().toJson(output));
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}