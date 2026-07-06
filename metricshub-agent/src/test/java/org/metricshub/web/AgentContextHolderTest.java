package org.metricshub.web;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.metricshub.agent.context.AgentContext;

class AgentContextHolderTest {

	@Test
	void constructorRejectsNull() {
		assertThrows(NullPointerException.class, () -> new AgentContextHolder(null));
	}

	@Test
	void bootstrapGenerationIsOne() {
		final AgentContext boot = mock(AgentContext.class);
		final AgentContextHolder holder = new AgentContextHolder(boot);

		assertSame(boot, holder.getAgentContext());
		assertEquals(1L, holder.getGeneration());
	}

	@Test
	void setAgentContextRejectsNull() {
		final AgentContextHolder holder = new AgentContextHolder(mock(AgentContext.class));
		assertThrows(NullPointerException.class, () -> holder.setAgentContext(null));
	}

	@Test
	void setAgentContextIncrementsGeneration() {
		final AgentContext boot = mock(AgentContext.class);
		final AgentContext next = mock(AgentContext.class);
		final AgentContext third = mock(AgentContext.class);
		final AgentContextHolder holder = new AgentContextHolder(boot);

		holder.setAgentContext(next);
		assertSame(next, holder.getAgentContext());
		assertEquals(2L, holder.getGeneration());

		holder.setAgentContext(third);
		assertSame(third, holder.getAgentContext());
		assertEquals(3L, holder.getGeneration());
	}
}
