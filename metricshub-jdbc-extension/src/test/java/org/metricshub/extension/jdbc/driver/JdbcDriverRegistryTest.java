package org.metricshub.extension.jdbc.driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.sql.Driver;
import java.sql.DriverManager;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JdbcDriverRegistryTest {

	private static final JdbcDriverDescriptor H2_BUILTIN = new JdbcDriverDescriptor(
		"org.h2.Driver",
		"H2",
		List.of("jdbc:h2:"),
		9092,
		DriverOrigin.BUILT_IN,
		List.of("org.h2"),
		null
	);

	@Test
	void resolveBuiltInUsesParentLoaderAndCaches() {
		try (
			JdbcDriverRegistry registry = new JdbcDriverRegistry(
				List.of(H2_BUILTIN),
				JdbcDriverJarLocator.noOp(),
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
	void closeDeregistersShieldedDrivers() {
		final int before = Collections.list(DriverManager.getDrivers()).size();
		try (
			JdbcDriverRegistry registry = new JdbcDriverRegistry(
				List.of(H2_BUILTIN),
				JdbcDriverJarLocator.noOp(),
				JdbcDriverRegistryTest.class.getClassLoader()
			)
		) {
			registry.resolve("org.h2.Driver", (String) null);
			final int during = Collections.list(DriverManager.getDrivers()).size();
			assertEquals(before + 1, during, "ShieldedDriver must be registered with DriverManager");
		}
		final int after = Collections.list(DriverManager.getDrivers()).size();
		assertEquals(before, after, "close() must deregister every ShieldedDriver");
	}

	@Test
	void unknownDriverAndNoJarYieldsDiagnostic() {
		try (
			JdbcDriverRegistry registry = new JdbcDriverRegistry(
				List.of(),
				JdbcDriverJarLocator.noOp(),
				JdbcDriverRegistryTest.class.getClassLoader()
			)
		) {
			final DriverResolutionException ex = assertThrows(DriverResolutionException.class, () ->
				registry.resolve("com.acme.Nope", (String) null)
			);
			assertTrue(ex.getMessage().contains("com.acme.Nope"));
			assertTrue(ex.getMessage().contains("extensions/jdbc"), ex.getMessage());
		}
	}

	@Test
	void explicitJarPathMissingMessageNamesPath() {
		try (
			JdbcDriverRegistry registry = new JdbcDriverRegistry(
				List.of(H2_BUILTIN),
				JdbcDriverJarLocator.noOp(),
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
	void descriptorForUrlMatchesPrefix() {
		try (
			JdbcDriverRegistry registry = new JdbcDriverRegistry(
				List.of(H2_BUILTIN),
				JdbcDriverJarLocator.noOp(),
				JdbcDriverRegistryTest.class.getClassLoader()
			)
		) {
			assertSame(H2_BUILTIN, registry.descriptorForUrl("jdbc:h2:mem:test").orElse(null));
			assertSame(Optional.empty(), registry.descriptorForUrl("jdbc:mysql://x"));
			assertSame(Optional.empty(), registry.descriptorForUrl(null));
		}
	}

	@Test
	void duplicateDescriptorsAreIgnored() {
		final JdbcDriverDescriptor first = new JdbcDriverDescriptor(
			"org.h2.Driver",
			"H2-a",
			List.of("jdbc:h2:"),
			9092,
			DriverOrigin.BUILT_IN,
			List.of("org.h2"),
			null
		);
		final JdbcDriverDescriptor second = new JdbcDriverDescriptor(
			"org.h2.Driver",
			"H2-b",
			List.of("jdbc:h2:"),
			9092,
			DriverOrigin.USER_DEFAULT,
			List.of("org.h2"),
			null
		);
		try (
			JdbcDriverRegistry registry = new JdbcDriverRegistry(
				List.of(first, second),
				JdbcDriverJarLocator.noOp(),
				JdbcDriverRegistryTest.class.getClassLoader()
			)
		) {
			assertEquals(1, registry.descriptors().size());
			assertEquals("H2-a", registry.descriptors().iterator().next().displayName());
		}
	}

	@Test
	void distinctExplicitPathsLoadIntoDistinctClassLoaders() {
		// Build a synthetic locator that always points at the H2 JAR (which is on the test classpath),
		// regardless of the explicit path. Verifies that two distinct paths on the same driverClass
		// produce two distinct ClassLoaders and two distinct Class<Driver> identities.
		final URL h2JarUrl = org.h2.Driver.class.getProtectionDomain().getCodeSource().getLocation();
		assertNotNull(h2JarUrl, "test setup: h2 jar must be locatable");

		final JdbcDriverJarLocator locator = (_, _) ->
			Optional.of(new JdbcDriverJarLocator.LocatedDriverJars(new URL[] { h2JarUrl }, DriverOrigin.USER_EXPLICIT));

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
