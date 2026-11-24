package org.metricshub.extension.oscommand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.monitor.task.source.CommandLineSource;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.strategy.utils.OsCommandResult;
import org.metricshub.engine.telemetry.HostProperties;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * Pins the shape of a <code>commandLine</code> source result: a text, the filtered command output joined with line
 * feeds, with no trailing <code>;</code> added. A reader of it as a table gets the canonical conversion, which keeps
 * the empty cells but the one after a trailing <code>;</code> and drops the blank lines (before stage 4 of #1508, the
 * source also held a table split with <code>String.split(";")</code>, which dropped every trailing empty cell and
 * turned a blank line into a row holding one empty cell).
 */
class CommandLineSourceProcessorTest {

	static final String CONNECTOR_ID = "connector";

	@Test
	void textKeepsLinesAsIsAndTableIsTheCanonicalConversion() throws Exception {
		final SourceTable result = process("a;b;;\n\nc;d\n;e\n", CommandLineSource.builder().commandLine("cmd").build());

		assertEquals(SourceTable.ofText("a;b;;\n\nc;d\n;e"), result);
		assertEquals(List.of(List.of("a", "b", ""), List.of("c", "d"), List.of("", "e")), result.asTable());
	}

	/**
	 * A host that answers with CRLF gives the text of a host that answers with LF: the lines are filtered and their
	 * columns selected without the carriage return, which a column selection would otherwise move inside the line.
	 */
	@Test
	void crlfOutputGivesLfTextAndColumnsWithoutCarriageReturn() throws Exception {
		assertEquals(
			"a;b\nc;d",
			process("a;b\r\nc;d\r\n", CommandLineSource.builder().commandLine("cmd").build()).getText()
		);
		assertEquals(
			"b;a\nd;c",
			process(
				"a;b\r\nc;d\r\n",
				CommandLineSource.builder().commandLine("cmd").separators(";").selectColumns("2,1").build()
			).getText()
		);
	}

	/**
	 * The job deadline expires while the command runs: throwing the {@link InterruptedException} cleared the interrupt,
	 * which the processor restores so that the engine stops the job instead of running its next commands.
	 */
	@Test
	void interruptedCommandKeepsTheInterrupt() throws Exception {
		final OsCommandService osCommandService = mock(OsCommandService.class);
		doThrow(new InterruptedException()).when(osCommandService)
			.runOsCommand(any(), any(), any(), anyBoolean(), anyBoolean(), any());

		try {
			final SourceTable result = new CommandLineSourceProcessor(osCommandService).process(
				CommandLineSource.builder().commandLine("cmd").build(),
				CONNECTOR_ID,
				newTelemetryManager()
			);

			assertTrue(result.isEmpty());
			assertTrue(Thread.currentThread().isInterrupted());
		} finally {
			Thread.interrupted();
		}
	}

	/**
	 * Runs a <code>commandLine</code> source on a host whose command prints the given output.
	 *
	 * @param output The output of the command
	 * @param source The source
	 * @return the result of the source
	 */
	private static SourceTable process(final String output, final CommandLineSource source) throws Exception {
		final OsCommandService osCommandService = mock(OsCommandService.class);
		doReturn(new OsCommandResult(output, "cmd")).when(osCommandService)
			.runOsCommand(any(), any(), any(), anyBoolean(), anyBoolean(), any());

		return new CommandLineSourceProcessor(osCommandService).process(source, CONNECTOR_ID, newTelemetryManager());
	}

	/**
	 * A telemetry manager for a Linux localhost holding a connector without embedded files.
	 *
	 * @return a new telemetry manager
	 */
	static TelemetryManager newTelemetryManager() {
		final ConnectorStore connectorStore = new ConnectorStore();
		connectorStore.addOne(CONNECTOR_ID, Connector.builder().embeddedFiles(Map.of()).build());
		return TelemetryManager.builder()
			.hostConfiguration(
				HostConfiguration.builder().hostname("localhost").hostId("localhost").hostType(DeviceKind.LINUX).build()
			)
			.hostProperties(HostProperties.builder().build())
			.connectorStore(connectorStore)
			.build();
	}
}
