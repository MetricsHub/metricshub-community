package org.metricshub.engine.common.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.common.helpers.FileHelper.extractBasePath;
import static org.metricshub.engine.common.helpers.FileHelper.extractFilename;
import static org.metricshub.engine.common.helpers.FileHelper.parseResolvedPathsFromCommandResult;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.common.DeviceKind;

class FileHelperTest {

	private static final String HOSTNAME = "hostname";

	private final String LINUX_ABSOLUTE_PATH = "/opt/metricshub/logs/*.log";

	private final String WINDOWS_ABSOLUTE_PATH = "C:\\Program Files\\MetricsHub\\logs\\*.log";

	@Test
	void testGetExtension() {
		// Verify extensions
		assertEquals(".pdf", FileHelper.getExtension("example.pdf"));
		assertEquals(".gz", FileHelper.getExtension("archive.tar.gz"));
		assertEquals(MetricsHubConstants.EMPTY, FileHelper.getExtension(".env"));
		assertEquals(MetricsHubConstants.EMPTY, FileHelper.getExtension("no_extension"));
	}

	@Test
	void testGetBaseName() {
		// Verify base names
		assertEquals("example", FileHelper.getBaseName("example.pdf"));
		assertEquals("archive.tar", FileHelper.getBaseName("archive.tar.gz"));
		assertEquals(".env", FileHelper.getBaseName(".env"));
		assertEquals("no_extension", FileHelper.getBaseName("no_extension"));
	}

	@Test
	void testExtractBasePath() {
		// Null and empty path
		assertEquals("", extractBasePath(null, DeviceKind.AIX));
		assertEquals("", extractBasePath("", DeviceKind.AIX));

		// Linux Paths
		assertEquals("/opt/metricshub/logs", extractBasePath(LINUX_ABSOLUTE_PATH, DeviceKind.AIX));

		// Windows Paths
		assertEquals("C:\\Program Files\\MetricsHub\\logs", extractBasePath(WINDOWS_ABSOLUTE_PATH, DeviceKind.WINDOWS));
	}

	@Test
	void testExtractFilename() {
		// Null and empty path
		assertEquals("", extractFilename(null, DeviceKind.AIX));
		assertEquals("", extractFilename("", DeviceKind.AIX));

		// Linux paths
		assertEquals("*.log", extractFilename(LINUX_ABSOLUTE_PATH, DeviceKind.AIX));

		// Windows paths
		assertEquals("*.log", extractFilename(WINDOWS_ABSOLUTE_PATH, DeviceKind.WINDOWS));
	}

	@Test
	void parseResolvedPathsFromCommandResult_emptyOrNull_returnsEmpty() {
		assertTrue(parseResolvedPathsFromCommandResult(null, DeviceKind.WINDOWS, HOSTNAME, "C:\\temp\\*.log").isEmpty());
		assertTrue(parseResolvedPathsFromCommandResult("", DeviceKind.WINDOWS, HOSTNAME, "C:\\temp\\*.log").isEmpty());
	}

	@Test
	void parseResolvedPathsFromCommandResult_windowsDrivePath_returnsPath() {
		final String path = "C:\\Users\\Administrator\\Desktop\\filesource\\file1.log";
		final var result = parseResolvedPathsFromCommandResult(path, DeviceKind.WINDOWS, HOSTNAME, "C:\\temp\\*.log");
		assertEquals(Set.of(path), result);
	}

	@Test
	void parseResolvedPathsFromCommandResult_windowsUncPath_returnsPath() {
		final String path = "\\\\server\\share\\folder\\file.log";
		final var result = parseResolvedPathsFromCommandResult(
			path,
			DeviceKind.WINDOWS,
			HOSTNAME,
			"\\\\server\\share\\*.log"
		);
		assertEquals(Set.of(path), result);
	}

	@Test
	void parseResolvedPathsFromCommandResult_windowsInvalidLine_skipped() {
		final String invalidOutput = "Get-ChildItem : Cannot find path...";
		final var result = parseResolvedPathsFromCommandResult(
			invalidOutput,
			DeviceKind.WINDOWS,
			HOSTNAME,
			"C:\\temp\\*.log"
		);
		assertTrue(result.isEmpty());
	}

