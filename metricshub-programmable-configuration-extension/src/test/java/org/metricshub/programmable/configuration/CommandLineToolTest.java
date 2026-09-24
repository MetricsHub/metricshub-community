package org.metricshub.programmable.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.engine.common.helpers.LocalOsHandler;

class CommandLineToolTest {

	private final CommandLineTool commandLineTool = new CommandLineTool();

	@Test
	void testExecuteThroughVelocityTemplate() {
		// The sample fixture: $command.execute(...) discovers hosts and the template builds a
		// "resources:" fragment out of them, the same way the $http/$sql/$file fixtures do.
		final Path templatePath = Paths.get("src/test/resources/command/command.vm");
		assertTrue(templatePath.toFile().exists(), "Template file should exist");

		final VelocityConfigurationLoader loader = new VelocityConfigurationLoader(
			templatePath,
			Map.of("command", commandLineTool)
		);
		final String yaml = loader.generateYaml();

		assertNotNull(yaml, "Generated YAML should not be null");
		assertEquals(
			"""

			resources:
			  host-01:
			    attributes:
			      host.name: host-01
			      host.type: linux
			    protocols:
			      ssh:
			        username: admin
			  host-02:
			    attributes:
			      host.name: host-02
			      host.type: linux
			    protocols:
			      ssh:
			        username: admin
			""",
			yaml
		);
	}

	@Test
	void testExecuteSimpleCommand() throws Exception {
		final CommandLineResult result = commandLineTool.execute("echo hello");
		assertEquals(0, result.getExitCode());
		assertTrue(result.getStdout().contains("hello"), "stdout should contain the echoed text");
	}

	@Test
	void testExitCode() throws Exception {
		final CommandLineResult result = commandLineTool.execute("exit 3");
		assertEquals(3, result.getExitCode());
	}

	@Test
	void testFailOnErrorDefaultsToFalse() throws Exception {
		final CommandLineResult result = commandLineTool.execute(Map.of("command", "exit 3"));
		assertEquals(3, result.getExitCode());
	}

	@Test
	void testFailOnErrorFalseReturnsResult() throws Exception {
		final CommandLineResult result = commandLineTool.execute(Map.of("command", "exit 3", "failOnError", "false"));
		assertEquals(3, result.getExitCode());
	}

	@Test
	void testFailOnErrorTrueThrowsOnNonZeroExit() {
		final IOException exception = assertThrows(IOException.class, () ->
			commandLineTool.execute(Map.of("command", echoToStderrCommand("boom") + " && exit 2", "failOnError", "true"))
		);
		assertTrue(exception.getMessage().contains("exit code 2"), "message should include the exit code");
		assertTrue(exception.getMessage().contains("boom"), "message should include stderr");
	}

	@Test
	void testFailOnErrorTrueDoesNotThrowOnSuccess() throws Exception {
		final CommandLineResult result = commandLineTool.execute(Map.of("command", "echo ok", "failOnError", "true"));
		assertEquals(0, result.getExitCode());
	}

	@Test
	void testStderr() throws Exception {
		final CommandLineResult result = commandLineTool.execute(echoToStderrCommand("oops"));
		assertTrue(result.getStderr().contains("oops"), "stderr should contain the echoed text");
	}

	@Test
	void testEnvironmentVariable() throws Exception {
		final CommandLineResult result = commandLineTool.execute(
			Map.of("command", printEnvCommand("MY_TEST_VAR"), "env", "MY_TEST_VAR=hello-env")
		);
		assertTrue(result.getStdout().contains("hello-env"), "stdout should contain the environment variable value");
	}

	@Test
	void testWorkingDirectory(@TempDir final Path tempDir) throws Exception {
		final CommandLineResult result = commandLineTool.execute(
			Map.of("command", "echo marker > marker.txt", "workingDirectory", tempDir.toString())
		);
		assertEquals(0, result.getExitCode());
		assertTrue(
			Files.exists(tempDir.resolve("marker.txt")),
			"the command should have run from the given working directory"
		);
	}

	@Test
	void testTimeout() {
		final TimeoutException exception = assertThrows(TimeoutException.class, () ->
			commandLineTool.execute(Map.of("command", sleepCommand(3), "timeout", "1"))
		);
		assertTrue(exception.getMessage().contains("timed out"), "exception message should mention the timeout");
	}

	@Test
	void testMissingCommandThrows() {
		final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
			commandLineTool.execute(Map.of())
		);
		assertTrue(exception.getMessage().contains("command"), "exception message should mention the missing argument");
	}

	@Test
	void testInvalidEnvFormatThrows() {
		final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
			commandLineTool.execute(Map.of("command", "echo hi", "env", "NOT_A_KEY_VALUE_PAIR"))
		);
		assertTrue(
			exception.getMessage().contains("environment variable"),
			"exception message should mention the invalid environment variable"
		);
	}

	private static String echoToStderrCommand(final String text) {
		return LocalOsHandler.isWindows() ? "echo " + text + " 1>&2" : "echo " + text + " >&2";
	}

	private static String printEnvCommand(final String name) {
		return LocalOsHandler.isWindows() ? "echo %" + name + "%" : "echo $" + name;
	}

	private static String sleepCommand(final int seconds) {
		return LocalOsHandler.isWindows() ? "ping -n " + (seconds + 1) + " 127.0.0.1 >NUL" : "sleep " + seconds;
	}
}
