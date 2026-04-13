package org.metricshub.engine.connector.model.monitor.task.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class FileSourceProcessingModeTest {

	@Test
	void interpretValueOfNullThrows() {
		IllegalArgumentException ex = assertThrows(
			IllegalArgumentException.class,
			() -> FileSourceProcessingMode.interpretValueOf(null)
		);
		assertEquals("FileSourceMode value cannot be null or blank.", ex.getMessage());
	}

	@Test
	void interpretValueOfBlankThrows() {
		IllegalArgumentException ex = assertThrows(
			IllegalArgumentException.class,
			() -> FileSourceProcessingMode.interpretValueOf("   ")
		);
		assertEquals("FileSourceMode value cannot be null or blank.", ex.getMessage());
	}

	@Test
	void interpretValueOfEmptyStringThrows() {
		IllegalArgumentException ex = assertThrows(
			IllegalArgumentException.class,
			() -> FileSourceProcessingMode.interpretValueOf("")
		);
		assertEquals("FileSourceMode value cannot be null or blank.", ex.getMessage());
	}

	@Test
	void interpretValueOfFlatReturnsFlat() {
		assertEquals(FileSourceProcessingMode.FLAT, FileSourceProcessingMode.interpretValueOf("flat"));
		assertEquals(FileSourceProcessingMode.FLAT, FileSourceProcessingMode.interpretValueOf("Flat"));
		assertEquals(FileSourceProcessingMode.FLAT, FileSourceProcessingMode.interpretValueOf("FLAT"));
		assertEquals(FileSourceProcessingMode.FLAT, FileSourceProcessingMode.interpretValueOf("  flat  "));
	}

	@Test
	void interpretValueOfLogReturnsLog() {
		assertEquals(FileSourceProcessingMode.LOG, FileSourceProcessingMode.interpretValueOf("log"));
		assertEquals(FileSourceProcessingMode.LOG, FileSourceProcessingMode.interpretValueOf("Log"));
		assertEquals(FileSourceProcessingMode.LOG, FileSourceProcessingMode.interpretValueOf("LOG"));
		assertEquals(FileSourceProcessingMode.LOG, FileSourceProcessingMode.interpretValueOf("  log  "));
	}

	@Test
	void interpretValueOfUnsupportedThrows() {
		IllegalArgumentException ex = assertThrows(
			IllegalArgumentException.class,
			() -> FileSourceProcessingMode.interpretValueOf("other")
		);
		assertEquals("'other' is not a supported FileSourceMode. Supported values: 'Flat', 'Log'.", ex.getMessage());
	}

	@Test
	void interpretValueOfInvalidThrowsWithValueInMessage() {
		IllegalArgumentException ex = assertThrows(
			IllegalArgumentException.class,
			() -> FileSourceProcessingMode.interpretValueOf("stream")
		);
		assertEquals("'stream' is not a supported FileSourceMode. Supported values: 'Flat', 'Log'.", ex.getMessage());
	}
}
