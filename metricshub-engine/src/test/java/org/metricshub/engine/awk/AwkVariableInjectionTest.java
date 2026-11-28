package org.metricshub.engine.awk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * The connector values a script uses are added to its variables.
 */
class AwkVariableInjectionTest {

	private static final String CONNECTOR_ID = "connector";

	private static final Set<String> USED = Set.of("BLOCK_SIZE", "scale", "model", "explicit", "REF", "missing", "NR");

	private static TelemetryManager telemetryManager(final ConnectorStore connectorStore) {
		return TelemetryManager.builder()
			.hostConfiguration(HostConfiguration.builder().hostname("host").hostId("host").build())
			.connectorStore(connectorStore)
			.build();
	}

	private static ConnectorStore storeWith(final Connector connector) {
		final ConnectorStore connectorStore = new ConnectorStore();
		connectorStore.getStore().put(CONNECTOR_ID, connector);
		return connectorStore;
	}

	@Test
	void testOnlyUsedNamesAreAddedAndExplicitEntriesWin() {
		final Connector connector = Connector.builder()
			.constants(Map.of("BLOCK_SIZE", "512", "unused", "u", "REF", "${source::monitors.m.discovery.sources.x}"))
			// "missing" is a declared variable without value: absent
			.variableValues(Map.of("scale", "2"))
			.build();
		final Map<String, Object> explicit = new LinkedHashMap<>();
		explicit.put("explicit", "e");
		explicit.put("scale", "9");

		final Map<String, Object> variables = AwkVariableHelper.resolveVariables(
			explicit,
			telemetryManager(storeWith(connector)),
			CONNECTOR_ID,
			null,
			"key",
			USED,
			Map.of("model", "X1", "other", "o")
		);

		assertEquals(Map.of("explicit", "e", "scale", "9", "BLOCK_SIZE", "512", "model", "X1"), variables);
		assertThrows(UnsupportedOperationException.class, () -> variables.put("x", "y"));
	}

	@Test
	void testNullStoreConnectorOrTextsAreEmpty() {
		final Map<String, String> texts = Map.of("model", "X1");
		final Map<String, Object> explicit = Map.of("explicit", "e");

		assertEquals(
			Map.of("explicit", "e", "model", "X1"),
			AwkVariableHelper.resolveVariables(explicit, telemetryManager(null), CONNECTOR_ID, null, "key", USED, texts)
		);
		assertEquals(
			Map.of("model", "X1"),
			AwkVariableHelper.resolveVariables(
				null,
				telemetryManager(new ConnectorStore()),
				CONNECTOR_ID,
				null,
				"key",
				USED,
				texts
			)
		);
		assertEquals(
			Map.of(),
			AwkVariableHelper.resolveVariables(
				null,
				telemetryManager(storeWith(Connector.builder().constants(null).variableValues(null).build())),
				CONNECTOR_ID,
				null,
				"key",
				USED,
				null
			)
		);
	}
}
