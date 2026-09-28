package org.metricshub.programmable.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
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
	void testOversizedOutputIsRejected(@TempDir final Path tempDir) throws Exception {
		// A file larger than the cap, read back by the command: the reader must stop and kill the
		// command instead of holding it all in memory.
		final Path bigFile = tempDir.resolve("big.txt");
		final byte[] megabyte = new byte[1024 * 1024];
		java.util.Arrays.fill(megabyte, (byte) 'x');
		try (java.io.OutputStream out = Files.newOutputStream(bigFile)) {
			for (int i = 0; i < (CommandLineTool.MAX_OUTPUT_BYTES / megabyte.length) + 2; i++) {
				out.write(megabyte);
			}
		}

		final IOException exception = assertThrows(IOException.class, () ->
			commandLineTool.execute(dumpFileCommand(bigFile))
		);
		assertTrue(exception.getMessage().contains("produced more than"), "message should report the overflow");
	}

	@Test
	void testTimeoutAppliesToOutputCollection() {
		// The shell exits at once but leaves a child holding the output pipes: collecting that output
		// must not run past the deadline.
		final long start = System.currentTimeMillis();
		assertThrows(TimeoutException.class, () ->
			commandLineTool.execute(Map.of("command", detachedSleepCommand(20), "timeout", "1"))
		);
		final long elapsed = System.currentTimeMillis() - start;
		assertTrue(elapsed < 10_000, "should have given up near the timeout, but took " + elapsed + " ms");
	}

	@Test
	void testInterruptionKillsTheCommand() throws Exception {
		final long baseline = countSleepProcesses();
		final AtomicReference<Exception> thrown = new AtomicReference<>();
		final Thread worker = new Thread(() -> {
			try {
				commandLineTool.execute(Map.of("command", sleepCommand(30), "timeout", "60"));
			} catch (Exception e) {
				thrown.set(e);
			}
		});
		worker.start();

		assertTrue(waitUntil(() -> countSleepProcesses() > baseline), "the command should have started");
		worker.interrupt();
		worker.join(30_000);

		assertInstanceOf(InterruptedException.class, thrown.get(), "the caller should see the interruption");
		assertTrue(waitUntil(() -> countSleepProcesses() <= baseline), "the interrupted command should have been killed");
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	void testCommandLineIsNeverReportedInMessagesOnWindows() {
		final String secret = "s3cr3t-" + UUID.randomUUID();
		assertCommandLineNotReported(
			secret,
			"echo " + secret + ">NUL & echo boom 1>&2 & exit 1",
			"echo " + secret + ">NUL & ping -n 6 127.0.0.1 >NUL"
		);
	}

	@Test
	@EnabledOnOs(OS.LINUX)
	void testCommandLineIsNeverReportedInMessagesOnLinux() {
		final String secret = "s3cr3t-" + UUID.randomUUID();
		assertCommandLineNotReported(
			secret,
			"echo " + secret + " >/dev/null; echo boom >&2; exit 1",
			"echo " + secret + " >/dev/null; sleep 5"
		);
	}

	/**
	 * Checks that neither a failure nor a timeout reports the command line, which can carry
	 * credentials passed as arguments.
	 *
	 * @param secret         the text the commands mention, which must never be reported
	 * @param failingCommand a command mentioning the secret that writes to stderr and exits non-zero
	 * @param slowCommand    a command mentioning the secret that outlives a one second timeout
	 */
	private void assertCommandLineNotReported(
		final String secret,
		final String failingCommand,
		final String slowCommand
	) {
		final IOException failure = assertThrows(IOException.class, () ->
			commandLineTool.execute(Map.of("command", failingCommand, "failOnError", "true"))
		);
		assertFalse(failure.getMessage().contains(secret), "the failure message must not carry the command line");

		final TimeoutException timeout = assertThrows(TimeoutException.class, () ->
			commandLineTool.execute(Map.of("command", slowCommand, "timeout", "1"))
		);
		assertFalse(timeout.getMessage().contains(secret), "the timeout message must not carry the command line");
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

	private static String dumpFileCommand(final Path file) {
		return (LocalOsHandler.isWindows() ? "type " : "cat ") + file;
	}

	/** A command whose shell exits at once, leaving a child holding the output pipes. */
	private static String detachedSleepCommand(final int seconds) {
		return LocalOsHandler.isWindows() ? "start /b ping -n " + (seconds + 1) + " 127.0.0.1" : "sleep " + seconds + " &";
	}

	/** The number of live processes of the program the sleeping command runs. */
	private static long countSleepProcesses() {
		final String name = LocalOsHandler.isWindows() ? "ping.exe" : "sleep";
		return ProcessHandle.allProcesses()
			.filter(handle ->
				handle
					.info()
					.command()
					.map(command -> command.toLowerCase(Locale.ROOT).endsWith(name))
					.orElse(false)
			)
			.count();
	}

	/** Waits up to 15 seconds for the given condition to hold. */
	private static boolean waitUntil(final BooleanSupplier condition) {
		for (int i = 0; i < 150; i++) {
			if (condition.getAsBoolean()) {
				return true;
			}
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return false;
			}
		}
		return false;
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
