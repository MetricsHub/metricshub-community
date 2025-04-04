package org.metricshub.engine.strategy.utils;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2025 MetricsHub
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
import static org.metricshub.engine.common.helpers.MetricsHubConstants.WHITE_SPACE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.strategy.source.SourceTable;

/**
 * Utility class for handling PSL (PATROL Script Language) regular expressions
 * and formatting data in an extended JSON format.
 */
@Slf4j
public class PslUtils {

	private static final String SPECIAL_CHARACTERS = "^$.*+?[]\\";
	private static final String BACKSLASH_B = "\\b";
	private static final String DOT_PLUS = ".+";
	private static final String DOT = ".";
	private static final char BACKSLASH_CHAR = '\\';
	private static final char OPENING_PARENTHESIS_CHAR = '(';
	private static final char CLOSING_PARENTHESIS_CHAR = ')';
	private static final char PIPE_CHAR = '|';
	private static final char OPENING_CURLY_BRACKET_CHAR = '{';
	private static final char CLOSING_CURLY_BRACKET_CHAR = '}';
	private static final char OPENING_SQUARE_BRACKET_CHAR = '[';
	private static final char CLOSING_SQUARE_BRACKET_CHAR = ']';
	private static final char LOWER_THAN_CHAR = '<';
	private static final char GREATER_THAN_CHAR = '>';

	// Private constructor to prevent instantiation of PslUtils
	private PslUtils() {}

	/**
	 * Converts a PSL regex into its Java equivalent.
	 * Method shamelessly taken from somewhere else.
	 * <br>
	 * @param pslRegex Regular expression as used in PSL's grep() function.
	 * @return Regular expression that can be used in Java's Pattern.compile.
	 */
	public static String psl2JavaRegex(final String pslRegex) {
		if (pslRegex == null || pslRegex.isEmpty()) {
			return "";
		}

		if (DOT.equals(pslRegex)) {
			return DOT_PLUS;
		}

		// We 're going to build the regex char by char
		StringBuilder javaRegex = new StringBuilder();

		// Parse the PSL regex char by char (the very last char will be added unconditionally)
		int i;
		boolean inRange = false;
		for (i = 0; i < pslRegex.length(); i++) {
			char c = pslRegex.charAt(i);

			if (c == BACKSLASH_CHAR && i < pslRegex.length() - 1) {
				i = handleBackSlash(pslRegex, inRange, i, javaRegex);
			} else if (
				// CHECKSTYLE:OFF
				c == OPENING_PARENTHESIS_CHAR ||
				c == CLOSING_PARENTHESIS_CHAR ||
				c == PIPE_CHAR ||
				c == OPENING_CURLY_BRACKET_CHAR ||
				c == CLOSING_CURLY_BRACKET_CHAR
				// CHECKSTYLE:ON
			) {
				javaRegex.append(BACKSLASH_CHAR).append(c);
			} else if (c == OPENING_SQUARE_BRACKET_CHAR) {
				// Regex ranges have a different escaping system, so we need to
				// know when we're inside a [a-z] range or not
				javaRegex.append(c);
				inRange = true;
			} else if (c == CLOSING_SQUARE_BRACKET_CHAR) {
				// Getting out of a [] range
				javaRegex.append(c);
				inRange = false;
			} else {
				// Other cases
				javaRegex.append(c);
			}
		}

		return javaRegex.toString();
	}

	/**
	 * Properly converts a backslash (present in the given PSL regular expression)
	 * to its Java regular expression version,
	 * and appends the result to the given Java regular expression {@link StringBuilder}.
	 *
	 * @param pslRegex	Regular expression as used in PSL's grep() function.
	 * @param inRange	Indicate whether the backslash is within a range (i.e. between '[' and ']').
	 * @param index		The index of the backslash in the regular expression.
	 * @param javaRegex	The {@link StringBuilder} holding the resulting Java regular expression.
	 *
	 * @return			The new value of the index
	 */
	private static int handleBackSlash(
		final String pslRegex,
		final boolean inRange,
		final int index,
		final StringBuilder javaRegex
	) {
		int result = index;

		if (inRange) {
			// Escape works differently in [] ranges
			// We simply need to double backslashes
			javaRegex.append("\\\\");
		} else {
			// Backslashes in PSL regex are utterly broken
			// We need to handle all cases here to convert to proper regex in Java
			char nextChar = pslRegex.charAt(index + 1);
			if (nextChar == LOWER_THAN_CHAR || nextChar == GREATER_THAN_CHAR) {
				// Replace \< and \> with \b
				javaRegex.append(BACKSLASH_B);
				result++;
			} else if (SPECIAL_CHARACTERS.indexOf(nextChar) > -1) {
				// Append the backslash and what it's protecting, as is
				javaRegex.append(BACKSLASH_CHAR).append(nextChar);
				result++;
			} else {
				// Append the next character
				javaRegex.append(nextChar);
				result++;
			}
		}

		return result;
	}

