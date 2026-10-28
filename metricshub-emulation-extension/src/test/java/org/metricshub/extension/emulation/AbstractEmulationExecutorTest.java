package org.metricshub.extension.emulation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link AbstractEmulationExecutor}.
 */
class AbstractEmulationExecutorTest {

	@TempDir
	Path tempDir;

	@Test
	void testReadResponseFileConvertsCrlfToLf() throws Exception {
		final Path crlf = Files.writeString(tempDir.resolve("crlf.txt"), "a;b\r\nc;d\r\n");
		final Path lf = Files.writeString(tempDir.resolve("lf.txt"), "a;b\nc;d\n");
		assertEquals("a;b\nc;d\n", AbstractEmulationExecutor.readResponseFile(crlf));
		assertEquals("a;b\nc;d\n", AbstractEmulationExecutor.readResponseFile(lf));
	}
}
