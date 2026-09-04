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

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link CommandExecutor} backed by {@link ProcessBuilder}. Used to launch the detached upgrade
 * runner: the launcher command ({@code systemd-run} / {@code schtasks}) registers the transient
 * unit or scheduled task and returns quickly, so a bounded wait is sufficient.
 */
@Slf4j
public class ProcessCommandExecutor implements CommandExecutor {

	private static final long LAUNCH_TIMEOUT_SECONDS = 30;

	@Override
	public int run(final List<String> command) throws IOException, InterruptedException {
		final Process process = new ProcessBuilder(command)
			.redirectOutput(ProcessBuilder.Redirect.DISCARD)
			.redirectErrorStream(true)
			.start();
		if (!process.waitFor(LAUNCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
			process.destroyForcibly();
			throw new IOException("The upgrade runner launcher did not return within " + LAUNCH_TIMEOUT_SECONDS + " seconds");
		}
		return process.exitValue();
	}
}
