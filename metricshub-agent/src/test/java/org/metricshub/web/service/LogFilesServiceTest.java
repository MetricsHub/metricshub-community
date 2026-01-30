package org.metricshub.web.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.agent.helper.ConfigHelper;
import org.metricshub.web.dto.LogFile;
import org.metricshub.web.exception.LogFilesException;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class LogFilesServiceTest {

	@TempDir
	Path tempLogsDir;

	/**
	 * Create a LogFilesService with a mocked ConfigHelper that returns the given
	 * logs dir.
	 *
	 * @param dir the logs dir to return from the mocked ConfigHelper
	 * @return the service instance
	 */
	private LogFilesService newServiceWithDir(Path dir) {
		return new LogFilesService() {
			@Override
			public List<LogFile> getAllLogFiles() throws LogFilesException {
				return getAllLogFilesWithMockedDir(dir);
			}

			@Override
			public String getFileTail(final String fileName, final long maxBytes) throws LogFilesException {
				return getFileTailWithMockedDir(dir, fileName, maxBytes);
			}

			@Override
			public byte[] getFileForDownload(final String fileName) throws LogFilesException {
				return getFileForDownloadWithMockedDir(dir, fileName);
			}
		};
	}

	/**
	 * Helper method to get all log files using a mocked directory.
	 */
	private List<LogFile> getAllLogFilesWithMockedDir(Path dir) throws LogFilesException {
		try (MockedStatic<ConfigHelper> mockedConfigHelper = Mockito.mockStatic(ConfigHelper.class)) {
			mockedConfigHelper.when(ConfigHelper::getDefaultOutputDirectory).thenReturn(dir);
			return new LogFilesService().getAllLogFiles();
		}
	}

	/**
	 * Helper method to get file tail using a mocked directory.
	 */
	private String getFileTailWithMockedDir(Path dir, String fileName, long maxBytes) throws LogFilesException {
		try (MockedStatic<ConfigHelper> mockedConfigHelper = Mockito.mockStatic(ConfigHelper.class)) {
			mockedConfigHelper.when(ConfigHelper::getDefaultOutputDirectory).thenReturn(dir);
			return new LogFilesService().getFileTail(fileName, maxBytes);
		}
	}

	/**
	 * Helper method to get file for download using a mocked directory.
	 */
	private byte[] getFileForDownloadWithMockedDir(Path dir, String fileName) throws LogFilesException {
		try (MockedStatic<ConfigHelper> mockedConfigHelper = Mockito.mockStatic(ConfigHelper.class)) {
			mockedConfigHelper.when(ConfigHelper::getDefaultOutputDirectory).thenReturn(dir);
			return new LogFilesService().getFileForDownload(fileName);
		}
	}

	@Test
	void testShouldListLogFilesAtDepthOneSorted() throws Exception {
		final Path aLog = tempLogsDir.resolve("a.log");
		final Path bLog = tempLogsDir.resolve("B.log");
		final Path notLog = tempLogsDir.resolve("ignore.txt");
		final Path subDir = tempLogsDir.resolve("sub");
		final Path deepLog = subDir.resolve("deep.log");

		Files.writeString(aLog, "Log entry 1");
		Files.writeString(bLog, "Log entry 2");
		Files.writeString(notLog, "Not a log file");
		Files.createDirectories(subDir);
		Files.writeString(deepLog, "Too deep");

		final LogFilesService service = newServiceWithDir(tempLogsDir);

		final List<LogFile> files = service.getAllLogFiles();

		assertEquals(2, files.size(), "Only .log files at depth 1 should be listed");

		final LogFile first = files.get(0);
		final LogFile second = files.get(1);

		assertEquals("a.log", first.getName(), "First file should be a.log");
		assertEquals("B.log", second.getName(), "Second file should be B.log");

		assertEquals(Files.size(aLog), first.getSize(), "Size of a.log should match");
		assertEquals(Files.size(bLog), second.getSize(), "Size of B.log should match");

		assertNotNull(first.getLastModificationTime(), "lastModificationTime must not be null");
		assertNotNull(second.getLastModificationTime(), "lastModificationTime must not be null");
	}

	@Test
	void testGetFileTailOk() throws Exception {
		final LogFilesService service = newServiceWithDir(tempLogsDir);
		final Path file = tempLogsDir.resolve("metricshub.log");
		Files.writeString(file, "hello world log", StandardCharsets.UTF_8);

		String content = service.getFileTail("metricshub.log", LogFilesService.DEFAULT_MAX_TAIL_BYTES);
		assertEquals("hello world log", content, "File content should match");
	}

	@Test
	void testGetFileTailReturnsLastNBytes() throws Exception {
		final LogFilesService service = newServiceWithDir(tempLogsDir);
		final Path file = tempLogsDir.resolve("test.log");
		final String fullContent = "0123456789ABCDEF"; // 16 bytes
		Files.writeString(file, fullContent, StandardCharsets.UTF_8);

		String content = service.getFileTail("test.log", 5);
		assertEquals("BCDEF", content, "Should return last 5 bytes");
	}

	@Test
	void testGetFileTailNotFound() {
		final LogFilesService service = newServiceWithDir(tempLogsDir);

		LogFilesException ex = assertThrows(
			LogFilesException.class,
			() -> service.getFileTail("missing.log", 1024),
			"Service should throw when file is missing"
		);
		assertEquals(LogFilesException.Code.FILE_NOT_FOUND, ex.getCode(), "Error code should match");
		assertTrue(ex.getMessage().toLowerCase().contains("not found"), "Message should indicate not found");
	}

	@Test
	void testGetFileTailInvalidNameRejectTraversal() {
		final LogFilesService service = newServiceWithDir(tempLogsDir);

		LogFilesException ex = assertThrows(
			LogFilesException.class,
			() -> service.getFileTail("../evil.log", 1024),
			"Service should throw on path traversal attempt"
		);
		assertEquals(LogFilesException.Code.INVALID_FILE_NAME, ex.getCode(), "Error code should match");
	}

	@Test
	void testGetFileTailInvalidExtension() {
		final LogFilesService service = newServiceWithDir(tempLogsDir);

		LogFilesException ex = assertThrows(
			LogFilesException.class,
			() -> service.getFileTail("config.txt", 1024),
			"Service should throw on invalid file extension"
		);
		assertEquals(LogFilesException.Code.INVALID_FILE_NAME, ex.getCode(), "Error code should match");
		assertTrue(ex.getMessage().contains(".log"), "Message should indicate valid extensions");
	}

	@Test
	void testGetFileForDownloadOk() throws Exception {
		final LogFilesService service = newServiceWithDir(tempLogsDir);
		final Path file = tempLogsDir.resolve("download.log");
		final byte[] expectedContent = "Full log content".getBytes(StandardCharsets.UTF_8);
		Files.write(file, expectedContent);

		byte[] content = service.getFileForDownload("download.log");
		assertArrayEquals(expectedContent, content, "File content should match");
	}

	@Test
	void testGetFileForDownloadNotFound() {
		final LogFilesService service = newServiceWithDir(tempLogsDir);

		LogFilesException ex = assertThrows(
			LogFilesException.class,
			() -> service.getFileForDownload("missing.log"),
			"Service should throw when file is missing"
		);
		assertEquals(LogFilesException.Code.FILE_NOT_FOUND, ex.getCode(), "Error code should match");
	}

	@Test
	void testGetFileForDownloadInvalidNameRejectTraversal() {
		final LogFilesService service = newServiceWithDir(tempLogsDir);

		LogFilesException ex = assertThrows(
			LogFilesException.class,
			() -> service.getFileForDownload("../../../etc/passwd.log"),
			"Service should throw on path traversal attempt"
		);
		assertEquals(LogFilesException.Code.INVALID_FILE_NAME, ex.getCode(), "Error code should match");
	}

	@Test
	void testGetFileTailEmptyFile() throws Exception {
		final LogFilesService service = newServiceWithDir(tempLogsDir);
		final Path file = tempLogsDir.resolve("empty.log");
		Files.writeString(file, "", StandardCharsets.UTF_8);

		String content = service.getFileTail("empty.log", 1024);
		assertEquals("", content, "Empty file should return empty string");
	}
}
