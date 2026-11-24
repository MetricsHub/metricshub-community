package org.metricshub.engine.telemetry;

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

import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import lombok.NonNull;

/**
 * Cumulative request counters of one monitored resource, by protocol and {@link RequestOutcome}. One instance
 * belongs to each {@link TelemetryManager}: the counters live and die with the resource, and the requests sent
 * for another resource, a CLI command or a connection test never reach them.
 * <p>
 * A protocol records exactly one outcome per request attempt it actually sends, in the thread that waits for the
 * request, once the outcome is known. Thread-safe.
 */
public class RequestStats {

	/**
	 * Counters by protocol. The keys are the protocol labels, a small fixed set.
	 */
	private final Map<String, LongAdder[]> countersByProtocol = new ConcurrentHashMap<>();

	/**
	 * Records the outcome of one request attempt.
	 *
	 * @param protocol The protocol label, exported as the {@code operation_type} attribute (e.g. {@code snmp}).
	 * @param outcome  The terminal state of the attempt.
	 */
	public void record(@NonNull final String protocol, @NonNull final RequestOutcome outcome) {
		countersByProtocol.computeIfAbsent(protocol, key -> newCounters())[outcome.ordinal()].increment();
	}

	/**
	 * Returns the current counters, by protocol (sorted) and outcome. Every outcome of a protocol that recorded at
	 * least one attempt is present, with {@code 0} when it never occurred.
	 *
	 * @return A new map of the counters.
	 */
	public Map<String, Map<RequestOutcome, Long>> snapshot() {
		final Map<String, Map<RequestOutcome, Long>> snapshot = new TreeMap<>();
		countersByProtocol.forEach((protocol, counters) -> {
			final Map<RequestOutcome, Long> byOutcome = new EnumMap<>(RequestOutcome.class);
			for (final RequestOutcome outcome : RequestOutcome.values()) {
				byOutcome.put(outcome, counters[outcome.ordinal()].sum());
			}
			snapshot.put(protocol, byOutcome);
		});
		return snapshot;
	}

	/**
	 * Creates one counter per {@link RequestOutcome}.
	 *
	 * @return The counters, indexed by {@link RequestOutcome#ordinal()}.
	 */
	private static LongAdder[] newCounters() {
		final LongAdder[] counters = new LongAdder[RequestOutcome.values().length];
		for (int i = 0; i < counters.length; i++) {
			counters[i] = new LongAdder();
		}
		return counters;
	}
}
