package org.metricshub.engine.connector.deserializer.custom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;

import com.fasterxml.jackson.core.json.JsonReadContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.connector.model.monitor.task.source.CopySource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SourcesDeserializerTest {

	private static final SourcesDeserializer SOURCES_DESERIALIZER = new SourcesDeserializer();
	private final JsonReadContext readContext = new JsonReadContext(null, 0, null, 0, 0, 0);

	@Mock
	private YAMLParser yamlParser;

	@BeforeEach
	void beforeEach() {
		lenient().doReturn(readContext).when(yamlParser).getParsingContext();
	}

	@Test
	void testNull() throws IOException {
		{
			assertEquals(new LinkedHashMap<>(), SOURCES_DESERIALIZER.deserialize(null, null));
		}

		{
			assertEquals(new LinkedHashMap<>(), SOURCES_DESERIALIZER.deserialize(yamlParser, null));
		}
		{
			doReturn(null).when(yamlParser).readValueAs(any(TypeReference.class));
			assertEquals(new LinkedHashMap<>(), SOURCES_DESERIALIZER.deserialize(yamlParser, null));
		}
		{
			doReturn(new LinkedHashMap<>()).when(yamlParser).readValueAs(any(TypeReference.class));
			assertEquals(new LinkedHashMap<>(), SOURCES_DESERIALIZER.deserialize(yamlParser, null));
		}
	}

	@Test
	void testBadValue() throws IOException {
		{
			Map<String, Source> pre = new LinkedHashMap<>();
			pre.put(null, new CopySource());

			doReturn(pre).when(yamlParser).readValueAs(any(TypeReference.class));
			assertThrows(IOException.class, () -> SOURCES_DESERIALIZER.deserialize(yamlParser, null));
		}

		{
			Map<String, Source> pre = new LinkedHashMap<>();
			pre.put(" ", new CopySource());

			doReturn(pre).when(yamlParser).readValueAs(any(TypeReference.class));
			assertThrows(InvalidFormatException.class, () -> SOURCES_DESERIALIZER.deserialize(yamlParser, null));
		}
	}
}
