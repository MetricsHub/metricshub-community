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
import org.metricshub.engine.connector.model.identity.ConnectionType;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConnectionTypeSetDeserializerTest {

	private static final ConnectionTypeSetDeserializer CONNECTION_TYPE_DESERIALIZER = new ConnectionTypeSetDeserializer();

	@Mock
	private YAMLParser yamlParser;

	@Test
	void testNull() throws IOException {
		{
			assertEquals(Collections.emptySet(), CONNECTION_TYPE_DESERIALIZER.deserialize(null, null));
		}

		{
			doReturn(false).when(yamlParser).isExpectedStartArrayToken();
			doReturn(null).when(yamlParser).getValueAsString();
			assertEquals(Collections.emptySet(), CONNECTION_TYPE_DESERIALIZER.deserialize(yamlParser, null));
		}
		{
			doReturn(true).when(yamlParser).isExpectedStartArrayToken();
			doReturn(null).when(yamlParser).readValueAs(any(TypeReference.class));
			assertEquals(Collections.emptySet(), CONNECTION_TYPE_DESERIALIZER.deserialize(yamlParser, null));
		}
		{
			doReturn(true).when(yamlParser).isExpectedStartArrayToken();
			doReturn(Collections.emptySet()).when(yamlParser).readValueAs(any(TypeReference.class));
			assertEquals(Collections.emptySet(), CONNECTION_TYPE_DESERIALIZER.deserialize(yamlParser, null));
		}
	}

	@Test
	void testBadValue() throws IOException {
		{
			doReturn(false).when(yamlParser).isExpectedStartArrayToken();
			doReturn("unknown").when(yamlParser).getValueAsString();
			assertThrows(IOException.class, () -> CONNECTION_TYPE_DESERIALIZER.deserialize(yamlParser, null));
		}

		{
			doReturn(false).when(yamlParser).isExpectedStartArrayToken();
			doReturn("").when(yamlParser).getValueAsString();
			assertThrows(IOException.class, () -> CONNECTION_TYPE_DESERIALIZER.deserialize(yamlParser, null));
		}

		{
			doReturn(true).when(yamlParser).isExpectedStartArrayToken();
			doReturn(Set.of("unknown")).when(yamlParser).readValueAs(any(TypeReference.class));
			assertThrows(IOException.class, () -> CONNECTION_TYPE_DESERIALIZER.deserialize(yamlParser, null));
		}
		{
			doReturn(true).when(yamlParser).isExpectedStartArrayToken();
			doReturn(Set.of("")).when(yamlParser).readValueAs(any(TypeReference.class));
			assertThrows(IOException.class, () -> CONNECTION_TYPE_DESERIALIZER.deserialize(yamlParser, null));
		}
	}

	@Test
	void testDeserializeArray() throws IOException {
		{
			final Set<String> connectionTypes = Set.of("remote", "local");
			doReturn(true).when(yamlParser).isExpectedStartArrayToken();
			doReturn(connectionTypes).when(yamlParser).readValueAs(any(TypeReference.class));
			assertEquals(Set.of(ConnectionType.values()), CONNECTION_TYPE_DESERIALIZER.deserialize(yamlParser, null));
		}

		{
			final Set<String> connectionTypes = Set.of("REMOTE", "local");
			doReturn(true).when(yamlParser).isExpectedStartArrayToken();
			doReturn(connectionTypes).when(yamlParser).readValueAs(any(TypeReference.class));
			assertEquals(Set.of(ConnectionType.values()), CONNECTION_TYPE_DESERIALIZER.deserialize(yamlParser, null));
		}
	}
}
