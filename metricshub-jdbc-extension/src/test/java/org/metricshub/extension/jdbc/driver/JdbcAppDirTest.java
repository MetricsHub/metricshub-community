package org.metricshub.extension.jdbc.driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class JdbcAppDirTest {

	@Test
	void computeSubPathProductionLinuxLayout() {
		final Path codeSource = Paths.get("/opt/metricshub/lib/app/metricshub-agent.jar");
		final Path resolved = JdbcAppDir.computeSubPath(codeSource, "extensions/jdbc");
		assertEquals(Paths.get("/opt/metricshub/lib/extensions/jdbc").toAbsolutePath(), resolved);
	}

	@Test
	void computeSubPathProductionWindowsLayout() {
		// Use a forward-slash representation; Paths normalises separators per the running OS.
		final Path codeSource = Paths.get("/Program Files/MetricsHub/app/metricshub-agent.jar");
		final Path resolved = JdbcAppDir.computeSubPath(codeSource, "extensions/jdbc");
		assertEquals(Paths.get("/Program Files/MetricsHub/extensions/jdbc").toAbsolutePath(), resolved);
	}

	@Test
	void computeSubPathDevelopmentClassesLayout() {
		// codeSource is the target/classes directory (Maven test JVM); its parent is target/,
		// and resolving "../extensions/jdbc" lands at <project>/extensions/jdbc — same math as
		// ConfigHelper.getSubPath in the agent.
		final Path codeSource = Paths.get("/tmp/project/target/classes");
		final Path resolved = JdbcAppDir.computeSubPath(codeSource, "extensions/jdbc");
		assertEquals(Paths.get("/tmp/project/extensions/jdbc").toAbsolutePath(), resolved);
	}

	@Test
	void resolveSubPathRejectsBlank() {
		assertThrows(IllegalArgumentException.class, () -> JdbcAppDir.resolveSubPath(null));
		assertThrows(IllegalArgumentException.class, () -> JdbcAppDir.resolveSubPath(""));
		assertThrows(IllegalArgumentException.class, () -> JdbcAppDir.resolveSubPath("   "));
	}

	@Test
	void resolveSubPathReturnsAbsolutePath() {
		// Live call: must not throw, must produce an absolute normalized path ending with the requested fragment.
		final Path resolved = JdbcAppDir.resolveSubPath("extensions/jdbc");
		assertNotNull(resolved);
		assertTrue(resolved.isAbsolute(), "resolved path must be absolute, got: " + resolved);
		assertTrue(
			resolved.endsWith(Paths.get("extensions", "jdbc")),
			"resolved path must end with extensions/jdbc, got: " + resolved
		);
	}
}
