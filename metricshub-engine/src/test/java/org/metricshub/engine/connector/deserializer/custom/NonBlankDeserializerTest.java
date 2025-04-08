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
import org.metricshub.engine.common.helpers.MetricsHubConstants;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NonBlankDeserializerTest {

	private static final NonBlankDeserializer NON_BLANK_DERSERIALIZER = new NonBlankDeserializer();

	@Mock
	private YAMLParser yamlParser;

	@Test
	void testNull() throws IOException {
		{
			assertNull(NON_BLANK_DERSERIALIZER.deserialize(null, null));
		}

		{
			doReturn(null).when(yamlParser).getValueAsString();
			assertNull(NON_BLANK_DERSERIALIZER.deserialize(yamlParser, null));
		}
	}

	@Test
	void testValue() throws IOException {
		doReturn("value").when(yamlParser).getValueAsString();
		assertEquals("value", NON_BLANK_DERSERIALIZER.deserialize(yamlParser, null));
	}

	@Test
	void testBlankValue() throws Exception {
		{
			doReturn("  ").when(yamlParser).getValueAsString();
			doReturn("key").when(yamlParser).currentName();
			try {
				NON_BLANK_DERSERIALIZER.deserialize(yamlParser, null);
				fail("Expected IOException to be thrown");
			} catch (InvalidFormatException e) {
				String message = "Invalid blank value encountered for property 'key'.";
				assertTrue(
					e.getMessage().contains(message),
					() -> "Expected exception contains: " + message + ". But got: " + e.getMessage()
				);
			}
		}

		{
			doReturn(MetricsHubConstants.EMPTY).when(yamlParser).getValueAsString();
			doReturn("key").when(yamlParser).currentName();
			try {
				NON_BLANK_DERSERIALIZER.deserialize(yamlParser, null);
				fail("Expected IOException to be thrown");
			} catch (InvalidFormatException e) {
				String message = "Invalid blank value encountered for property 'key'.";
				assertTrue(
					e.getMessage().contains(message),
					() -> "Expected exception contains: " + message + ". But got: " + e.getMessage()
				);
			}
		}
	}
}
