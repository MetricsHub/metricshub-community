package org.metricshub.extension.jdbc.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DriverLoaderTest {

	@Test
	void testLoadValidDriver() {
		DriverLoader driverLoader = DriverLoader.getInstance();

		// Test loading a valid driver (H2 in this case)
		assertDoesNotThrow(() -> {
			driverLoader.loadDriver("org.h2.Driver", true);
		});
	}

	@Test
	void testLoadInvalidDriverThrowsClassNotFoundException() {
		DriverLoader driverLoader = DriverLoader.getInstance();

		// Test loading an invalid driver (which should throw ClassNotFoundException)
		assertThrows(
			ClassNotFoundException.class,
			() -> {
				driverLoader.loadDriver("com.invalid.Driver", true);
			}
		);
	}
}
