package org.metricshub.agent.upgrade;

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

/**
 * Version normalization and comparison for upgrade decisions. Package versions use the
 * dot-separated numeric MetricsHub scheme (e.g. {@code 3.9.05}); running versions may carry a
 * {@code -SNAPSHOT} or other qualifier that is ignored for comparison.
 */
public class VersionHelper {

	private VersionHelper() {}

	/**
	 * Normalizes a version for comparison: trims and strips any qualifier introduced by a dash
	 * (e.g. {@code 3.9.05-SNAPSHOT} becomes {@code 3.9.05}).
	 *
	 * @param version the raw version
	 * @return the normalized version; never {@code null}
	 */
	public static String normalize(final String version) {
		if (version == null) {
			return "";
		}
		final String trimmed = version.trim();
		final int dash = trimmed.indexOf('-');
		return dash >= 0 ? trimmed.substring(0, dash) : trimmed;
	}

	/**
	 * Indicates whether two versions are the same once normalized.
	 *
	 * @param left  the first version
	 * @param right the second version
	 * @return {@code true} when the normalized versions are equal
	 */
	public static boolean isSameVersion(final String left, final String right) {
		return normalize(left).equals(normalize(right));
	}

	/**
	 * Compares two normalized versions segment by segment. Numeric segments are compared as
	 * numbers; non-numeric segments fall back to lexicographic comparison.
	 *
	 * @param left  the first version
	 * @param right the second version
	 * @return a negative value when {@code left} is older, zero when equal, positive when newer
	 */
	public static int compare(final String left, final String right) {
		final String[] leftSegments = normalize(left).split("\\.");
		final String[] rightSegments = normalize(right).split("\\.");
		final int length = Math.max(leftSegments.length, rightSegments.length);
		for (int i = 0; i < length; i++) {
			final String leftSegment = i < leftSegments.length ? leftSegments[i] : "0";
			final String rightSegment = i < rightSegments.length ? rightSegments[i] : "0";
			final int result = compareSegments(leftSegment, rightSegment);
			if (result != 0) {
				return result;
			}
		}
		return 0;
	}

	/**
	 * Compares two version segments, numerically when both are numeric.
	 *
	 * @param left  the first segment
	 * @param right the second segment
	 * @return the comparison result
	 */
	private static int compareSegments(final String left, final String right) {
		try {
			return Long.compare(Long.parseLong(left), Long.parseLong(right));
		} catch (NumberFormatException e) {
			return left.compareTo(right);
		}
	}
}
