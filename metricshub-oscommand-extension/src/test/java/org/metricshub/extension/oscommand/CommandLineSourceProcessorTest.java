package org.metricshub.extension.oscommand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
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

	@Test
	void textKeepsLinesAsIsAndTableIsTheCanonicalConversion() throws Exception {
		final String connectorId = "connector";
		final ConnectorStore connectorStore = new ConnectorStore();
		connectorStore.addOne(connectorId, Connector.builder().embeddedFiles(Map.of()).build());
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostConfiguration(
				HostConfiguration.builder().hostname("localhost").hostId("localhost").hostType(DeviceKind.LINUX).build()
			)
			.hostProperties(HostProperties.builder().build())
			.connectorStore(connectorStore)
			.build();

		final OsCommandService osCommandService = mock(OsCommandService.class);
		doReturn(new OsCommandResult("a;b;;\n\nc;d\n;e\n", "cmd")).when(osCommandService)
			.runOsCommand(any(), any(), any(), anyBoolean(), anyBoolean(), any());

		final SourceTable result = new CommandLineSourceProcessor(osCommandService).process(
			CommandLineSource.builder().commandLine("cmd").build(),
			connectorId,
			telemetryManager
		);

		assertEquals(SourceTable.ofText("a;b;;\n\nc;d\n;e"), result);
		assertEquals(List.of(List.of("a", "b", ""), List.of("c", "d"), List.of("", "e")), result.asTable());
	}
}
