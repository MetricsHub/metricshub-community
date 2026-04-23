package org.metricshub.extension.emulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link EmulationPathHelper}.
 */
class EmulationPathHelperTest {

	@TempDir
	Path tempDir;

	@Test
	void testResolveSecurelyWithValidFilename() {
		final Path result = EmulationPathHelper.resolveSecurely(tempDir, "response-001.txt");
		assertNotNull(result);
		assertEquals(tempDir.resolve("response-001.txt").normalize(), result);
	}

	@Test
	void testResolveSecurelyWithSubdirectory() {
		final Path result = EmulationPathHelper.resolveSecurely(tempDir, "subdir/response.txt");
		assertNotNull(result);
		assertEquals(tempDir.resolve("subdir/response.txt").normalize(), result);
	}

	@Test
	void testResolveSecurelyRejectsPathTraversal() {
		assertNull(EmulationPathHelper.resolveSecurely(tempDir, "../../../etc/passwd"));
	}

	@Test
	void testResolveSecurelyRejectsPathTraversalWithSubdir() {
		assertNull(EmulationPathHelper.resolveSecurely(tempDir, "subdir/../../etc/passwd"));
	}

	@Test
	void testResolveSecurelyRejectsAbsolutePath() {
		assertNull(EmulationPathHelper.resolveSecurely(tempDir, "/etc/passwd"));
	}

	@Test
	void testResolveSecurelyAllowsDotInFilename() {
		final Path result = EmulationPathHelper.resolveSecurely(tempDir, "response.v1.txt");
		assertNotNull(result);
	}

	@Test
	void testResolveSecurelyRejectsDoubleDotTraversal() {
		assertNull(EmulationPathHelper.resolveSecurely(tempDir, ".."));
	}
}
