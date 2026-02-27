package org.metricshub.engine.connector.deserializer.custom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;

import com.fasterxml.jackson.core.JsonParser;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.connector.model.monitor.task.source.FileSourceProcessingMode;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileSourceModeDeserializerTest {

	private static final FileSourceModeDeserializer DESERIALIZER = new FileSourceModeDeserializer();

	@Mock
	private JsonParser parser;

	@Test
	void deserializeNullParserReturnsNull() throws IOException {
		assertNull(DESERIALIZER.deserialize(null, null));
	}

	@Test
	void deserializeFlatReturnsFlat() throws IOException {
		doReturn("flat").when(parser).getValueAsString();
		assertEquals(FileSourceProcessingMode.FLAT, DESERIALIZER.deserialize(parser, null));
	}

	@Test
	void deserializeFlatCaseInsensitiveReturnsFlat() throws IOException {
		doReturn("Flat").when(parser).getValueAsString();
		assertEquals(FileSourceProcessingMode.FLAT, DESERIALIZER.deserialize(parser, null));
		doReturn("FLAT").when(parser).getValueAsString();
		assertEquals(FileSourceProcessingMode.FLAT, DESERIALIZER.deserialize(parser, null));
	}

	@Test
	void deserializeLogReturnsLog() throws IOException {
		doReturn("log").when(parser).getValueAsString();
		assertEquals(FileSourceProcessingMode.LOG, DESERIALIZER.deserialize(parser, null));
	}

	@Test
	void deserializeLogCaseInsensitiveReturnsLog() throws IOException {
		doReturn("Log").when(parser).getValueAsString();
		assertEquals(FileSourceProcessingMode.LOG, DESERIALIZER.deserialize(parser, null));
		doReturn("LOG").when(parser).getValueAsString();
		assertEquals(FileSourceProcessingMode.LOG, DESERIALIZER.deserialize(parser, null));
	}

	@Test
	void deserializeNullValueThrowsIOException() throws IOException {
		doReturn(null).when(parser).getValueAsString();

		IOException ex = assertThrows(IOException.class, () -> DESERIALIZER.deserialize(parser, null));
		assertTrue(ex.getMessage().contains("cannot be null or blank"));
	}

	@Test
	void deserializeBlankValueThrowsIOException() throws IOException {
		doReturn("   ").when(parser).getValueAsString();

		IOException ex = assertThrows(IOException.class, () -> DESERIALIZER.deserialize(parser, null));
		assertTrue(ex.getMessage().contains("cannot be null or blank"));
	}

	@Test
	void deserializeUnsupportedValueThrowsIOException() throws IOException {
		doReturn("stream").when(parser).getValueAsString();

		IOException ex = assertThrows(IOException.class, () -> DESERIALIZER.deserialize(parser, null));
		assertTrue(ex.getMessage().contains("not a supported FileSourceMode"));
		assertTrue(ex.getMessage().contains("stream"));
	}
}
