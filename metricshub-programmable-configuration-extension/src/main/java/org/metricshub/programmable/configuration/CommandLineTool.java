package org.metricshub.programmable.configuration;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Programmable Configuration Extension
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2026 MetricsHub
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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.LocalOsHandler;

/**
 * This class provides a tool for executing local OS commands and capturing their output.
 */
@Slf4j
public class CommandLineTool {

	/**
	 * The default command execution timeout in seconds.
	 */
	protected static final int DEFAULT_TIMEOUT = 60;

	/**
	 * The maximum number of bytes captured from each of the command's output streams. A command
	 * writing more than this is killed: its output is held in memory, so an endless producer would
	 * otherwise exhaust the heap long before the timeout fires.
	 */
	protected static final int MAX_OUTPUT_BYTES = 10 * 1024 * 1024;

	/**
	 * The shell used to run the command, and the option that makes it read the command from its
	 * next argument: {@code cmd.exe /C} on Windows, {@code $SHELL -c} (or {@code /bin/sh -c}) on
	 * other platforms.
	 */
	private static final String[] LOCAL_SHELL_COMMAND = buildShellCommand();

	/**
	 * Executes the given command line with the default timeout, inheriting the current working
	 * directory, environment and charset.
	 *
	 * @param command the command line to execute
	 * @return the {@link CommandLineResult} holding stdout, stderr and the exit code
	 * @throws IOException          if the process cannot be started or its output cannot be read
	 * @throws InterruptedException if the current thread is interrupted while waiting for completion
	 * @throws TimeoutException     if the command does not complete within the timeout
	 */
	public CommandLineResult execute(final String command) throws IOException, InterruptedException, TimeoutException {
		return execute(Map.of("command", command));
	}

	/**
	 * Executes a command described by the given arguments.
	 *
	 * @param arguments A map containing the following keys:
	 *                  <ul>
	 *                  <li>{@code command} (required): the command line to execute</li>
	 *                  <li>{@code timeout}: timeout in seconds (default: {@value #DEFAULT_TIMEOUT})</li>
	 *                  <li>{@code workingDirectory}: the directory the command is executed from</li>
	 *                  <li>{@code charset}: the charset used to decode stdout/stderr (default: UTF-8)</li>
	 *                  <li>{@code env}: additional environment variables, one {@code KEY=VALUE} pair per line</li>
	 *                  <li>{@code failOnError}: {@code true} to throw when the command exits with a non-zero code (default: {@code false})</li>
	 *                  </ul>
	 * @return the {@link CommandLineResult} holding stdout, stderr and the exit code
	 * @throws IOException          if the process cannot be started or its output cannot be read, or if
	 *                              {@code failOnError} is {@code true} and the command exits with a non-zero code
	 * @throws InterruptedException if the current thread is interrupted while waiting for completion
	 * @throws TimeoutException     if the command does not complete within the timeout
	 */
	public CommandLineResult execute(final Map<String, String> arguments)
		throws IOException, InterruptedException, TimeoutException {
		final String command = arguments.get("command");
		if (command == null || command.isBlank()) {
			throw new IllegalArgumentException("The 'command' argument is required.");
		}

		var timeout = DEFAULT_TIMEOUT;
		final String timeoutString = arguments.get("timeout");
		if (timeoutString != null && !timeoutString.isBlank()) {
			timeout = Integer.parseInt(timeoutString.trim());
		}

		final CommandLineResult result = run(
			command,
			timeout,
			arguments.get("workingDirectory"),
			resolveCharset(arguments.get("charset")),
			parseEnv(arguments.get("env"))
		);

		final String stderr = result.getStderr().trim();

		// The command line is never logged: it may carry credentials passed as arguments.
		if (result.getExitCode() != 0) {
			log.warn("Command exited with code {}. Stderr: {}", result.getExitCode(), stderr);
		} else if (!stderr.isEmpty()) {
			// Many tools write progress or notices to stderr even when they succeed.
			log.debug("Command succeeded but wrote to stderr: {}", stderr);
		}

		final String failOnError = arguments.get("failOnError");
		if (failOnError != null && Boolean.parseBoolean(failOnError.trim()) && result.getExitCode() != 0) {
			throw new IOException(String.format("Command failed with exit code %d: %s", result.getExitCode(), stderr));
		}

		return result;
	}

