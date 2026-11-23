package org.metricshub.extension.oscommand;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.extension.oscommand.CommandLineSourceProcessorTest.CONNECTOR_ID;
import static org.metricshub.extension.oscommand.CommandLineSourceProcessorTest.newTelemetryManager;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.identity.criterion.CommandLineCriterion;
import org.metricshub.engine.strategy.detection.CriterionTestResult;

class CommandLineCriterionProcessorTest {

	/**
	 * The job deadline expires while the command runs: throwing the {@link InterruptedException} cleared the interrupt,
	 * which the processor restores so that the engine stops the detection instead of running its next criteria.
	 */
	@Test
	void interruptedCommandKeepsTheInterrupt() throws Exception {
		final OsCommandService osCommandService = mock(OsCommandService.class);
		doThrow(new InterruptedException()).when(osCommandService)
			.runOsCommand(any(), any(), any(), anyBoolean(), anyBoolean(), any());

		try {
			final CriterionTestResult result = new CommandLineCriterionProcessor(CONNECTOR_ID, osCommandService).process(
				CommandLineCriterion.builder().commandLine("cmd").expectedResult("ok").build(),
				newTelemetryManager()
			);

			assertFalse(result.isSuccess());
			assertTrue(Thread.currentThread().isInterrupted());
		} finally {
			Thread.interrupted();
		}
	}
}
