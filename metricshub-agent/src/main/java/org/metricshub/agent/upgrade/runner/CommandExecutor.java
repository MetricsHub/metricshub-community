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

/**
 * Runs a short-lived command (the launcher of the detached runner, e.g. {@code systemd-run} or
 * {@code schtasks}) and returns its exit code. Abstracted so the launchers can be unit-tested
 * without spawning real processes.
 */
@FunctionalInterface
public interface CommandExecutor {
	/**
	 * Runs the command, waits for it to complete and returns its exit code.
	 *
	 * @param command the command and its arguments
	 * @return the command exit code
	 * @throws IOException          when the command cannot be started
	 * @throws InterruptedException when the calling thread is interrupted
	 */
	int run(List<String> command) throws IOException, InterruptedException;
}
