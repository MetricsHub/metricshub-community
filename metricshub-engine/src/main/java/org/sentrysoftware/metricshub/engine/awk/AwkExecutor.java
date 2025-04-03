package org.sentrysoftware.metricshub.engine.awk;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
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

import java.text.ParseException;
import java.util.concurrent.ConcurrentHashMap;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.metricshub.jawk.intermediate.AwkTuples;

/**
 * Utility class for executing AWK scripts.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AwkExecutor {

	/**
	 * Map of the scripts that have already been transformed to intermediate code
	 */
	static ConcurrentHashMap<String, AwkTuples> awkCodeMap = new ConcurrentHashMap<>();

	/**
	 * Execute the given <code>awkScript</code> on the <code>awkInput</code>
	 *
	 * @param awkScript The AWK script to process and interpret
	 * @param awkInput  The input to modify via the AWK script
	 * @return The result of the AWK script
	 * @throws AwkException if execution fails
	 */
	public static String executeAwk(final String awkScript, final String awkInput) throws AwkException {
		// We're using our ConcurrentHashMap to cache the intermediate
		// code, so we don't "compile" it every time.
		// This saves a lot of CPU.
		final AwkTuples tuples;
		try {
			tuples =
				awkCodeMap.computeIfAbsent(
					awkScript,
					code -> {
						try {
							return Awk.getIntermediateCode(code);
						} catch (ParseException e) {
							// Throw a RuntimeException so the e.getMessage() can be passed
							// through the call stack
							throw new RuntimeException(e.getMessage());
						}
					}
				);
		} catch (Exception e) {
			throw new AwkException("Failed to get intermediate code.", e);
		}

		if (tuples == null) {
			throw new AwkException("Failed to interpret the AWK script below:\n" + awkScript);
		}

		final String result = Awk.interpret(awkInput, tuples);

		if (result == null) {
			throw new AwkException(
				String.format("Null result for the script below on the specified input:\n%s\n\nInput:\n%s", awkScript, awkInput)
			);
		}

		return result;
	}

	/**
	 * Clear the {@link ConcurrentHashMap} <code>awkCodeMap</code>
	 */
	public static void resetCache() {
		awkCodeMap.clear();
	}
}
