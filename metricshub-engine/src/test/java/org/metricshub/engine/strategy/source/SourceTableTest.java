package org.metricshub.engine.strategy.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.constants.Constants.MY_CONNECTOR_1_NAME;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.telemetry.HostProperties;
import org.metricshub.engine.telemetry.TelemetryManager;

class SourceTableTest {

	private static final String HARDCODED_SOURCE = "Hardcoded Source";
	private static final String SOURCE_REF_KEY = "${source::monitors.cpu.discovery.sources.source5}";

	@Test
	void testLookupSourceTableFromHardcodedSource() {
		final HostProperties hostProperties = HostProperties.builder().build();

		hostProperties.getConnectorNamespace(MY_CONNECTOR_1_NAME);

		final TelemetryManager telemetryManager = TelemetryManager.builder().hostProperties(hostProperties).build();

		Optional<SourceTable> sourceTableOpt = SourceTable.lookupSourceTable(
			HARDCODED_SOURCE,
			MY_CONNECTOR_1_NAME,
			telemetryManager
		);
		assertTrue(sourceTableOpt.isPresent());

		assertEquals(HARDCODED_SOURCE, sourceTableOpt.get().getTable().get(0).get(0));
	}

	@Test
	void testLookupSourceTableFromReferencedSource() {
		final HostProperties hostProperties = HostProperties.builder().build();

		final SourceTable expected = SourceTable.builder().table(List.of(List.of("value"))).build();

		hostProperties.getConnectorNamespace(MY_CONNECTOR_1_NAME).addSourceTable(SOURCE_REF_KEY, expected);

		final TelemetryManager telemetryManager = TelemetryManager.builder().hostProperties(hostProperties).build();

		final Optional<SourceTable> sourceTableOpt = SourceTable.lookupSourceTable(
			SOURCE_REF_KEY,
			MY_CONNECTOR_1_NAME,
			telemetryManager
		);
		assertTrue(sourceTableOpt.isPresent());

		assertEquals(expected, sourceTableOpt.get());
	}

	@Test
	void testLookupSourceTableFromReferencedSourceNotFound() {
		final HostProperties hostProperties = HostProperties.builder().build();

		hostProperties.getConnectorNamespace(MY_CONNECTOR_1_NAME);

		final TelemetryManager telemetryManager = TelemetryManager.builder().hostProperties(hostProperties).build();

		assertTrue(SourceTable.lookupSourceTable(SOURCE_REF_KEY, MY_CONNECTOR_1_NAME, telemetryManager).isEmpty());
	}
}
