package org.metricshub.engine.connector.deserializer.source;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.deserializer.DeserializerTest;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.monitor.task.source.EventLogLevel;
import org.metricshub.engine.connector.model.monitor.task.source.EventLogSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;

class EventLogSourceDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/source/eventLog/";
	}

	@Test
	void testDeserializeEventLogSource() throws IOException {
		final Connector connector = getConnector("eventLog");

		final Map<String, Source> expected = new LinkedHashMap<>(
			Map.of(
				"testEventLogSource",
				EventLogSource
					.builder()
					.key("${source::beforeAll.testEventLogSource}")
					.type("eventLog")
					.logName("System")
					.sources(new LinkedHashSet<String>(List.of("WindowsUpdateClient", "Kernel-Power")))
					.eventIds(new LinkedHashSet<String>(List.of("566", "507", "19", "43", "44")))
					.maxEventsPerPoll(30)
					.levels(
						new LinkedHashSet<EventLogLevel>(
							List.of(EventLogLevel.INFORMATION, EventLogLevel.WARNING, EventLogLevel.ERROR)
						)
					)
					.build()
			)
		);

		assertEquals(expected, connector.getBeforeAll());
	}

	@Test
	void testDeserializeEventLogSourceSourceWithNegativeMaxEventsPerPoll() throws IOException {
		final Connector connector = getConnector("eventLog2");

		final Map<String, Source> expected = new LinkedHashMap<>(
			Map.of(
				"testEventLogSource",
				EventLogSource
					.builder()
					.key("${source::beforeAll.testEventLogSource}")
					.type("eventLog")
					.logName("System")
					.sources(Set.of("Kernel-Power", "WindowsUpdateClient"))
					.eventIds((Set.of("566", "507", "19", "43", "44")))
					.maxEventsPerPoll(-1)
					.levels(Set.of(EventLogLevel.INFORMATION, EventLogLevel.WARNING, EventLogLevel.ERROR))
					.build()
			)
		);

		assertEquals(expected, connector.getBeforeAll());
	}

	@Test
	void testDeserializeEventLogSourceWithoutMaxEventsPerPoll() throws IOException {
		final Connector connector = getConnector("eventLog3");

		final Map<String, Source> expected = new LinkedHashMap<>(
			Map.of(
				"testEventLogSource",
				EventLogSource
					.builder()
					.key("${source::beforeAll.testEventLogSource}")
					.type("eventLog")
					.logName("System")
					.sources(Set.of("Kernel-Power", "WindowsUpdateClient"))
					.eventIds((Set.of("566", "507", "19", "43", "44")))
					.maxEventsPerPoll(50)
					.levels(Set.of(EventLogLevel.INFORMATION, EventLogLevel.WARNING, EventLogLevel.ERROR))
					.build()
			)
		);

		assertEquals(expected, connector.getBeforeAll());
	}
}
