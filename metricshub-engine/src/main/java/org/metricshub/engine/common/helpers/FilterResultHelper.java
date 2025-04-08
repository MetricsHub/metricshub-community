package org.metricshub.engine.common.helpers;

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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.COMMA;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.EMPTY;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.TAB;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.TABLE_SEP;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.WHITE_SPACE;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.metricshub.engine.strategy.utils.PslUtils;

/**
 * Utility class for filtering and selecting columns in a list of lines.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FilterResultHelper {

	/**
	 * Filter the lines:
	 * <ul>
	 * 	<li>In removing the header if exists: all the lines from start to removeHeader number.</li>
	 * 	<li>In removing the Footer if exists: all the removeFooter number lines from the end.</li>
	 * 	<li>In removing all the lines matching to the excludeRegExp if exist</li>
	 * 	<li>In keeping only the lines matching to the keepOnlyRegExp if exist</li>
	 * </ul>
	 *
	 * @param lines The lines to be filtered. (mandatory)
	 * @param removeHeader The number of lines to ignored from the start.
	 * @param removeFooter  The number of lines to ignored from the end.
	 * @param excludeRegExp The PSL regexp to exclude lines.
	 * @param keepOnlyRegExp The PSL regexp for lines to keep.
	 * @return The filterd lines.
	 */
	public static List<String> filterLines(
		@NonNull final List<String> lines,
		final Integer removeHeader,
		final Integer removeFooter,
		final String excludeRegExp,
		final String keepOnlyRegExp
	) {
		// Remove header : remove number of lines from beginning
		final int begin = removeHeader != null ? removeHeader : 0;

		// Remove footer : remove number of lines from the end.
		final int end = removeFooter != null ? lines.size() - removeFooter : lines.size();

		final Pattern excludePattern = excludeRegExp == null || excludeRegExp.isEmpty()
			? null
			: Pattern.compile(PslUtils.psl2JavaRegex(excludeRegExp));

		final Pattern keepOnlyPattern = keepOnlyRegExp == null || keepOnlyRegExp.isEmpty()
			? null
			: Pattern.compile(PslUtils.psl2JavaRegex(keepOnlyRegExp));

		// Remove lines containing a given regular expression excludeRegExp
		// Keep only the lines containing a given regular expression
		return IntStream
			.range(begin, end)
			.mapToObj(lines::get)
			.filter(line ->
				(excludePattern == null || !excludePattern.matcher(line).find()) &&
				(keepOnlyPattern == null || keepOnlyPattern.matcher(line).find())
			)
			.collect(Collectors.toList());
	}

	/**
	 * Select the columns in the lines.
	 * Extract separators and split each line with these separators
	 * keep only values (from the split result) which index matches with the selected column list
	 * @param lines The lines (mandatory)
	 * @param separators The separators
	 * @param selectColumns The list of the selected columns position.
	 *
	 * @return The lines with the selected columns.
	 */
	public static List<String> selectedColumns(
		@NonNull final List<String> lines,
		final String separators,
		final String selectColumns
	) {
		if (separators == null || separators.isEmpty() || selectColumns == null || selectColumns.isBlank()) {
			return lines;
		}

		return lines
			.stream()
			.map(line -> {
				// protect the initial string that contains ";" and replace it with "," if this
				// latest is not in Separators list. Otherwise, just remove the ";"
				// replace all separators by ";", which is the standard separator used by MS_HW
				if (!separators.contains(TABLE_SEP) && !separators.contains(COMMA)) {
					return line.replace(TABLE_SEP, COMMA);
				}
				if (!separators.contains(TABLE_SEP)) {
					return line.replace(TABLE_SEP, EMPTY);
				}
				return line;
			})
			.map(line ->
				!separators.contains(TAB) && !separators.contains(WHITE_SPACE)
					? PslUtils.nthArgf(line, selectColumns, separators, TABLE_SEP) // equivalent to ntharg // if separator = tab or simple space, then ignore empty cells
					: PslUtils.nthArg(line, selectColumns, separators, TABLE_SEP)
			)
			.collect(Collectors.toList());
	}
}
