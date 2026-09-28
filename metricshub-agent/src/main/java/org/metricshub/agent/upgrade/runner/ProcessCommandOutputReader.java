package org.metricshub.agent.upgrade.runner;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link CommandOutputReader} backed by {@link ProcessBuilder}, used by
 * {@link ServiceNameResolver} to discover the installed MetricsHub service. The output is drained
 * on a separate thread so a probe that hangs without closing its output cannot block the caller —
 * service resolution runs on the sole upgrade worker, which must never wait beyond the deadline
 * while holding the upgrade lock.
 */
@Slf4j
public class ProcessCommandOutputReader implements CommandOutputReader {

	private static final long TIMEOUT_SECONDS = 15;

	/**
	 * Extra time granted to the drain thread after the process ended (or was killed) to deliver
	 * the collected output.
	 */
	private static final long DRAIN_GRACE_SECONDS = 5;

	@Override
	public List<String> readLines(final List<String> command) {
		try {
			final Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
			final CompletableFuture<List<String>> output = CompletableFuture.supplyAsync(() -> drain(process));
			if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
				log.debug("The command {} did not finish within {} seconds; killing it.", command, TIMEOUT_SECONDS);
				process.destroyForcibly();
				output.cancel(true);
				return List.of();
			}
			// The process ended: its output pipe is closed, so the drain completes promptly
			return output.get(DRAIN_GRACE_SECONDS, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return List.of();
		} catch (Exception e) {
			log.debug("The command {} failed: {}", String.join(" ", command), e.getMessage());
			return List.of();
		}
	}

	/**
	 * Reads the whole process output. Runs on a pool thread: when the process is killed on
	 * timeout, the pipe closes and this returns instead of blocking the caller.
	 *
	 * @param process the running process
	 * @return the output lines collected until the stream ended
	 */
	private static List<String> drain(final Process process) {
		final List<String> lines = new ArrayList<>();
		try (
			BufferedReader reader = new BufferedReader(
				new InputStreamReader(process.getInputStream(), Charset.defaultCharset())
			)
		) {
			String line;
			while ((line = reader.readLine()) != null) {
				lines.add(line);
			}
		} catch (IOException e) {
			log.debug("Reading the command output failed: {}", e.getMessage());
		}
		return lines;
	}
}
