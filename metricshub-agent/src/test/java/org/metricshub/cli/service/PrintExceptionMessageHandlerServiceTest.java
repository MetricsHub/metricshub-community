package org.metricshub.cli.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class PrintExceptionMessageHandlerServiceTest {

	@Test
	void testHandleExecutionException() {
		final CommandLine commandLine = new CommandLine(new MetricsHubCliService());
		assertDoesNotThrow(() -> {
			return new PrintExceptionMessageHandlerService()
				.handleExecutionException(new Exception("message 2", new Exception("message 1")), commandLine, null);
		});
	}
}
