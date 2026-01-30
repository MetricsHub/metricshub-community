package org.metricshub.web.service;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2025 MetricsHub
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.helper.ConfigHelper;
import org.metricshub.web.dto.LogFile;
import org.metricshub.web.exception.LogFilesException;
import org.springframework.stereotype.Service;

/**
 * Service class for managing log files.
 */
@Slf4j
@Service
public class LogFilesService {

    /**
     * Default maximum bytes to read from the tail of a log file (1 MB).
     */
    public static final long DEFAULT_MAX_TAIL_BYTES = 1024 * 1024;

    /**
     * Allowed file extensions for log files.
     */
    private static final Set<String> LOG_EXTENSIONS = Set.of(".log");

    /**
     * Retrieves a list of all log files with their metadata.
     *
     * @return A list of {@link LogFile} representing all log files.
     * @throws LogFilesException if an IO error occurs when listing files
     */
    public List<LogFile> getAllLogFiles() throws LogFilesException {
        final Path logsDirectory = getLogsDir();

        // Only list top-level log files
        try (Stream<Path> files = Files.list(logsDirectory)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(LogFilesService::hasLogExtension)
                    .map(this::buildLogFile)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(LogFile::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (Exception e) {
            log.error("Failed to list logs directory: '{}'. Error: {}", logsDirectory, e.getMessage());
            log.debug("Failed to list logs directory: '{}'. Exception:", logsDirectory, e);
            throw new LogFilesException(LogFilesException.Code.IO_FAILURE, "Failed to list log files.", e);
        }
    }

    /**
     * Returns the content of the tail (last N bytes) of a log file as UTF-8 text.
     *
     * @param fileName the log file name (e.g. metricshub.log)
     * @param maxBytes the maximum number of bytes to read from the end of the file
     * @return the tail content of the file
     * @throws LogFilesException if the file is not found or cannot be read
     */
    public String getFileTail(final String fileName, final long maxBytes) throws LogFilesException {
        final Path dir = getLogsDir();
        final Path file = resolveSafeLogFile(dir, fileName);
        log.info("Reading tail of log file: {}", file.toAbsolutePath());

        if (!Files.exists(file)) {
            throw new LogFilesException(LogFilesException.Code.FILE_NOT_FOUND, "Log file not found.");
        }

        try {
            final long fileSize = Files.size(file);
            final long bytesToRead = Math.min(maxBytes, fileSize);
            final long startPosition = fileSize - bytesToRead;

            try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
                raf.seek(startPosition);
                final byte[] buffer = new byte[(int) bytesToRead];
                final int bytesRead = raf.read(buffer);
                if (bytesRead <= 0) {
                    return "";
                }
                return new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.error("Failed to read log file: '{}'. Error: {}", file, e.getMessage());
            log.debug("Failed to read log file: '{}'. Exception:", file, e);
            throw new LogFilesException(LogFilesException.Code.IO_FAILURE, "Failed to read log file.", e);
        }
    }

    /**
     * Returns the full content of a log file as a byte array for download.
     *
     * @param fileName the log file name (e.g. metricshub.log)
     * @return the file content as byte array
     * @throws LogFilesException if the file is not found or cannot be read
     */
    public byte[] getFileForDownload(final String fileName) throws LogFilesException {
        final Path dir = getLogsDir();
        final Path file = resolveSafeLogFile(dir, fileName);
        log.info("Preparing log file for download: {}", file.toAbsolutePath());

        if (!Files.exists(file)) {
            throw new LogFilesException(LogFilesException.Code.FILE_NOT_FOUND, "Log file not found.");
        }

        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            log.error("Failed to read log file for download: '{}'. Error: {}", file, e.getMessage());
            log.debug("Failed to read log file for download: '{}'. Exception:", file, e);
            throw new LogFilesException(LogFilesException.Code.IO_FAILURE, "Failed to read log file.", e);
        }
    }

    /**
     * Deletes a log file.
     *
     * @param fileName the log file name (e.g. metricshub.log)
     * @throws LogFilesException if the file cannot be deleted
     */
    public void deleteFile(final String fileName) throws LogFilesException {
        final Path dir = getLogsDir();
        final Path file = resolveSafeLogFile(dir, fileName);
        log.info("Deleting log file: {}", file.toAbsolutePath());

        try {
            if (!Files.deleteIfExists(file)) {
                throw new LogFilesException(LogFilesException.Code.FILE_NOT_FOUND, "Log file not found.");
            }
        } catch (IOException e) {
            log.error("Failed to delete log file: '{}'. Error: {}", file, e.getMessage());
            log.debug("Failed to delete log file: '{}'. Exception:", file, e);
            throw new LogFilesException(LogFilesException.Code.IO_FAILURE, "Failed to delete log file.", e);
        }
    }

    /**
     * Deletes all log files in the logs directory.
     *
     * @return the number of files deleted
     * @throws LogFilesException if an IO error occurs during deletion
     */
    public int deleteAllFiles() throws LogFilesException {
        final Path logsDirectory = getLogsDir();
        log.info("Deleting all log files in: {}", logsDirectory.toAbsolutePath());

        int deletedCount = 0;
        try (Stream<Path> files = Files.list(logsDirectory)) {
            final List<Path> logFiles = files.filter(Files::isRegularFile).filter(LogFilesService::hasLogExtension)
                    .toList();

            for (Path file : logFiles) {
                try {
                    Files.deleteIfExists(file);
                    deletedCount++;
                } catch (IOException e) {
                    log.warn("Failed to delete log file: '{}'. Error: {}", file, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to list logs directory for deletion: '{}'. Error: {}", logsDirectory, e.getMessage());
            log.debug("Failed to list logs directory for deletion: '{}'. Exception:", logsDirectory, e);
            throw new LogFilesException(LogFilesException.Code.IO_FAILURE, "Failed to delete log files.", e);
        }

        return deletedCount;
    }

    /**
     * Builds a {@link LogFile} from the given file path.
     *
     * @param path the path to the log file
     * @return the LogFile with file metadata, or null if an error occurs
     */
    private LogFile buildLogFile(final Path path) {
        try {
            return LogFile
                    .builder()
                    .name(path.getFileName().toString())
                    .size(Files.size(path))
                    .lastModificationTime(Files.getLastModifiedTime(path).toString())
                    .build();
        } catch (IOException e) {
            log.error("Failed to read log file metadata: '{}'. Error: {}", path, e.getMessage());
            log.debug("Failed to read log file metadata: '{}'. Exception:", path, e);
            return null;
        }
    }

    /**
     * Checks if the file has a valid log extension.
     *
     * @param path the path to the file
     * @return true if the file has a valid log extension, false otherwise
     */
    private static boolean hasLogExtension(Path path) {
        final var fileName = path.getFileName().toString();
        return hasLogExtension(fileName);
    }

    /**
     * Checks if the file name has a valid log extension.
     *
     * @param fileName the file name we want to check
     * @return true if the file name has a valid log extension, false otherwise
     */
    private static boolean hasLogExtension(final String fileName) {
        final String lower = fileName.toLowerCase(Locale.ROOT);
        return LOG_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    /**
     * Resolves and validates a log file name within a base directory (no
     * traversal, correct extension).
     *
     * @param baseDir  logs directory
     * @param fileName simple file name (no path)
     * @return resolved path
     * @throws LogFilesException if the file name is invalid or resolution fails
     */
    private Path resolveSafeLogFile(final Path baseDir, final String fileName) throws LogFilesException {
        validateSimpleFileName(fileName);
        if (!hasLogExtension(fileName)) {
            throw new LogFilesException(LogFilesException.Code.INVALID_FILE_NAME, "Only .log files are allowed.");
        }
        return resolveWithinDir(baseDir, fileName);
    }

    /**
     * Validates that the file name is simple (no path separators or traversal).
     *
     * @param fileName the file name to validate
     * @throws LogFilesException if the file name is invalid
     */
    private void validateSimpleFileName(final String fileName) throws LogFilesException {
        if (fileName == null || fileName.isBlank()) {
            throw new LogFilesException(LogFilesException.Code.INVALID_FILE_NAME, "File name cannot be empty.");
        }
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            throw new LogFilesException(
                    LogFilesException.Code.INVALID_FILE_NAME,
                    "File name must not contain path separators or traversal sequences.");
        }
    }

    /**
     * Resolves and validates a file name within a base directory (no traversal).
     *
     * @param baseDir  base directory
     * @param fileName simple file name (no path)
     * @return resolved path
     * @throws LogFilesException if resolution fails or path escapes the base
     *                           directory
     */
    private Path resolveWithinDir(final Path baseDir, final String fileName) throws LogFilesException {
        final Path resolved = baseDir.resolve(fileName).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new LogFilesException(LogFilesException.Code.INVALID_PATH, "Invalid file path.");
        }
        return resolved;
    }

    /**
     * Ensures the logs directory is available.
     *
     * @return logs directory path
     * @throws LogFilesException if the directory is not available
     */
    private Path getLogsDir() throws LogFilesException {
        final Path logsDir = ConfigHelper.getDefaultOutputDirectory();
        if (logsDir == null || !Files.exists(logsDir)) {
            throw new LogFilesException(LogFilesException.Code.LOGS_DIR_UNAVAILABLE,
                    "Logs directory is not available.");
        }
        return logsDir;
    }
}
