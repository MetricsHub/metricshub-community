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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.sql.Driver;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JdbcDriverRegistryTest {

	private static final IJdbcDriverJarLocator NO_OP = (_, _) -> Optional.empty();

	private static final JdbcDriverDescriptor H2_BUILTIN = new JdbcDriverDescriptor(
		"org.h2.Driver",
		"H2",
		DriverOrigin.BUILT_IN,
		List.of("org.h2")
	);

	@Test
	void resolveBuiltInUsesParentLoaderAndCaches() {
		try (
			JdbcDriverRegistry registry = new JdbcDriverRegistry(
				List.of(H2_BUILTIN),
				NO_OP,
				JdbcDriverRegistryTest.class.getClassLoader()
			)
		) {
			final LoadedDriver first = registry.resolve("org.h2.Driver", (String) null);
			assertNotNull(first);
			assertSame(JdbcDriverRegistryTest.class.getClassLoader(), first.classLoader());
			assertSame(DriverOrigin.BUILT_IN, first.descriptor().origin());

			final LoadedDriver second = registry.resolve("org.h2.Driver", (String) null);
			assertSame(first, second, "registry must cache by (driverClass, explicitJarPath)");
		}
	}

	@Test
	void unknownDriverThrows() {
		try (
			JdbcDriverRegistry registry = new JdbcDriverRegistry(
				List.of(),
				NO_OP,
				JdbcDriverRegistryTest.class.getClassLoader()
			)
		) {
			final DriverResolutionException ex = assertThrows(DriverResolutionException.class, () ->
				registry.resolve("com.acme.Nope", (String) null)
			);
			assertTrue(ex.getMessage().contains("com.acme.Nope"));
			assertTrue(ex.getMessage().contains("IJdbcDriverProvider"), ex.getMessage());
		}
	}

	@Test
	void explicitJarPathMissingMessageNamesPath() {
		try (
			JdbcDriverRegistry registry = new JdbcDriverRegistry(
				List.of(H2_BUILTIN),
				NO_OP,
				JdbcDriverRegistryTest.class.getClassLoader()
			)
		) {
			// Explicit path is set so the BUILT_IN shortcut is skipped; locator returns empty.
			final String explicit = "/does/not/exist/h2-test.jar";
			final DriverResolutionException ex = assertThrows(DriverResolutionException.class, () ->
				registry.resolve("org.h2.Driver", explicit)
			);
			assertTrue(ex.getMessage().contains("h2-test.jar"), ex.getMessage());
		}
	}

	@Test
	void duplicateDescriptorsAreIgnored() {
		final JdbcDriverDescriptor first = new JdbcDriverDescriptor(
			"org.h2.Driver",
			"H2-a",
			DriverOrigin.BUILT_IN,
			List.of("org.h2")
		);
		final JdbcDriverDescriptor second = new JdbcDriverDescriptor(
			"org.h2.Driver",
			"H2-b",
			DriverOrigin.USER_DEFAULT,
			List.of("org.h2")
		);
		try (
			JdbcDriverRegistry registry = new JdbcDriverRegistry(
				List.of(first, second),
				NO_OP,
				JdbcDriverRegistryTest.class.getClassLoader()
			)
		) {
			assertEquals(1, registry.descriptors().size());
			assertSame(DriverOrigin.BUILT_IN, registry.descriptors().iterator().next().origin());
		}
	}

	@Test
	void userDefaultFallsBackToParentLoaderWhenJarMissingButClassOnClasspath() {
		// USER_DEFAULT descriptor for a class that *is* on the test classpath (H2). With a no-op
		// locator and no explicit jarPath, the registry must fall back to the parent classloader
		// rather than throwing — this models the enterprise distribution where driver JARs are
		// shipped on the agent classpath.
		final JdbcDriverDescriptor h2UserDefault = new JdbcDriverDescriptor(
			"org.h2.Driver",
			"H2 (user-default)",
			DriverOrigin.USER_DEFAULT,
			List.of("org.h2")
		);
		try (
			JdbcDriverRegistry registry = new JdbcDriverRegistry(
				List.of(h2UserDefault),
				NO_OP,
				JdbcDriverRegistryTest.class.getClassLoader()
			)
		) {
			final LoadedDriver loaded = registry.resolve("org.h2.Driver", (String) null);
			assertNotNull(loaded);
			assertSame(JdbcDriverRegistryTest.class.getClassLoader(), loaded.classLoader());
		}
	}

	@Test
	void userDefaultThrowsWhenJarMissingAndClassNotOnClasspath() {
		// USER_DEFAULT descriptor for a class absent from both isolated jars and the parent loader
		// must still throw — Fix A only opens the parent fallback, it does not silently succeed
		// when the class is genuinely missing.
		final JdbcDriverDescriptor missing = new JdbcDriverDescriptor(
			"com.example.absent.Driver",
			"Absent",
			DriverOrigin.USER_DEFAULT,
			List.of("com.example.absent")
		);
		try (
			JdbcDriverRegistry registry = new JdbcDriverRegistry(
				List.of(missing),
				NO_OP,
				JdbcDriverRegistryTest.class.getClassLoader()
			)
		) {
			final DriverResolutionException ex = assertThrows(DriverResolutionException.class, () ->
				registry.resolve("com.example.absent.Driver", (String) null)
			);
			assertTrue(ex.getMessage().contains("com.example.absent.Driver"), ex.getMessage());
		}
	}

	@Test
	void distinctExplicitPathsLoadIntoDistinctClassLoaders() {
		// regardless of the explicit path. Verifies that two distinct paths on the same driverClass
		// produce two distinct ClassLoaders and two distinct Class<Driver> identities.
		final URL h2JarUrl = org.h2.Driver.class.getProtectionDomain().getCodeSource().getLocation();
		assertNotNull(h2JarUrl, "test setup: h2 jar must be locatable");

		final IJdbcDriverJarLocator locator = (_, _) ->
			Optional.of(new IJdbcDriverJarLocator.LocatedDriverJars(new URL[] { h2JarUrl }, DriverOrigin.USER_EXPLICIT));

		try (
			JdbcDriverRegistry registry = new JdbcDriverRegistry(
				List.of(H2_BUILTIN),
				locator,
				JdbcDriverRegistryTest.class.getClassLoader()
			)
		) {
			final LoadedDriver a = registry.resolve("org.h2.Driver", "/fake/h2-a.jar");
			final LoadedDriver b = registry.resolve("org.h2.Driver", "/fake/h2-b.jar");

			assertNotSame(a, b);
			assertNotSame(a.classLoader(), b.classLoader());
			final Class<? extends Driver> classA = a.driver().getClass();
			final Class<? extends Driver> classB = b.driver().getClass();
			assertNotSame(classA, classB, "child-first loading must define a distinct Class per loader");
			assertEquals(classA.getName(), classB.getName());
			assertSame(DriverOrigin.USER_EXPLICIT, a.descriptor().origin());
		}
	}
}
