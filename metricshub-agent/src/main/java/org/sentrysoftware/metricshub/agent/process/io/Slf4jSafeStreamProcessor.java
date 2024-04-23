package org.sentrysoftware.metricshub.agent.process.io;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2024 Sentry Software
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

import lombok.AllArgsConstructor;
import lombok.NonNull;

/**
 * A {@link StreamProcessor} implementation designed to safely handle logging of
 * process output blocks through the Slf4j logger facade. This processor ensures
 * that log messages containing placeholder tokens (like "{}") are processed
 * correctly by escaping them to avoid accidental formatting errors during logging.
 *
 * This class is particularly useful when you have logs that may contain untrusted
 * input that should not affect the log format and should be handled in a secure manner.
 **/
@AllArgsConstructor
public class Slf4jSafeStreamProcessor implements StreamProcessor {

	@NonNull
	private final StreamProcessor destination;

	@Override
	public void process(String block) {
		destination.process(block != null ? block.replace("{}", "{ }") : block);
	}
}
