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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.TABLE_SEP;

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
 * Utility class for executing AWK_PLUS_UTILITY scripts.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AwkExecutor {

	/**
	 * Standard Jawk instance, with the MetricsHub extension
	 */
	private static final Awk AWK_PLUS_UTILITY = new Awk(UtilityExtensionForJawk.INSTANCE);

	/**
	 * Map of the scripts that have already been transformed to intermediate code
	 */
	private static final ConcurrentHashMap<String, AwkTuples> TUPLES_CACHE = new ConcurrentHashMap<>();

	/**
	 * Compiles the specified AWK_PLUS_UTILITY script into AwkTuples.
	 * <p>
	 * Retrieves the AwkTuples in the cache if present to avoid compiling the same
	 * script again and again.
	 * <p>
	 *
	 * @param awkScript Script to compile
	 * @param awkEngine The Awk engine used to compile the script
	 * @return the corresponding AwkTuples
	 * @throws AwkException when unable to compile the script
	 */
	private static AwkTuples getAwkTuples(String awkScript, Awk awkEngine) throws AwkException {
		// We're using our ConcurrentHashMap to cache the intermediate
		// code, so we don't "compile" it every time.
		// This saves a lot of CPU.
		try {
			return TUPLES_CACHE.computeIfAbsent(
				awkScript,
				code -> {
					try {
						return awkEngine.compile(code);
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
	 * Compiles the specified AWK_PLUS_UTILITY expression into AwkTuples.
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
			return TUPLES_CACHE.computeIfAbsent(
				awkExpression,
				code -> {
					try {
						return AWK_PLUS_UTILITY.compileForEval(code);
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
	 * on the specified Awk engine.
	 * <p>
	 * Use this method when you need to execute Awk scripts with specific extensions.
	 *
	 * @param awkScript The AWK_PLUS_UTILITY script to process and interpret
	 * @param awkInput The input to modify via the AWK_PLUS_UTILITY script
	 * @return The result of the AWK_PLUS_UTILITY script
	 * @throws AwkException if execution fails
	 */
	public static String executeAwk(final String awkScript, final String awkInput) throws AwkException {
		return executeAwk(awkScript, awkInput, AWK_PLUS_UTILITY);
	}

	/**
	 * Execute the given <code>awkScript</code> on the <code>awkInput</code>
	 * on the specified Awk engine.
	 * <p>
	 * Use this method when you need to execute Awk scripts with specific extensions.
	 *
	 * @param awkScript The AWK_PLUS_UTILITY script to process and interpret
	 * @param awkInput The input to modify via the AWK_PLUS_UTILITY script
	 * @param awkEngine The Awk engine where the script needs to be executed
	 * @return The result of the AWK_PLUS_UTILITY script
	 * @throws AwkException if execution fails
	 */
	public static String executeAwk(final String awkScript, final String awkInput, final Awk awkEngine)
		throws AwkException {
		var tuples = getAwkTuples(awkScript, awkEngine);
		if (tuples == null) {
			throw new AwkException("Failed to compile Awk script:\n" + awkScript);
		}

		var settings = new AwkSettings();
		settings.setInput(
			awkInput == null
				? InputStream.nullInputStream()
				: new ByteArrayInputStream(awkInput.getBytes(StandardCharsets.UTF_8))
		);

		// Create the OutputStream to collect the result as a String
		final var resultBytesStream = new ByteArrayOutputStream();
		final var resultStream = new PrintStream(resultBytesStream);
		settings.setOutputStream(resultStream);

		// We force \n as the Record Separator (RS) because even if running on Windows
		// we're passing Java strings, where end of lines are simple \n
		settings.setDefaultORS("\n");
		settings.setDefaultRS("\n");

		// Interpret
		try {
			awkEngine.invoke(tuples, settings);
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
	 * @param awkExpression The AWK_PLUS_UTILITY script to process and interpret
	 * @param awkInput The input to modify via the AWK_PLUS_UTILITY script
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
			return String.valueOf(AWK_PLUS_UTILITY.eval(tuples, awkInput, TABLE_SEP));
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage());
		}
	}

	/**
	 * Clear the {@link ConcurrentHashMap} <code>TUPLES_CACHE</code>
	 */
	public static void resetCache() {
		TUPLES_CACHE.clear();
	}
}
