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

import java.util.List;
import org.metricshub.web.dto.ConfigurationFile;
import org.metricshub.web.service.ConfigurationFilesService;
import org.springframework.beans.factory.annotation.Autowired;
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

/**
 * Controller for managing configuration files.
 */
@RestController
@RequestMapping(value = "/api/config-files")
public class ConfigurationFilesController {

	private final ConfigurationFilesService configurationFilesService;

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
	 * Endpoint to get the content of a specific configuration file.
	 *
	 * @param fileName The name of the configuration file.
	 * @return The content of the file as plain text.
	 */
	@GetMapping(value = "/{fileName}", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> getConfigurationFile(@PathVariable String fileName) {
		String content = configurationFilesService.getFileContent(fileName);
		return ResponseEntity.ok(content);
	}

	/**
	 * Endpoint to create or update the content of a configuration file.
	 *
	 * @param fileName The name of the configuration file.
	 * @param content  The new content of the configuration file.
	 * @return A success message.
	 */
	@PutMapping(value = "/{fileName}", consumes = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> saveOrUpdateConfigurationFile(
		@PathVariable String fileName,
		@RequestBody String content
	) {
		configurationFilesService.saveOrUpdateFile(fileName, content);
		return ResponseEntity.ok("Configuration file saved successfully.");
	}

	/**
	 * Endpoint to validate the content of a configuration file.
	 *
	 * @param fileName The name of the configuration file.
	 * @param content  The content to validate.
	 * @return A JSON object containing validation errors or success message.
	 */
	@PostMapping(
		value = "/{fileName}",
		consumes = MediaType.TEXT_PLAIN_VALUE,
		produces = MediaType.APPLICATION_JSON_VALUE
	)
	public ResponseEntity<Object> validateConfigurationFile(@PathVariable String fileName, @RequestBody String content) {
		var validationResult = configurationFilesService.validateFile(fileName, content);
		return ResponseEntity.ok(validationResult);
	}

	/**
	 * Endpoint to delete a configuration file.
	 *
	 * @param fileName The name of the configuration file to delete.
	 * @return A success message.
	 */
	@DeleteMapping(value = "/{fileName}")
	public ResponseEntity<String> deleteConfigurationFile(@PathVariable String fileName) {
		configurationFilesService.deleteFile(fileName);
		return ResponseEntity.ok("Configuration file deleted successfully.");
	}

	/**
	 * Endpoint to rename a configuration file.
	 *
	 * @param fileName      The current name of the configuration file.
	 * @param renameRequest The request body containing the new file name.
	 * @return A success message.
	 */
	@PatchMapping(value = "/{fileName}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> renameConfigurationFile(
		@PathVariable String fileName,
		@RequestBody RenameRequest renameRequest
	) {
		configurationFilesService.renameFile(fileName, renameRequest.getNewName());
		return ResponseEntity.ok("Configuration file renamed successfully.");
	}

	/**
	 * DTO for rename request body.
	 */
	public static class RenameRequest {

		private String newName;

		public String getNewName() {
			return newName;
		}

		public void setNewName(String newName) {
			this.newName = newName;
		}
	}
}
