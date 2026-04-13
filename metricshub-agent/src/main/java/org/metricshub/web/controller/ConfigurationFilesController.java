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
import jakarta.validation.Valid;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.metricshub.agent.deserialization.DeserializationFailure;
import org.metricshub.web.dto.ConfigurationFile;
import org.metricshub.web.dto.FileNewName;
import org.metricshub.web.exception.ConfigFilesException;
import org.metricshub.web.exception.TextPlainException;
import org.metricshub.web.service.ConfigurationFilesService;
import org.metricshub.web.service.VelocityTemplateService;
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
@Tag(name = "Configuration Files", description = "MetricsHub configuration file management")
public class ConfigurationFilesController {

	/** Pattern to extract line and column from Velocity error messages. */
	private static final Pattern VELOCITY_LINE_COL_PATTERN = Pattern.compile("\\[line\\s+(\\d+),\\s*column\\s+(\\d+)\\]");

	/** Service handling configuration file operations. */
	private ConfigurationFilesService configurationFilesService;

	/** Service for evaluating Velocity templates. */
	private VelocityTemplateService velocityTemplateService;

	/**
	 * Constructor for ConfigurationFilesController.
	 *
	 * @param configurationFilesService the ConfigurationFilesService to handle
	 *                                  configuration file requests.
	 * @param velocityTemplateService   the VelocityTemplateService to evaluate
	 *                                  Velocity templates.
	 */

	public ConfigurationFilesController(
		final ConfigurationFilesService configurationFilesService,
		final VelocityTemplateService velocityTemplateService
	) {
		this.configurationFilesService = configurationFilesService;
		this.velocityTemplateService = velocityTemplateService;
	}

