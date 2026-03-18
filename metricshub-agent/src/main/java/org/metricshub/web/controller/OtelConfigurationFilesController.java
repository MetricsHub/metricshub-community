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

import jakarta.validation.Valid;
import java.util.List;
import org.metricshub.web.dto.ConfigurationFile;
import org.metricshub.web.dto.FileNewName;
import org.metricshub.web.exception.ConfigFilesException;
import org.metricshub.web.service.OtelConfigurationFilesService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Controller for managing OpenTelemetry Collector configuration files.
 *
 * <p>
 * This controller mirrors {@link ConfigurationFilesController} but is dedicated to
 * OTEL configuration files managed under the <code>/api/otel/config-files</code>
 * namespace.
 * </p>
 */
@RestController
@RequestMapping(value = "/api/otel/config-files")
public class OtelConfigurationFilesController {

	private final OtelConfigurationFilesService otelConfigurationFilesService;

	/**
	 * Constructor for OtelConfigurationFilesController.
	 *
	 * @param otelConfigurationFilesService the service handling OTEL configuration
	 *                                      file operations.
	 */
	public OtelConfigurationFilesController(final OtelConfigurationFilesService otelConfigurationFilesService) {
		this.otelConfigurationFilesService = otelConfigurationFilesService;
	}

	/**
	 * Endpoint to list all OTEL configuration files with their metadata.
	 *
	 * @return A list of ConfigurationFile representing all OTEL configuration files.
	 * @throws ConfigFilesException an IO error occurs when listing files
	 */
	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	public List<ConfigurationFile> listConfigurationFiles() throws ConfigFilesException {
		return otelConfigurationFilesService.getAllConfigurationFiles();
	}

	/**
	 * Endpoint to get the content of a specific OTEL configuration file by its name.
	 *
	 * @param fileName the name of the configuration file to retrieve.
	 * @return the content of the configuration file as plain text.
	 * @throws ConfigFilesException if the file is not found or cannot be read
	 */
	@GetMapping(value = "/{fileName}", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> getConfigurationFileContent(@PathVariable("fileName") String fileName)
		throws ConfigFilesException {
		final String content = otelConfigurationFilesService.getFileContent(fileName);
		return ResponseEntity.ok(content);
	}

	/**
	 * Endpoint to create or update an OTEL configuration file.
	 *
	 * @param fileName       the name of the configuration file to create or update.
	 * @param content        the content to write to the configuration file.
	 * @param skipValidation if true, skips validation of the content before saving.
	 * @return a ResponseEntity with the {@link ConfigurationFile} DTO reporting the
	 *         saved file's metadata.
	 * @throws ConfigFilesException if the file cannot be written
	 */
	@PutMapping(value = "/{fileName}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<ConfigurationFile> saveOrUpdateConfigurationFile(
		@PathVariable("fileName") String fileName,
		@RequestBody(required = false) String content,
		@RequestParam(name = "skipValidation", defaultValue = "false") boolean skipValidation
	) throws ConfigFilesException {
		if (!skipValidation) {
			final var validation = otelConfigurationFilesService.validate(content, fileName);
			if (!validation.isValid()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, validation.getFirst());
			}
		}
		return ResponseEntity.ok(otelConfigurationFilesService.saveOrUpdateFile(fileName, content));
	}

