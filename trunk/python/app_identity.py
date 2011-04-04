#!/usr/bin/env python
#
# Copyright 2007 Google Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#





"""Provides access functions for the app identity service."""









from google.appengine.api import apiproxy_stub_map
import app_identity_service_pb
from google.appengine.runtime import apiproxy_errors

__all__ = ["BackendDeadlineExceeded",
           "BlobSizeTooLarge",
           "InternalError",
           "Error",
           "create_rpc",
           "make_sign_blob_call",
           "make_get_public_certificates_call",
           "make_get_service_account_name_call",
           "sign_blob",
           "get_public_certificates",
           "PublicCertificate",
           "get_service_account_name"
          ]


_APP_IDENTITY_SERVICE_NAME = "app_identity_service"
_SIGN_FOR_APP_METHOD_NAME = "SignForApp"
_GET_CERTS_METHOD_NAME = "GetPublicCertificatesForApp"
_GET_SERVICE_ACCOUNT_NAME_METHOD_NAME = "GetServiceAccountName"


class Error(Exception):
  """Base error type."""


class BackendDeadlineExceeded(Error):
  """Communication to backend service timed-out."""


class BlobSizeTooLarge(Error):
  """Size of blob to sign is larger than the allowed limit."""


class InternalError(Error):
  """Unspecified internal failure."""


def _to_app_identity_error(error):
  """Translate an application error to an external Error, if possible.

  Args:
    error: An ApplicationError to translate.

  Returns:
    error: app identity API specific error message.
  """
  error_map = {
      app_identity_service_pb.AppIdentityServiceError.NOT_A_VALID_APP:
      InternalError,
      app_identity_service_pb.AppIdentityServiceError.DEADLINE_EXCEEDED:
      BackendDeadlineExceeded,
      app_identity_service_pb.AppIdentityServiceError.BLOB_TOO_LARGE:
      BlobSizeTooLarge,
      app_identity_service_pb.AppIdentityServiceError.UNKNOWN_ERROR:
      InternalError,
      }
  if error.application_error in error_map:
    return error_map[error.application_error](error.error_detail)
  else:
    return error


class PublicCertificate(object):
  """Info about public certificate.

  Attributes:
    key_name: name of the certificate.
    x509_certificate_pem: x509 cerficiates in pem format.
  """

  def __init__(self, key_name, x509_certificate_pem):
    self.key_name = key_name
    self.x509_certificate_pem = x509_certificate_pem


def create_rpc(deadline=None, callback=None):
  """Creates an RPC object for use with the App identity API.

  Args:
    deadline: Optional deadline in seconds for the operation; the default
      is a system-specific deadline (typically 5 seconds).
    callback: Optional callable to invoke on completion.

  Returns:
    An apiproxy_stub_map.UserRPC object specialized for this service.
  """
  return apiproxy_stub_map.UserRPC(_APP_IDENTITY_SERVICE_NAME,
                                   deadline, callback)


def make_sign_blob_call(rpc, bytes_to_sign):
  """Executes the RPC call to sign a blob.

  Args:
    rpc: a UserRPC instance.
    bytes_to_sign: blob that needs to be signed.

  Returns:
    A tuple that contains signature and signing key name.

  Raises:
    TypeError: when bytes_to_sign is not a str.
  """
  if not isinstance(bytes_to_sign, str):
    raise TypeError("bytes_to_sign must be str: %s"
                    % bytes_to_sign)
  request = app_identity_service_pb.SignForAppRequest()
  request.set_bytes_to_sign(bytes_to_sign)
  response = app_identity_service_pb.SignForAppResponse()

  if rpc.deadline is not None:
    request.set_deadline(rpc.deadline)

  def signing_for_app_result(rpc):
    """Check success, handle exceptions, and return converted RPC result.

    This method waits for the RPC if it has not yet finished, and calls the
    post-call hooks on the first invocation.

    Args:
      rpc: A UserRPC object.

    Returns:
      A tuple that contains signing key name and signature.
    """
    assert rpc.service == _APP_IDENTITY_SERVICE_NAME, repr(rpc.service)
    assert rpc.method == _SIGN_FOR_APP_METHOD_NAME, repr(rpc.method)
    try:
      rpc.check_success()
    except apiproxy_errors.ApplicationError, err:
      raise _to_app_identity_error(err)

    return (response.key_name(), response.signature_bytes())


  rpc.make_call(_SIGN_FOR_APP_METHOD_NAME, request,
                response, signing_for_app_result)


