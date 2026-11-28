package org.metricshub.extension.jawk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.monitor.task.source.JawkSource;
import org.metricshub.engine.strategy.source.SourceProcessor;
import org.metricshub.engine.strategy.source.SourceResult;
import org.metricshub.engine.telemetry.HostProperties;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * An awk source reads the constants, the connector variables and the earlier text sources of its job by name (#1684).
 */
@ExtendWith(MockitoExtension.class)
class JawkSourceInjectionTest {

	private static final String CONNECTOR_ID = "connectorId";

	@Mock
	private SourceProcessor sourceProcessorMock;

	@Test
	void connectorValuesReachTheScript() {
		final ConnectorStore connectorStore = new ConnectorStore();
		connectorStore.getStore()
			.put(
				CONNECTOR_ID,
				Connector.builder().constants(Map.of("BLOCK_SIZE", "512")).variableValues(Map.of("scale", "3")).build()
			);
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostConfiguration(HostConfiguration.builder().hostname("test-host").hostId("test-host").build())
			.hostProperties(HostProperties.builder().build())
			.connectorStore(connectorStore)
			.build();
		doReturn(Map.of("model", "X1")).when(sourceProcessorMock).getTextSources();

		final JawkSource source = JawkSource.builder()
			.type("awk")
			.script("BEGIN { print BLOCK_SIZE * scale \";\" model }")
			.build();

		assertEquals(
			SourceResult.ofText("1536;X1\n"),
			new JawkSourceExtension().processSource(source, CONNECTOR_ID, telemetryManager, sourceProcessorMock)
		);
	}
}
