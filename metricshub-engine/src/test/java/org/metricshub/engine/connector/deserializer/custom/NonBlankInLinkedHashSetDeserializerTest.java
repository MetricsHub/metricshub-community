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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NonBlankInLinkedHashSetDeserializerTest {

	private static final NonBlankInLinkedHashSetDeserializer DESERIALIZER = new NonBlankInLinkedHashSetDeserializer();

	@Mock
	private YAMLParser yamlParser;

	@Test
	void testNull() throws IOException {
		{
			assertEquals(Collections.emptySet(), DESERIALIZER.deserialize(null, null));
		}

		{
			doReturn(false).when(yamlParser).isExpectedStartArrayToken();
			doReturn(null).when(yamlParser).getValueAsString();
			assertEquals(Collections.emptySet(), DESERIALIZER.deserialize(yamlParser, null));
		}
		{
			doReturn(true).when(yamlParser).isExpectedStartArrayToken();
			doReturn(null).when(yamlParser).readValueAs(any(TypeReference.class));
			assertEquals(Collections.emptySet(), DESERIALIZER.deserialize(yamlParser, null));
		}
		{
			doReturn(true).when(yamlParser).isExpectedStartArrayToken();
			doReturn(Collections.emptySet()).when(yamlParser).readValueAs(any(TypeReference.class));
			assertEquals(Collections.emptySet(), DESERIALIZER.deserialize(yamlParser, null));
		}
	}

	@Test
	void testBadValue() throws IOException {
		{
			doReturn(false).when(yamlParser).isExpectedStartArrayToken();
			doReturn("").when(yamlParser).getValueAsString();
			assertThrows(IOException.class, () -> DESERIALIZER.deserialize(yamlParser, null));
		}

		{
			doReturn(true).when(yamlParser).isExpectedStartArrayToken();
			doReturn(Set.of("   ")).when(yamlParser).readValueAs(any(TypeReference.class));
			assertThrows(IOException.class, () -> DESERIALIZER.deserialize(yamlParser, null));
		}
	}

	@Test
	void testValues() throws IOException {
		{
			doReturn(false).when(yamlParser).isExpectedStartArrayToken();
			doReturn("value").when(yamlParser).getValueAsString();
			assertEquals(Set.of("value"), DESERIALIZER.deserialize(yamlParser, null));
		}

		{
			doReturn(true).when(yamlParser).isExpectedStartArrayToken();
			doReturn(Set.of("value1", "values2")).when(yamlParser).readValueAs(any(TypeReference.class));
			assertEquals(Set.of("value1", "values2"), DESERIALIZER.deserialize(yamlParser, null));
		}
	}
}
