package org.springframework.security.web.webauthn.registration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.webauthn.api.registration.PublicKeyCredentialUserEntity;
import org.springframework.security.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.webauthn.management.UserCredential;
import org.springframework.security.webauthn.management.UserCredentialRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class DefaultRegistrationPageGeneratingFilter extends OncePerRequestFilter {

	private RequestMatcher matcher = AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/webauthn/register");

	private final PublicKeyCredentialUserEntityRepository userEntities;

	private final UserCredentialRepository userCredentials;

	public DefaultRegistrationPageGeneratingFilter(PublicKeyCredentialUserEntityRepository userEntities, UserCredentialRepository userCredentials) {
		this.userEntities = userEntities;
		this.userCredentials = userCredentials;
	}

	@Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
		if (!this.matcher.matches(request)) {
			filterChain.doFilter(request, response);
			return;
		}

		boolean success = request.getParameterMap().containsKey("success");
		CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
		response.setContentType(MediaType.TEXT_HTML_VALUE);
		Map<String, Object> context = new HashMap<>();
		context.put("contextPath", request.getContextPath());
		context.put("csrfToken", csrfToken.getToken());
		context.put("csrfParameterName", csrfToken.getParameterName());
		context.put("csrfHeaderName", csrfToken.getHeaderName());
		context.put("message", success ? SUCCESS_MESSAGE : "");
		context.put("passkeys", passkeyRows(request.getRemoteUser(), context));
		response.getWriter().write(processTemplate(HTML_TEMPLATE, context));
	}

	private String passkeyRows(String username, Map<String,Object> baseContext) {
		PublicKeyCredentialUserEntity userEntity = this.userEntities.findByUsername(username);
		List<UserCredential> credentials = userEntity == null ? Collections.emptyList() : this.userCredentials.findByUserId(userEntity.getId());
		if (credentials.isEmpty()) {
			return """
				<tr><td colspan="4">No Passkeys</td></tr>
				""";
		}
		String html = "";
		for (UserCredential credential : credentials) {
			Map<String, Object> context = new HashMap<>(baseContext);
			context.put("label", credential.getLabel());
			context.put("created", credential.getCreated());
			context.put("lastUsed", credential.getLastUsed());
			context.put("credentialId", credential.getCredentialId().getBytesAsBase64());
			html += processTemplate(PASSKEY_ROW_TEMPLATE, context);
		}
		return html;
	}

	private String processTemplate(String template, Map<String,Object> context) {
		for (Map.Entry<String, Object> entry : context.entrySet()) {
			String pattern = Pattern.quote("${" + entry.getKey() + "}");
			String value = String.valueOf(entry.getValue());
			template = template.replaceAll(pattern, value);
		}
		return template;
	}

	private static final String HTML_TEMPLATE = """
		<html>
			<head>
				<meta charset="utf-8">
				<meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no">
				<meta name="description" content="">
				<meta name="author" content="">
				<title>WebAuthn Registration</title>
				<link href="https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0-beta/css/bootstrap.min.css" rel="stylesheet" integrity="sha384-/Y6pD6FV/Vv2HJnA6t+vslU6fwYXjCFtcEpHbNJ0lyAFsXTsjBbfaDjzALeQsN6M" crossorigin="anonymous">
				<link href="https://getbootstrap.com/docs/4.0/examples/signin/signin.css" rel="stylesheet" crossorigin="anonymous"/>
				<script src="https://unpkg.com/@simplewebauthn/browser@8.3.1/dist/bundle/index.umd.min.js"></script>
				<script type="text/javascript" id="registration-script" data-csrf-token="${csrfToken}" data-csrf-header-name="${csrfHeaderName}">
				<!--
					document.addEventListener("DOMContentLoaded", function(event) {
						setup()
					});
					function setVisibility(elmt, value) {
						elmt.style.display = value ? 'block' : 'none'
					}
					function setup() {
						const config = document.getElementById('registration-script').dataset
						const csrfToken = config.csrfToken
						const csrfHeaderName = config.csrfHeaderName
						const { startRegistration } = SimpleWebAuthnBrowser;
						// <button>
						const elemBegin = document.getElementById('register');
						// <span>/<p>/etc...
						const elemSuccess = document.getElementById('success');
						// <span>/<p>/etc...
						const elemError = document.getElementById('error');
						setVisibility(elemSuccess, false)
						setVisibility(elemError, false)

						// Start registration when the user clicks a button
						elemBegin.addEventListener('click', async () => {
							// Reset success/error messages
							setVisibility(elemSuccess, false)
							setVisibility(elemError, false)
							elemSuccess.innerHTML = '';
							elemError.innerHTML = '';

							const label = document.getElementById('label').value
							if (!label) {
								setVisibility(elemError, true)
								elemError.innerText = 'Error: Passkey Label is required'
								return;
							}

							// GET registration options from the endpoint that calls
							// @simplewebauthn/server -> generateRegistrationOptions()
							const resp = await fetch('${contextPath}/webauthn/register/options');

							let attResp;
							try {
								// Pass the options to the authenticator and wait for a response
								const options = await resp.json()
								attResp = await startRegistration(options);
							} catch (error) {
								// FIXME: For error handling see https://www.w3.org/TR/webauthn-3/#sctn-op-make-cred
								// Some basic error handling
								setVisibility(elemError, true)
								if (error.name === 'InvalidStateError') {
									elemError.innerText = 'Error: Authenticator was probably already registered by user';
								} else {
									elemError.innerText = error;
								}
								throw error;
							}
							const registrationRequest = {
								"publicKey": {
									"credential": attResp,
									"label": label,
								}
							}
							const registrationRequestJSON = JSON.stringify(registrationRequest, null, 2)
							console.log(registrationRequestJSON)

							// POST the response to the endpoint that calls
							// @simplewebauthn/server -> verifyRegistrationResponse()
							const verificationResp = await fetch('${contextPath}/webauthn/register', {
								method: 'POST',
								headers: {
									'Content-Type': 'application/json',
									[csrfHeaderName]: csrfToken,
								},
								body: registrationRequestJSON,
							});

							// Wait for the results of verification
							const verificationJSON = await verificationResp.json();
			
							// Show UI appropriate for the `verified` status
							if (verificationJSON && verificationJSON.verified) {
								window.location.href = '${contextPath}/webauthn/register?success'
							} else {
								setVisibility(elemError, true)
								elemError.innerHTML = `Oh no, something went wrong! Response: <pre>${JSON.stringify(verificationJSON,null,2)}</pre>`;
							}
						});
					}
				//-->
				</script>
			</head>
			<body>
				<div class="container">
					<form class="form-signin" method="post" action="#" onclick="return false">
						<h2 class="form-signin-heading">WebAuthn Registration</h2>
						${message}
						<div id="success" class="alert alert-success" role="alert"></div>
						<div id="error" class="alert alert-danger" role="alert"></div>
						<p>
							<input type="text" id="label" name="label" class="form-control" placeholder="Passkey Label" required autofocus>
						</p>
						<button id="register" class="btn btn-lg btn-primary btn-block" type="submit">Register</button>
					</form>
					<table class="table table-striped">
						<thead>
							<tr><th>Label</th><th>Created</th><th>Last used</th><th>Delete</th></tr>
						</thead>
						<tbody>
							${passkeys}
						</tbody>
					</table>
				</div>
			</body>
		</html>
""";

	private static final String PASSKEY_ROW_TEMPLATE = """
		<tr>
			<td>${label}</td>
			<td>${created}</td>
			<td>${lastUsed}</td>
			<td>
				<form method="post" action="${contextPath}/webauthn/register/${credentialId}">
					<input type="hidden" name="method" value="delete">
					<input type="hidden" name="${csrfParameterName}" value="${csrfToken}">
					<button class="btn btn-sm btn-primary btn-block" type="submit">Delete</button>
				</form>
			</td>
		</tr>
	""";

	private static final String SUCCESS_MESSAGE = """
 		<div class="alert alert-success" role="alert">Success!</div>
	""";
}