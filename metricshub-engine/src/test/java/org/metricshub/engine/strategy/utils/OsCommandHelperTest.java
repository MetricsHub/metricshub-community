package org.metricshub.engine.strategy.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.WINDOWS_NEW_LINE;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.engine.connector.model.common.EmbeddedFile;

class OsCommandHelperTest {

	@TempDir
	Path tempDir;

	@Test
	void testConvertLineEndings() {
		assertArrayEquals(
			"\r\necho %{SUDO:x}\r\nexit\r\n\r".getBytes(),
			OsCommandHelper.convertLineEndings("\necho %{SUDO:x}\r\nexit\n\r".getBytes(), WINDOWS_NEW_LINE)
		);

		final byte[] text = "echo\n".getBytes();
		assertSame(text, OsCommandHelper.convertLineEndings(text, NEW_LINE));

		final byte[] binary = { 0x7f, 'E', 'L', 'F', 0, '\n' };
		assertSame(binary, OsCommandHelper.convertLineEndings(binary, WINDOWS_NEW_LINE));
	}

	@Test
	void testCreateTempFileWithEmbeddedFileContentWindows() throws IOException {
		final EmbeddedFile embeddedFile = EmbeddedFile.builder()
			.content("%{SUDO:cmd} cmd\nexit\n".getBytes())
			.filename("script.bat")
			.id(1)
			.build();

		final File file = OsCommandHelper.createTempFileWithEmbeddedFileContent(
			embeddedFile,
			null,
			(baseName, extension) -> tempDir.resolve(baseName + extension).toFile(),
			WINDOWS_NEW_LINE
		);

		assertArrayEquals(" cmd\r\nexit\r\n".getBytes(), Files.readAllBytes(file.toPath()));
	}
}
