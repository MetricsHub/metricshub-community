package org.metricshub.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.programmable.configuration.ProgrammableConfigurationProvider;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.exception.ConfigFilesException;
import org.mockito.Mockito;

class VelocityTemplateServiceTest {

	@TempDir
	Path tempConfigDir;

	/**
	 * Create a VelocityTemplateService with a mocked AgentContextHolder whose extension
	 * manager exposes a real {@link ProgrammableConfigurationProvider}.
	 */
	private VelocityTemplateService newServiceWithDir(final Path dir) {
		final AgentContextHolder holder = Mockito.mock(AgentContextHolder.class);
		final AgentContext agentContext = Mockito.mock(AgentContext.class);
		final ExtensionManager extensionManager = ExtensionManager.builder()
			.withConfigurationProviderExtensions(List.of(new ProgrammableConfigurationProvider()))
			.build();
		when(holder.getAgentContext()).thenReturn(agentContext);
		when(agentContext.getConfigDirectory()).thenReturn(dir);
		when(agentContext.getExtensionManager()).thenReturn(extensionManager);
		return new VelocityTemplateService(holder);
	}

	@Test
	void testEvaluateValidTemplateOnDisk() throws Exception {
		final VelocityTemplateService service = newServiceWithDir(tempConfigDir);
		final String templateContent = "#set($name = \"world\")\nhello: $name\n";
		Files.writeString(tempConfigDir.resolve("test.vm"), templateContent, StandardCharsets.UTF_8);

		final String result = service.evaluate("test.vm", null);

		assertNotNull(result, "Result should not be null");
		assertTrue(result.contains("hello: world"), "Generated YAML should contain the resolved variable");
	}

	@Test
	void testEvaluateValidTemplateFromContent() throws Exception {
		final VelocityTemplateService service = newServiceWithDir(tempConfigDir);
		final String templateContent = "#set($name = \"world\")\nhello: $name\n";

		final String result = service.evaluate("test.vm", templateContent);

		assertNotNull(result, "Result should not be null");
		assertTrue(result.contains("hello: world"), "Generated YAML should contain the resolved variable");
	}

	@Test
	void testEvaluateTemplateWithMathTool() throws Exception {
		final VelocityTemplateService service = newServiceWithDir(tempConfigDir);
		final String templateContent = "#set($val = $math.add(1, 2))\nresult: $val\n";

		final String result = service.evaluate("math-test.vm", templateContent);

		assertNotNull(result, "Result should not be null");
		assertTrue(result.contains("result: 3"), "Generated YAML should contain the math result");
	}

	@Test
	void testEvaluateDraftTemplateFromContent() throws Exception {
		final VelocityTemplateService service = newServiceWithDir(tempConfigDir);
		final String templateContent = "#set($name = \"world\")\nhello: $name\n";

		final String result = service.evaluate("test.vm.draft", templateContent);

		assertNotNull(result, "Result should not be null");
		assertTrue(result.contains("hello: world"), "Draft template should be evaluated like its base .vm file");
	}

	@Test
	void testEvaluateDraftTemplateOnDisk() throws Exception {
		final VelocityTemplateService service = newServiceWithDir(tempConfigDir);
		final String templateContent = "#set($name = \"world\")\nhello: $name\n";
		Files.writeString(tempConfigDir.resolve("test.vm.draft"), templateContent, StandardCharsets.UTF_8);

		final String result = service.evaluate("test.vm.draft", null);

		assertNotNull(result, "Result should not be null");
		assertTrue(result.contains("hello: world"), "Draft template should be evaluated like its base .vm file");
	}

	@Test
	void testEvaluateFileNotFound() {
		final VelocityTemplateService service = newServiceWithDir(tempConfigDir);

		final ConfigFilesException ex = assertThrows(
			ConfigFilesException.class,
			() -> service.evaluate("nonexistent.vm", null),
			"Should throw when file not found"
		);
		assertEquals(ConfigFilesException.Code.FILE_NOT_FOUND, ex.getCode(), "Error code should be FILE_NOT_FOUND");
	}

	@Test
	void testEvaluateConfigDirUnavailable() {
		final AgentContextHolder holder = Mockito.mock(AgentContextHolder.class);
		when(holder.getAgentContext()).thenReturn(null);
		final VelocityTemplateService service = new VelocityTemplateService(holder);

		final ConfigFilesException ex = assertThrows(
			ConfigFilesException.class,
			() -> service.evaluate("test.vm", null),
			"Should throw when config dir unavailable"
		);
		assertEquals(
			ConfigFilesException.Code.CONFIG_DIR_UNAVAILABLE,
			ex.getCode(),
			"Error code should be CONFIG_DIR_UNAVAILABLE"
		);
	}

	@Test
	void testEvaluateTempFileCleanedUp() throws Exception {
		final VelocityTemplateService service = newServiceWithDir(tempConfigDir);
		final String templateContent = "#set($name = \"world\")\nhello: $name\n";

		service.evaluate("cleanup-test.vm", templateContent);

		// The temp file should have been cleaned up
		final Path tmpFile = tempConfigDir.resolve("cleanup-test.vm.test.tmp");
		assertTrue(!Files.exists(tmpFile), "Temp file should be cleaned up after evaluation");
	}
}
