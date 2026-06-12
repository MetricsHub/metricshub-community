package org.metricshub.extension.jdbc.driver;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub JDBC Extension
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

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class JdbcDriverDescriptorTest {

	@Test
	void rejectsNullDriverClass() {
		assertThrows(NullPointerException.class, () ->
			new JdbcDriverDescriptor(null, "x", DriverOrigin.BUILT_IN, List.of())
		);
	}

	@Test
	void rejectsBlankDriverClass() {
		assertThrows(IllegalArgumentException.class, () ->
			new JdbcDriverDescriptor("   ", "x", DriverOrigin.BUILT_IN, List.of())
		);
	}

	@Test
	void rejectsNullOrigin() {
		assertThrows(NullPointerException.class, () -> new JdbcDriverDescriptor("a.B", "x", null, List.of()));
	}

	@Test
	void normalizesNullPackages() {
		final JdbcDriverDescriptor d = new JdbcDriverDescriptor("a.B", "x", DriverOrigin.BUILT_IN, null);
		assertEquals(List.of(), d.driverPackages());
	}

	@Test
	void defensivelyCopiesPackages() {
		final List<String> packages = new ArrayList<>();
		packages.add("a");
		final JdbcDriverDescriptor d = new JdbcDriverDescriptor("a.B", "x", DriverOrigin.BUILT_IN, packages);
		packages.add("b");
		assertEquals(1, d.driverPackages().size(), "descriptor must not reflect post-construction mutation");
		assertThrows(UnsupportedOperationException.class, () -> d.driverPackages().add("c"));
	}

	@Test
	void recordEqualityAndHashing() {
		final JdbcDriverDescriptor a = new JdbcDriverDescriptor("a.B", "X", DriverOrigin.BUILT_IN, List.of("a"));
		final JdbcDriverDescriptor b = new JdbcDriverDescriptor("a.B", "X", DriverOrigin.BUILT_IN, List.of("a"));
		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
		assertSame(DriverOrigin.BUILT_IN, a.origin());
	}
}
