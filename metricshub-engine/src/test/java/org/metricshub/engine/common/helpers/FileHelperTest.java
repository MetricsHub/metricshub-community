package org.metricshub.engine.common.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.common.helpers.FileHelper.findFilesByPattern;
import static org.metricshub.engine.common.helpers.FileHelper.parsePathPattern;
import static org.metricshub.engine.common.helpers.FileHelper.parseResolvedPathsFromCommandResult;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.engine.common.helpers.FileHelper.PathPattern;
import org.metricshub.engine.connector.model.common.DeviceKind;

class FileHelperTest {

	private static final String HOSTNAME = "hostname";

	private final String LINUX_ABSOLUTE_PATH = "/opt/metricshub/logs/*.log";

	private final String WINDOWS_ABSOLUTE_PATH = "C:\\Program Files\\MetricsHub\\logs\\*.log";

	@TempDir
	Path tempDir;

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
	void parsePathPattern_linuxFilenameWildcard() {
		final PathPattern pattern = parsePathPattern(LINUX_ABSOLUTE_PATH, DeviceKind.AIX);
		assertNotNull(pattern);
		assertEquals("/opt/metricshub/logs", pattern.root());
		assertEquals(List.of("*.log"), pattern.segments());
		assertEquals("*.log", pattern.filename());
		assertEquals(1, pattern.depth());
		assertFalse(pattern.hasDirectoryWildcard());
		assertEquals(LINUX_ABSOLUTE_PATH, pattern.fullPattern());
	}

	@Test
	void parsePathPattern_linuxDirectoryWildcard() {
		final PathPattern pattern = parsePathPattern("/opt/autosys/autouser*/out/event_demon*PE2", DeviceKind.LINUX);
		assertNotNull(pattern);
		assertEquals("/opt/autosys", pattern.root());
		assertEquals(List.of("autouser*", "out", "event_demon*PE2"), pattern.segments());
		assertEquals(3, pattern.depth());
		assertTrue(pattern.hasDirectoryWildcard());
		assertEquals("/opt/autosys/autouser*/out/event_demon*PE2", pattern.fullPattern());
	}

	@Test
	void parsePathPattern_linuxFirstSegmentWildcard_rootIsSlash() {
		final PathPattern pattern = parsePathPattern("/opt*/x.log", DeviceKind.LINUX);
		assertNotNull(pattern);
		assertEquals("/", pattern.root());
		assertEquals(List.of("opt*", "x.log"), pattern.segments());
		assertEquals("/opt*/x.log", pattern.fullPattern());
	}

	@Test
	void parsePathPattern_trailingDelimiter_matchesAllFiles() {
		final PathPattern linux = parsePathPattern("/var/log/", DeviceKind.LINUX);
		assertNotNull(linux);
		assertEquals("/var/log", linux.root());
		assertEquals(List.of("*"), linux.segments());
		assertEquals("/var/log/*", linux.fullPattern());

		final PathPattern windows = parsePathPattern("C:\\logs\\", DeviceKind.WINDOWS);
		assertNotNull(windows);
		assertEquals("C:\\logs", windows.root());
		assertEquals(List.of("*"), windows.segments());
		assertEquals("C:\\logs\\*", windows.fullPattern());
	}

	@Test
	void parsePathPattern_windowsFilenameWildcard() {
		final PathPattern pattern = parsePathPattern(WINDOWS_ABSOLUTE_PATH, DeviceKind.WINDOWS);
		assertNotNull(pattern);
		assertEquals("C:\\Program Files\\MetricsHub\\logs", pattern.root());
		assertEquals(List.of("*.log"), pattern.segments());
		assertFalse(pattern.hasDirectoryWildcard());
		assertEquals(WINDOWS_ABSOLUTE_PATH, pattern.fullPattern());
	}

	@Test
	void parsePathPattern_windowsDirectoryWildcard() {
		final PathPattern pattern = parsePathPattern(
			"D:\\Autosys_waae\\autouser*\\out\\event_demon*PE2",
			DeviceKind.WINDOWS
		);
		assertNotNull(pattern);
		assertEquals("D:\\Autosys_waae", pattern.root());
		assertEquals(List.of("autouser*", "out", "event_demon*PE2"), pattern.segments());
		assertTrue(pattern.hasDirectoryWildcard());
		assertEquals("D:\\Autosys_waae\\autouser*\\out\\event_demon*PE2", pattern.fullPattern());
	}

