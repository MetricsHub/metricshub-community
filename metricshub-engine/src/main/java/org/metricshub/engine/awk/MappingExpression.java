package org.metricshub.engine.awk;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
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

import io.jawk.Awk;
import io.jawk.AwkExpression;
import io.jawk.jrt.JRT;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.Getter;

/**
 * The compiled and classified form of a mapping value that starts with <code>=</code>: one AWK expression. Immutable
 * and shared by every host through a static cache keyed by the section kind and the text.
 */
@Getter
public final class MappingExpression {

	/**
	 * How the value is evaluated.
	 */
	public enum Kind {
		/**
		 * <code>$n</code> with n &ge; 1: the cell as written, without an AVM
		 */
		FIELD,
		/**
		 * A constant expression: its text, computed once
		 */
		CONSTANT,
		/**
		 * Evaluated on the AVM of the evaluator
		 */
		EVAL,
		/**
		 * Refused: see {@link Problem}
		 */
		INVALID
	}

	/**
	 * Why a value is {@link Kind#INVALID}.
	 */
	public enum Problem {
		/**
		 * The value holds a reference (<code>${source::</code>, <code>${constant::</code>...)
		 */
		REFERENCE,
		/**
		 * The value names <code>ENVIRON</code> or <code>SYMTAB</code>
		 */
		ENVIRON,
		/**
		 * A syntax error
		 */
		SYNTAX,
		/**
		 * An unknown function or another semantic error
		 */
		UNKNOWN_FUNCTION,
		/**
		 * A wrong number of arguments
		 */
		ARGUMENTS,
		/**
		 * A command or a file
		 */
		SANDBOX,
		/**
		 * A function of the metrics section in another section
		 */
		METRICS_FUNCTION_OUTSIDE_METRICS,
		/**
		 * fakeCounter, accumulate or computePowerShareRatio that is not the whole value
		 */
		NOT_WHOLE_VALUE,
		/**
		 * Another compile error
		 */
		OTHER
	}

	private static final Pattern ENVIRON_PATTERN = Pattern.compile("\\b(ENVIRON|SYMTAB)\\b");

	/**
	 * The call of a whole-value function, then AWK comments at most: once the argument compiles, its literals are
	 * closed, so a <code>#</code> after the closing parenthesis starts a comment
	 */
	private static final Pattern WHOLE_VALUE_PATTERN = Pattern.compile(
		"^(fakeCounter|accumulate|computePowerShareRatio)\\s*\\((.*)\\)(?:\\s*#[^\\n]*)*$",
		Pattern.DOTALL
	);

