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
		assertTrue(result.isPresent(), "Expected 'cpu' to be a valid monitor type");
		assertEquals(KnownMonitorType.CPU, result.get(), "Expected 'cpu' to match KnownMonitorType.CPU");

		// Test with a valid monitor type string in different case
		result = KnownMonitorType.fromString("MeMoRy");
		assertTrue(result.isPresent(), "Expected 'MeMoRy' to be a known monitor type");
		assertEquals(KnownMonitorType.MEMORY, result.get(), "Expected 'MeMoRy' to match KnownMonitorType.MEMORY");

		// Test with an invalid monitor type string
		result = KnownMonitorType.fromString("net");
		assertFalse(result.isPresent(), "Expected 'net' to not be a known monitor type");

		// Test with a valid monitor type string in upper case
		result = KnownMonitorType.fromString("PHYSICAL_DISK");
		assertTrue(result.isPresent(), "Expected 'PHYSICAL_DISK' to be a known monitor type");
		assertEquals(
			KnownMonitorType.PHYSICAL_DISK,
			result.get(),
			"Expected 'PHYSICAL_DISK' to match KnownMonitorType.PHYSICAL_DISK"
		);

		// Test with null input
		result = KnownMonitorType.fromString(null);
		assertFalse(result.isPresent(), "Expected null to not be a known monitor type");

		// Test with empty string input
		result = KnownMonitorType.fromString("");
		assertFalse(result.isPresent(), "Expected empty string to not be a known monitor type");
	}

	@Test
	void testIsConnector() {
		// Test with a connector type
		assertTrue(KnownMonitorType.isConnector("connector"), "Expected 'connector' to be a connector type");

		// Test with a non-connector type
		assertFalse(KnownMonitorType.isConnector("cpu"), "Expected 'cpu' to not be a connector type");
		assertFalse(KnownMonitorType.isConnector("memory"), "Expected 'memory' to not be a connector type");
	}
}
