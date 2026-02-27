package org.metricshub.engine.connector.deserializer.custom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SizeDeserializerTest {

	private static final SizeDeserializer DESERIALIZER = new SizeDeserializer();
	private static final long KIB = 1024L;
	private static final long MIB = KIB * 1024L;
	private static final long GIB = MIB * 1024L;
	private static final long DEFAULT_BYTES = 5 * MIB;

	@Mock
	private JsonParser parser;

	@Test
	void deserializeNullParserReturnsNull() throws IOException {
		assertNull(DESERIALIZER.deserialize(null, null));
	}

	@Test
	void deserializeNullTokenReturnsDefaultBytes() throws IOException {
		doReturn(null).when(parser).currentToken();
		assertEquals(DEFAULT_BYTES, DESERIALIZER.deserialize(parser, null));
	}

	@Test
	void deserializeNumberIntPositiveReturnsMegabytes() throws IOException {
		doReturn(JsonToken.VALUE_NUMBER_INT).when(parser).currentToken();
		doReturn(5L).when(parser).getLongValue();
		assertEquals(5 * MIB, DESERIALIZER.deserialize(parser, null));
	}

	@Test
	void deserializeNumberIntZeroReturnsDefaultBytes() throws IOException {
		doReturn(JsonToken.VALUE_NUMBER_INT).when(parser).currentToken();
		doReturn(0L).when(parser).getLongValue();
		assertEquals(DEFAULT_BYTES, DESERIALIZER.deserialize(parser, null));
	}

	@Test
	void deserializeNumberIntNegativeReturnsUnlimited() throws IOException {
		doReturn(JsonToken.VALUE_NUMBER_INT).when(parser).currentToken();
		doReturn(-1L).when(parser).getLongValue();
		assertEquals(SizeDeserializer.UNLIMITED, DESERIALIZER.deserialize(parser, null));
	}

	@Test
	void deserializeNumberFloatPositiveReturnsMegabytes() throws IOException {
		doReturn(JsonToken.VALUE_NUMBER_FLOAT).when(parser).currentToken();
		doReturn(1.5).when(parser).getDoubleValue();
		assertEquals((long) (1.5 * MIB), DESERIALIZER.deserialize(parser, null));
	}

	@Test
	void deserializeNumberFloatZeroReturnsDefaultBytes() throws IOException {
		doReturn(JsonToken.VALUE_NUMBER_FLOAT).when(parser).currentToken();
		doReturn(0.0).when(parser).getDoubleValue();
		assertEquals(DEFAULT_BYTES, DESERIALIZER.deserialize(parser, null));
	}

	@Test
	void deserializeNumberFloatNegativeReturnsUnlimited() throws IOException {
		doReturn(JsonToken.VALUE_NUMBER_FLOAT).when(parser).currentToken();
		doReturn(-1.0).when(parser).getDoubleValue();
		assertEquals(SizeDeserializer.UNLIMITED, DESERIALIZER.deserialize(parser, null));
	}

	@Test
	void deserializeStringUnlimitedReturnsUnlimited() throws IOException {
		doReturn(JsonToken.VALUE_STRING).when(parser).currentToken();
		doReturn("unlimited").when(parser).getValueAsString();
		assertEquals(SizeDeserializer.UNLIMITED, DESERIALIZER.deserialize(parser, null));
	}

	@Test
	void deserializeStringMinusOneReturnsUnlimited() throws IOException {
		doReturn(JsonToken.VALUE_STRING).when(parser).currentToken();
		doReturn("-1").when(parser).getValueAsString();
		assertEquals(SizeDeserializer.UNLIMITED, DESERIALIZER.deserialize(parser, null));
	}

	@Test
	void deserializeStringNullReturnsDefaultBytes() throws IOException {
		doReturn(JsonToken.VALUE_STRING).when(parser).currentToken();
		doReturn(null).when(parser).getValueAsString();
		assertEquals(DEFAULT_BYTES, DESERIALIZER.deserialize(parser, null));
	}

	@Test
	void deserializeStringBlankReturnsDefaultBytes() throws IOException {
		doReturn(JsonToken.VALUE_STRING).when(parser).currentToken();
		doReturn("   ").when(parser).getValueAsString();
		assertEquals(DEFAULT_BYTES, DESERIALIZER.deserialize(parser, null));
	}

	@Test
	void deserializeStringWithUnitMbReturnsMegabytes() throws IOException {
		doReturn(JsonToken.VALUE_STRING).when(parser).currentToken();
		doReturn("5Mb").when(parser).getValueAsString();
		assertEquals(5 * MIB, DESERIALIZER.deserialize(parser, null));
	}

	@Test
	void deserializeStringWithUnitGbReturnsGigabytes() throws IOException {
		doReturn(JsonToken.VALUE_STRING).when(parser).currentToken();
		doReturn("1gb").when(parser).getValueAsString();
		assertEquals(GIB, DESERIALIZER.deserialize(parser, null));
	}

	@Test
	void deserializeStringWithUnitKbReturnsKilobytes() throws IOException {
		doReturn(JsonToken.VALUE_STRING).when(parser).currentToken();
		doReturn("512kb").when(parser).getValueAsString();
		assertEquals(512 * KIB, DESERIALIZER.deserialize(parser, null));
	}

	@Test
	void deserializeStringWithUnitBytesReturnsBytes() throws IOException {
		doReturn(JsonToken.VALUE_STRING).when(parser).currentToken();
		doReturn("100byte").when(parser).getValueAsString();
		assertEquals(100L, DESERIALIZER.deserialize(parser, null));
	}

	@Test
	void deserializeStringWithUnitKibReturnsKibibytes() throws IOException {
		doReturn(JsonToken.VALUE_STRING).when(parser).currentToken();
		doReturn("2kib").when(parser).getValueAsString();
		assertEquals(2 * KIB, DESERIALIZER.deserialize(parser, null));
	}

	@Test
	void deserializeStringNoUnitDefaultsToMegabytes() throws IOException {
		doReturn(JsonToken.VALUE_STRING).when(parser).currentToken();
		doReturn("3").when(parser).getValueAsString();
		assertEquals(3 * MIB, DESERIALIZER.deserialize(parser, null));
	}

	@Test
	void deserializeStringDecimalWithUnit() throws IOException {
		doReturn(JsonToken.VALUE_STRING).when(parser).currentToken();
		doReturn("1.5 MB").when(parser).getValueAsString();
		assertEquals((long) (1.5 * MIB), DESERIALIZER.deserialize(parser, null));
	}

	@Test
	void deserializeStringInvalidFormatThrows() throws IOException {
		doReturn(JsonToken.VALUE_STRING).when(parser).currentToken();
		doReturn("not a size").when(parser).getValueAsString();

		InvalidFormatException ex = assertThrows(
			InvalidFormatException.class,
			() -> DESERIALIZER.deserialize(parser, null)
		);
		assertTrue(ex.getMessage().contains("Invalid size"));
		assertTrue(ex.getMessage().contains("not a size"));
	}

	@Test
	void deserializeUnexpectedTokenThrows() throws IOException {
		doReturn(JsonToken.START_OBJECT).when(parser).currentToken();
		doReturn(JsonToken.START_OBJECT).when(parser).getCurrentToken();

		InvalidFormatException ex = assertThrows(
			InvalidFormatException.class,
			() -> DESERIALIZER.deserialize(parser, null)
		);
		assertTrue(ex.getMessage().contains("Invalid size"));
	}
}
