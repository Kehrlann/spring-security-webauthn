
package org.springframework.security.webauthn.api.core;

import java.security.SecureRandom;
import java.util.Base64;

// FIXME: Consider replacing with UrlBase64String

public class BufferSource {

	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

	private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

	private static final SecureRandom RANDOM = new SecureRandom();

	private final byte[] bytes;

	public BufferSource(byte[] bytes) {
		this.bytes = bytes;
	}

	public byte[] getBytes() {
		return this.bytes;
	}

	public String getBytesAsBase64() {
		return ENCODER.encodeToString(getBytes());
	}

	@Override
	public int hashCode() {
		return getBytesAsBase64().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof BufferSource buffer) {
			return buffer.getBytesAsBase64().equals(getBytesAsBase64());
		}
		return false;
	}

	public static BufferSource fromBase64(String base64String) {
		byte[] bytes = DECODER.decode(base64String);
		return new BufferSource(bytes);
	}

	public static BufferSource random() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return new BufferSource(bytes);
	}
}