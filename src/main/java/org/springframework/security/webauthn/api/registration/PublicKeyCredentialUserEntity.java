package org.springframework.security.webauthn.api.registration;

import org.springframework.security.webauthn.api.core.BufferSource;

// https://www.w3.org/TR/webauthn-3/#dictdef-publickeycredentialuserentity
public class PublicKeyCredentialUserEntity {
	/**
	 * When inherited by PublicKeyCredentialUserEntity, it is a human-palatable identifier for a user account. It is
	 * intended only for display, i.e., aiding the user in determining the difference between user accounts with similar
	 * displayNames. For example, "alexm", "alex.mueller@example.com" or "+14255551234".
	 *
	 * The Relying Party MAY let the user choose this value. The Relying Party SHOULD perform enforcement, as prescribed
	 * in Section 3.4.3 of [RFC8265] for the UsernameCasePreserved Profile of the PRECIS IdentifierClass [RFC8264], when
	 * setting name's value, or displaying the value to the user.
	 *
	 * This string MAY contain language and direction metadata. Relying Parties SHOULD consider providing this
	 * information. See § 6.4.2 Language and Direction Encoding about how this metadata is encoded.
	 *
	 * Clients SHOULD perform enforcement, as prescribed in Section 3.4.3 of [RFC8265] for the UsernameCasePreserved
	 * Profile of the PRECIS IdentifierClass [RFC8264], on name's value prior to displaying the value to the user or
	 * including the value as a parameter of the authenticatorMakeCredential operation.
	 */
	private final String name;

	/**
	 * The user handle of the user account entity. A user handle is an opaque byte sequence with a maximum size of 64
	 * bytes, and is not meant to be displayed to the user.
	 *
	 * To ensure secure operation, authentication and authorization decisions MUST be made on the basis of this id
	 * member, not the displayName nor name members. See Section 6.1 of [RFC8266].
	 *
	 * The user handle MUST NOT contain personally identifying information about the user, such as a username or e-mail
	 * address; see § 14.6.1 User Handle Contents for details. The user handle MUST NOT be empty, though it MAY be
	 * null.
	 *
	 * Note: the user handle ought not be a constant value across different accounts, even for non-discoverable
	 * credentials, because some authenticators always create discoverable credentials. Thus a constant user handle
	 * would prevent a user from using such an authenticator with more than one account at the Relying Party.
	 */
	private final BufferSource id;

	/**
	 * A human-palatable name for the user account, intended only for display. For example, "Alex Müller" or "田中倫".
	 * The Relying Party SHOULD let the user choose this, and SHOULD NOT restrict the choice more than necessary.
	 *
	 * Relying Parties SHOULD perform enforcement, as prescribed in Section 2.3 of [RFC8266] for the Nickname Profile of
	 * the PRECIS FreeformClass [RFC8264], when setting displayName's value, or displaying the value to the user.
	 *
	 * This string MAY contain language and direction metadata. Relying Parties SHOULD consider providing this
	 * information. See § 6.4.2 Language and Direction Encoding about how this metadata is encoded.
	 *
	 * Clients SHOULD perform enforcement, as prescribed in Section 2.3 of [RFC8266] for the Nickname Profile of the
	 * PRECIS FreeformClass [RFC8264], on displayName's value prior to displaying the value to the user or including the
	 * value as a parameter of the authenticatorMakeCredential operation.
	 *
	 * When clients, client platforms, or authenticators display a displayName's value, they should always use UI
	 * elements to provide a clear boundary around the displayed value, and not allow overflow into other elements
	 * [css-overflow-3].
	 *
	 * Authenticators MUST accept and store a 64-byte minimum length for a displayName member’s value. Authenticators
	 * MAY truncate a displayName member’s value so that it fits within 64 bytes. See § 6.4.1 String Truncation
	 * about truncation and other considerations.
	 */
	private final String displayName;

	public PublicKeyCredentialUserEntity(String name, BufferSource id, String displayName) {
		this.name = name;
		this.id = id;
		this.displayName = displayName;
	}

	public String getName() {
		return this.name;
	}

	public BufferSource getId() {
		return this.id;
	}

	public String getDisplayName() {
		return this.displayName;
	}

	public static PublicKeyCredentialUserEntityBuilder builder() {
		return new PublicKeyCredentialUserEntityBuilder();
	}

	public static final class PublicKeyCredentialUserEntityBuilder {
		private String name;
		private BufferSource id;
		private String displayName;

		private PublicKeyCredentialUserEntityBuilder() {
		}

		public PublicKeyCredentialUserEntityBuilder name(String name) {
			this.name = name;
			return this;
		}

		public PublicKeyCredentialUserEntityBuilder id(BufferSource id) {
			this.id = id;
			return this;
		}

		public PublicKeyCredentialUserEntityBuilder displayName(String displayName) {
			this.displayName = displayName;
			return this;
		}

		public PublicKeyCredentialUserEntity build() {
			return new PublicKeyCredentialUserEntity(name, id, displayName);
		}
	}
}