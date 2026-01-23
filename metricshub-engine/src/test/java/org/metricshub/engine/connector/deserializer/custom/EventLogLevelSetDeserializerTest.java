package org.metricshub.engine.connector.deserializer.custom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.connector.model.monitor.task.source.EventLogLevel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventLogLevelSetDeserializerTest {

	private static final EventLogLevelSetDeserializer EVENT_LOG_LEVEL_DESERIALIZER = new EventLogLevelSetDeserializer();

	@Mock
	private YAMLParser yamlParser;

	@Test
	void testNull() throws IOException {
		{
			assertEquals(Collections.emptySet(), EVENT_LOG_LEVEL_DESERIALIZER.deserialize(null, null));
		}

		{
			doReturn(false).when(yamlParser).isExpectedStartArrayToken();
			doReturn(null).when(yamlParser).getValueAsString();
			assertEquals(Collections.emptySet(), EVENT_LOG_LEVEL_DESERIALIZER.deserialize(yamlParser, null));
		}
		{
			doReturn(false).when(yamlParser).isExpectedStartArrayToken();
			doReturn("").when(yamlParser).getValueAsString();
			assertEquals(Collections.emptySet(), EVENT_LOG_LEVEL_DESERIALIZER.deserialize(yamlParser, null));
		}
		{
			doReturn(true).when(yamlParser).isExpectedStartArrayToken();
			doReturn(null).when(yamlParser).readValueAs(any(TypeReference.class));
			assertEquals(Collections.emptySet(), EVENT_LOG_LEVEL_DESERIALIZER.deserialize(yamlParser, null));
		}
		{
			doReturn(true).when(yamlParser).isExpectedStartArrayToken();
			doReturn(Collections.emptySet()).when(yamlParser).readValueAs(any(TypeReference.class));
			assertEquals(Collections.emptySet(), EVENT_LOG_LEVEL_DESERIALIZER.deserialize(yamlParser, null));
		}
	}

	@Test
	void testBadValue() throws IOException {
		{
			doReturn(false).when(yamlParser).isExpectedStartArrayToken();
			doReturn("unknown").when(yamlParser).getValueAsString();
			assertThrows(IOException.class, () -> EVENT_LOG_LEVEL_DESERIALIZER.deserialize(yamlParser, null));
		}

		{
			doReturn(true).when(yamlParser).isExpectedStartArrayToken();
			doReturn(Set.of("unknown")).when(yamlParser).readValueAs(any(TypeReference.class));
			assertThrows(IOException.class, () -> EVENT_LOG_LEVEL_DESERIALIZER.deserialize(yamlParser, null));
		}
		{
			doReturn(true).when(yamlParser).isExpectedStartArrayToken();
			doReturn(Set.of("")).when(yamlParser).readValueAs(any(TypeReference.class));
			assertEquals(Collections.emptySet(), EVENT_LOG_LEVEL_DESERIALIZER.deserialize(yamlParser, null));
		}
	}

	@Test
	void testDeserializeArrayAndString() throws IOException {
		{
			doReturn(true).when(yamlParser).isExpectedStartArrayToken();
			doReturn(Set.of("error", "warning", "information", "audit success", "audit failure"))
				.when(yamlParser)
				.readValueAs(any(TypeReference.class));
			assertEquals(
				Set.of(
					EventLogLevel.ERROR,
					EventLogLevel.WARNING,
					EventLogLevel.INFORMATION,
					EventLogLevel.AUDIT_SUCCESS,
					EventLogLevel.AUDIT_FAILURE
				),
				EVENT_LOG_LEVEL_DESERIALIZER.deserialize(yamlParser, null)
			);
		}

		{
			doReturn(false).when(yamlParser).isExpectedStartArrayToken();
			doReturn("1,2,3,4,5").when(yamlParser).getValueAsString();
			assertEquals(
				Set.of(
					EventLogLevel.ERROR,
					EventLogLevel.WARNING,
					EventLogLevel.INFORMATION,
					EventLogLevel.AUDIT_SUCCESS,
					EventLogLevel.AUDIT_FAILURE
				),
				EVENT_LOG_LEVEL_DESERIALIZER.deserialize(yamlParser, null)
			);
		}
	}
}
