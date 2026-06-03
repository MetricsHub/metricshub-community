package org.metricshub.extension.jdbc.driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.identity.DriverInfo;

class JdbcDriverRegistryHolderTest {

	@BeforeEach
	@AfterEach
	void reset() {
		System.clearProperty(JdbcDriverRegistryHolder.DRIVERS_DIR_PROPERTY);
		JdbcDriverRegistryHolder.resetForTests();
	}

	@Test
	void getReturnsSingleton() {
		final JdbcDriverRegistry first = JdbcDriverRegistryHolder.get();
		final JdbcDriverRegistry second = JdbcDriverRegistryHolder.get();
		assertNotNull(first);
		assertSame(first, second);
	}

	@Test
	void resetForTestsBuildsFreshInstance() {
		final JdbcDriverRegistry first = JdbcDriverRegistryHolder.get();
		JdbcDriverRegistryHolder.resetForTests();
		final JdbcDriverRegistry second = JdbcDriverRegistryHolder.get();
		assertNotNull(second);
		assertTrue(first != second, "resetForTests must drop the cached instance");
	}

	@Test
	void systemPropertyOverridesDirectoryResolution() {
		final Path custom = Paths.get("target", "test-jdbc-drivers-dir");
		System.setProperty(JdbcDriverRegistryHolder.DRIVERS_DIR_PROPERTY, custom.toString());
		assertEquals(custom, JdbcDriverRegistryHolder.resolveDriversDir());
	}

	@Test
	void resolveSelectionIsNullSafe() {
		assertNull(JdbcDriverRegistryHolder.resolveSelection(null));
		// JdbcInfo with blank className cannot be built via factory; build via Lombok to exercise the guard.
		final DriverInfo emptyClass = DriverInfo.builder().className("").build();
		assertNull(JdbcDriverRegistryHolder.resolveSelection(emptyClass));
	}

	@Test
	void resolveSelectionSwallowsResolutionFailures() {
		// Unknown driver class => DriverResolutionException raised by the registry => returns null.
		final DriverInfo info = DriverInfo.builder()
			.className("com.example.NoSuchDriver")
			.jarPath("$USER_HOME/no-such.jar")
			.build();
		assertNull(JdbcDriverRegistryHolder.resolveSelection(info));
	}

	@Test
	void resolveSelectionRejectsInvalidPath() {
		// Path with traversal must be rejected and yield null.
		final DriverInfo info = DriverInfo.builder()
			.className("com.example.NoSuchDriver")
			.jarPath("/etc/../shadow")
			.build();
		assertNull(JdbcDriverRegistryHolder.resolveSelection(info));
	}
}
