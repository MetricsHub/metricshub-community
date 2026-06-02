package org.metricshub.extension.jdbc.driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class JdbcDriverDescriptorTest {

	@Test
	void rejectsNullDriverClass() {
		assertThrows(NullPointerException.class, () ->
			new JdbcDriverDescriptor(null, "x", List.of(), -1, DriverOrigin.BUILT_IN, List.of(), null)
		);
	}

	@Test
	void rejectsBlankDriverClass() {
		assertThrows(IllegalArgumentException.class, () ->
			new JdbcDriverDescriptor("   ", "x", List.of(), -1, DriverOrigin.BUILT_IN, List.of(), null)
		);
	}

	@Test
	void rejectsNullOrigin() {
		assertThrows(NullPointerException.class, () ->
			new JdbcDriverDescriptor("a.B", "x", List.of(), -1, null, List.of(), null)
		);
	}

	@Test
	void normalisesNullLists() {
		final JdbcDriverDescriptor d = new JdbcDriverDescriptor("a.B", "x", null, -1, DriverOrigin.BUILT_IN, null, null);
		assertEquals(List.of(), d.urlPrefixes());
		assertEquals(List.of(), d.driverPackages());
	}

	@Test
	void defensivelyCopiesLists() {
		final List<String> prefixes = new ArrayList<>();
		prefixes.add("jdbc:x:");
		final JdbcDriverDescriptor d = new JdbcDriverDescriptor(
			"a.B",
			"x",
			prefixes,
			-1,
			DriverOrigin.BUILT_IN,
			List.of(),
			null
		);
		prefixes.add("jdbc:y:");
		assertEquals(1, d.urlPrefixes().size(), "descriptor must not reflect post-construction mutation");
		assertThrows(UnsupportedOperationException.class, () -> d.urlPrefixes().add("jdbc:z:"));
	}

	@Test
	void recordEqualityAndHashing() {
		final JdbcDriverDescriptor a = new JdbcDriverDescriptor(
			"a.B",
			"X",
			List.of("jdbc:x:"),
			1,
			DriverOrigin.BUILT_IN,
			List.of("a"),
			null
		);
		final JdbcDriverDescriptor b = new JdbcDriverDescriptor(
			"a.B",
			"X",
			List.of("jdbc:x:"),
			1,
			DriverOrigin.BUILT_IN,
			List.of("a"),
			null
		);
		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
		assertSame(DriverOrigin.BUILT_IN, a.origin());
		assertTrue(a.urlPrefixes().contains("jdbc:x:"));
		assertFalse(a.urlPrefixes().contains("jdbc:y:"));
	}
}