	/**
	 * Endpoint to list all configuration files with their metadata.
	 *
	 * @return A list of ConfigurationFile representing all configuration files.
	 * @throws ConfigFilesException an IO error occurs when listing files
	 */
	@Operation(
		summary = "List configuration files",
		description = "Lists all configuration files with their metadata.",
		responses = { @ApiResponse(responseCode = "200", description = "Configuration files listed successfully") }
	)
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
	@Operation(
		summary = "Get configuration file content",
		description = "Returns the content of a specific configuration file as plain text.",
		responses = {
			@ApiResponse(responseCode = "200", description = "File content retrieved successfully"),
			@ApiResponse(responseCode = "404", description = "File not found")
		}
	)
	@GetMapping(value = "/{fileName}", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> getConfigurationFileContent(
		@Parameter(description = "Configuration file name") @PathVariable("fileName") String fileName
	) throws ConfigFilesException {
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
	@Operation(
		summary = "Save or update a configuration file",
		description = "Creates or updates a configuration file. Optionally validates content before saving.",
		responses = {
			@ApiResponse(responseCode = "200", description = "File saved successfully"),
			@ApiResponse(responseCode = "400", description = "Validation failed")
		}
	)
	@PutMapping(value = "/{fileName}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<ConfigurationFile> saveOrUpdateConfigurationFile(
		@Parameter(description = "Configuration file name") @PathVariable("fileName") String fileName,
		@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "File content") @RequestBody(
			required = false
		) String content,
		@Parameter(description = "Whether to skip content validation before saving") @RequestParam(
			name = "skipValidation",
			defaultValue = "false"
		) boolean skipValidation
	) throws ConfigFilesException {
		if (!skipValidation) {
			if (ConfigurationFilesService.isVmFile(fileName)) {
				// Two-step .vm validation:
				// 1) Execute the Velocity template → throws on syntax/runtime error
				final String generatedYaml = velocityTemplateService.evaluate(fileName, content);
				// 2) Validate the generated YAML with the standard AgentConfig validator
				var v = configurationFilesService.validate(generatedYaml, fileName);
				if (!v.isValid()) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, v.getFirst());
				}
			} else {
				var v = configurationFilesService.validate(content, fileName);
				if (!v.isValid()) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, v.getFirst());
				}
			}
		}
		return ResponseEntity.ok(configurationFilesService.saveOrUpdateFile(fileName, content));
	}

	/**
	 * Endpoint to create or update a draft configuration file.
	 *
	 * @param fileName       the name of the configuration file to create or update.
	 * @param content        the content to write to the configuration file.
	 * @param skipValidation if true, skips validation of the content before saving.
	 * @return a ResponseEntity with the {@link ConfigurationFile} DTO reporting the
	 *         saved file's metadata.
	 * @throws ConfigFilesException if the file cannot be written
	 */
	@Operation(
		summary = "Save or update a draft configuration file",
		description = "Creates or updates a draft configuration file. Optionally validates content before saving.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Draft file saved successfully"),
			@ApiResponse(responseCode = "400", description = "Validation failed")
		}
	)
	@PutMapping(
		value = "/draft/{fileName}",
		produces = MediaType.APPLICATION_JSON_VALUE,
		consumes = MediaType.TEXT_PLAIN_VALUE
	)
	public ResponseEntity<ConfigurationFile> saveOrUpdateDraftFile(
		@Parameter(description = "Configuration file name") @PathVariable("fileName") String fileName,
		@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "File content") @RequestBody(
			required = false
		) String content,
		@Parameter(description = "Whether to skip content validation before saving") @RequestParam(
			name = "skipValidation",
			defaultValue = "false"
		) boolean skipValidation
	) throws ConfigFilesException {
		if (!skipValidation) {
			if (ConfigurationFilesService.isVmFile(fileName)) {
				// Two-step .vm validation:
				final String generatedYaml = velocityTemplateService.evaluate(fileName, content);
				var v = configurationFilesService.validate(generatedYaml, fileName);
				if (!v.isValid()) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, v.getFirst());
				}
			} else {
				var v = configurationFilesService.validate(content, fileName);
				if (!v.isValid()) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, v.getFirst());
				}
			}
		}
		return ResponseEntity.ok(configurationFilesService.saveOrUpdateDraftFile(fileName, content));
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
	@Operation(
		summary = "Validate a configuration file",
		description = "Validates a configuration file's content. If no content is provided, validates the file on disk.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Validation result returned"),
			@ApiResponse(responseCode = "404", description = "File not found")
		}
	)
	@PostMapping(
		value = "/{fileName}",
		produces = MediaType.APPLICATION_JSON_VALUE,
		consumes = MediaType.TEXT_PLAIN_VALUE
	)
	public ResponseEntity<ConfigurationFilesService.Validation> validateConfigurationFile(
		@Parameter(description = "Configuration file name") @PathVariable("fileName") String fileName,
		@io.swagger.v3.oas.annotations.parameters.RequestBody(
			description = "File content to validate; if null, validates the file on disk"
		) @RequestBody(required = false) String content
	) throws ConfigFilesException {
		if (ConfigurationFilesService.isVmFile(fileName)) {
			// For .vm files: only validate that the Velocity script executes successfully.
			// The generated YAML is NOT validated here — that validation happens in the
			// Test panel when the frontend passes the generated YAML to this endpoint
			// as a YAML file.
			try {
				velocityTemplateService.evaluate(fileName, content);
				// Script executed successfully — return valid
				return ResponseEntity.ok(ConfigurationFilesService.Validation.ok(fileName));
			} catch (ConfigFilesException e) {
				// Template execution failed — wrap as validation failure with line/column
				final var failure = new DeserializationFailure();
				addVelocityError(failure, e.getMessage());
				return ResponseEntity.ok(ConfigurationFilesService.Validation.fail(fileName, failure, e));
			}
		}
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
	@Operation(
		summary = "Delete a configuration file",
		description = "Deletes a configuration file by name.",
		responses = {
			@ApiResponse(responseCode = "204", description = "File deleted successfully"),
			@ApiResponse(responseCode = "404", description = "File not found")
		}
	)
	@DeleteMapping("/{fileName}")
	public ResponseEntity<Void> deleteConfigurationFile(
		@Parameter(description = "Configuration file name") @PathVariable("fileName") String fileName
	) throws ConfigFilesException {
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
	@Operation(
		summary = "Rename a configuration file",
		description = "Renames a configuration file.",
		responses = {
			@ApiResponse(responseCode = "200", description = "File renamed successfully"),
			@ApiResponse(responseCode = "404", description = "File not found")
		}
	)
	@PatchMapping(
		value = "/{fileName}",
		produces = MediaType.APPLICATION_JSON_VALUE,
		consumes = MediaType.APPLICATION_JSON_VALUE
	)
	public ResponseEntity<ConfigurationFile> renameConfigurationFile(
		@Parameter(description = "Current file name") @PathVariable("fileName") String oldName,
		@io.swagger.v3.oas.annotations.parameters.RequestBody(
			description = "New file name"
		) @Valid @RequestBody FileNewName fileNewName
	) throws ConfigFilesException {
		return ResponseEntity.ok(configurationFilesService.renameFile(oldName, fileNewName.getNewName()));
	}

	/**
	 * Endpoint to create or update a backup file.
	 */
	@Operation(
		summary = "Save or update a backup file",
		description = "Creates or updates a backup of a configuration file.",
		responses = { @ApiResponse(responseCode = "200", description = "Backup file saved successfully") }
	)
	@PutMapping(
		value = "/backup/{fileName}",
		produces = MediaType.APPLICATION_JSON_VALUE,
		consumes = MediaType.TEXT_PLAIN_VALUE
	)
	public ResponseEntity<ConfigurationFile> saveOrUpdateBackupFile(
		@Parameter(description = "Backup file name") @PathVariable("fileName") String fileName,
		@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "File content") @RequestBody(
			required = false
		) String content
	) throws ConfigFilesException {
		return ResponseEntity.ok(configurationFilesService.saveOrUpdateBackupFile(fileName, content));
	}

	/**
	 * Endpoint to list all backup files with their metadata.
	 *
	 * @return A list of ConfigurationFile representing all backup files.
	 * @throws ConfigFilesException an IO error occurs when listing files
	 */
	@Operation(
		summary = "List backup files",
		description = "Lists all backup configuration files with their metadata.",
		responses = { @ApiResponse(responseCode = "200", description = "Backup files listed successfully") }
	)
	@GetMapping(value = "/backup", produces = MediaType.APPLICATION_JSON_VALUE)
	public List<ConfigurationFile> listBackupFiles() throws ConfigFilesException {
		return configurationFilesService.listAllBackupFiles();
	}

	/**
	 * Endpoint to get the content of a backup file by its name.
	 */
	@Operation(
		summary = "Get backup file content",
		description = "Returns the content of a backup file as plain text.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Backup file content retrieved successfully"),
			@ApiResponse(responseCode = "404", description = "Backup file not found")
		}
	)
	@GetMapping(value = "/backup/{fileName}", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> getBackupFileContent(
		@Parameter(description = "Backup file name") @PathVariable("fileName") String fileName
	) throws ConfigFilesException {
		final String content = configurationFilesService.getBackupFileContent(fileName);
		return ResponseEntity.ok(content);
	}

	/**
	 * Endpoint to delete a backup file by its name.
	 */
	@Operation(
		summary = "Delete a backup file",
		description = "Deletes a backup file by name.",
		responses = {
			@ApiResponse(responseCode = "204", description = "Backup file deleted successfully"),
			@ApiResponse(responseCode = "404", description = "Backup file not found")
		}
	)
	@DeleteMapping("/backup/{fileName}")
	public ResponseEntity<Void> deleteBackupFile(
		@Parameter(description = "Backup file name") @PathVariable("fileName") String fileName
	) throws ConfigFilesException {
		configurationFilesService.deleteBackupFile(fileName);
		return ResponseEntity.noContent().build();
	}

	/**
	 * Endpoint to evaluate a Velocity template (.vm) and return the generated YAML.
	 * <p>
	 * If a request body is provided, it is used as the template content
	 * (for testing unsaved editor content). Otherwise, the on-disk file is evaluated.
	 * <p>
	 * Unlike save-time validation, this endpoint does NOT validate the generated YAML
	 * against the AgentConfig schema — it returns the raw Velocity output so the user
	 * can inspect it.
	 *
	 * @param fileName the .vm file name
	 * @param content  optional template content from the editor
	 * @return the generated YAML as plain text
	 */
	@Operation(
		summary = "Test a Velocity template",
		description = "Evaluates a Velocity template (.vm) and returns the generated YAML without schema validation.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Template evaluated successfully"),
			@ApiResponse(responseCode = "400", description = "Not a .vm file or template evaluation failed")
		}
	)
	@PostMapping(value = "/test/{fileName}", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> testVelocityTemplate(
		@Parameter(description = "Velocity template file name (.vm)") @PathVariable("fileName") String fileName,
		@io.swagger.v3.oas.annotations.parameters.RequestBody(
			description = "Optional template content from the editor"
		) @RequestBody(required = false) String content
	) {
		if (!ConfigurationFilesService.isVmFile(fileName)) {
			throw new TextPlainException(HttpStatus.BAD_REQUEST, "Only .vm files can be tested.");
		}
		try {
			final String result = velocityTemplateService.evaluate(fileName, content);
			return ResponseEntity.ok(result);
		} catch (ConfigFilesException e) {
			throw new TextPlainException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
		}
	}

	/**
	 * Parses a Velocity error message for line/column information and registers
	 * the error in the given {@link org.metricshub.agent.deserialization.DeserializationFailure}.
	 * <p>
	 * Velocity parse errors follow the format:
	 * {@code Encountered "..." at file.vm[line 23, column 1]}.
	 *
	 * @param failure the failure container to add the error to
	 * @param message the error message to parse
	 */
	private static void addVelocityError(final DeserializationFailure failure, final String message) {
		if (message == null) {
			failure.addError("Velocity template evaluation failed.");
			return;
		}
		final Matcher matcher = VELOCITY_LINE_COL_PATTERN.matcher(message);
		if (matcher.find()) {
			final int line = Integer.parseInt(matcher.group(1));
			final int column = Integer.parseInt(matcher.group(2));
			failure.addError(message, line, column);
		} else {
			failure.addError(message);
		}
	}
}
