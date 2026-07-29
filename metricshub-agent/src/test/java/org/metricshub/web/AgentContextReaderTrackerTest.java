package org.metricshub.web;

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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.context.AgentContext;

class AgentContextReaderTrackerTest {

	@Test
	void testLeasesAreTrackedPerGeneration() {
		final AgentContextHolder holder = new AgentContextHolder(mock(AgentContext.class));
		final AgentContextReaderTracker tracker = new AgentContextReaderTracker(holder);
		final long generation = holder.getGeneration();

		assertFalse(tracker.hasReadersAtOrBefore(generation), "No reader initially");

		tracker.acquire(generation);
		assertTrue(tracker.hasReadersAtOrBefore(generation), "Active lease at the generation");
		assertTrue(tracker.hasReadersAtOrBefore(generation + 1), "An older lease blocks newer generations");
		assertFalse(tracker.hasReadersAtOrBefore(generation - 1), "A newer lease does not block older generations");

		tracker.release(generation);
		assertFalse(tracker.hasReadersAtOrBefore(generation), "Released lease no longer counts");
	}

	@Test
	void testDoFilterHoldsLeaseForTheRequestDuration() throws Exception {
		final AgentContextHolder holder = new AgentContextHolder(mock(AgentContext.class));
		final AgentContextReaderTracker tracker = new AgentContextReaderTracker(holder);
		final long generation = holder.getGeneration();
		final AtomicBoolean leaseHeldDuringChain = new AtomicBoolean();

		tracker.doFilter(null, null, (request, response) ->
			leaseHeldDuringChain.set(tracker.hasReadersAtOrBefore(generation))
		);

		assertTrue(leaseHeldDuringChain.get(), "The lease must be held while the chain executes");
		assertFalse(tracker.hasReadersAtOrBefore(generation), "The lease must be released after the request completes");
	}
}
