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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Test of {@link EmulationRoundRobinManager}
 */
class EmulationRoundRobinManagerTest {

	@Test
	void testNextIndexCyclesRoundRobin() {
		final EmulationRoundRobinManager manager = new EmulationRoundRobinManager();

		assertEquals(0, manager.nextIndex("/path/image.yaml", "key1", 3));
		assertEquals(1, manager.nextIndex("/path/image.yaml", "key1", 3));
		assertEquals(2, manager.nextIndex("/path/image.yaml", "key1", 3));
		// Wraps around
		assertEquals(0, manager.nextIndex("/path/image.yaml", "key1", 3));
		assertEquals(1, manager.nextIndex("/path/image.yaml", "key1", 3));
	}

	@Test
	void testNextIndexSingleMatch() {
		final EmulationRoundRobinManager manager = new EmulationRoundRobinManager();

		// With only one match, always returns 0
		assertEquals(0, manager.nextIndex("/path/image.yaml", "key1", 1));
		assertEquals(0, manager.nextIndex("/path/image.yaml", "key1", 1));
		assertEquals(0, manager.nextIndex("/path/image.yaml", "key1", 1));
	}

	@Test
	void testNextIndexDifferentKeysAreIndependent() {
		final EmulationRoundRobinManager manager = new EmulationRoundRobinManager();

		assertEquals(0, manager.nextIndex("/path/image.yaml", "keyA", 2));
		assertEquals(0, manager.nextIndex("/path/image.yaml", "keyB", 2));
		assertEquals(1, manager.nextIndex("/path/image.yaml", "keyA", 2));
		assertEquals(1, manager.nextIndex("/path/image.yaml", "keyB", 2));
	}

	@Test
	void testNextIndexDifferentImagePathsAreIndependent() {
		final EmulationRoundRobinManager manager = new EmulationRoundRobinManager();

		assertEquals(0, manager.nextIndex("/path1/image.yaml", "key1", 2));
		assertEquals(0, manager.nextIndex("/path2/image.yaml", "key1", 2));
		assertEquals(1, manager.nextIndex("/path1/image.yaml", "key1", 2));
		assertEquals(1, manager.nextIndex("/path2/image.yaml", "key1", 2));
	}

	@Test
	void testNextIndexMatchCountLessThanOneThrows() {
		final EmulationRoundRobinManager manager = new EmulationRoundRobinManager();

		assertThrows(IllegalArgumentException.class, () -> manager.nextIndex("/path/image.yaml", "key1", 0));
		assertThrows(IllegalArgumentException.class, () -> manager.nextIndex("/path/image.yaml", "key1", -1));
	}

	@Test
	void testNextIndexTwoMatches() {
		final EmulationRoundRobinManager manager = new EmulationRoundRobinManager();

		assertEquals(0, manager.nextIndex("/path/image.yaml", "key1", 2));
		assertEquals(1, manager.nextIndex("/path/image.yaml", "key1", 2));
		assertEquals(0, manager.nextIndex("/path/image.yaml", "key1", 2));
		assertEquals(1, manager.nextIndex("/path/image.yaml", "key1", 2));
	}
}