def make_get_public_certificates_call(rpc):
  """Executes the RPC call to get a list of public certificates.

  Args:
    rpc: a UserRPC instance.

  Returns:
    A list of PublicCertificate object.
  """
  request = app_identity_service_pb.GetPublicCertificateForAppRequest()
  response = app_identity_service_pb.GetPublicCertificateForAppResponse()

  if rpc.deadline is not None:
    request.set_deadline(rpc.deadline)

  def get_certs_result(rpc):
    """Check success, handle exceptions, and return converted RPC result.

    This method waits for the RPC if it has not yet finished, and calls the
    post-call hooks on the first invocation.

    Args:
      rpc: A UserRPC object.

    Returns:
      A list of PublicCertificate object.
    """
    assert rpc.service == _APP_IDENTITY_SERVICE_NAME, repr(rpc.service)
    assert rpc.method == _GET_CERTS_METHOD_NAME, repr(rpc.method)
    try:
      rpc.check_success()
    except apiproxy_errors.ApplicationError, err:
      raise _to_app_identity_error(err)
    result = []
    for cert in response.public_certificate_list_list():
      result.append(PublicCertificate(
          cert.key_name(), cert.x509_certificate_pem()))
    return result


  rpc.make_call(_GET_CERTS_METHOD_NAME, request, response, get_certs_result)


def make_get_service_account_name_call(rpc):
  """Get service account name of the app.

  Args:
    deadline: Optional deadline in seconds for the operation; the default
      is a system-specific deadline (typically 5 seconds).

  Returns:
    Service account name of the app.
  """
  request = app_identity_service_pb.GetServiceAccountNameRequest()
  response = app_identity_service_pb.GetServiceAccountNameResponse()

  if rpc.deadline is not None:
    request.set_deadline(rpc.deadline)

  def get_service_account_name_result(rpc):
    """Check success, handle exceptions, and return converted RPC result.

    This method waits for the RPC if it has not yet finished, and calls the
    post-call hooks on the first invocation.

    Args:
      rpc: A UserRPC object.

    Returns:
      A string which is service account name of the app.
    """
    assert rpc.service == _APP_IDENTITY_SERVICE_NAME, repr(rpc.service)
    assert rpc.method == _GET_SERVICE_ACCOUNT_NAME_METHOD_NAME, repr(rpc.method)
    try:
      rpc.check_success()
    except apiproxy_errors.ApplicationError, err:
      raise _to_app_identity_error(err)

    return response.service_account_name()


  rpc.make_call(_GET_SERVICE_ACCOUNT_NAME_METHOD_NAME, request,
                response, get_service_account_name_result)


def sign_blob(bytes_to_sign, deadline=None):
  """Signs a blob.

  Args:
    bytes_to_sign: blob that needs to be signed.
    deadline: Optional deadline in seconds for the operation; the default
      is a system-specific deadline (typically 5 seconds).

  Returns:
    signature and signing key name.
  """
  rpc = create_rpc(deadline)
  make_sign_blob_call(rpc, bytes_to_sign)
  rpc.wait()
  return rpc.get_result()


def get_public_certificates(deadline=None):
  """Get public certificates.

  Args:
    deadline: Optional deadline in seconds for the operation; the default
      is a system-specific deadline (typically 5 seconds).

  Returns:
    A list of PublicCertificate object.
  """
  rpc = create_rpc(deadline)
  make_get_public_certificates_call(rpc)
  rpc.wait()
  return rpc.get_result()


def get_service_account_name(deadline=None):
  """Get service account name of the app.

  Args:
    deadline: Optional deadline in seconds for the operation; the default
      is a system-specific deadline (typically 5 seconds).

  Returns:
    Service account name of the app.
  """
  rpc = create_rpc(deadline)
  make_get_service_account_name_call(rpc)
  rpc.wait()
  return rpc.get_result()
