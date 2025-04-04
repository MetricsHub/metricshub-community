package org.metricshub.engine.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MonitorTest {

	@Test
	void testFormatIdentifyingAttributes() {
		{
			final Monitor monitor = Monitor.builder().attributes(Map.of("id", "1", "name", "test")).build();
			assertEquals("1", monitor.formatIdentifyingAttributes());
		}

		{
			final Monitor monitor = Monitor
				.builder()
				.identifyingAttributeKeys(Set.of("id", "name"))
				.attributes(Map.of("id", "1", "name", "test"))
				.build();
			assertEquals("1_test", monitor.formatIdentifyingAttributes());
		}

		{
			final Monitor monitor = Monitor
				.builder()
				.identifyingAttributeKeys(Set.of("id", "name"))
				.attributes(Map.of("id", "1"))
				.build();
			assertEquals("1_", monitor.formatIdentifyingAttributes());
		}
	}
}
