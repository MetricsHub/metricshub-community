package org.metricshub.cli.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class ConsoleServiceTest {

	@Test
	void testHasConsole() {
		assertDoesNotThrow(() -> ConsoleService.hasConsole());
	}
}
