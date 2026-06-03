package org.metricshub.extension.jdbc.driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class JdbcPathExpressionTest {

	private static final Path INSTALL = Paths.get("target/test-install").toAbsolutePath().normalize();
	private static final Path HOME = Paths.get("target/test-home").toAbsolutePath().normalize();
	private static final Path CWD = Paths.get("target/test-cwd").toAbsolutePath().normalize();

	private static JdbcPathExpression resolver() {
		return new JdbcPathExpression(() -> INSTALL, () -> HOME, () -> CWD);
	}

	@Test
	void resolvesInstallDir() {
		final String expected = INSTALL.toString() + "/lib/extensions/jdbc/jt400.jar";
		assertEquals(expected, resolver().resolve("$INSTALL_DIR/lib/extensions/jdbc/jt400.jar"));
	}

	@Test
	void resolvesUserHome() {
		final String expected = HOME.toString() + "/.metricshub/drivers/acme.jar";
		assertEquals(expected, resolver().resolve("$USER_HOME/.metricshub/drivers/acme.jar"));
	}

	@Test
	void resolvesWorkingDir() {
		final String expected = CWD.toString() + "/drivers/test.jar";
		assertEquals(expected, resolver().resolve("$WORKING_DIR/drivers/test.jar"));
	}

	@Test
	void absolutePathPassesThrough() {
		assertEquals("/opt/oracle/ojdbc11.jar", resolver().resolve("/opt/oracle/ojdbc11.jar"));
	}

	@Test
	void unknownPlaceholderRejected() {
		final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
			resolver().resolve("$INSTALL_DIR/$NOPE/foo.jar")
		);
		assertTrue(ex.getMessage().contains("$NOPE"), ex.getMessage());
	}

	@Test
	void traversalRejected() {
		assertThrows(IllegalArgumentException.class, () -> resolver().resolve("$INSTALL_DIR/../etc/shadow"));
		assertThrows(IllegalArgumentException.class, () -> resolver().resolve("/opt/../etc/shadow"));
	}

	@Test
	void blankExpressionRejected() {
		assertThrows(IllegalArgumentException.class, () -> resolver().resolve(null));
		assertThrows(IllegalArgumentException.class, () -> resolver().resolve("   "));
	}

	@Test
	void relativePathPassesThrough() {
		assertEquals("drivers/x.jar", resolver().resolve("drivers/x.jar"));
	}

	@Test
	void globPatternsArePreservedAsStrings() {
		// Globs are not evaluated by the resolver; they pass through after token expansion.
		final String expected = INSTALL.toString() + "/lib/extensions/jdbc/ojdbc*.jar";
		assertEquals(expected, resolver().resolve("$INSTALL_DIR/lib/extensions/jdbc/ojdbc*.jar"));
	}
}