	@Test
	void parsePathPattern_windowsDriveRoot() {
		final PathPattern pattern = parsePathPattern("D:\\auto*\\x.log", DeviceKind.WINDOWS);
		assertNotNull(pattern);
		assertEquals("D:\\", pattern.root());
		assertEquals(List.of("auto*", "x.log"), pattern.segments());
		assertEquals("D:\\auto*\\x.log", pattern.fullPattern());

		final PathPattern drive = parsePathPattern("D:\\", DeviceKind.WINDOWS);
		assertNotNull(drive);
		assertEquals("D:\\", drive.root());
		assertEquals(List.of("*"), drive.segments());
		assertEquals("D:\\*", drive.fullPattern());
	}

	@Test
	void parsePathPattern_windowsUnc() {
		final PathPattern pattern = parsePathPattern("\\\\server\\share\\a*\\b.log", DeviceKind.WINDOWS);
		assertNotNull(pattern);
		assertEquals("\\\\server\\share", pattern.root());
		assertEquals(List.of("a*", "b.log"), pattern.segments());
		assertEquals("\\\\server\\share\\a*\\b.log", pattern.fullPattern());

		final PathPattern literalDirectories = parsePathPattern("\\\\server\\share\\logs\\*.log", DeviceKind.WINDOWS);
		assertNotNull(literalDirectories);
		assertEquals("\\\\server\\share\\logs", literalDirectories.root());
		assertEquals(List.of("*.log"), literalDirectories.segments());

		// Server and share names are always literal
		assertNull(parsePathPattern("\\\\server\\sha*\\b.log", DeviceKind.WINDOWS));
		assertNull(parsePathPattern("\\\\server", DeviceKind.WINDOWS));
	}

	@Test
	void parsePathPattern_invalid_returnsNull() {
		assertNull(parsePathPattern(null, DeviceKind.LINUX));
		assertNull(parsePathPattern("  ", DeviceKind.LINUX));
		assertNull(parsePathPattern("relative/path.log", DeviceKind.LINUX));
		assertNull(parsePathPattern("file.log", DeviceKind.LINUX));
		assertNull(parsePathPattern("logs\\file.log", DeviceKind.WINDOWS));
		assertNull(parsePathPattern("/opt/x.log", DeviceKind.WINDOWS));
	}

	@Test
	void parsePathPattern_questionMarkIsWildcard() {
		final PathPattern pattern = parsePathPattern("/opt/node?/app.log", DeviceKind.LINUX);
		assertNotNull(pattern);
		assertEquals("/opt", pattern.root());
		assertEquals(List.of("node?", "app.log"), pattern.segments());
		assertTrue(pattern.hasDirectoryWildcard());
		assertTrue(FileHelper.containsWildcard("node?"));
		assertTrue(FileHelper.containsWildcard("*"));
		assertFalse(FileHelper.containsWildcard("node"));
		assertFalse(FileHelper.containsWildcard(null));
	}

	@Test
	void escapeGlobSpecials_keepsOnlyStarAndQuestionMarkAsWildcards() {
		assertEquals("app\\[1\\]*.log?", FileHelper.escapeGlobSpecials("app[1]*.log?"));
		assertEquals("a\\{b\\}", FileHelper.escapeGlobSpecials("a{b}"));
		assertEquals("plain", FileHelper.escapeGlobSpecials("plain"));
	}

	@Test
	void escapePowerShellBrackets_doublesBacktickForDoubleQuotedString() {
		assertEquals("C:\\logs\\app``[1``].log", FileHelper.escapePowerShellBrackets("C:\\logs\\app[1].log"));
		assertEquals("D:\\``[prod``]\\node*\\app?.log", FileHelper.escapePowerShellBrackets("D:\\[prod]\\node*\\app?.log"));
		assertEquals("C:\\logs\\*.log", FileHelper.escapePowerShellBrackets("C:\\logs\\*.log"));
	}

	private DeviceKind localDeviceKind() {
		return LocalOsHandler.isWindows() ? DeviceKind.WINDOWS : DeviceKind.LINUX;
	}

	private String localPattern(final String... segments) {
		return tempDir.toString() + File.separator + String.join(File.separator, segments);
	}

	private String createTreeFile(final String... segments) throws IOException {
		Path file = tempDir;
		for (final String segment : segments) {
			file = file.resolve(segment);
		}
		Files.createDirectories(file.getParent());
		Files.createFile(file);
		return file.toString();
	}

