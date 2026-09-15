package org.metricshub.opamp.client.state;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UuidV7Test {

	@Test
	void generateShouldProduceValidUuidV7() {
		final long beforeMs = System.currentTimeMillis();
		final byte[] uuid = UuidV7.generate();
		final long afterMs = System.currentTimeMillis();

		assertEquals(16, uuid.length);
		// Version nibble must be 7
		assertEquals(0x70, uuid[6] & 0xF0);
		// IETF variant: two high bits of byte 8 must be 10
		assertEquals(0x80, uuid[8] & 0xC0);

		// The first 6 bytes carry the Unix timestamp in milliseconds
		long timestampMs = 0;
		for (int i = 0; i < 6; i++) {
			timestampMs = (timestampMs << 8) | (uuid[i] & 0xFF);
		}
		assertTrue(timestampMs >= beforeMs && timestampMs <= afterMs);
	}

	@Test
	void generateShouldProduceUniqueValues() {
		final Set<String> seen = new HashSet<>();
		for (int i = 0; i < 1000; i++) {
			assertTrue(seen.add(UuidV7.toCanonicalString(UuidV7.generate())));
		}
	}

	@Test
	void canonicalStringConversionShouldRoundTrip() {
		final byte[] uuid = UuidV7.generate();
		final String canonical = UuidV7.toCanonicalString(uuid);
		assertEquals(36, canonical.length());
		assertArrayEquals(uuid, UuidV7.fromCanonicalString(canonical));
	}

	@Test
	void toCanonicalStringShouldRejectInvalidLength() {
		assertThrows(IllegalArgumentException.class, () -> UuidV7.toCanonicalString(new byte[8]));
		assertThrows(IllegalArgumentException.class, () -> UuidV7.toCanonicalString(null));
	}

	@Test
	void fromCanonicalStringShouldRejectGarbage() {
		assertThrows(IllegalArgumentException.class, () -> UuidV7.fromCanonicalString("not-a-uuid"));
		assertFalse(UuidV7.toCanonicalString(UuidV7.generate()).isEmpty());
	}
}
