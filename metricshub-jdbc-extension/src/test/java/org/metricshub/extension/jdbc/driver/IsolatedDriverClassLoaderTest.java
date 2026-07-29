package org.metricshub.extension.jdbc.driver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.util.List;
import org.junit.jupiter.api.Test;

class IsolatedDriverClassLoaderTest {

	@Test
	void isChildFirstMatchesPrefix() {
		try (
			IsolatedDriverClassLoader loader = new IsolatedDriverClassLoader(
				"t",
				new URL[0],
				IsolatedDriverClassLoaderTest.class.getClassLoader(),
				List.of("com.example.driver", "org.foo")
			)
		) {
			assertTrue(loader.isChildFirst("com.example.driver.Driver"));
			assertTrue(loader.isChildFirst("org.foo.Bar"));
			assertFalse(loader.isChildFirst("org.metricshub.engine.Foo"));
			assertFalse(loader.isChildFirst("java.sql.Driver"));
			assertFalse(loader.isChildFirst("javax.sql.DataSource"));
		} catch (java.io.IOException e) {
			throw new AssertionError(e);
		}
	}

	@Test
	void childFirstListNullSafeAndImmutable() {
		try (
			IsolatedDriverClassLoader loader = new IsolatedDriverClassLoader(
				"t",
				new URL[0],
				IsolatedDriverClassLoaderTest.class.getClassLoader(),
				null
			)
		) {
			assertFalse(loader.isChildFirst("anything"));
		} catch (java.io.IOException e) {
			throw new AssertionError(e);
		}
	}

	@Test
	void parentFirstReturnsParentClassForJavaSqlDriver() throws Exception {
		final ClassLoader parent = IsolatedDriverClassLoaderTest.class.getClassLoader();
		try (
			IsolatedDriverClassLoader loader = new IsolatedDriverClassLoader("t", new URL[0], parent, List.of("com.example"))
		) {
			final Class<?> driverClass = loader.loadClass("java.sql.Driver");
			assertNotNull(driverClass);
			// The Class identity must match what the parent loader sees.
			assertSame(parent.loadClass("java.sql.Driver"), driverClass);
		}
	}

	@Test
	void loadClassNullName() {
		try (
			IsolatedDriverClassLoader loader = new IsolatedDriverClassLoader(
				"t",
				new URL[0],
				IsolatedDriverClassLoaderTest.class.getClassLoader(),
				List.of()
			)
		) {
			assertThrows(NullPointerException.class, () -> loader.loadClass(null));
		} catch (java.io.IOException e) {
			throw new AssertionError(e);
		}
	}

	@Test
	void deregistrarRemovesDriversRegisteredFromThisLoader() throws Exception {
		// Locate the H2 jar from the test classpath and load a child-first copy of org.h2.Driver:
		// its static initialization self-registers that copy in the JVM-global DriverManager.
		final URL h2Jar = org.h2.Driver.class.getProtectionDomain().getCodeSource().getLocation();
		try (
			IsolatedDriverClassLoader loader = new IsolatedDriverClassLoader(
				"h2-isolated",
				new URL[] { h2Jar },
				IsolatedDriverClassLoaderTest.class.getClassLoader(),
				List.of("org.h2")
			)
		) {
			final Class<?> childDriver = Class.forName("org.h2.Driver", true, loader);
			assertSame(loader, childDriver.getClassLoader(), "The driver copy must be defined by the isolated loader");

			// The deregistrar copy defined INSIDE the loader passes DriverManager's caller-visibility
			// check for that loader's registrations; code loaded by the parent cannot see them.
			final Class<?> deregistrar = loader.defineDeregistrar();
			assertSame(loader, deregistrar.getClassLoader(), "The deregistrar must be defined by the isolated loader");

			final int first = (int) deregistrar.getMethod("deregisterOwnDrivers").invoke(null);
			assertTrue(first >= 1, "The isolated driver's self-registration must be deregistered");

			final int second = (int) deregistrar.getMethod("deregisterOwnDrivers").invoke(null);
			assertTrue(second == 0, "Nothing must remain registered from the isolated loader");
		}
	}
}
