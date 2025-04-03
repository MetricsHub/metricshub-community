package org.metricshub.extension.snmp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.extension.snmp.SnmpConfiguration.SnmpVersion;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.dataformat.yaml.YAMLParser;

@ExtendWith(MockitoExtension.class)
class SnmpVersionDeserializerTest {

	@Mock
	private YAMLParser yamlParserMock;

	@Test
	void testNull() throws IOException {
		assertNull(new SnmpVersionDeserializer().deserialize(null, null));
	}

	@Test
	void testBadValue() throws IOException {
		{
			doReturn(null).when(yamlParserMock).getValueAsString();
			assertThrows(IOException.class, () -> new SnmpVersionDeserializer().deserialize(yamlParserMock, null));
		}

		{
			doReturn("unknown").when(yamlParserMock).getValueAsString();
			assertThrows(IOException.class, () -> new SnmpVersionDeserializer().deserialize(yamlParserMock, null));
		}

		{
			doReturn("").when(yamlParserMock).getValueAsString();
			assertThrows(IOException.class, () -> new SnmpVersionDeserializer().deserialize(yamlParserMock, null));
		}
	}

	@Test
	void testV1() throws IOException {
		doReturn("v1").when(yamlParserMock).getValueAsString();
		assertEquals(SnmpVersion.V1, new SnmpVersionDeserializer().deserialize(yamlParserMock, null));

		doReturn("1").when(yamlParserMock).getValueAsString();
		assertEquals(SnmpVersion.V1, new SnmpVersionDeserializer().deserialize(yamlParserMock, null));
	}

	@Test
	void testV2() throws IOException {
		doReturn("v2").when(yamlParserMock).getValueAsString();
		assertEquals(SnmpVersion.V2C, new SnmpVersionDeserializer().deserialize(yamlParserMock, null));

		doReturn("2").when(yamlParserMock).getValueAsString();
		assertEquals(SnmpVersion.V2C, new SnmpVersionDeserializer().deserialize(yamlParserMock, null));

		doReturn("v2c").when(yamlParserMock).getValueAsString();
		assertEquals(SnmpVersion.V2C, new SnmpVersionDeserializer().deserialize(yamlParserMock, null));
	}
}