	/**
	 * Builds the following tree under {@link #tempDir} and returns the two files matching a pattern with a
	 * wildcard in both the first directory segment and the filename (autouser prefix, out, event_demon prefix, PE2 suffix):
	 * <pre>
	 * autouser01/out/event_demon.PE2
	 * autouser02/out/event_demon_XPE2
	 * autouser02/out/event_demonPE2.log
	 * autouser02/out/event_demon_DIRPE2/   (directory)
	 * other/out/event_demon.PE2
	 * </pre>
	 */
	private Set<String> createTree() throws IOException {
		final String first = createTreeFile("autouser01", "out", "event_demon.PE2");
		final String second = createTreeFile("autouser02", "out", "event_demon_XPE2");
		createTreeFile("autouser02", "out", "event_demonPE2.log");
		Files.createDirectories(tempDir.resolve("autouser02").resolve("out").resolve("event_demon_DIRPE2"));
		createTreeFile("other", "out", "event_demon.PE2");
		return Set.of(first, second);
	}

	@Test
	void findFilesByPattern_directoryWildcard() throws IOException {
		final Set<String> expected = createTree();

		final Set<String> resolved = findFilesByPattern(
			HOSTNAME,
			Set.of(localPattern("autouser*", "out", "event_demon*PE2")),
			localDeviceKind()
		);

		assertEquals(expected, resolved);
	}

	@Test
	void findFilesByPattern_filenameWildcardOnly() throws IOException {
		createTree();

		final Set<String> resolved = findFilesByPattern(
			HOSTNAME,
			Set.of(localPattern("autouser02", "out", "event_demon*")),
			localDeviceKind()
		);

		// The matching directory event_demon_DIRPE2 is excluded
		assertEquals(
			Set.of(
				tempDir.resolve("autouser02").resolve("out").resolve("event_demon_XPE2").toString(),
				tempDir.resolve("autouser02").resolve("out").resolve("event_demonPE2.log").toString()
			),
			resolved
		);
	}

	@Test
	void findFilesByPattern_questionMarkWildcard() throws IOException {
		createTree();

		final Set<String> resolved = findFilesByPattern(
			HOSTNAME,
			Set.of(localPattern("autouser0?", "out", "event_demon.PE2")),
			localDeviceKind()
		);

		assertEquals(Set.of(tempDir.resolve("autouser01").resolve("out").resolve("event_demon.PE2").toString()), resolved);
	}

	@Test
	void findFilesByPattern_literalFileAndLiteralDirectory() throws IOException {
		createTree();

		final String literalFile = localPattern("autouser01", "out", "event_demon.PE2");
		assertEquals(Set.of(literalFile), findFilesByPattern(HOSTNAME, Set.of(literalFile), localDeviceKind()));

		// A literal directory without a trailing delimiter is not a regular file
		assertTrue(findFilesByPattern(HOSTNAME, Set.of(localPattern("autouser01", "out")), localDeviceKind()).isEmpty());
	}

	@Test
	void findFilesByPattern_trailingDelimiterListsAllFiles() throws IOException {
		createTree();

		final Set<String> resolved = findFilesByPattern(
			HOSTNAME,
			Set.of(localPattern("autouser02", "out") + File.separator),
			localDeviceKind()
		);

		assertEquals(
			Set.of(
				tempDir.resolve("autouser02").resolve("out").resolve("event_demon_XPE2").toString(),
				tempDir.resolve("autouser02").resolve("out").resolve("event_demonPE2.log").toString()
			),
			resolved
		);
	}

	@Test
	void findFilesByPattern_nonExistingRootAndNullPaths() throws IOException {
		createTree();

		assertTrue(findFilesByPattern(HOSTNAME, Set.of(localPattern("nope", "*.log")), localDeviceKind()).isEmpty());
		assertTrue(
			findFilesByPattern(HOSTNAME, Set.of(localPattern("nope*", "out", "*.log")), localDeviceKind()).isEmpty()
		);
		assertTrue(findFilesByPattern(HOSTNAME, null, localDeviceKind()).isEmpty());
	}

