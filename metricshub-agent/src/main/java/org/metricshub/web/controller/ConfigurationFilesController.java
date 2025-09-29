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

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import org.metricshub.web.dto.ConfigurationFile;
import org.metricshub.web.service.ConfigurationFilesService;
import org.springframework.beans.factory.annotation.Autowired;
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
	@Autowired
	public ConfigurationFilesController(final ConfigurationFilesService configurationFilesService) {
		this.configurationFilesService = configurationFilesService;
	}

	/**
	 * Endpoint to list all configuration files with their metadata.
	 *
	 * @return A list of ConfigurationFile representing all configuration files.
	 */
	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	public List<ConfigurationFile> listConfigurationFiles() {
		return configurationFilesService.getAllConfigurationFiles();
	}

	/**
	 * Endpoint to get the content of a specific configuration file by its name.
	 *
	 * @param fileName the name of the configuration file to retrieve.
	 * @return the content of the configuration file as plain text.
	 * @throws ResponseStatusException with status 404 if the file is not found,
	 *                                 400 if the file name is invalid, or 500 for
	 *                                 other unexpected errors.
	 */
	@GetMapping(value = "/{fileName}", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> getConfigurationFileContent(@PathVariable("fileName") String fileName) {
		String content = configurationFilesService.getFileContent(fileName);
		return ResponseEntity.ok(content);
	}

	/**
	 * Endpoint to create or update a configuration file.
	 *
	 * @param fileName the name of the configuration file to create or update.
	 * @param content  the content to write to the configuration file.
	 * @return a ResponseEntity with a success message.
	 */
	@PutMapping(value = "/{fileName}", consumes = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> saveOrUpdateConfigurationFile(
		@PathVariable("fileName") String fileName,
		@RequestBody(required = false) String content
	) {
		configurationFilesService.saveOrUpdateFile(fileName, content);
		return ResponseEntity.ok("Configuration file saved successfully.");
	}

	/**
	 * Endpoint to validate a configuration file's content.
	 * If no content is provided in the request body, the file currently on disk
	 *
	 * @param fileName the name of the configuration file to validate.
	 * @param content  the content to validate; if null, validates the file on disk.
	 * @return an ObjectNode containing validation results.
	 * @throws ResponseStatusException with status 404 if the file is not found,
	 */
	@PostMapping(
		value = "/{fileName}",
		produces = MediaType.APPLICATION_JSON_VALUE,
		consumes = MediaType.TEXT_PLAIN_VALUE
	)
	public ResponseEntity<ObjectNode> validateConfigurationFile(
		@PathVariable("fileName") String fileName,
		@RequestBody(required = false) String content
	) {
		// If no body is provided, validate the file currently on disk
		if (content == null) {
			content = configurationFilesService.getFileContent(fileName);
		}

		return ResponseEntity.ok(configurationFilesService.validateFile(fileName, content));
	}

	/**
	 * Endpoint to delete a configuration file by its name.
	 *
	 * @param fileName the name of the configuration file to delete.
	 * @return a ResponseEntity with no content.
	 * @throws ResponseStatusException with status 404 if the file is not found.
	 */
	@DeleteMapping("/{fileName}")
	public ResponseEntity<Void> deleteConfigurationFile(@PathVariable("fileName") String fileName) {
		configurationFilesService.deleteFile(fileName);
		return ResponseEntity.noContent().build(); // Returns 204 No Content
	}

	/**
	 * Endpoint to rename a configuration file.
	 *
	 * @param oldName the current name of the configuration file.
	 * @param body    a map containing the new name with key "newName".
	 * @return a ResponseEntity with no content.
	 * @throws ResponseStatusException with status 400 if the new name is missing or
	 *                                 invalid
	 */
	@PatchMapping("/{fileName}")
	public ResponseEntity<Void> renameConfigurationFile(
		@PathVariable("fileName") String oldName,
		@RequestBody Map<String, String> body
	) {
		final String newName = body == null ? null : body.get("newName");

		if (newName == null || newName.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Field 'newName' is required.");
		}

		configurationFilesService.renameFile(oldName, newName);
		return ResponseEntity.noContent().build(); // 204 No Content
	}
}
