package org.metricshub.extension.emulation;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Emulation Extension
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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A protocol-agnostic, thread-safe round-robin manager for emulation playback.
 *
 * <p>Each emulation image ({@code image.yaml}) may contain multiple entries with the
 * same request signature. This manager tracks which response was last served for
 * each unique request, cycling through matching entries in order.
 *
 * <p>State is organized as:
 * <pre>
 * imagePath &rarr; (requestKey &rarr; lastRespondedIndex)
 * </pre>
 *
 * <p>Where:
 * <ul>
 *   <li>{@code imagePath} — the absolute path of the image file, identifying a specific emulation image</li>
 *   <li>{@code requestKey} — a string that uniquely identifies a request definition (computed by each protocol)</li>
 *   <li>{@code lastRespondedIndex} — an atomic counter tracking the next response to serve</li>
 * </ul>
 *
 * <p>Thread-safe: uses {@link ConcurrentHashMap} and {@link AtomicInteger} so that
 * parallel engine queries can safely advance the round-robin counters.
 */
public class EmulationRoundRobinManager {

	private final ConcurrentHashMap<String, ConcurrentHashMap<String, AtomicInteger>> state = new ConcurrentHashMap<>();

	/**
	 * Returns the next index (0-based) for the given request key within the given
	 * image, cycling through {@code 0 .. matchCount-1} in round-robin order.
	 *
	 * @param imagePath  The absolute path of the emulation image file.
	 * @param requestKey The unique identifier for the request definition (protocol-specific).
	 * @param matchCount The total number of matching entries for this request key.
	 * @return The 0-based index of the next response to serve.
	 * @throws IllegalArgumentException If {@code matchCount} is less than 1.
	 */
	public int nextIndex(final String imagePath, final String requestKey, final int matchCount) {
		if (matchCount < 1) {
			throw new IllegalArgumentException("matchCount must be >= 1, got: " + matchCount);
		}
		final ConcurrentHashMap<String, AtomicInteger> imageState = state.computeIfAbsent(
			imagePath,
			key -> new ConcurrentHashMap<>()
		);
		final AtomicInteger counter = imageState.computeIfAbsent(requestKey, key -> new AtomicInteger(0));
		final int index = counter.getAndIncrement();
		return index % matchCount;
	}
}
