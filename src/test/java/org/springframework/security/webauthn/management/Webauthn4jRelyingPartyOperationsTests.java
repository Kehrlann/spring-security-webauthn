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

package org.springframework.security.webauthn.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yubico.internal.util.JacksonCodecs;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.codec.Utf8;
import org.springframework.security.webauthn.api.*;

import java.util.Base64;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Webauthn4jRelyingPartyOperationsTests {
	private UserCredentialRepository credentials = new MapUserCredentialRepository();
	// AuthenticatorDataFlags.Bitmasks
	private static byte UP = 0x01;
	private static byte UV = 0x04;
	private static byte BE = 0x08;
	private static byte BS = 0x10;

	WebAuthnRelyingPartyOperations rpOperations = new Webauthn4JRelyingPartyOperations(new MapPublicKeyCredentialUserEntityRepository(), this.credentials, PublicKeyCredentialRpEntity.builder()
		.id("example.localhost")
		.name("Spring Security Relying Party")
		.build(),
		Set.of("https://example.localhost:8443"));

	String label = "Phone";

	/**
	 * https://www.w3.org/TR/webauthn-3/#sctn-registering-a-new-credential
	 *
	 * 7. Verify that the value of C.type is webauthn.create.
	 */
	@Test
	void registerCredentialWhenCTypeIsNotWebAuthn() {
		PublicKeyCredentialCreationOptions options = TestPublicKeyCredentialCreationOptions.createPublicKeyCredentialCreationOptions()
				.build();
		AuthenticatorAttestationResponse.AuthenticatorAttestationResponseBuilder responseBldr = TestAuthenticatorAttestationResponse.createAuthenticatorAttestationResponse();
		PublicKeyCredential publicKey = TestPublicKeyCredential.createPublicKeyCredential(responseBldr.build())
				.build();
		RelyingPartyRegistrationRequest registrationRequest = new RelyingPartyRegistrationRequest(options, new RelyingPartyPublicKey(publicKey, this.label));

		CredentialRecord credentialRecord = this.rpOperations.registerCredential(registrationRequest);
		String base64String = Base64.getUrlEncoder().encodeToString(credentialRecord.getPublicKey().getBytes());
		assertThat(base64String).isEqualTo("pQECAyYgASFYIOB5K59pGxpqWU3aA2VDa6aaPdzqoEFezjc1b6ORiwhXIlggq3-siEIIKtgX2Z7WsMUbAQW1hvxVpGAKFPMj4qUvuYY=");
	}

	/**
	 * https://www.w3.org/TR/webauthn-3/#sctn-registering-a-new-credential
	 *
	 * 8. Verify that the value of C.challenge equals the base64url encoding of options.challenge.
	 */
	@Test
	void registerCredentialWhenCChallengeNotEqualBase64UrlEncodingOptionsChallenge() {
		PublicKeyCredentialCreationOptions options = TestPublicKeyCredentialCreationOptions.createPublicKeyCredentialCreationOptions()
				.challenge(Base64Url.fromBase64("h0vgwGQjoCzAzDUsmzPpk-JVIJRRgn0L4KVSYNRcEZc"))
				.build();
		AuthenticatorAttestationResponse.AuthenticatorAttestationResponseBuilder responseBldr = TestAuthenticatorAttestationResponse.createAuthenticatorAttestationResponse();
		responseBldr.clientDataJSON(new Base64Url(Utf8.encode("{\"type\":\"webauthn.create\",\"challenge\":\"IBQnuY1Z0K1HqBoFWCp2xlJl8-oq_aFIXzyT_F0-0GU\",\"origin\":\"https://example.localhost:8080\",\"crossOrigin\":false}")));
		PublicKeyCredential publicKey = TestPublicKeyCredential.createPublicKeyCredential(responseBldr.build())
				.build();
		RelyingPartyRegistrationRequest registrationRequest = new RelyingPartyRegistrationRequest(options, new RelyingPartyPublicKey(publicKey, this.label));

		assertThatThrownBy(() -> this.rpOperations.registerCredential(registrationRequest)).hasMessageContaining("challenge");
	}

	/**
	 * https://www.w3.org/TR/webauthn-3/#sctn-registering-a-new-credential
	 *
	 * 9. Verify that the value of C.origin is an origin expected by the Relying Party. See § 13.4.9 Validating the origin of a credential for guidance.
	 */
	@Test
	void registerCredentialWhenCOriginNotExpected() {
		PublicKeyCredentialCreationOptions options = TestPublicKeyCredentialCreationOptions.createPublicKeyCredentialCreationOptions()
				.build();
		AuthenticatorAttestationResponse.AuthenticatorAttestationResponseBuilder responseBldr = TestAuthenticatorAttestationResponse.createAuthenticatorAttestationResponse();
		responseBldr.clientDataJSON(new Base64Url(Utf8.encode("{\"type\":\"webauthn.create\",\"challenge\":\"q7lCdd3SVQxdC-v8pnRAGEn1B2M-t7ZECWPwCAmhWvc\",\"origin\":\"https://example.com\",\"crossOrigin\":false}")));
		PublicKeyCredential publicKey = TestPublicKeyCredential.createPublicKeyCredential(responseBldr.build())
				.build();
		RelyingPartyRegistrationRequest registrationRequest = new RelyingPartyRegistrationRequest(options, new RelyingPartyPublicKey(publicKey, this.label));

		assertThatThrownBy(() -> this.rpOperations.registerCredential(registrationRequest)).hasMessageContaining("origin");
	}

	/**
	 * https://www.w3.org/TR/webauthn-3/#sctn-registering-a-new-credential
	 *
	 * 13. Verify that the rpIdHash in authData is the SHA-256 hash of the RP ID expected by the Relying Party.
	 */
	@Test
	void registerCredentialWhenClientDataJSONDoesNotMatchHash() {
		this.rpOperations = new Webauthn4JRelyingPartyOperations(new MapPublicKeyCredentialUserEntityRepository(), this.credentials, PublicKeyCredentialRpEntity.builder()
				.id("invalid")
				.name("Spring Security Relying Party")
				.build(),
				Set.of("https://example.localhost:8443"));
		PublicKeyCredentialCreationOptions options = TestPublicKeyCredentialCreationOptions.createPublicKeyCredentialCreationOptions()
				.rp(PublicKeyCredentialRpEntity.builder().id("invalid").name("Spring Security").build())
				.build();
		AuthenticatorAttestationResponse.AuthenticatorAttestationResponseBuilder responseBldr = TestAuthenticatorAttestationResponse.createAuthenticatorAttestationResponse();
		PublicKeyCredential publicKey = TestPublicKeyCredential.createPublicKeyCredential(responseBldr.build())
				.build();
		RelyingPartyRegistrationRequest registrationRequest = new RelyingPartyRegistrationRequest(options, new RelyingPartyPublicKey(publicKey, this.label));

		assertThatThrownBy(() -> this.rpOperations.registerCredential(registrationRequest)).hasMessageContaining("hash");
	}

	/**
	 * https://www.w3.org/TR/webauthn-3/#sctn-registering-a-new-credential
	 *
	 * 14. Verify that the UP bit of the flags in authData is set.
	 */
	@Test
	void registerCredentialWhenUPFlagsNotSet() throws Exception {
		PublicKeyCredentialCreationOptions options = TestPublicKeyCredentialCreationOptions.createPublicKeyCredentialCreationOptions()
				.build();

		PublicKeyCredential publicKey = TestPublicKeyCredential.createPublicKeyCredential(setFlag(UP))
				.build();
		RelyingPartyRegistrationRequest registrationRequest = new RelyingPartyRegistrationRequest(options, new RelyingPartyPublicKey(publicKey, this.label));

		assertThatThrownBy(() -> this.rpOperations.registerCredential(registrationRequest)).hasMessageContaining("User Presence");
	}

	/**
	 * https://www.w3.org/TR/webauthn-3/#sctn-registering-a-new-credential
	 *
	 * 15. If the Relying Party requires user verification for this registration, verify that the UV bit of the flags in authData is set.
	 */
	@Test
	void registerCredentialWhenUVBitNotSet() throws Exception {
		PublicKeyCredentialCreationOptions options = TestPublicKeyCredentialCreationOptions.createPublicKeyCredentialCreationOptions()
				.authenticatorSelection(AuthenticatorSelectionCriteria.builder()
					.userVerification(UserVerificationRequirement.REQUIRED)
					.build())
				.build();
		PublicKeyCredential publicKey = TestPublicKeyCredential.createPublicKeyCredential(setFlag(UV))
				.build();
		RelyingPartyRegistrationRequest registrationRequest = new RelyingPartyRegistrationRequest(options, new RelyingPartyPublicKey(publicKey, this.label));

		assertThatThrownBy(() -> this.rpOperations.registerCredential(registrationRequest)).hasMessageContaining("User Verification is required");
	}

	/**
	 * https://www.w3.org/TR/webauthn-3/#sctn-registering-a-new-credential
	 *
	 * 16. If the BE bit of the flags in authData is not set, verify that the BS bit is not set.
	 */
	@Test
	void registerCredentialWhenBENotSetAndBSSet() throws Exception {
		PublicKeyCredentialCreationOptions options = TestPublicKeyCredentialCreationOptions.createPublicKeyCredentialCreationOptions()
				.build();
		PublicKeyCredential publicKey = TestPublicKeyCredential.createPublicKeyCredential(setFlag(BE))
				.build();
		RelyingPartyRegistrationRequest registrationRequest = new RelyingPartyRegistrationRequest(options, new RelyingPartyPublicKey(publicKey, this.label));

		assertThatThrownBy(() -> this.rpOperations.registerCredential(registrationRequest)).hasMessageContaining("Flag combination is invalid");
	}

	/**
	 * https://www.w3.org/TR/webauthn-3/#sctn-registering-a-new-credential
	 *
	 * 17. If the Relying Party uses the credential’s backup eligibility to inform its user experience flows and/or policies, evaluate the BE bit of the flags in authData.
	 */
	@Test
	void registerCredentialWhenBEInformsUserExperienceBETrue() {
		// FIXME: Implement this
	}

	/**
	 * https://www.w3.org/TR/webauthn-3/#sctn-registering-a-new-credential
	 *
	 * 18. If the Relying Party uses the credential’s backup state to inform its user experience flows and/or policies, evaluate the BS bit of the flags in authData.
	 */
	@Test
	void registerCredentialWhenBSInformsUserExperienceBSTrue() {
		// FIXME: Search for AuthenticatorDataFlags.BS to implement
	}

	/**
	 * https://www.w3.org/TR/webauthn-3/#sctn-registering-a-new-credential
	 *
	 * 19. Verify that the "alg" parameter in the credential public key in authData matches the alg attribute of one of the items in options.pubKeyCredParams.
	 */
	@Test
	void registerCredentialWhenAlgDoesNotMatchOptions() {
		PublicKeyCredentialCreationOptions options = TestPublicKeyCredentialCreationOptions.createPublicKeyCredentialCreationOptions()
				.pubKeyCredParams(PublicKeyCredentialParameters.RS1)
				.build();
		PublicKeyCredential<AuthenticatorAttestationResponse> publicKey = TestPublicKeyCredential.createPublicKeyCredential()
				.build();
		RelyingPartyRegistrationRequest registrationRequest = new RelyingPartyRegistrationRequest(options, new RelyingPartyPublicKey(publicKey, this.label));

		assertThatThrownBy(() -> this.rpOperations.registerCredential(registrationRequest)).hasMessageContaining("Unrequested credential key algorithm");
	}

	/**
	 * https://www.w3.org/TR/webauthn-3/#sctn-registering-a-new-credential
	 *
	 * 20. Verify that the values of the client extension outputs in clientExtensionResults and the authenticator
	 * extension outputs in the extensions in authData are as expected, considering the client extension input values
	 * that were given in options.extensions and any specific policy of the Relying Party regarding unsolicited
	 * extensions, i.e., those that were not specified as part of options.extensions. In the general case, the meaning
	 * of "are as expected" is specific to the Relying Party and which extensions are in use.
	 */
	@Test
	void registerCredentialWhenClientExtensionOutputsDoNotMatch() {
		// FIXME: Implement this
	}

	/**
	 * https://www.w3.org/TR/webauthn-3/#reg-ceremony-verify-attestation
	 *
	 * 22. Verify that attStmt is a correct attestation statement, conveying a valid attestation signature, by using
	 * the attestation statement format fmt’s verification procedure given attStmt, authData and hash.
	 */
	@Test
	void registerCredentialWhenFmtNotValid() throws Exception {
		PublicKeyCredentialCreationOptions options = TestPublicKeyCredentialCreationOptions.createPublicKeyCredentialCreationOptions()
				.build();
		PublicKeyCredential publicKey = TestPublicKeyCredential.createPublicKeyCredential() //setFmt("packed")
				.build();
		RelyingPartyRegistrationRequest registrationRequest = new RelyingPartyRegistrationRequest(options, new RelyingPartyPublicKey(publicKey, this.label));

		// FIXME: Implement this test
//		assertThatThrownBy(() -> this.rpOperations.registerCredential(registrationRequest)).hasMessageContaining("Flag combination is invalid");
	}

	private static AuthenticatorAttestationResponse setFlag(byte flag) throws Exception {
		AuthenticatorAttestationResponse.AuthenticatorAttestationResponseBuilder authAttResponseBldr = TestAuthenticatorAttestationResponse.createAuthenticatorAttestationResponse();
		byte[] originalAttestationObjBytes = authAttResponseBldr.build().getAttestationObject().getBytes();
		ObjectMapper cbor = JacksonCodecs.cbor();
		ObjectNode attObj = (ObjectNode) cbor.readTree(originalAttestationObjBytes);
		byte[] rawAuthData = attObj.get("authData").binaryValue();

		rawAuthData[32] ^=  flag;
		JsonNodeFactory f = JsonNodeFactory.instance;
		byte[] updatedAttObjBytes = cbor.writeValueAsBytes(attObj.setAll(Map.of("authData", f.binaryNode(rawAuthData))));
		authAttResponseBldr.attestationObject(new Base64Url(updatedAttObjBytes)).authenticatorData(new Base64Url(rawAuthData));
		return authAttResponseBldr.build();
	}

	private static AuthenticatorAttestationResponse setFmt(String fmt) throws Exception {
		AuthenticatorAttestationResponse.AuthenticatorAttestationResponseBuilder authAttResponseBldr = TestAuthenticatorAttestationResponse.createAuthenticatorAttestationResponse();
		byte[] originalAttestationObjBytes = authAttResponseBldr.build().getAttestationObject().getBytes();
		ObjectMapper cbor = JacksonCodecs.cbor();
		ObjectNode attObj = (ObjectNode) cbor.readTree(originalAttestationObjBytes);
		JsonNodeFactory f = JsonNodeFactory.instance;
		byte[] updatedAttObjBytes = cbor.writeValueAsBytes(attObj.setAll(Map.of("fmt", f.textNode(fmt))));
		authAttResponseBldr.attestationObject(new Base64Url(updatedAttObjBytes));
		return authAttResponseBldr.build();
	}

}