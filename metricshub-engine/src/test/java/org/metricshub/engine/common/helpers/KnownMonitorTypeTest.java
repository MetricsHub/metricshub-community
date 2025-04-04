package org.metricshub.engine.common.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class KnownMonitorTypeTest {

	@Test
	void testFromString() {
		// Test with a valid monitor type string
		Optional<KnownMonitorType> result = KnownMonitorType.fromString("cpu");
		assertTrue(result.isPresent());
		assertEquals(KnownMonitorType.CPU, result.get());

		// Test with a valid monitor type string in different case
		result = KnownMonitorType.fromString("MeMoRy");
		assertTrue(result.isPresent());
		assertEquals(KnownMonitorType.MEMORY, result.get());

		// Test with an invalid monitor type string
		result = KnownMonitorType.fromString("net");
		assertFalse(result.isPresent());

		// Test with a valid monitor type string in upper case
		result = KnownMonitorType.fromString("PHYSICAL_DISK");
		assertTrue(result.isPresent());
		assertEquals(KnownMonitorType.PHYSICAL_DISK, result.get());

		// Test with null input
		result = KnownMonitorType.fromString(null);
		assertFalse(result.isPresent());

		// Test with empty string input
		result = KnownMonitorType.fromString("");
		assertFalse(result.isPresent());
	}
}