	@Test
	void findFilesByPattern_invalidPatternDoesNotHideValidOnes() throws IOException {
		final Set<String> expected = createTree();

		final Set<String> resolved = findFilesByPattern(
			HOSTNAME,
			Set.of(localPattern("autouser*", "out", "event_demon*PE2"), "relative/path.log"),
			localDeviceKind()
		);

		assertEquals(expected, resolved);
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	void findFilesByPattern_illegalCharacterDoesNotHideValidOnes() throws IOException {
		final Set<String> expected = createTree();

		// ':' is illegal in a Windows path segment and makes Path.of throw InvalidPathException
		final Set<String> resolved = findFilesByPattern(
			HOSTNAME,
			Set.of(localPattern("autouser*", "out", "event_demon*PE2"), localPattern("bad:name", "*.log")),
			DeviceKind.WINDOWS
		);

		assertEquals(expected, resolved);
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

	@Test
	void escapeSemiColon_replacesSemicolons() {
		assertEquals("a,b", FileHelper.escapeSemiColon("a;b"));
		assertEquals("no change", FileHelper.escapeSemiColon("no change"));
	}

	@Test
	void isSingleAbsolutePath_trueWhenSingleMatchingPathAndOneRow() {
		final Set<String> paths = Set.of("/var/log/app.log");
		final Set<String> resolved = Set.of("/var/log/app.log");
		final List<List<String>> oneRow = List.of(List.of("/var/log/app.log", "content"));
		assertTrue(FileHelper.isSinglePathMapping(paths, resolved, oneRow));
	}

	@Test
	void isSingleAbsolutePath_falseWhenPatternsDiffer() {
		final Set<String> paths = Set.of("/var/log/*.log");
		final Set<String> resolved = Set.of("/var/log/app.log");
		final List<List<String>> oneRow = List.of(List.of("/var/log/app.log", "content"));
		assertFalse(FileHelper.isSinglePathMapping(paths, resolved, oneRow));
	}

	@Test
	void isSingleAbsolutePath_falseWhenMultipleConfiguredOrResolved() {
		final List<List<String>> oneRow = List.of(List.of("/a", "content"));
		assertFalse(FileHelper.isSinglePathMapping(Set.of("/a", "/b"), Set.of("/a"), oneRow));
		assertFalse(FileHelper.isSinglePathMapping(Set.of("/a"), Set.of("/a", "/b"), oneRow));
	}

	@Test
	void isSingleAbsolutePath_falseWhenMonitoringResultsSizeIsNotOne() {
		final Set<String> paths = Set.of("/var/log/app.log");
		final Set<String> resolved = Set.of("/var/log/app.log");
		assertFalse(FileHelper.isSinglePathMapping(paths, resolved, List.of()));
		assertFalse(
			FileHelper.isSinglePathMapping(
				paths,
				resolved,
				List.of(List.of("/var/log/app.log", "a"), List.of("/var/log/app.log", "b"))
			)
		);
	}

	@Test
	void buildLogBlock_singleAbsolutePath_emitsContentOnlyRows() {
		final List<List<String>> rows = new ArrayList<>();
		rows.add(List.of("/opt/a.log", "line1;part"));
		final String out = FileHelper.buildLogBlock(rows, Set.of("/opt/a.log"), Set.of("/opt/a.log"));
		assertEquals("line1,part", out);
	}

	@Test
	void buildLogBlock_multipleResolution_emitsOneMarkedCell() {
		final List<List<String>> rows = new ArrayList<>();
		rows.add(List.of("/opt/a.log", FileHelper.escapeNewLines("A\n")));
		rows.add(List.of("/opt/b.log", FileHelper.escapeNewLines("B")));
		final String out = FileHelper.buildLogBlock(rows, Set.of("/opt/*.log"), Set.of("/opt/a.log", "/opt/b.log"));
		assertNotNull(out);
		assertTrue(out.contains("<<<LOG:file=\"/opt/a.log\">>>"));
		assertTrue(out.contains("<<<LOG:file=\"/opt/b.log\">>>"));
		assertTrue(out.contains("<<<END_LOG>>>"));
		assertTrue(out.contains(FileHelper.escapeSemiColon(FileHelper.escapeNewLines("A\n"))));
		assertTrue(out.contains(FileHelper.escapeSemiColon(FileHelper.escapeNewLines("B"))));
	}

	@Test
	void buildLogBlock_emptyWhenNoRowsAndNotSingleAbsolute() {
		final String out = FileHelper.buildLogBlock(new ArrayList<>(), Set.of("/opt/*.log"), Set.of("/opt/a.log"));
		assertNull(out);
	}
}