	/**
	 * Converts an entry and its result into an extended JSON format:
	 * 	{
	 * 		"Entry":{
	 * 			"Full":"&lt;entry&gt;",
	 * 			"Column(1)":"&lt;1st field value&gt;",
	 * 	    	"Column(2)":"&lt;2nd field value&gt;",
	 * 			"Column(3)":"&lt;3rd field value&gt;",
	 * 			"Value":&lt;result&gt; &lt;- Result must be properly formatted (either "result" or {"property":"value"}
	 * 		}
	 * 	}
	 *
	 * @param row The row of values.
	 * @param tableResult The output returned by the SourceVisitor.
	 * @return String value
	 */
	public static String formatExtendedJSON(@NonNull String row, @NonNull SourceTable tableResult)
		throws IllegalArgumentException {
		if (row.isEmpty()) {
			log.error("formatExtendedJSON received Empty row of values. Returning empty string.");
			return EMPTY;
		}

		String rawData = tableResult.getRawData();
		if (rawData == null || rawData.isEmpty()) {
			log.error("formatExtendedJSON received Empty SourceTable data {}. Returning empty string.", tableResult);
			return EMPTY;
		}

		StringBuilder jsonContent = new StringBuilder();
		jsonContent.append("{\n\"Entry\":{\n\"Full\":\"").append(row).append("\",\n");

		int i = 1;

		for (String value : row.split(",")) {
			jsonContent.append("\"Column(").append(i).append(")\":\"").append(value).append("\",\n");
			i++;
		}

		jsonContent.append("\"Value\":").append(rawData).append("\n}\n}");

		return jsonContent.toString();
	}

	/**
	 * Extracts and formats the nth group in the given text based on the specified columns, separators, and result separator.
	 *
	 * @param text              The text that should be parsed.
	 * @param selectColumns     The list/range(s) of columns that should be extracted from the text.
	 * @param separators        The set of characters used to split the given text.
	 * @param resultSeparator   The separator used to join the resulting elements.
	 * @return                  The nth group in the given text,
	 *                          formatted according to the given separators and column numbers.
	 */
	public static String nthArgf(String text, String selectColumns, String separators, String resultSeparator) {
		return nthArgCommon(text, selectColumns, separators, resultSeparator, false);
	}

	/**
	 * Extracts and formats the nth group in the given text based on the specified columns, separators, and result separator.
	 *
	 * @param text              The text that should be parsed.
	 * @param selectColumns     The list/range(s) of columns that should be extracted from the text.
	 * @param separators        The set of characters used to split the given text.
	 * @param resultSeparator   The separator used to join the resulting elements.
	 * @return                  The nth group in the given text,
	 *                          formatted according to the given separators and column numbers.
	 */
	public static String nthArg(String text, String selectColumns, String separators, String resultSeparator) {
		return nthArgCommon(text, selectColumns, separators, resultSeparator, true);
	}

	/**
	 * @param text				The text that should be parsed.
	 * @param selectColumns		The list/range(s) of columns that should be extracted from the text.
	 * @param separators		The set of characters used to split the given text.
	 * @param resultSeparator	The separator used to join the resulting elements.
	 * @param isNthArg			Indicates whether an <em>nthArg</em> operation should be performed
	 *                          (as opposed to a <em>nthArgf</em> operation).
	 *
	 * @return					The nth group in the given text,
	 * 							as formatted according to the given separators and column numbers.
	 */
	// CHECKSTYLE:OFF
	private static String nthArgCommon(
		String text,
		final String selectColumns,
		final String separators,
		String resultSeparator,
		final boolean isNthArg
	) {
		// If any arg is null, then return empty String
		if (
			text == null ||
			selectColumns == null ||
			separators == null ||
			text.isEmpty() ||
			selectColumns.isEmpty() ||
			separators.isEmpty()
		) {
			return "";
		}

		// Replace special chars with their literal equivalents
		final String separatorsRegExp = String.format(
			"[%s]",
			separators.replaceAll("([()\\[\\]{}\\\\^\\-$|?*+.])", "\\\\$1")
		);

		if (isNthArg) {
			// Remove redundant separators
			text = text.replaceAll(String.format("(%s)(%s)+", separatorsRegExp, separatorsRegExp), "$1");
			// Remove leading separators
			text = text.replaceAll("^" + separatorsRegExp + "+", "");
		}

		// Check the result separator
		if (resultSeparator == null) {
			resultSeparator = WHITE_SPACE;
		}

		// The list holding the final result
		final List<String> finalResult = new ArrayList<>();

		// Split the text value using the new line separator
		final String[] textArray = text.split(NEW_LINE);
		for (String line : textArray) {
			processText(line, selectColumns, separatorsRegExp, resultSeparator, finalResult, isNthArg);
		}

		// Alright, we did it!
		return finalResult.stream().collect(Collectors.joining(resultSeparator));
	}

