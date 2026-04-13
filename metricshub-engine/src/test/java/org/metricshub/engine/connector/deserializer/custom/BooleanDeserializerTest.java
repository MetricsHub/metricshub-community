package org.metricshub.engine.connector.deserializer.custom;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BooleanDeserializerTest {

	private static final BooleanDeserializer DERSERIALIZER = new BooleanDeserializer();

	@Mock
	private YAMLParser yamlParser;

	@Test
	void testNull() throws IOException {
		{
			assertFalse(DERSERIALIZER.deserialize(null, null));
		}
		{
			doReturn(null).when(yamlParser).getValueAsString();
			assertFalse(DERSERIALIZER.deserialize(yamlParser, null));
		}
	}

	@Test
	void testBoolean() throws IOException {
		{
			doReturn("1").when(yamlParser).getValueAsString();
			assertTrue(DERSERIALIZER.deserialize(yamlParser, null));
		}
		{
			doReturn("true").when(yamlParser).getValueAsString();
			assertTrue(DERSERIALIZER.deserialize(yamlParser, null));
		}
		{
			doReturn("0").when(yamlParser).getValueAsString();
			assertFalse(DERSERIALIZER.deserialize(yamlParser, null));
		}
		{
			doReturn("false").when(yamlParser).getValueAsString();
			assertFalse(DERSERIALIZER.deserialize(yamlParser, null));
		}
	}

	@Test
	void testException() throws IOException {
		doReturn("invalid").when(yamlParser).getValueAsString();
		assertThrows(InvalidFormatException.class, () -> DERSERIALIZER.deserialize(yamlParser, null));
	}
}
