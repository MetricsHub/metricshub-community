package org.metricshub.engine.common.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.metricshub.engine.constants.Constants.HOST_ID;
import static org.metricshub.engine.constants.Constants.HOST_NAME;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.configuration.IProtocolScopedPropertyAccessor;
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

	@Test
	void testGetProtocolPropertyWithProtocolScopedAccessor() {
		final ProtocolScopedTestConfiguration scopedTestConfiguration = new ProtocolScopedTestConfiguration();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration
					.builder()
					.hostId(HOST_ID)
					.hostname(HOST_NAME)
					.sequential(false)
					.configurations(Map.of(ProtocolScopedTestConfiguration.class, scopedTestConfiguration))
					.build()
			)
			.build();

		assertEquals("http-timeout", ProtocolPropertyReferenceHelper.getProtocolProperty("http.timeout", telemetryManager));
		assertEquals("ssh-timeout", ProtocolPropertyReferenceHelper.getProtocolProperty("ssh.timeout", telemetryManager));
	}

	private static final class ProtocolScopedTestConfiguration
		implements IConfiguration, IProtocolScopedPropertyAccessor {

		@Override
		public String getHostname() {
			return null;
		}

		@Override
		public void setHostname(final String hostname) {}

		@Override
		public void setTimeout(final Long timeout) {}

		@Override
		public void validateConfiguration(final String resourceKey) {}

		@Override
		public IConfiguration copy() {
			return this;
		}

		@Override
		public String getProperty(final String property) {
			return "fallback-" + property;
		}

		@Override
		public boolean isCorrespondingProtocol(final String protocol) {
			return "http".equalsIgnoreCase(protocol) || "ssh".equalsIgnoreCase(protocol);
		}

		@Override
		public String getProperty(final String protocol, final String property) {
			return protocol.toLowerCase() + "-" + property.toLowerCase();
		}
	}
}
