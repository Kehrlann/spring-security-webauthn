/*
 * Copyright 2002-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

const base64url = {
  encode: function(buffer) {
    const base64 = window.btoa(String.fromCharCode(...new Uint8Array(buffer)));
    return base64.replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  },
  decode: function(base64url) {
    const base64 = base64url.replace(/-/g, '+').replace(/_/g, '/');
    const binStr = window.atob(base64);
    const bin = new Uint8Array(binStr.length);
    for (let i = 0; i < binStr.length; i++) {
      bin[i] = binStr.charCodeAt(i);
    }
    return bin.buffer;
  }
}

async function conditionalMediation(headers, contextPath) {
  const available = await isConditionalMediationAvailable()
  if (available) {
    authenticate(headers, contextPath, true)
  }
  return available
}

async function isConditionalMediationAvailable() {
  return window.PublicKeyCredential &&
    PublicKeyCredential.isConditionalMediationAvailable &&
    await PublicKeyCredential.isConditionalMediationAvailable()
}

async function post(headers, url, body) {
  const options = {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...headers
    },
  }
  if (body) {
    options.body = JSON.stringify(body)
  }
  return fetch(url,options)
}

async function authenticate(headers, contextPath, useConditionalMediation) {
  const abortController = new AbortController()
  // FIXME: add contextRoot
  const options = await post(headers, `${contextPath}/webauthn/authenticate/options`).then(r => r.json());
  // FIXME: Use https://www.w3.org/TR/webauthn-3/#sctn-parseRequestOptionsFromJSON
  options.challenge = base64url.decode(options.challenge);

  // Invoke the WebAuthn get() method.
  const credentialOptions = {
    publicKey: options,
    signal: abortController.signal,
  }
  if (useConditionalMediation) {
    // Request a conditional UI
    credentialOptions.mediation = 'conditional'
  }
  const cred = await navigator.credentials.get(credentialOptions);
  const { response, credType } = cred;
  let userHandle = undefined;
  if (response.userHandle) {
    userHandle = base64url.encode(response.userHandle);
  }
  const body = {
    id: cred.id,
    rawId: base64url.encode(cred.rawId),
    response: {
      authenticatorData: base64url.encode(response.authenticatorData),
      clientDataJSON: base64url.encode(response.clientDataJSON),
      signature: base64url.encode(response.signature),
      userHandle,
    },
    credType,
    clientExtensionResults: cred.getClientExtensionResults(),
    authenticatorAttachment: cred.authenticatorAttachment,
  };

  // FIXME: add contextRoot
  // POST the response to the endpoint that calls
  const authenticationResponse = await fetch(`${contextPath}/login/webauthn`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...headers
      },
      body: JSON.stringify(body),
    }
  )
    .then(response => {
      if (response.ok) {
        return response.json()
      } else {
        return { 'errorUrl': '/login?error' }
      }
    });

  // Show UI appropriate for the `verified` status
  if (authenticationResponse && authenticationResponse.authenticated) {
    window.location.href = authenticationResponse.redirectUrl;
  } else {
    window.location.href = authenticationResponse.errorUrl
  }
}

async function setup(headers, contextPath, signinButton) {
  await conditionalMediation(headers, contextPath);

  // Start authentication when the user clicks a button
  signinButton.addEventListener('click',() => authenticate(headers, contextPath, false));
}