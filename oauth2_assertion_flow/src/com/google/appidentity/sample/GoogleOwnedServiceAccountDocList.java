package com.google.appidentity.sample;

import com.google.appengine.api.appidentity.AppIdentityService;
import com.google.appengine.api.appidentity.AppIdentityServiceFactory;

import java.io.IOException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author jcai@google.com (Jian Cai)
 *
 */
public class GoogleOwnedServiceAccountDocList extends HttpServlet {
    
  private String serviceAccountName;
  
  public GoogleOwnedServiceAccountDocList() {
    AppIdentityService service = AppIdentityServiceFactory.getAppIdentityService();
    serviceAccountName = service.getServiceAccountName();
  }
  
  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    doGet(req, resp);
  }

  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    DocListImpl impl = new DocListImpl("/doclist-google", this.serviceAccountName);
    impl.service(req, resp);
  }
}
