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
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link CommandOutputReader} backed by {@link ProcessBuilder}, used by
 * {@link ServiceNameResolver} to discover the installed MetricsHub service.
 */
@Slf4j
public class ProcessCommandOutputReader implements CommandOutputReader {

	private static final long TIMEOUT_SECONDS = 15;

	@Override
	public List<String> readLines(final List<String> command) {
		final List<String> lines = new ArrayList<>();
		try {
			final Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
			try (
				BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream(), Charset.defaultCharset())
				)
			) {
				String line;
				while ((line = reader.readLine()) != null) {
					lines.add(line);
				}
			}
			if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				return List.of();
			}
			return lines;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return List.of();
		} catch (Exception e) {
			log.debug("The command {} failed: {}", String.join(" ", command), e.getMessage());
			return List.of();
		}
	}
}
