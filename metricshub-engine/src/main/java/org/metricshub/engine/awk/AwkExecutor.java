package org.metricshub.engine.awk;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright (C) 2023 - 2025 MetricsHub
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.metricshub.jawk.Awk;
import org.metricshub.jawk.ExitException;
import org.metricshub.jawk.intermediate.AwkTuples;
import org.metricshub.jawk.util.AwkSettings;

/**
 * Utility class for executing AWK scripts.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AwkExecutor {

	/**
	 * Standard Jawk instance, with the MetricsHub extension
	 */
	private static final org.metricshub.jawk.Awk AWK = new Awk(new MetricsHubExtensionForJawk());

	/**
	 * Map of the scripts that have already been transformed to intermediate code
	 */
	static ConcurrentHashMap<String, AwkTuples> awkCodeMap = new ConcurrentHashMap<>();

	/**
	 * Compiles the specified AWK script into AwkTuples.
	 * <p>
	 * Retrieves the AwkTuples in the cache if present to avoid compiling the same
	 * script again and again.
	 * <p>
	 *
	 * @param awkScript Script to compile
	 * @return the corresponding AwkTuples
	 * @throws AwkException when unable to compile the script
	 */
	private static AwkTuples getAwkTuples(String awkScript) throws AwkException {
		// We're using our ConcurrentHashMap to cache the intermediate
		// code, so we don't "compile" it every time.
		// This saves a lot of CPU.
		try {
			return awkCodeMap.computeIfAbsent(
				awkScript,
				code -> {
					try {
						return AWK.compile(code);
					} catch (IOException e) {
						// Throw a RuntimeException so the e.getMessage() can be passed
						// through the call stack
						throw new RuntimeException(e.getMessage());
					}
				}
			);
		} catch (Exception e) {
			throw new AwkException("Failed to compile Awk script:\n" + awkScript, e);
		}
	}

	/**
	 * Compiles the specified AWK expression into AwkTuples.
	 * <p>
	 * Retrieves the AwkTuples in the cache if present to avoid compiling the same
	 * expression again and again.
	 * <p>
	 *
	 * @param awkExpression Script to compile
	 * @return the corresponding AwkTuples
	 * @throws AwkException when unable to compile the expression
	 */
	private static AwkTuples getEvalAwkTuples(String awkExpression) throws AwkException {
		// We're using our ConcurrentHashMap to cache the intermediate
		// code, so we don't "compile" it every time.
		// This saves a lot of CPU.
		try {
			return awkCodeMap.computeIfAbsent(
				awkExpression,
				code -> {
					try {
						return AWK.compileForEval(code);
					} catch (IOException e) {
						// Throw a RuntimeException so the e.getMessage() can be passed
						// through the call stack
						throw new RuntimeException(e.getMessage());
					}
				}
			);
		} catch (Exception e) {
			throw new AwkException("Failed to compile Awk expression: " + awkExpression, e);
		}
	}

	/**
	 * Execute the given <code>awkScript</code> on the <code>awkInput</code>
	 *
	 * @param awkScript The AWK script to process and interpret
	 * @param awkInput The input to modify via the AWK script
	 * @return The result of the AWK script
	 * @throws AwkException if execution fails
	 */
	public static String executeAwk(final String awkScript, final String awkInput) throws AwkException {
		return executeAwk(awkScript, awkInput, new AwkSettings());
	}

	/**
	 * Execute the given <code>awkScript</code> on the <code>awkInput</code>
	 *
	 * @param awkScript The AWK script to process and interpret
	 * @param awkInput The input to modify via the AWK script
	 * @return The result of the AWK script
	 * @throws AwkException if execution fails
	 */
	public static String executeAwk(final String awkScript, final String awkInput, final AwkSettings settings)
		throws AwkException {
		AwkTuples tuples = getAwkTuples(awkScript);
		if (tuples == null) {
			throw new AwkException("Failed to compile Awk script:\n" + awkScript);
		}

		settings.setInput(
			awkInput == null
				? InputStream.nullInputStream()
				: new ByteArrayInputStream(awkInput.getBytes(StandardCharsets.UTF_8))
		);

		// Create the OutputStream to collect the result as a String
		final ByteArrayOutputStream resultBytesStream = new ByteArrayOutputStream();
		final PrintStream resultStream = new PrintStream(resultBytesStream);
		settings.setOutputStream(resultStream);

		// We force \n as the Record Separator (RS) because even if running on Windows
		// we're passing Java strings, where end of lines are simple \n
		settings.setDefaultORS("\n");
		settings.setDefaultRS("\n");

		// Interpret
		try {
			AWK.invoke(tuples, settings);
		} catch (ExitException e) {
			// ExitException code 0 means exit OK
			if (e.getCode() != 0) {
				throw new RuntimeException(e.getMessage());
			}
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage());
		}

		// Result
		return resultBytesStream.toString(StandardCharsets.UTF_8);
	}

	/**
	 * Evaluate the given <code>awkExpression</code> on the <code>awkInput</code>
	 *
	 * @param awkExpression The AWK script to process and interpret
	 * @param awkInput The input to modify via the AWK script
	 * @return The result of the Awk expression
	 * @throws AwkException if evaluation fails
	 */
	public static String evalAwk(final String awkExpression, final String awkInput) throws AwkException {
		AwkTuples tuples = getEvalAwkTuples(awkExpression);
		if (tuples == null) {
			throw new AwkException("Failed to compile Awk script:\n" + awkExpression);
		}

		// Interpret
		try {
			return String.valueOf(AWK.eval(tuples, awkInput, ";"));
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage());
		}
	}

	/**
	 * Clear the {@link ConcurrentHashMap} <code>awkCodeMap</code>
	 */
	public static void resetCache() {
		awkCodeMap.clear();
	}
}
