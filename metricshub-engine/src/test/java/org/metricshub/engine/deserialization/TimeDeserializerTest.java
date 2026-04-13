package org.metricshub.engine.deserialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;

import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TimeDeserializerTest {

	@Mock
	private YAMLParser yamlParserMock;

	@Test
	void testDeserialize() throws IOException {
		doReturn("5s").when(yamlParserMock).getValueAsString();
		assertEquals(5L, new TimeDeserializer().deserialize(yamlParserMock, null));
	}

	@Test
	void testInterpretValueOf() {
		// null timeout.
		assertThrows(IllegalArgumentException.class, () -> TimeDeserializer.interpretValueOf(null));

		// wrong timeout
		assertThrows(IllegalArgumentException.class, () -> TimeDeserializer.interpretValueOf("1dy"));

		// empty timeout.
		assertEquals(0L, TimeDeserializer.interpretValueOf(""));

		// not empty timeout.
		assertEquals(34409233L, TimeDeserializer.interpretValueOf("1y4w5d6h7m5s8000ms"));
		assertEquals(31536000L, TimeDeserializer.interpretValueOf("1y"));
		assertEquals(2419200L, TimeDeserializer.interpretValueOf("4w"));
		assertEquals(432000L, TimeDeserializer.interpretValueOf("5d"));
		assertEquals(21600L, TimeDeserializer.interpretValueOf("6h"));
		assertEquals(420L, TimeDeserializer.interpretValueOf("7m"));
		assertEquals(5L, TimeDeserializer.interpretValueOf("5s"));
		assertEquals(8L, TimeDeserializer.interpretValueOf("8000ms"));
		assertEquals(34409233L, TimeDeserializer.interpretValueOf("01y04w05d06h07m05s08000ms"));
		assertEquals(120L, TimeDeserializer.interpretValueOf("120"));
	}
}
