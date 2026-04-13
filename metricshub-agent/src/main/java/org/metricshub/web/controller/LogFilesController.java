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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Log Files", description = "Log file listing, viewing, downloading, and deletion")
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
	@Operation(
		summary = "List log files",
		description = "Lists all log files with their metadata.",
		responses = { @ApiResponse(responseCode = "200", description = "Log files listed successfully") }
	)
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
	@Operation(
		summary = "Get log file tail",
		description = "Returns the last N bytes of a specific log file.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Log tail retrieved successfully"),
			@ApiResponse(responseCode = "404", description = "Log file not found")
		}
	)
	@GetMapping(value = "/{fileName}/tail", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> getLogFileTail(
		@Parameter(description = "Log file name") @PathVariable("fileName") String fileName,
		@Parameter(description = "Maximum number of bytes to read from the end of the file") @RequestParam(
			name = "maxBytes",
			defaultValue = "1048576"
		) long maxBytes
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
	@Operation(
		summary = "Download log file",
		description = "Downloads a log file as an octet-stream attachment.",
		responses = {
			@ApiResponse(responseCode = "200", description = "File download started"),
			@ApiResponse(responseCode = "404", description = "Log file not found")
		}
	)
	@GetMapping(value = "/{fileName}/download", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public ResponseEntity<InputStreamResource> downloadLogFile(
		@Parameter(description = "Log file name") @PathVariable("fileName") String fileName
	) throws LogFilesException {
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
	@Operation(
		summary = "Delete a log file",
		description = "Deletes a specific log file by name.",
		responses = {
			@ApiResponse(responseCode = "204", description = "Log file deleted successfully"),
			@ApiResponse(responseCode = "404", description = "Log file not found")
		}
	)
	@DeleteMapping("/{fileName}")
	public ResponseEntity<Void> deleteLogFile(
		@Parameter(description = "Log file name") @PathVariable("fileName") String fileName
	) throws LogFilesException {
		logFilesService.deleteFile(fileName);
		return ResponseEntity.noContent().build();
	}

	/**
	 * Endpoint to delete all log files.
	 *
	 * @return the number of files deleted.
	 * @throws LogFilesException if an IO error occurs during deletion
	 */
	@Operation(
		summary = "Delete all log files",
		description = "Deletes all log files and returns the number of files deleted.",
		responses = { @ApiResponse(responseCode = "200", description = "All log files deleted successfully") }
	)
	@DeleteMapping
	public ResponseEntity<Integer> deleteAllLogFiles() throws LogFilesException {
		final int deletedCount = logFilesService.deleteAllFiles();
		return ResponseEntity.ok(deletedCount);
	}
}