	@Test
	void parseResolvedPathsFromCommandResult_windowsMixedValidAndInvalid_returnsOnlyValid() {
		final String validPath = "C:\\temp\\valid.log";
		final String input = "Warning: some message\n" + validPath + "\nNot a path\nError: something";
		final var result = parseResolvedPathsFromCommandResult(input, DeviceKind.WINDOWS, HOSTNAME, "C:\\temp\\*.log");
		assertEquals(Set.of(validPath), result);
	}

	@Test
	void parseResolvedPathsFromCommandResult_windowsSplitsByNewline() {
		final String path1 = "C:\\temp\\file1.log";
		final String path2 = "C:\\temp\\file2.log";
		final String input = path1 + "\n" + path2;
		final var result = parseResolvedPathsFromCommandResult(input, DeviceKind.WINDOWS, HOSTNAME, "C:\\temp\\*.log");
		assertEquals(Set.of(path1, path2), result);
	}

	@Test
	void parseResolvedPathsFromCommandResult_windowsSplitsByCrLf() {
		final String path1 = "C:\\temp\\file1.log";
		final String path2 = "C:\\temp\\file2.log";
		final String input = path1 + "\r\n" + path2;
		final var result = parseResolvedPathsFromCommandResult(input, DeviceKind.WINDOWS, HOSTNAME, "C:\\temp\\*.log");
		assertEquals(Set.of(path1, path2), result);
	}

	@Test
	void parseResolvedPathsFromCommandResult_windowsEmptyLinesAndTrailingNewline_skipped() {
		final String path = "C:\\temp\\file.log";
		final String input = "\n\n" + path + "\n\n";
		final var result = parseResolvedPathsFromCommandResult(input, DeviceKind.WINDOWS, HOSTNAME, "C:\\temp\\*.log");
		assertEquals(Set.of(path), result);
	}

	@Test
	void parseResolvedPathsFromCommandResult_linuxAbsolutePath_returnsPath() {
		final String path = "/opt/metricshub/logs/test.log";
		final var result = parseResolvedPathsFromCommandResult(
			path,
			DeviceKind.LINUX,
			HOSTNAME,
			"/opt/metricshub/logs/*.log"
		);
		assertEquals(Set.of(path), result);
	}

	@Test
	void parseResolvedPathsFromCommandResult_linuxInvalidLine_skipped() {
		final String invalidOutput = "find: Permission denied";
		final var result = parseResolvedPathsFromCommandResult(invalidOutput, DeviceKind.LINUX, HOSTNAME, "/opt/*.log");
		assertTrue(result.isEmpty());
	}

	@Test
	void parseResolvedPathsFromCommandResult_linuxMixedValidAndInvalid_returnsOnlyValid() {
		final String validPath = "/var/log/app.log";
		final String input = "find: some warning\n" + validPath + "\nrelative/path\n";
		final var result = parseResolvedPathsFromCommandResult(input, DeviceKind.LINUX, HOSTNAME, "/var/log/*.log");
		assertEquals(Set.of(validPath), result);
	}

	@Test
	void testEscapeNewLines_handlesAllNewlineVariants() {
		assertEquals(
			"Line1@{newLine}@Line2",
			FileHelper.escapeNewLines("Line1\r\nLine2"),
			"escapeNewLines() must replace Windows line endings (\\r\\n) with @{newLine}@."
		);

		assertEquals(
			"Line1@{newLine}@Line2",
			FileHelper.escapeNewLines("Line1\rLine2"),
			"escapeNewLines() must replace Mac line endings (\\r) with @{newLine}@."
		);

		assertEquals(
			"Line1@{newLine}@@{newLine}@Line2",
			FileHelper.escapeNewLines("Line1\r\n\r\nLine2"),
			"escapeNewLines() must replace multiple Windows line endings."
		);

		assertEquals(
			"Line1@{newLine}@Line2@{newLine}@Line3",
			FileHelper.escapeNewLines("Line1\nLine2\r\nLine3"),
			"escapeNewLines() must handle mixed line endings."
		);

		assertEquals(null, FileHelper.escapeNewLines(null), "escapeNewLines(null) must return null.");
		assertEquals(
			"No newlines",
			FileHelper.escapeNewLines("No newlines"),
			"escapeNewLines() must preserve strings without newlines."
		);
	}
}
