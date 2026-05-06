package org.metricshub.cli.service.protocol;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link EmulationConfigCli} option enablement.
 */
class EmulationConfigCliTest {

	@Test
	void testIsEnabledWithoutOptions() {
		final EmulationConfigCli emulationConfigCli = new EmulationConfigCli();
		assertFalse(emulationConfigCli.isEnabled());
	}

	@Test
	void testIsEnabledWithWbemOption() {
		final EmulationConfigCli emulationConfigCli = new EmulationConfigCli();
		emulationConfigCli.setEmulateWbem("C:/recordings/wbem");
		assertTrue(emulationConfigCli.isEnabled());
	}
}
