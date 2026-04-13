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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.deserialization.DeserializationFailure;
import org.metricshub.agent.deserialization.TrackingDeserializationProblemHandler;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.web.AgentContextHolder;

/**
 * Unit tests for {@link ConfigurationFilesService#validate(String, String)} covering different
 * error paths.
 */
class ConfigurationFilesServiceValidateTest {

	/**
	 * Create a new {@link ConfigurationFilesService} instance backed by the provided context.
	 *
	 * @param agentContext context supplying configuration directories and extensions
	 * @return configured service instance
	 */
	private ConfigurationFilesService newService(AgentContext agentContext) {
		return new ConfigurationFilesService(new AgentContextHolder(agentContext));
	}

	/**
	 * Ensures that the {@link TrackingDeserializationProblemHandler} records non-fatal Jackson
	 * issues and that they are surfaced through the validation response.
	 */
	@Test
	void validateShouldCollectProblemHandlerErrors(@TempDir Path tempDir) throws Exception {
		AgentContext agentContext = new LightweightAgentContext(tempDir);
		ConfigurationFilesService service = newService(agentContext);

		String yaml = "jobPoolSize: invalid";
		ConfigurationFilesService.Validation validation = service.validate(yaml, "test.yaml");

		assertFalse(validation.isValid(), "Validation should fail for invalid numeric value");
		assertEquals("test.yaml", validation.getFileName());
		assertTrue(
			validation
				.getErrors()
				.stream()
				.anyMatch(error -> error.getMessage() != null && error.getMessage().contains("Weird string value for type")),
			"Tracking handler should record the weird string value"
		);
		assertTrue(
			validation.getErrors().stream().allMatch(error -> error.getLine() != null && error.getLine() >= 1),
			"All errors should provide a line number"
		);
	}

	/**
	 * Ensures that a {@link com.fasterxml.jackson.core.JsonProcessingException} enriches the
	 * {@link DeserializationFailure} with user-friendly error messages.
	 */
	@Test
	void validateShouldEnrichErrorsFromJsonProcessingException(@TempDir Path tempDir) throws Exception {
		AgentContext agentContext = new LightweightAgentContext(tempDir);
		ConfigurationFilesService service = newService(agentContext);

		String yaml = "jobPoolSize: [1, 2"; // missing closing bracket
		ConfigurationFilesService.Validation validation = service.validate(yaml, "broken.yaml");

		assertFalse(validation.isValid(), "Validation should fail for malformed YAML");
		assertEquals("broken.yaml", validation.getFileName());
		assertTrue(
			validation.getErrors().stream().anyMatch(error -> error.getMessage() != null && !error.getMessage().isBlank()),
			"JsonProcessingException should enrich the failure with at least one message"
		);
	}

	/**
	 * Ensures that generic exceptions are captured and converted into validation errors instead
	 * of bubbling up to the caller.
	 */
	@Test
	void validateShouldHandleGenericExceptions(@TempDir Path tempDir) throws Exception {
		AgentContext agentContext = new ThrowingAgentContext(tempDir);
		ConfigurationFilesService service = newService(agentContext);

		ConfigurationFilesService.Validation validation = service.validate("jobPoolSize: 5", "boom.yaml");

		assertFalse(validation.isValid(), "Validation should fail when a runtime exception is raised");
		assertEquals("boom.yaml", validation.getFileName());
		assertTrue(
			validation
				.getErrors()
				.stream()
				.anyMatch(error -> error.getMessage() != null && error.getMessage().contains("boom")),
			"Failure should contain the propagated exception message"
		);
	}

	/**
	 * Lightweight {@link AgentContext} implementation exposing the temporary directory for
	 * configuration access.
	 */
	private static class LightweightAgentContext extends AgentContext {

		LightweightAgentContext(Path configDir) throws IOException {
			super(configDir.toString(), ExtensionManager.empty());
		}

		@Override
		public void build(String alternateConfigDirectory, boolean createConnectorStore) throws IOException {
			setConfigDirectory(Path.of(alternateConfigDirectory));
		}
	}

	/**
	 * {@link AgentContext} variant that throws an {@link IllegalStateException} when the
	 * extension manager is accessed to simulate unexpected runtime issues.
	 */
	private static class ThrowingAgentContext extends AgentContext {

		ThrowingAgentContext(Path configDir) throws IOException {
			super(configDir.toString(), ExtensionManager.empty());
		}

		@Override
		public void build(String alternateConfigDirectory, boolean createConnectorStore) throws IOException {
			setConfigDirectory(Path.of(alternateConfigDirectory));
		}

		@Override
		public ExtensionManager getExtensionManager() {
			throw new IllegalStateException("boom");
		}
	}
}
