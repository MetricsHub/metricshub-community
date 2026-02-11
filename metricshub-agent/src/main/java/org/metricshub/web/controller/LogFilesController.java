package org.metricshub.web.controller;

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

import java.io.InputStream;
import java.util.List;
import org.metricshub.web.dto.LogFile;
import org.metricshub.web.exception.LogFilesException;
import org.metricshub.web.service.LogFilesService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for managing log files.
 */
@RestController
@RequestMapping(value = "/api/log-files")
public class LogFilesController {

	/** Service handling log file operations. */
	private LogFilesService logFilesService;

	/**
	 * Constructor for LogFilesController.
	 *
	 * @param logFilesService the LogFilesService to handle log file requests.
	 */
	public LogFilesController(final LogFilesService logFilesService) {
		this.logFilesService = logFilesService;
	}

	/**
	 * Endpoint to list all log files with their metadata.
	 *
	 * @return A list of LogFile representing all log files.
	 * @throws LogFilesException if an IO error occurs when listing files
	 */
	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	public List<LogFile> listLogFiles() throws LogFilesException {
		return logFilesService.getAllLogFiles();
	}

	/**
	 * Endpoint to get the tail (last N bytes) of a specific log file.
	 *
	 * @param fileName the name of the log file to retrieve.
	 * @param maxBytes the maximum number of bytes to read from the end of the file.
	 *                 Defaults to 1 MB.
	 * @return the tail content of the log file as plain text.
	 * @throws LogFilesException if the file is not found or cannot be read
	 */
	@GetMapping(value = "/{fileName}/tail", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> getLogFileTail(
		@PathVariable("fileName") String fileName,
		@RequestParam(name = "maxBytes", defaultValue = "1048576") long maxBytes
	) throws LogFilesException {
		final String content = logFilesService.getFileTail(fileName, maxBytes);
		return ResponseEntity.ok(content);
	}

	/**
	 * Endpoint to download a log file.
	 *
	 * @param fileName the name of the log file to download.
	 * @return the file content as an octet-stream for download.
	 * @throws LogFilesException if the file is not found or cannot be read
	 */
	@GetMapping(value = "/{fileName}/download", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public ResponseEntity<InputStreamResource> downloadLogFile(@PathVariable("fileName") String fileName)
		throws LogFilesException {
		final InputStream inputStream = logFilesService.getFileForDownload(fileName);
		final InputStreamResource resource = new InputStreamResource(inputStream);

		final HttpHeaders headers = new HttpHeaders();
		headers.setContentDisposition(ContentDisposition.attachment().filename(fileName).build());

		return ResponseEntity.ok().headers(headers).body(resource);
	}

	/**
	 * Endpoint to delete a specific log file.
	 *
	 * @param fileName the name of the log file to delete.
	 * @return a ResponseEntity with no content.
	 * @throws LogFilesException if the file is not found or cannot be deleted
	 */
	@DeleteMapping("/{fileName}")
	public ResponseEntity<Void> deleteLogFile(@PathVariable("fileName") String fileName) throws LogFilesException {
		logFilesService.deleteFile(fileName);
		return ResponseEntity.noContent().build();
	}

	/**
	 * Endpoint to delete all log files.
	 *
	 * @return the number of files deleted.
	 * @throws LogFilesException if an IO error occurs during deletion
	 */
	@DeleteMapping
	public ResponseEntity<Integer> deleteAllLogFiles() throws LogFilesException {
		final int deletedCount = logFilesService.deleteAllFiles();
		return ResponseEntity.ok(deletedCount);
	}
}