	/**
	 * Endpoint to create or update a draft OTEL configuration file.
	 *
	 * @param fileName       the name of the configuration file to create or update.
	 * @param content        the content to write to the configuration file.
	 * @param skipValidation if true, skips validation of the content before saving.
	 * @return a ResponseEntity with the {@link ConfigurationFile} DTO reporting the
	 *         saved file's metadata.
	 * @throws ConfigFilesException if the file cannot be written
	 */
	@PutMapping(
		value = "/draft/{fileName}",
		produces = MediaType.APPLICATION_JSON_VALUE,
		consumes = MediaType.TEXT_PLAIN_VALUE
	)
	public ResponseEntity<ConfigurationFile> saveOrUpdateDraftFile(
		@PathVariable("fileName") String fileName,
		@RequestBody(required = false) String content,
		@RequestParam(name = "skipValidation", defaultValue = "false") boolean skipValidation
	) throws ConfigFilesException {
		if (!skipValidation) {
			final var validation = otelConfigurationFilesService.validate(content, fileName);
			if (!validation.isValid()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, validation.getFirst());
			}
		}
		return ResponseEntity.ok(otelConfigurationFilesService.saveOrUpdateDraftFile(fileName, content));
	}

	/**
	 * Endpoint to validate an OTEL configuration file's content.
	 * <p>
	 * If no content is provided in the request body, the file content on disk is
	 * fetched and validated.
	 * </p>
	 *
	 * @param fileName the name of the configuration file to validate.
	 * @param content  the content to validate; if null, validates the file on disk.
	 * @return an {@link OtelConfigurationFilesService.Validation} object containing
	 *         validation results.
	 * @throws ConfigFilesException if the file content cannot be read
	 */
	@PostMapping(
		value = "/{fileName}",
		produces = MediaType.APPLICATION_JSON_VALUE,
		consumes = MediaType.TEXT_PLAIN_VALUE
	)
	public ResponseEntity<OtelConfigurationFilesService.Validation> validateConfigurationFile(
		@PathVariable("fileName") String fileName,
		@RequestBody(required = false) String content
	) throws ConfigFilesException {
		if (content == null) {
			content = otelConfigurationFilesService.getFileContent(fileName);
		}
		return ResponseEntity.ok(otelConfigurationFilesService.validate(content, fileName));
	}

	/**
	 * Endpoint to delete an OTEL configuration file by its name.
	 *
	 * @param fileName the name of the configuration file to delete.
	 * @return a ResponseEntity with no content.
	 * @throws ConfigFilesException if an IO error occurs during deletion
	 */
	@DeleteMapping("/{fileName}")
	public ResponseEntity<Void> deleteConfigurationFile(@PathVariable("fileName") String fileName)
		throws ConfigFilesException {
		otelConfigurationFilesService.deleteFile(fileName);
		// Returns 204 No Content
		return ResponseEntity.noContent().build();
	}

	/**
	 * Endpoint to rename an OTEL configuration file.
	 *
	 * @param fileNewName the DTO containing the new name for the file.
	 * @return a ResponseEntity with the {@link ConfigurationFile} DTO reporting the
	 *         renamed file's metadata.
	 * @throws ConfigFilesException if an IO error occurs during renaming
	 */
	@PatchMapping(
		value = "/{fileName}",
		produces = MediaType.APPLICATION_JSON_VALUE,
		consumes = MediaType.APPLICATION_JSON_VALUE
	)
	public ResponseEntity<ConfigurationFile> renameConfigurationFile(
		@PathVariable("fileName") String oldName,
		@Valid @RequestBody FileNewName fileNewName
	) throws ConfigFilesException {
		return ResponseEntity.ok(otelConfigurationFilesService.renameFile(oldName, fileNewName.getNewName()));
	}

	/**
	 * Endpoint to create or update an OTEL backup file.
	 */
	@PutMapping(
		value = "/backup/{fileName}",
		produces = MediaType.APPLICATION_JSON_VALUE,
		consumes = MediaType.TEXT_PLAIN_VALUE
	)
	public ResponseEntity<ConfigurationFile> saveOrUpdateBackupFile(
		@PathVariable("fileName") String fileName,
		@RequestBody(required = false) String content
	) throws ConfigFilesException {
		return ResponseEntity.ok(otelConfigurationFilesService.saveOrUpdateBackupFile(fileName, content));
	}

	/**
	 * Endpoint to list all OTEL backup files with their metadata.
	 *
	 * @return A list of ConfigurationFile representing all OTEL backup files.
	 * @throws ConfigFilesException an IO error occurs when listing files
	 */
	@GetMapping(value = "/backup", produces = MediaType.APPLICATION_JSON_VALUE)
	public List<ConfigurationFile> listBackupFiles() throws ConfigFilesException {
		return otelConfigurationFilesService.listAllBackupFiles();
	}

	/**
	 * Endpoint to get the content of an OTEL backup file by its name.
	 */
	@GetMapping(value = "/backup/{fileName}", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> getBackupFileContent(@PathVariable("fileName") String fileName)
		throws ConfigFilesException {
		final String content = otelConfigurationFilesService.getBackupFileContent(fileName);
		return ResponseEntity.ok(content);
	}

	/**
	 * Endpoint to delete an OTEL backup file by its name.
	 */
	@DeleteMapping("/backup/{fileName}")
	public ResponseEntity<Void> deleteBackupFile(@PathVariable("fileName") String fileName) throws ConfigFilesException {
		otelConfigurationFilesService.deleteBackupFile(fileName);
		return ResponseEntity.noContent().build();
	}
}
