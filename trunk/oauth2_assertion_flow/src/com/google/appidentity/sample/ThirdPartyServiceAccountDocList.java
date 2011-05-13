package com.google.appidentity.sample;

import java.io.IOException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author jcai@google.com (Jian Cai)
 *
 */
public class ThirdPartyServiceAccountDocList extends HttpServlet {
  
  public static final String EXTERNAL_ACCOUNT_NAME = "robot@testing.gserviceaccount.com";
    
  public ThirdPartyServiceAccountDocList() {
  }
  
  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    doGet(req, resp);
  }

  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    // currently ask me for registration.
    DocListImpl impl = new DocListImpl("/doclist-3rd", EXTERNAL_ACCOUNT_NAME);
    impl.service(req, resp);
  }
}
