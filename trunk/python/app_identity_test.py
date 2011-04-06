#!/usr/bin/env python
# Copyright 2011 Google Inc.
# Jian Cai(jcai@google.com)

import logging
import base64
import time
from django.utils import simplejson as json
from google.appengine.ext import db
from google.appengine.ext import webapp
from google.appengine.ext.webapp.util import run_wsgi_app
from google.appengine.api import oauth

from django.utils import simplejson

from google.appengine.api import urlfetch
import app_identity
import urllib

GOOGLE_TOKEN_ENDPOINT = "https://accounts.google.com/accounts/o8/oauth2/token"
AUDIENCE = GOOGLE_TOKEN_ENDPOINT

SIG_ALG = "RS256"
SCOPE = "https://docs.google.com/feeds/"


# TODO: Don't hardcode this when app service account name is exposed via API.
# Service account name can be constructed by:
# for Consumer app: <your app display id>@appspot.com
# for Google apps app: <your app display id>@<domain name>.a.appspot.com
#
# NOTICE: format are subject to change.
APP_SERVICE_ACCOUNT_NAME = 'app-identity-python@appspot.com'

# valid for 15 mins
NOW = time.time()
NOT_BEFORE = str(NOW)
NOT_AFTER =  str(NOW + 60 * 15)

RESOURCE_URL = "http://app-identity-java.appspot.com/resource"

class MainPage(webapp.RequestHandler):
  def buildjwt(self):
    #rpc = app_identity.create_rpc()
    #app_identity.make_get_app_service_account_name_call(rpc)
    #rpc.wait()
    #app_service_account_name = rpc.get_result()
    jsonpayload = ("{\"iss\":\""
                   + APP_SERVICE_ACCOUNT_NAME
                   + "\",\"nonce\":\"nonce\",\"aud\":\""
                   + AUDIENCE
                   + "\",\"scope\":\""
                   + SCOPE
                   +"\",\"iat\":"
                   + NOT_BEFORE
                   + ",\"exp\":"
                   + NOT_AFTER
                   + "}"
                   )
    header = ("{\"alg\":\""
              + SIG_ALG
              + "\",\"kid\":\""
              + ''
              + "\"}"
              )
    basesignaturestring = (base64.urlsafe_b64encode(header)
                           + "."
                           + base64.urlsafe_b64encode(jsonpayload)
                           )
    rpc = app_identity.create_rpc()
    app_identity.make_sign_blob_call(rpc, basesignaturestring)
    rpc.wait()
    result = rpc.get_result()
    keyid = result[0]
    sig = result[1]
    signedjsontoken = (basesignaturestring
                       + "."
                       + base64.urlsafe_b64encode(sig))
    return signedjsontoken

  def get(self):
    self.response.headers['Content-Type'] = 'text/html'
    self.response.out.write(r'<html><head></head><body>')
    signedjsontoken = self.buildjwt()
    self.response.out.write("<b>Generate JWT:</b> " + signedjsontoken + "\n")
    self.response.out.write("<p><b>Access resource at: </b>" + RESOURCE_URL + "</p>")

    url = RESOURCE_URL + "?" + urllib.urlencode(
        [("jwt", signedjsontoken),
         ("certurl", "http://app-identity-python.appspot.com/certs")])
    self.response.out.write("<p><b>Send http request to: </b>" + url + " </p>")
    result = urlfetch.fetch(url)
    if result.status_code == 200:
      self.response.out.write("<b>Result: </b>" + result.content)
    elif result.status_code == 403:
      self.response.out.write("<p><b>Access denied, go to http://app-identity-java.appspot.com/ to grant access, public certificates of your app can be found at http://http://app-identity-python.appspot.com/certs</b> </p>")
    else:
      self.response.out.write("<p><b>Access denied, go to http://app-identity-java.appspot.com/ to grant access, public certificates of your app can be found at http://http://app-identity-python.appspot.com/certs</b> </p>")

    self.response.out.write(r'</body></html>')

class CertsPage(webapp.RequestHandler):
  def buildjwt(self):
    rpc = app_identity.create_rpc()
    sig = result[1]
    signedjsontoken = (basesignaturestring
                       + "."
                       + base64.urlsafe_b64encode(sig))
    return signedjsontoken

  def get(self):
    certs = app_identity.get_public_certificates()
    self.response.headers['Content-Type'] = 'application/json'
    cert_map = {}
    for cert in certs:
      cert_map[cert.key_name] = cert.x509_certificate_pem
    self.response.out.write(simplejson.dumps(cert_map))

application = webapp.WSGIApplication(
    [('/', MainPage), ('/certs', CertsPage)],
    debug=True)

def main():
  run_wsgi_app(application)

if __name__ == "__main__":
  main()
