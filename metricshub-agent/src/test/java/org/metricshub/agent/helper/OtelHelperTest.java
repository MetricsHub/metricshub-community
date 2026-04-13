package org.metricshub.agent.helper;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.agent.helper.TestConstants.COMPUTE_HOST_TYPE;
import static org.metricshub.agent.helper.TestConstants.HOST_TYPE_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.TestConstants.OS_TYPE_ATTRIBUTE_KEY;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.HOST_NAME;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.helpers.MetricsHubConstants;

class OtelHelperTest {

	private static final String HOSTNAME_ATTRIBUTE_VALUE = "OtelHelperTestHostname." + UUID.randomUUID().toString();

	@Test
	void testBuildHostAttributes() {
		final Map<String, String> emptyMap = Map.of();
		{
			assertThrows(IllegalArgumentException.class, () -> OtelHelper.buildHostAttributes(null, emptyMap));
			assertThrows(IllegalArgumentException.class, () -> OtelHelper.buildHostAttributes(emptyMap, null));
			assertDoesNotThrow(() -> OtelHelper.buildHostAttributes(emptyMap, emptyMap));
		}
		{
			final Map<String, String> attributes = OtelHelper.buildHostAttributes(
				Map.of(
					HOST_NAME,
					HOSTNAME_ATTRIBUTE_VALUE,
					HOST_TYPE_ATTRIBUTE_KEY,
					COMPUTE_HOST_TYPE,
					OS_TYPE_ATTRIBUTE_KEY,
					MetricsHubConstants.OTEL_LINUX_OS_TYPE
				),
				Map.of(HOST_NAME, "extraHostname", HOST_TYPE_ATTRIBUTE_KEY, "extraHostType")
			);
			final Map<String, String> expected = Map.of(
				HOST_NAME,
				HOSTNAME_ATTRIBUTE_VALUE,
				HOST_TYPE_ATTRIBUTE_KEY,
				COMPUTE_HOST_TYPE,
				OS_TYPE_ATTRIBUTE_KEY,
				MetricsHubConstants.OTEL_LINUX_OS_TYPE
			);

			assertEquals(expected, attributes);
		}
		{
			final Map<String, String> attributes = OtelHelper.buildHostAttributes(
				Map.of(HOST_NAME, HOSTNAME_ATTRIBUTE_VALUE, HOST_TYPE_ATTRIBUTE_KEY, COMPUTE_HOST_TYPE),
				emptyMap
			);
			final Map<String, String> expected = Map.of(
				HOST_NAME,
				HOSTNAME_ATTRIBUTE_VALUE,
				HOST_TYPE_ATTRIBUTE_KEY,
				COMPUTE_HOST_TYPE
			);

			assertEquals(expected, attributes);
		}
		{
			final Map<String, String> attributes = OtelHelper.buildHostAttributes(
				Map.of(
					HOST_NAME,
					HOSTNAME_ATTRIBUTE_VALUE,
					HOST_TYPE_ATTRIBUTE_KEY,
					COMPUTE_HOST_TYPE,
					OS_TYPE_ATTRIBUTE_KEY,
					MetricsHubConstants.OTEL_LINUX_OS_TYPE
				),
				Map.of(OS_TYPE_ATTRIBUTE_KEY, MetricsHubConstants.OTEL_WINDOWS_OS_TYPE)
			);
			final Map<String, String> expected = Map.of(
				HOST_NAME,
				HOSTNAME_ATTRIBUTE_VALUE,
				HOST_TYPE_ATTRIBUTE_KEY,
				COMPUTE_HOST_TYPE,
				OS_TYPE_ATTRIBUTE_KEY,
				MetricsHubConstants.OTEL_WINDOWS_OS_TYPE
			);

			assertEquals(expected, attributes);
		}
	}

	@Test
	void testIsAcceptedKey() {
		assertFalse(OtelHelper.isAcceptedKey("__key"));
		assertTrue(OtelHelper.isAcceptedKey("key"));
	}
}
