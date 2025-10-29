package org.metricshub.agent.helper;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.agent.helper.AgentConstants.DEFAULT_OTEL_CRT_FILENAME;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.config.AgentConfig;
import org.metricshub.agent.opentelemetry.OtelConfigConstants;
import org.metricshub.agent.service.TestHelper;

class OtelConfigHelperTest {

	private static final String OTEL_EXPORTER_OTLP_HEADERS_PROPERTY = "otel.exporter.otlp.headers";

	private static final String TEST_SECURITY_PATH = "src/test/resources/security";

	private static final String HTTP_GRPC_ENDPOINT = "http://localhost:4317";
	private static final String HTTPS_GRPC_ENDPOINT = "https://localhost:4317";

	@Test
	void testGetSecurityFilePath() {
		// Null certificate file configured, the default is loaded
		assertTrue(
			OtelConfigHelper
				.getSecurityFilePath(TEST_SECURITY_PATH, DEFAULT_OTEL_CRT_FILENAME, null, HTTPS_GRPC_ENDPOINT)
				.isPresent()
		);

		// Empty certificate file configured, the default is loaded
		assertTrue(
			OtelConfigHelper
				.getSecurityFilePath(TEST_SECURITY_PATH, DEFAULT_OTEL_CRT_FILENAME, "", HTTPS_GRPC_ENDPOINT)
				.isPresent()
		);

		// Blank certificate file, the default is loaded
		assertTrue(
			OtelConfigHelper
				.getSecurityFilePath(TEST_SECURITY_PATH, DEFAULT_OTEL_CRT_FILENAME, " ", HTTPS_GRPC_ENDPOINT)
				.isPresent()
		);

		// Certificate file not found, it cannot be loaded
		final String randomFilename = UUID.randomUUID().toString();
		assertFalse(
			OtelConfigHelper
				.getSecurityFilePath(TEST_SECURITY_PATH, DEFAULT_OTEL_CRT_FILENAME, randomFilename, HTTPS_GRPC_ENDPOINT)
				.isPresent()
		);

		// HTTP instead of HTTPS, no certificate to load
		assertTrue(
			OtelConfigHelper
				.getSecurityFilePath(TEST_SECURITY_PATH, DEFAULT_OTEL_CRT_FILENAME, null, HTTP_GRPC_ENDPOINT)
				.isEmpty()
		);

		// The certificate file is found
		assertFalse(
			OtelConfigHelper
				.getSecurityFilePath(
					TEST_SECURITY_PATH,
					DEFAULT_OTEL_CRT_FILENAME,
					TEST_SECURITY_PATH + "/" + DEFAULT_OTEL_CRT_FILENAME,
					HTTPS_GRPC_ENDPOINT
				)
				.isEmpty()
		);
	}

	@Test
	void testBuildOtelConfiguration() {
		assertDoesNotThrow(() -> OtelConfigHelper.buildOtelConfiguration(new AgentConfig()));
	}

	@Test
	void testBuildOtelConfigurationHeaders() {
		TestHelper.configureGlobalLogger();
		{
			final Map<String, String> otelConfig = OtelConfigHelper.buildOtelConfiguration(AgentConfig.empty());
			assertFalse(otelConfig.containsKey(OTEL_EXPORTER_OTLP_HEADERS_PROPERTY));
		}

		{
			final AgentConfig agentConfig = AgentConfig
				.builder()
				.otelConfig(OtelConfigConstants.DEFAULT_CONFIGURATION)
				.build();
			final Map<String, String> otelConfig = OtelConfigHelper.buildOtelConfiguration(agentConfig);
			final Map<String, String> expectedConfig = new HashMap<>();
			expectedConfig.putAll(OtelConfigConstants.DEFAULT_CONFIGURATION);
			expectedConfig.put(
				OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_POOL_SIZE,
				String.valueOf(agentConfig.getJobPoolSize())
			);

			assertEquals(expectedConfig, otelConfig);
		}
	}
}