	/**
	 * Starts the command in the platform shell and waits, at most {@code timeout} seconds, for it
	 * to complete.
	 *
	 * @param command          the command line to execute
	 * @param timeout          the timeout in seconds
	 * @param workingDirectory the directory the command is executed from, or {@code null} to inherit the current one
	 * @param charset          the charset used to decode stdout/stderr
	 * @param env              additional environment variables to set on top of the inherited ones
	 * @return the {@link CommandLineResult} holding stdout, stderr and the exit code
	 * @throws IOException          if the process cannot be started or its output cannot be read
	 * @throws InterruptedException if the current thread is interrupted while waiting for completion
	 * @throws TimeoutException     if the command does not complete within the timeout
	 */
	private CommandLineResult run(
		final String command,
		final int timeout,
		final String workingDirectory,
		final Charset charset,
		final Map<String, String> env
	) throws IOException, InterruptedException, TimeoutException {
		final ProcessBuilder processBuilder = new ProcessBuilder(LOCAL_SHELL_COMMAND[0], LOCAL_SHELL_COMMAND[1], command);
		if (workingDirectory != null && !workingDirectory.isBlank()) {
			processBuilder.directory(Paths.get(workingDirectory).toFile());
		}
		if (!env.isEmpty()) {
			processBuilder.environment().putAll(env);
		}

		final Process process = processBuilder.start();

		// One deadline for the whole operation. Waiting for the process to exit is not enough: the shell
		// can exit while a background child it started still holds the output pipes open, and collecting
		// that output would then run past the timeout, or never end at all.
		final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeout);

		// Stdout and stderr are read concurrently, on their own threads: reading them one after the
		// other could deadlock if the child fills up the other stream's pipe buffer while this thread
		// is still draining the first one.
		final ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			final Future<String> stdoutFuture = executor.submit(readStreamTask(process, process.getInputStream(), charset));
			final Future<String> stderrFuture = executor.submit(readStreamTask(process, process.getErrorStream(), charset));

			if (!process.waitFor(timeout, TimeUnit.SECONDS)) {
				killTree(process);
				throw timedOut(timeout);
			}

			final String stdout = awaitOutput(stdoutFuture, process, deadline, timeout);
			final String stderr = awaitOutput(stderrFuture, process, deadline, timeout);

