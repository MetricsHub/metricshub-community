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
import org.metricshub.web.service.ConfigurationFilesService;
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
 * Controller for managing configuration files.
 */
@RestController
@RequestMapping(value = "/api/config-files")
public class ConfigurationFilesController {

	private ConfigurationFilesService configurationFilesService;

	/**
	 * Constructor for ConfigurationFilesController.
	 *
	 * @param configurationFilesService the ConfigurationFilesService to handle
	 *                                  configuration file requests.
	 */

	public ConfigurationFilesController(final ConfigurationFilesService configurationFilesService) {
		this.configurationFilesService = configurationFilesService;
	}

	/**
	 * Endpoint to list all configuration files with their metadata.
	 *
	 * @return A list of ConfigurationFile representing all configuration files.
	 * @throws ConfigFilesException an IO error occurs when listing files
	 */
	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	public List<ConfigurationFile> listConfigurationFiles() throws ConfigFilesException {
		return configurationFilesService.getAllConfigurationFiles();
	}

	/**
	 * Endpoint to get the content of a specific configuration file by its name.
	 *
	 * @param fileName the name of the configuration file to retrieve.
	 * @return the content of the configuration file as plain text.
	 * @throws ConfigFilesException if the file is not found or cannot be read
	 */
	@GetMapping(value = "/{fileName}", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> getConfigurationFileContent(@PathVariable("fileName") String fileName)
		throws ConfigFilesException {
		final String content = configurationFilesService.getFileContent(fileName);
		return ResponseEntity.ok(content);
	}

	/**
	 * Endpoint to create or update a configuration file.
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
			var v = configurationFilesService.validate(content, fileName);
			if (!v.isValid()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, v.getError());
			}
		}
		return ResponseEntity.ok(configurationFilesService.saveOrUpdateFile(fileName, content));
	}

	/**
	 * Endpoint to validate a configuration file's content.<br>
	 * If no content is provided in the request body, the file content on disk is
	 * fetched and validated.
	 *
	 *
	 * @param fileName the name of the configuration file to validate.
	 * @param content  the content to validate; if null, validates the file on disk.
	 * @return an {@link ConfigurationFilesService.Validation} object containing
	 *         validation results.
	 * @throws ConfigFilesException if the file content cannot be read
	 */
	@PostMapping(
		value = "/{fileName}",
		produces = MediaType.APPLICATION_JSON_VALUE,
		consumes = MediaType.TEXT_PLAIN_VALUE
	)
	public ResponseEntity<ConfigurationFilesService.Validation> validateConfigurationFile(
		@PathVariable("fileName") String fileName,
		@RequestBody(required = false) String content
	) throws ConfigFilesException {
		if (content == null) {
			content = configurationFilesService.getFileContent(fileName);
		}
		return ResponseEntity.ok(configurationFilesService.validate(content, fileName));
	}

	/**
	 * Endpoint to delete a configuration file by its name.
	 *
	 * @param fileName the name of the configuration file to delete.
	 * @return a ResponseEntity with no content.
	 * @throws ConfigFilesException if an IO error occurs during deletion
	 */
	@DeleteMapping("/{fileName}")
	public ResponseEntity<Void> deleteConfigurationFile(@PathVariable("fileName") String fileName)
		throws ConfigFilesException {
		configurationFilesService.deleteFile(fileName);
		// Returns 204 No Content
		return ResponseEntity.noContent().build();
	}

	/**
	 * Endpoint to rename a configuration file.
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
		return ResponseEntity.ok(configurationFilesService.renameFile(oldName, fileNewName.getNewName()));
	}

	/**
	 * Endpoint to create or update a backup file.
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
		return ResponseEntity.ok(configurationFilesService.saveOrUpdateBackupFile(fileName, content));
	}

	/**
	 * Endpoint to list all backup files with their metadata.
	 *
	 * @return A list of ConfigurationFile representing all backup files.
	 * @throws ConfigFilesException an IO error occurs when listing files
	 */
	@GetMapping(value = "/backup", produces = MediaType.APPLICATION_JSON_VALUE)
	public List<ConfigurationFile> listBackupFiles() throws ConfigFilesException {
		return configurationFilesService.listAllBackupFiles();
	}

	/**
	 * Endpoint to get the content of a backup file by its name.
	 */
	@GetMapping(value = "/backup/{fileName}", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> getBackupFileContent(@PathVariable("fileName") String fileName)
		throws ConfigFilesException {
		final String content = configurationFilesService.getBackupFileContent(fileName);
		return ResponseEntity.ok(content);
	}

	/**
	 * Endpoint to delete a backup file by its name.
	 */
	@DeleteMapping("/backup/{fileName}")
	public ResponseEntity<Void> deleteBackupFile(@PathVariable("fileName") String fileName) throws ConfigFilesException {
		configurationFilesService.deleteBackupFile(fileName);
		return ResponseEntity.noContent().build();
	}
}
