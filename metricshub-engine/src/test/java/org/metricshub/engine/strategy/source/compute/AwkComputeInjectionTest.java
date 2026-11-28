package org.metricshub.engine.strategy.source.compute;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Awk;
import org.metricshub.engine.strategy.source.SourceResult;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * An awk compute reads the constants, connector variables and earlier text sources it uses by name.
 */
class AwkComputeInjectionTest {

	private static final String CONNECTOR_ID = "connector";

	private static String compute(final Connector connector, final Map<String, String> textSources) {
		final ConnectorStore connectorStore = new ConnectorStore();
		connectorStore.getStore().put(CONNECTOR_ID, connector);
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostConfiguration(HostConfiguration.builder().hostname("host").hostId("host").build())
			.connectorStore(connectorStore)
			.build();
		final SourceResult sourceResult = SourceResult.ofTable(List.of(List.of("3")));
		final ComputeProcessor computeProcessor = ComputeProcessor.builder()
			.telemetryManager(telemetryManager)
			.connectorId(CONNECTOR_ID)
			.hostname("host")
			.sourceKey("${source::monitors.disk.discovery.sources.disks}")
			.index(0)
			.sourceResult(sourceResult)
			.textSources(textSources)
			.build();
		computeProcessor.process(Awk.builder().script("{ print $1 * BLOCK_SIZE * scale, model }").build());
		return computeProcessor.getSourceResult().getText();
	}

	@Test
	void testConnectorValuesReachTheCompute() {
		assertEquals(
			"3072 X1\n",
			compute(
				Connector.builder().constants(Map.of("BLOCK_SIZE", "512")).variableValues(Map.of("scale", "2")).build(),
				Map.of("model", "X1")
			)
		);
	}

	@Test
	void testWithoutValuesArithmeticStaysLenient() {
		assertEquals("0 \n", compute(Connector.builder().build(), null));
	}
}