			return new CommandLineResult(stdout, stderr, process.exitValue());
		} catch (final InterruptedException e) {
			// The caller gave up: the command must not outlive it.
			killTree(process);
			Thread.currentThread().interrupt();
			throw e;
		} catch (final ExecutionException e) {
			final Throwable cause = e.getCause();
			throw cause instanceof IOException ioException ? ioException : new IOException(cause);
		} finally {
			executor.shutdownNow();
		}
	}

	/**
	 * Waits for one of the output streams to be fully read, without going past the given deadline.
	 * On expiry the command is killed and its streams are closed: a blocking read is not interruptible,
	 * so closing the stream is what releases the reader thread.
	 *
	 * @param future   the reader task
	 * @param process  the running command
	 * @param deadline the deadline, as a {@link System#nanoTime()} value
	 * @param timeout  the configured timeout in seconds, for the error message
	 * @return the captured output
	 * @throws IOException          if the stream could not be read
	 * @throws InterruptedException if the current thread is interrupted while waiting
	 * @throws TimeoutException     if the output is not fully collected before the deadline
	 * @throws ExecutionException   if the reader task failed
	 */
	private static String awaitOutput(
		final Future<String> future,
		final Process process,
		final long deadline,
		final int timeout
	) throws IOException, InterruptedException, TimeoutException, ExecutionException {
		try {
			return future.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
		} catch (final TimeoutException e) {
			killTree(process);
			closeQuietly(process.getInputStream());
			closeQuietly(process.getErrorStream());
			future.cancel(true);
			throw timedOut(timeout);
		}
	}

	/**
	 * Builds the exception reporting that the command did not complete in time.
	 *
	 * @param timeout the configured timeout in seconds
	 * @return the exception to throw
	 */
	private static TimeoutException timedOut(final int timeout) {
		return new TimeoutException(String.format("Command execution has timed out after %d s", timeout));
	}

	/**
	 * Closes the given stream, ignoring any failure: it is only closed to release a reader blocked on
	 * it, and the command is already being abandoned.
	 *
	 * @param stream the stream to close
	 */
	private static void closeQuietly(final InputStream stream) {
		try {
			stream.close();
		} catch (final IOException e) {
			log.debug("Failed to close a stream of a command that timed out.", e);
		}
	}

	/**
	 * Builds a task that reads the given stream and decodes it with the given charset, up to
	 * {@link #MAX_OUTPUT_BYTES}. A stream exceeding that size kills the command, so that a runaway
	 * producer cannot fill the heap while the timeout is still running.
	 *
	 * @param process     the running command, killed when its output exceeds the limit
	 * @param inputStream the stream to read
	 * @param charset     the charset used to decode the stream's bytes
	 * @return the task, to be submitted to an executor
	 */
	private static Callable<String> readStreamTask(
		final Process process,
		final InputStream inputStream,
		final Charset charset
	) {
		return () -> {
			final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			final byte[] chunk = new byte[8192];
			int read;
			while ((read = inputStream.read(chunk)) != -1) {
				if (buffer.size() + read > MAX_OUTPUT_BYTES) {
					killTree(process);
					throw new IOException(String.format("Command produced more than %d bytes of output.", MAX_OUTPUT_BYTES));
				}
				buffer.write(chunk, 0, read);
			}
			return buffer.toString(charset);
		};
	}

	/**
	 * Kills the given command and everything it started. The children go first: once the shell is
	 * gone they are no longer its descendants, and on Windows killing the shell leaves them running.
	 *
	 * @param process the command to kill
	 */
	private static void killTree(final Process process) {
		process.descendants().forEach(ProcessHandle::destroyForcibly);
		process.destroyForcibly();
	}

	/**
	 * Resolves the charset to use for decoding stdout/stderr.
	 *
	 * @param charsetName the charset name, or {@code null}/blank for the default (UTF-8)
	 * @return the resolved {@link Charset}
	 */
	private static Charset resolveCharset(final String charsetName) {
		return charsetName == null || charsetName.isBlank() ? StandardCharsets.UTF_8 : Charset.forName(charsetName.trim());
	}

	/**
	 * Parses the given environment variables string, one {@code KEY=VALUE} pair per line.
	 *
	 * @param env the environment variables as a string, or {@code null}
	 * @return a mutable {@link Map} of environment variable names to values
	 */
	private static Map<String, String> parseEnv(final String env) {
		final Map<String, String> result = new HashMap<>();
		if (env == null || env.isBlank()) {
			return result;
		}
		for (final String line : env.split(NEW_LINE)) {
			if (line != null && !line.trim().isEmpty()) {
				final String[] tuple = line.split("=", 2);
				if (tuple.length != 2) {
					throw new IllegalArgumentException("Invalid environment variable format: " + line);
				}
				result.put(tuple[0].trim(), tuple[1].trim());
			}
		}
		return result;
	}

	/**
	 * Builds the shell command used to run the given command line: the shell executable and the
	 * option that makes it read the command from its next argument.
	 *
	 * @return a two-element array: the shell executable and its "run this command" option
	 */
	private static String[] buildShellCommand() {
		if (LocalOsHandler.isWindows()) {
			final var comSpec = System.getenv("ComSpec");
			return new String[] { comSpec == null || comSpec.isBlank() ? "cmd.exe" : comSpec, "/C" };
		}
		final var shell = System.getenv("SHELL");
		return new String[] { shell == null || shell.isBlank() ? "/bin/sh" : shell, "-c" };
	}
}
