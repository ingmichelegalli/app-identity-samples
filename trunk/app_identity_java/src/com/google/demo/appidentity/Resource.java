package com.google.demo.appidentity;

import com.google.common.collect.Lists;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.oauth.jsontoken.AudienceChecker;
import net.oauth.jsontoken.JsonTokenParser;
import net.oauth.jsontoken.crypto.RsaSHA256Verifier;
import net.oauth.jsontoken.crypto.SignatureAlgorithm;
import net.oauth.jsontoken.crypto.Verifier;
import net.oauth.jsontoken.discovery.VerifierProvider;
import net.oauth.jsontoken.discovery.VerifierProviders;

import org.apache.commons.codec.binary.Base64;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class Resource extends HttpServlet {
  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String jwt = req.getParameter("jwt");
    String publicCertUrl = App_identity_javaServlet.getPublicCertUrl();
    boolean verified = verify(jwt, publicCertUrl);

    resp.setContentType("text/plain");
    if (verified) {
      resp.getWriter().println("Access OK");
    } else {
      resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
    }
  }

  public boolean verify(String jwt, String publicCertUrl) {
    VerifierProviders providers = new VerifierProviders();
    providers.setVerifierProvider(SignatureAlgorithm.RS256, new myKeyLocator(publicCertUrl));
    JsonTokenParser parser = new JsonTokenParser(providers, new MyAudienceChecker());
    if (jwt == null || jwt.isEmpty()) {
      return false;
    }

    try {
      parser.verifyAndDeserialize(jwt);
    } catch (SignatureException e) {
      return false;
    }
    return true;
  }

  static class myKeyLocator implements VerifierProvider {

    private final String publicCertUrl;

    public myKeyLocator(String publicCertUrl) {
      this.publicCertUrl = publicCertUrl;
    }

    @Override
    public List<Verifier> findVerifier(String issuer, String keyId) {
      try {
        URL url = new URL(publicCertUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
          
          InputStreamReader in = new InputStreamReader((InputStream) connection.getContent());
          BufferedReader buff = new BufferedReader(in);
          StringBuffer content = new StringBuffer();
          String line = "";
          do {
            line = buff.readLine();
            content.append(line + "\n");
          } while (line != null);
          
          JsonParser parser = new JsonParser();
          JsonObject jsonObject = parser.parse(content.toString()).getAsJsonObject();
          List<Verifier> verifiers = Lists.newArrayList();
          
          for (Map.Entry<String, JsonElement> cert : jsonObject.entrySet()) {
            String x509PemCertString = cert.getValue().getAsString();
            // Parse pem format
            String[] parts = x509PemCertString.split("\n");
            if (parts.length < 3) {
              System.err.println("bad cert: " + x509PemCertString);
              return null;
            }
            String x509CertString = "";
            for (int i = 1; i < parts.length - 1; i++) {
              x509CertString += parts[i];
            }
            // parse x509
            byte[] certBytes = Base64.decodeBase64(x509CertString);
            CertificateFactory factory = CertificateFactory.getInstance("X509");
            X509Certificate x509Cert = 
              (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(certBytes));
            verifiers.add(new RsaSHA256Verifier(x509Cert.getPublicKey()));
          }
          return verifiers;  
        } else {
          return null;
        }
      } catch (MalformedURLException e) {
        return null;
      } catch (IOException e) {
        return null;
      } catch (CertificateException e) {
        return null;
      }
    }
  }

  static class MyAudienceChecker implements AudienceChecker {
    @Override
    public void checkAudience(String arg0) throws SignatureException {
      // pass
    }
  }
}
