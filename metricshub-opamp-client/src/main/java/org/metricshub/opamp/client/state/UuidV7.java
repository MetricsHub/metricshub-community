package org.metricshub.opamp.client.state;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub OpAMP Client
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2026 MetricsHub
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.UUID;

/**
 * Minimal UUID version 7 (time-ordered) generator, as recommended by the OpAMP specification for
 * the agent {@code instance_uid} (16 bytes).
 */
public final class UuidV7 {

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private UuidV7() {}

	/**
	 * Generates a new UUIDv7 as a 16-byte array: 48-bit big-endian Unix timestamp in
	 * milliseconds, followed by random bits with the version (7) and IETF variant bits set.
	 *
	 * @return a new 16-byte UUIDv7
	 */
	public static byte[] generate() {
		final byte[] bytes = new byte[16];
		SECURE_RANDOM.nextBytes(bytes);

		final long timestampMs = System.currentTimeMillis();
		bytes[0] = (byte) (timestampMs >>> 40);
		bytes[1] = (byte) (timestampMs >>> 32);
		bytes[2] = (byte) (timestampMs >>> 24);
		bytes[3] = (byte) (timestampMs >>> 16);
		bytes[4] = (byte) (timestampMs >>> 8);
		bytes[5] = (byte) timestampMs;

		// Version 7 in the high nibble of byte 6
		bytes[6] = (byte) ((bytes[6] & 0x0F) | 0x70);
		// IETF variant (10xxxxxx) in the two high bits of byte 8
		bytes[8] = (byte) ((bytes[8] & 0x3F) | 0x80);

		return bytes;
	}

	/**
	 * Converts a 16-byte UUID to its canonical 36-character string representation.
	 *
	 * @param uuidBytes the 16-byte UUID
	 * @return the canonical string representation (lowercase, dashed)
	 */
	public static String toCanonicalString(final byte[] uuidBytes) {
		if (uuidBytes == null || uuidBytes.length != 16) {
			throw new IllegalArgumentException("A UUID must be exactly 16 bytes long");
		}
		final ByteBuffer buffer = ByteBuffer.wrap(uuidBytes);
		return new UUID(buffer.getLong(), buffer.getLong()).toString();
	}

	/**
	 * Parses a canonical 36-character UUID string into its 16-byte representation.
	 *
	 * @param canonical the canonical UUID string
	 * @return the 16-byte UUID
	 */
	public static byte[] fromCanonicalString(final String canonical) {
		final UUID uuid = UUID.fromString(canonical.trim());
		final ByteBuffer buffer = ByteBuffer.allocate(16);
		buffer.putLong(uuid.getMostSignificantBits());
		buffer.putLong(uuid.getLeastSignificantBits());
		return buffer.array();
	}
}
