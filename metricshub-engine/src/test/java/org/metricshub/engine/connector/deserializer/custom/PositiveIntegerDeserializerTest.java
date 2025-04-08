package org.metricshub.engine.connector.deserializer.custom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.doReturn;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PositiveIntegerDeserializerTest {

	private static final PositiveIntegerDeserializer DERSERIALIZER = new PositiveIntegerDeserializer();

	@Mock
	private YAMLParser yamlParser;

	@Test
	void testNull() throws IOException {
		{
			assertNull(DERSERIALIZER.deserialize(null, null));
		}

		{
			doReturn(null).when(yamlParser).getValueAsString();
			assertNull(DERSERIALIZER.deserialize(yamlParser, null));
		}
	}

	@Test
	void testValue() throws IOException {
		doReturn("1234").when(yamlParser).getValueAsString();
		assertEquals(1234, DERSERIALIZER.deserialize(yamlParser, null));
	}

	@Test
	void testNegativeValue() throws Exception {
		doReturn("-1234").when(yamlParser).getValueAsString();
		doReturn("key").when(yamlParser).currentName();
		try {
			DERSERIALIZER.deserialize(yamlParser, null);
			fail("Expected InvalidFormatException to be thrown");
		} catch (InvalidFormatException e) {
			String message = "Invalid negative value encountered for property 'key'.";
			assertTrue(
				e.getMessage().contains(message),
				() -> "Expected exception contains: " + message + ". But got: " + e.getMessage()
			);
		}
	}

	@Test
	void testBadValue() throws Exception {
		doReturn("toto").when(yamlParser).getValueAsString();
		doReturn("key").when(yamlParser).currentName();
		try {
			DERSERIALIZER.deserialize(yamlParser, null);
			fail("Expected InvalidFormatException to be thrown");
		} catch (InvalidFormatException e) {
			String message = "Invalid value encountered for property 'key'.";
			assertTrue(
				e.getMessage().contains(message),
				() -> "Expected exception contains: " + message + ". But got: " + e.getMessage()
			);
		}
	}
}
