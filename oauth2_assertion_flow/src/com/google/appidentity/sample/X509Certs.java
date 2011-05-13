package com.google.appidentity.sample;

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

/**
 * @author jcai@google.com (Jian Cai)
 */
public class X509Certs extends HttpServlet {
  
  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    AppIdentityService service = AppIdentityServiceFactory.getAppIdentityService();
    Collection<PublicCertificate> certs = service.getPublicCertificatesForApp();
    Gson gson = new Gson();
    JsonObject jsonObject = new JsonObject();
    for (PublicCertificate cert : certs) {
      jsonObject.addProperty(cert.getCertificateName(), cert.getX509CertificateInPemFormat());
    }
    resp.setContentType("application/json");
    resp.getWriter().write(new Gson().toJson(jsonObject));
    resp.setHeader("Cache-Control", "max-age=3600");
  }
}
