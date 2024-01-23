package org.sentrysoftware.metricshub.agent.deserialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttributesDeserializerTest {

	@Mock
	private YAMLParser yamlParserMock;

	@Test
	void testDeserializeNullParser() throws IOException {
		assertEquals(Collections.emptyMap(), new AttributesDeserializer().deserialize(null, null));
	}

	@Test
	void testDeserialize() throws IOException {
		doReturn(Map.of("host.names", List.of("host1", "host2")))
			.when(yamlParserMock)
			.readValueAs(any(TypeReference.class));

		Map<String, String> result = new AttributesDeserializer().deserialize(yamlParserMock, null);
		assertEquals(Map.of("host.names", "host1,host2"), result);
	}
}