	private static final Pattern STRING_LITERAL = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"", Pattern.DOTALL);

	private static final double TWO_POWER_63 = 0x1p63;

	/**
	 * Compiles the expressions of the metrics section: every function
	 */
	private static final Awk METRICS_ENGINE = AwkExecutor.newAwk(";", new MappingExtensionForJawk());

	/**
	 * Compiles the expressions of the other sections: no function of the metrics section
	 */
	private static final Awk BASE_ENGINE = AwkExecutor.newAwk(
		";",
		MappingExtensionForJawk.without(MappingExtensionForJawk.METRICS_FUNCTIONS)
	);

	/**
	 * Finds a whole-value function by compilation
	 */
	private static final Awk NO_WHOLE_VALUE_ENGINE = AwkExecutor.newAwk(
		";",
		MappingExtensionForJawk.without(MappingExtensionForJawk.WHOLE_VALUE_FUNCTIONS)
	);

	/**
	 * Compiles the argument of a whole-value function: no function that keeps a state
	 */
	private static final Awk NO_STATE_ENGINE = AwkExecutor.newAwk(
		";",
		MappingExtensionForJawk.without(MappingExtensionForJawk.STATE_FUNCTIONS)
	);

	// ponytail: unbounded, bounded by the values of the connectors and by human edits in the web editor; LRU if it
	// ever matters
	private static final ConcurrentHashMap<String, MappingExpression> CACHE = new ConcurrentHashMap<>();

	private final Kind kind;
	private final Problem problem;
	private final String body;
	private final int fieldIndex;
	private final String constantText;
	private final AwkExpression expression;
	private final Set<String> referencedNames;
	private final Set<String> arrayNames;
	private final String function;

	/**
	 * The matched word for {@link Problem#ENVIRON}, the Jawk compiler message for a compile failure, <code>null</code>
	 * otherwise: connector code, never run-time data
	 */
	private final String error;

	private MappingExpression(
		final Kind kind,
		final Problem problem,
		final String body,
		final AwkExpression expression,
		final String function,
		final String error
	) {
		this.kind = kind;
		this.problem = problem;
		this.body = body;
		this.expression = expression;
		this.function = function;
		this.error = error;
		this.fieldIndex = kind == Kind.FIELD ? expression.getFieldIndex() : -1;
		this.constantText = kind == Kind.CONSTANT ? toText(expression.getConstantValue()) : null;
		if (expression == null) {
			referencedNames = Set.of();
			arrayNames = Set.of();
		} else {
			final Map<String, Boolean> arrays = expression.getGlobalVariableAarrayMap();
			referencedNames = expression.getGlobalVariableOffsetMap()
				.keySet()
				.stream()
				.filter(name -> !AwkNames.SPECIAL_VARIABLES.contains(name))
				.collect(Collectors.toUnmodifiableSet());
			arrayNames = referencedNames.stream()
				.filter(name -> Boolean.TRUE.equals(arrays.get(name)))
				.collect(Collectors.toUnmodifiableSet());
		}
	}

	/**
	 * Whether the value is an expression: its first character, as written, is <code>=</code>.
	 *
	 * @param value The mapping value
	 * @return whether it is an expression
	 */
	public static boolean isExpression(final String value) {
		return value != null && !value.isEmpty() && value.charAt(0) == '=';
	}

	/**
	 * The compiled form of the value, from the cache, compiled on a miss.
	 *
	 * @param metrics Whether the value is under <code>metrics</code>
	 * @param value   The mapping value, an expression; its trailing whitespace (the line feed of a YAML block scalar)
	 *                is ignored
	 * @return its compiled form, {@link Kind#INVALID} when it is refused
	 */
	public static MappingExpression of(final boolean metrics, final String value) {
		return CACHE.computeIfAbsent(
			(metrics ? "M" : "B") + value,
			k -> compile(metrics, value.substring(1).stripTrailing())
		);
	}

	/**
	 * The text of an evaluation result: a number is printed as an integer when it is one.
	 *
	 * @param result The result
	 * @return its text, <code>null</code> for <code>null</code> (an empty expression)
	 */
	public static String toText(final Object result) {
		if (result == null) {
			return null;
		}
		if (result instanceof String text) {
			return text;
		}
		if (result instanceof Double || result instanceof Float) {
			final double number = ((Number) result).doubleValue();
			if (JRT.isActuallyLong(number)) {
				final double rounded = Math.rint(number);
				if (rounded >= -TWO_POWER_63 && rounded < TWO_POWER_63) {
					return Long.toString((long) rounded);
				}
			}
			return Double.toString(number);
		}
		if (result instanceof Number number) {
			return String.valueOf(number.longValue());
		}
		return String.valueOf(result);
	}

	/**
	 * The expression without its string literals (<code>"..."</code> with their escapes), for the text checks.
	 *
	 * @param body The expression
	 * @return the expression without its string literals
	 */
	public static String withoutStringLiterals(final String body) {
		return STRING_LITERAL.matcher(body).replaceAll("");
	}

	private static MappingExpression compile(final boolean metrics, final String body) {
		if (AwkNames.holdsReference(body)) {
			return invalid(Problem.REFERENCE, body, null, null);
		}
		final Matcher environ = ENVIRON_PATTERN.matcher(body);
		if (environ.find()) {
			return invalid(Problem.ENVIRON, body, null, environ.group(1));
		}

		final AwkExpression expression;
		try {
			expression = (metrics ? METRICS_ENGINE : BASE_ENGINE).compileExpression(body);
		} catch (Exception e) {
			if (!metrics && compiles(METRICS_ENGINE, body)) {
				return invalid(Problem.METRICS_FUNCTION_OUTSIDE_METRICS, body, null, null);
			}
			return invalid(problemOf(e), body, null, e.getMessage());
		}

		String function = null;
		if (metrics && !compiles(NO_WHOLE_VALUE_ENGINE, body)) {
			// It calls fakeCounter, accumulate or computePowerShareRatio: the call must be the whole value. The argument
			// compiles only when its parentheses balance, so the anchored call is the whole value.
			final Matcher matcher = WHOLE_VALUE_PATTERN.matcher(body.trim());
			if (!matcher.matches() || !compiles(NO_STATE_ENGINE, matcher.group(2))) {
				return invalid(Problem.NOT_WHOLE_VALUE, body, firstWholeValueFunction(body), null);
			}
			function = matcher.group(1);
		}

		final Kind kind;
		if (expression.getFieldIndex() >= 1) {
			kind = Kind.FIELD;
		} else if (expression.isConstant()) {
			kind = Kind.CONSTANT;
		} else {
			kind = Kind.EVAL;
		}
		return new MappingExpression(kind, null, body, expression, function, null);
	}

	private static MappingExpression invalid(
		final Problem problem,
		final String body,
		final String function,
		final String error
	) {
		return new MappingExpression(Kind.INVALID, problem, body, null, function, error);
	}

	private static boolean compiles(final Awk engine, final String text) {
		try {
			engine.compileExpression(text);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private static Problem problemOf(final Exception e) {
		return switch (e.getClass().getSimpleName()) {
			case "ParserException", "LexerException" -> Problem.SYNTAX;
			case "SemanticException" -> Problem.UNKNOWN_FUNCTION;
			case "IllegalAwkArgumentException" -> Problem.ARGUMENTS;
			case "AwkSandboxException" -> Problem.SANDBOX;
			default -> Problem.OTHER;
		};
	}

	private static String firstWholeValueFunction(final String body) {
		String first = null;
		int firstIndex = Integer.MAX_VALUE;
		for (final String name : MappingExtensionForJawk.WHOLE_VALUE_FUNCTIONS) {
			final int index = body.indexOf(name);
			if (index >= 0 && index < firstIndex) {
				first = name;
				firstIndex = index;
			}
		}
		return first;
	}
}
