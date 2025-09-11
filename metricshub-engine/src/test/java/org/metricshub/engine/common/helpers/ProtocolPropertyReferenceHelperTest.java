package org.metricshub.engine.common.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.metricshub.engine.constants.Constants.HOST_ID;
import static org.metricshub.engine.constants.Constants.HOST_NAME;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.extension.TestConfiguration;
import org.metricshub.engine.telemetry.TelemetryManager;

class ProtocolPropertyReferenceHelperTest {

	@Test
	void testGetProtocolProperty() {
		final TestConfiguration testConfiguration = new TestConfiguration();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration
					.builder()
					.hostId(HOST_ID)
					.hostname(HOST_NAME)
					.sequential(false)
					.configurations(Map.of(TestConfiguration.class, testConfiguration))
					.build()
			)
			.build();

		assertNull(ProtocolPropertyReferenceHelper.getProtocolProperty(null, telemetryManager));
		assertNull(ProtocolPropertyReferenceHelper.getProtocolProperty("", telemetryManager));
		assertNull(ProtocolPropertyReferenceHelper.getProtocolProperty("myProperty", telemetryManager));
		assertNull(ProtocolPropertyReferenceHelper.getProtocolProperty("badTest.myProperty", telemetryManager));
		assertNull(ProtocolPropertyReferenceHelper.getProtocolProperty("test.myBadProperty", telemetryManager));
		assertEquals(
			"myPropertyValue",
			ProtocolPropertyReferenceHelper.getProtocolProperty("test.myProperty", telemetryManager)
		);
		assertEquals(
			"myPropertyValue",
			ProtocolPropertyReferenceHelper.getProtocolProperty("tEsT.mYProPerTy", telemetryManager)
		);
	}
}