	/**
	 * Process the given text value and update the finalResult {@link List}
	 *
	 * @param text             The text we wish to process
	 * @param selectColumns    The columns to select. E.g. <em>1-2</em> <em>1,2,3</em> <em>1-</em> <em>-4</em>
	 * @param separatorsRegExp The separator used to split the text value
	 * @param resultSeparator  The separator of the final result
	 * @param finalResult      The final result as {@link List}
	 * @param isNthArg         Indicate whether we want ntharg or nthargf.
	 */
	static void processText(
		final String text,
		final String selectColumns,
		final String separatorsRegExp,
		final String resultSeparator,
		final List<String> finalResult,
		final boolean isNthArg
	) {
		// Split the input text into a String array thanks to the separatorsRegExp
		final String[] splitText = text.split(separatorsRegExp, -1);

		// The user can specify several columns.
		// Split the selectColumns into an array too.
		final String[] columnsArray = selectColumns.split(",");

		// So, for each columns group requested
		String result = null;
		int[] columnsRange;
		int fromColumnNumber;
		int toColumnNumber;
		for (final String columns : columnsArray) {
			// Get the columns range
			columnsRange = getColumnsRange(columns, splitText.length);
			fromColumnNumber = columnsRange[0];
			toColumnNumber = columnsRange[1];

			// If we have valid fromColumnNumber and toColumnNumber, then retrieve these columns
			// which are actually items in the splitText array
			if (fromColumnNumber > 0 && fromColumnNumber <= toColumnNumber) {
				result =
					Arrays
						.stream(splitText, fromColumnNumber - 1, toColumnNumber)
						.filter(value -> !isNthArg || !value.trim().isEmpty())
						.collect(Collectors.joining(resultSeparator));

				finalResult.add(result);
			}
		}
	}

	// CHECKSTYLE:ON
	/**
	 * @param columns       The {@link String} denoting the range of columns, in one of the following forms:
	 *                      "m-n", "m-" or "-n".
	 * @param columnCount	The total number of columns.
	 *
	 * @return				A 2-element array A with:
	 * 						<ul>
	 * 							<li>A[0] being the start of the range, inclusive</li>
	 * 							<li>A[1] being the end of the range, inclusive</li>
	 * 						</ul>
	 */
	private static int[] getColumnsRange(final String columns, final int columnCount) {
		int fromColumnNumber;
		int toColumnNumber;

		try {
			final int dashIndex = columns.indexOf("-");
			final int columnsLength = columns.length();

			// If it is a simple number, we'll retrieve only that column number
			if (dashIndex == -1) {
				fromColumnNumber = Integer.parseInt(columns);
				toColumnNumber = fromColumnNumber;
			} else if (dashIndex == 0) {
				// If it is "-n", then we'll retrieve all columns til number n
				fromColumnNumber = 1;
				toColumnNumber = Integer.parseInt(columns.substring(1));
			} else if (dashIndex == columnsLength - 1) {
				// If it is "n-", then we'll retrieve all columns starting from n
				fromColumnNumber = Integer.parseInt(columns.substring(0, columnsLength - 1));
				toColumnNumber = columnCount;
			} else {
				// Else, if it is "m-n", we'll retrieve all columns starting from m til n
				fromColumnNumber = Integer.parseInt(columns.substring(0, dashIndex));
				toColumnNumber = Integer.parseInt(columns.substring(dashIndex + 1));
				if (toColumnNumber > columnCount) {
					toColumnNumber = columnCount;
				}
			}

			if (fromColumnNumber > columnCount || toColumnNumber > columnCount) {
				log.warn(
					"getColumnRange: Invalid range for a {}-length array: [{}-{}].",
					columnCount,
					fromColumnNumber,
					toColumnNumber
				);

				fromColumnNumber = 0;
				toColumnNumber = 0;
			}
		} catch (NumberFormatException e) {
			log.warn("getColumnRange: Could not determine the range denoted by {}: {}.", columns, e.getMessage());

			fromColumnNumber = 0;
			toColumnNumber = 0;
		}

		return new int[] { fromColumnNumber, toColumnNumber };
	}
}
