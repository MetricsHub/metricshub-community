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
 * Pins the legacy shape of a <code>commandLine</code> source result (issue #1509): the raw text is the filtered
 * command output joined with line feeds, with no trailing <code>;</code> added, and the table is each line split
 * with <code>String.split(";")</code>, which drops the trailing empty cells and turns a blank line into a row holding
 * one empty cell. Deleted in stage 4 of #1508 with the behavior it pins.
 */
class CommandLineSourceProcessorLegacyTest {

	@Test
	void rawTextKeepsLinesAsIsAndTableDropsTrailingEmptyCells() throws Exception {
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

		assertEquals("a;b;;\n\nc;d\n;e", result.getRawData());
		assertEquals(List.of(List.of("a", "b"), List.of(""), List.of("c", "d"), List.of("", "e")), result.getTable());
	}
}
