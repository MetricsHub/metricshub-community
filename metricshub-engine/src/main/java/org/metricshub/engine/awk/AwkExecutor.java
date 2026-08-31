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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.EMPTY;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.TABLE_SEP;

import io.jawk.Awk;
import io.jawk.AwkExpression;
import io.jawk.AwkProgram;
import io.jawk.util.AwkSettings;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Utility class for executing AWK scripts and expressions.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AwkExecutor {

	/**
	 * Standard Jawk instance, with the MetricsHub utility extension
	 */
	private static final Awk AWK_PLUS_UTILITY = new Awk(List.of(UtilityExtensionForJawk.INSTANCE), newAwkSettings(null));

	/**
	 * Jawk instance dedicated to expression evaluation, where the record is a table row and the fields are therefore
	 * separated by the table separator
	 */
	private static final Awk AWK_FOR_EVAL = new Awk(List.of(UtilityExtensionForJawk.INSTANCE), newAwkSettings(TABLE_SEP));

	/**
	 * Map of the scripts that have already been compiled to intermediate code
	 */
	private static final ConcurrentHashMap<String, AwkProgram> PROGRAM_CACHE = new ConcurrentHashMap<>();

	/**
	 * Map of the expressions that have already been compiled to intermediate code
	 */
	private static final ConcurrentHashMap<String, AwkExpression> EXPRESSION_CACHE = new ConcurrentHashMap<>();

	/**
	 * Build the {@link AwkSettings} MetricsHub expects from a Jawk engine.
	 *
	 * @param fieldSeparator The field separator (FS) to force, or <code>null</code> to keep the Jawk default.
	 * @return a new {@link AwkSettings} instance
	 */
	public static AwkSettings newAwkSettings(final String fieldSeparator) {
		final AwkSettings settings = new AwkSettings();

		// We force \n as the Record Separator (RS) because even when running on Windows
		// we are passing Java strings, where end of lines are simple \n.
		// The Output Record Separator (ORS) is \n on every platform since Jawk 6.
		settings.setDefaultRS(NEW_LINE);

		if (fieldSeparator != null) {
			settings.setFieldSeparator(fieldSeparator);
		}

		return settings;
	}

	/**
	 * Compiles the specified script into an {@link AwkProgram}.
	 * <p>
	 * Retrieves the program from the cache if present to avoid compiling the same
	 * script again and again.
	 * <p>
	 *
	 * @param awkScript Script to compile
	 * @param awkEngine The Awk engine used to compile the script
	 * @return the corresponding AwkProgram
	 * @throws AwkException when unable to compile the script
	 */
	private static AwkProgram getAwkProgram(String awkScript, Awk awkEngine) throws AwkException {
		// We're using our ConcurrentHashMap to cache the intermediate
		// code, so we don't "compile" it every time.
		// This saves a lot of CPU.
		try {
			return PROGRAM_CACHE.computeIfAbsent(awkScript, code -> {
				try {
					return awkEngine.compile(code);
				} catch (IOException e) {
					// Throw a RuntimeException so the e.getMessage() can be passed
					// through the call stack
					throw new RuntimeException(e.getMessage());
				}
			});
		} catch (Exception e) {
			throw new AwkException("Failed to compile Awk script:\n" + awkScript, e);
		}
	}

	/**
	 * Compiles the specified expression into an {@link AwkExpression}.
	 * <p>
	 * Retrieves the expression from the cache if present to avoid compiling the same
	 * expression again and again.
	 * <p>
	 *
	 * @param awkExpression Expression to compile
	 * @return the corresponding AwkExpression
	 * @throws AwkException when unable to compile the expression
	 */
	private static AwkExpression getAwkExpression(String awkExpression) throws AwkException {
		// We're using our ConcurrentHashMap to cache the intermediate
		// code, so we don't "compile" it every time.
		// This saves a lot of CPU.
		try {
			return EXPRESSION_CACHE.computeIfAbsent(awkExpression, code -> {
				try {
					return AWK_FOR_EVAL.compileExpression(code);
				} catch (IOException e) {
					// Throw a RuntimeException so the e.getMessage() can be passed
					// through the call stack
					throw new RuntimeException(e.getMessage());
				}
			});
		} catch (Exception e) {
			throw new AwkException("Failed to compile Awk expression: " + awkExpression, e);
		}
	}

	/**
	 * Execute the given <code>awkScript</code> on the <code>awkInput</code>.
	 *
	 * @param awkScript The AWK script to process and interpret
	 * @param awkInput The input to modify via the AWK script
	 * @return The result of the AWK script
	 * @throws AwkException if execution fails
	 */
	public static String executeAwk(final String awkScript, final String awkInput) throws AwkException {
		return executeAwk(awkScript, awkInput, AWK_PLUS_UTILITY);
	}

	/**
	 * Return the standard Jawk engine, the one carrying the MetricsHub utility functions.
	 * <p>
	 * Use it to reach {@link #executeAwk(String, String, Awk, Map)} without having to build an engine. There is
	 * deliberately no <code>executeAwk(script, input, variables)</code> overload: it would have the same arity as
	 * {@link #executeAwk(String, String, Awk)} and make the call site ambiguous.
	 *
	 * @return the standard Jawk engine
	 */
	public static Awk getUtilityEngine() {
		return AWK_PLUS_UTILITY;
	}

	/**
	 * Execute the given <code>awkScript</code> on the <code>awkInput</code>
	 * on the specified Awk engine.
	 * <p>
	 * Use this method when you need to execute Awk scripts with specific extensions.
	 *
	 * @param awkScript The AWK script to process and interpret
	 * @param awkInput The input to modify via the AWK script
	 * @param awkEngine The Awk engine where the script needs to be executed
	 * @return The result of the AWK script
	 * @throws AwkException if execution fails
	 */
	public static String executeAwk(final String awkScript, final String awkInput, final Awk awkEngine)
		throws AwkException {
		return executeAwk(awkScript, awkInput, awkEngine, null);
	}

	/**
	 * Execute the given <code>awkScript</code> on the <code>awkInput</code>
	 * on the specified Awk engine, seeding the given variables.
	 * <p>
	 * Use this method when you need to execute Awk scripts with specific extensions or variables.
	 *
	 * @param awkScript The AWK script to process and interpret
	 * @param awkInput The input to modify via the AWK script
	 * @param awkEngine The Awk engine where the script needs to be executed
	 * @param awkVariables The variables to expose to the script, can be <code>null</code>. A {@link List} or a
	 *                     {@link Map} value is exposed to the script as an AWK array.
	 * @return The result of the AWK script
	 * @throws AwkException if execution fails
	 */
	public static String executeAwk(
		final String awkScript,
		final String awkInput,
		final Awk awkEngine,
		final Map<String, Object> awkVariables
	) throws AwkException {
		final AwkProgram program = getAwkProgram(awkScript, awkEngine);
		if (program == null) {
			throw new AwkException("Failed to compile Awk script:\n" + awkScript);
		}

		final Awk.AwkRunBuilder runBuilder = awkEngine.script(program).input(awkInput == null ? EMPTY : awkInput);

		if (awkVariables != null && !awkVariables.isEmpty()) {
			runBuilder.variables(awkVariables);
		}

		// Interpret. Jawk already swallows a zero exit code, so any exception reaching us is a real failure
		try {
			return runBuilder.execute();
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage());
		}
	}

	/**
	 * Evaluate the given <code>awkExpression</code> on the <code>awkInput</code>
	 *
	 * @param awkExpression The AWK expression to process and interpret
	 * @param awkInput The record exposed to the expression as <code>$0</code>
	 * @return The result of the Awk expression
	 * @throws AwkException if evaluation fails
	 */
	public static String evalAwk(final String awkExpression, final String awkInput) throws AwkException {
		final AwkExpression expression = getAwkExpression(awkExpression);
		if (expression == null) {
			throw new AwkException("Failed to compile Awk expression: " + awkExpression);
		}

		// Interpret
		try {
			return String.valueOf(AWK_FOR_EVAL.eval(expression, awkInput));
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage());
		}
	}

	/**
	 * Clear the compiled program and expression caches
	 */
	public static void resetCache() {
		PROGRAM_CACHE.clear();
		EXPRESSION_CACHE.clear();
	}
}
