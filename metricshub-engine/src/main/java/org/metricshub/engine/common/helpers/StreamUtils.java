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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.stream.Stream;

/**
 * Utility class for working with streams.
 */
public class StreamUtils {

	private StreamUtils() {}

	/**
	 * Reverse the given {@link Stream} using a {@link Deque}.
	 *
	 * @param <T>    The type of elements in the stream.
	 * @param stream The input stream.
	 * @return Reversed {@link Stream}.
	 */
	public static <T> Stream<T> reverse(final Stream<T> stream) {
		final Deque<T> deque = new ArrayDeque<>();
		stream.forEach(deque::push);
		return deque.stream();
	}
}
