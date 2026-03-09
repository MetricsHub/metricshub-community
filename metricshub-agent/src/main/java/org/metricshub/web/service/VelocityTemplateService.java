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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.programmable.configuration.ProgrammableConfigurationProvider;
import org.metricshub.programmable.configuration.VelocityConfigurationLoader;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.exception.ConfigFilesException;
import org.springframework.stereotype.Service;

/**
 * Service for evaluating Velocity templates against the current configuration
 * directory.
 */
@Slf4j
@Service
public class VelocityTemplateService {

	private final AgentContextHolder agentContextHolder;

	/**
	 * Constructor for VelocityTemplateService.
	 *
	 * @param agentContextHolder the AgentContextHolder to access the agent context
	 *                           and configuration directory.
	 */
	public VelocityTemplateService(final AgentContextHolder agentContextHolder) {
		this.agentContextHolder = agentContextHolder;
	}

	/**
	 * Evaluates a Velocity template and returns the generated YAML content.
	 * <p>
	 * If {@code content} is non-null, it is written to a temporary file in the
	 * config directory before evaluation (so that Velocity's file-based resource
	 * loader can find it). The temp file is deleted after evaluation.
	 * <p>
	 * If {@code content} is null, the existing file at {@code fileName} in the
	 * config directory is evaluated directly.
	 *
	 * @param fileName the .vm file name
	 * @param content  optional template content (from editor); null to use the
	 *                 on-disk file
	 * @return the generated YAML string
	 * @throws ConfigFilesException if the template cannot be evaluated
	 */
	public String evaluate(final String fileName, final String content) throws ConfigFilesException {
		final Path configDir = getConfigDir();
		Path vmPath;

		if (content != null) {
			// Write to a temp file so the Velocity file resource loader can find it
			try {
				vmPath = configDir.resolve(fileName + ".test.tmp");
				Files.writeString(vmPath, content, StandardCharsets.UTF_8);
			} catch (IOException e) {
				throw new ConfigFilesException(
					ConfigFilesException.Code.IO_FAILURE,
					"Failed to write temporary Velocity template.",
					e
				);
			}
		} else {
			vmPath = configDir.resolve(fileName);
			if (!Files.exists(vmPath)) {
				throw new ConfigFilesException(ConfigFilesException.Code.FILE_NOT_FOUND, "Velocity template file not found.");
			}
		}

		try {
			final var loader = new VelocityConfigurationLoader(vmPath, ProgrammableConfigurationProvider.getTools());
			final String result = loader.generateYamlDangerous();
			return result;
		} catch (ConfigFilesException e) {
			throw e;
		} catch (Exception e) {
			log.error("Velocity template evaluation failed for '{}': {}", fileName, e.getMessage());
			log.debug("Velocity evaluation exception:", e);
			throw new ConfigFilesException(
				ConfigFilesException.Code.VALIDATION_FAILED,
				"Velocity template evaluation failed: " + e.getMessage(),
				e
			);
		} finally {
			// Clean up temp file if we created one
			if (content != null) {
				try {
					Files.deleteIfExists(vmPath);
				} catch (IOException ignored) {
					/* best effort */
				}
			}
		}
	}

	/**
	 * Ensures the agent configuration directory is available.
	 *
	 * @return configuration directory path
	 * @throws ConfigFilesException if the directory is not available
	 */
	private Path getConfigDir() throws ConfigFilesException {
		final var agentContext = agentContextHolder.getAgentContext();
		if (agentContext == null || agentContext.getConfigDirectory() == null) {
			throw new ConfigFilesException(
				ConfigFilesException.Code.CONFIG_DIR_UNAVAILABLE,
				"Configuration directory is not available."
			);
		}
		return agentContext.getConfigDirectory();
	}
}
