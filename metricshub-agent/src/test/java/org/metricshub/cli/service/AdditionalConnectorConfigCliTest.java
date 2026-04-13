package org.metricshub.cli.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.configuration.AdditionalConnector;

class AdditionalConnectorConfigCliTest {

	@Test
	void testToAdditionalConnector() {
		final AdditionalConnectorConfigCli additionalConnectorConfigCli = new AdditionalConnectorConfigCli();
		additionalConnectorConfigCli.setConnectorId("id");
		additionalConnectorConfigCli.setUses("usedConnectorId");
		additionalConnectorConfigCli.setVariables(Map.of("var1", "value1", "var2", "value2"));

		final AdditionalConnector additionalConnector = AdditionalConnector
			.builder()
			.force(true)
			.uses("usedConnectorId")
			.variables(Map.of("var1", "value1", "var2", "value2"))
			.build();

		assertEquals(additionalConnectorConfigCli.toAdditionalConnector(), additionalConnector);

		additionalConnectorConfigCli.setConnectorId("id");
		additionalConnectorConfigCli.setUses(null);

		additionalConnector.setUses("id");
		assertEquals(additionalConnectorConfigCli.toAdditionalConnector(), additionalConnector);
	}
}
