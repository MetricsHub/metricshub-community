package org.metricshub.engine.strategy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.common.EmbeddedFile;

class EmbeddedFileHelperTest {

	private static final EmbeddedFile EMBEDDED_FILE1 = EmbeddedFile
		.builder()
		.content("BEGIN { print \"welcome!\" }".getBytes())
		.filename("script-welcome.awk")
		.id(1)
		.build();
	private static final EmbeddedFile EMBEDDED_FILE2 = EmbeddedFile
		.builder()
		.content("BEGIN { print \"hello!\" }".getBytes())
		.filename("script-hello.awk")
		.id(2)
		.build();
	private static final String FILE_REF_FORMAT = "${file::%d}";

	@Test
	void testFindEmbeddedFiles() throws IOException {
		final Map<Integer, EmbeddedFile> connectorEmbeddedFiles = Map.of(1, EMBEDDED_FILE1, 2, EMBEDDED_FILE2);
		final Map<Integer, EmbeddedFile> expected = Map.of(1, EMBEDDED_FILE1);
		final Map<Integer, EmbeddedFile> actual = EmbeddedFileHelper.findEmbeddedFiles(
			String.format(FILE_REF_FORMAT, 1),
			connectorEmbeddedFiles
		);

		assertEquals(expected, actual);
	}

	@Test
	void testFindEmbeddedFile() throws IOException {
		final Map<Integer, EmbeddedFile> connectorEmbeddedFiles = Map.of(1, EMBEDDED_FILE1, 2, EMBEDDED_FILE2);

		assertEquals(
			Optional.of(EMBEDDED_FILE1),
			EmbeddedFileHelper.findEmbeddedFile(
				String.format(FILE_REF_FORMAT, 1),
				connectorEmbeddedFiles,
				"hostname",
				"connector-id"
			)
		);
	}
}
