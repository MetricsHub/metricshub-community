package org.metricshub.extension.jdbc.driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilesystemDriverScannerTest {

	@Test
	void rejectsNullDefaultDir() {
		assertThrows(NullPointerException.class, () -> new FilesystemDriverScanner(null));
	}

	@Test
	void missingDefaultDirectoryReturnsEmpty(@TempDir Path tmp) {
		final FilesystemDriverScanner scanner = new FilesystemDriverScanner(tmp.resolve("nope"));
		assertSame(Optional.empty(), scanner.locate("com.acme.Driver", null));
	}

	@Test
	void emptyDefaultDirectoryReturnsEmpty(@TempDir Path tmp) {
		final FilesystemDriverScanner scanner = new FilesystemDriverScanner(tmp);
		assertSame(Optional.empty(), scanner.locate("com.acme.Driver", null));
	}

	@Test
	void defaultDirReturnsRootJarsAndIgnoresSubdirs(@TempDir Path tmp) throws IOException {
		Files.createFile(tmp.resolve("driver-a.jar"));
		Files.createFile(tmp.resolve("driver-b.jar"));
		Files.createFile(tmp.resolve("not-a-jar.txt"));
		final Path subdir = Files.createDirectory(tmp.resolve("v1"));
		Files.createFile(subdir.resolve("driver-c.jar"));

		final FilesystemDriverScanner scanner = new FilesystemDriverScanner(tmp);
		final Optional<JdbcDriverJarLocator.LocatedDriverJars> located = scanner.locate("com.acme.Driver", null);
		assertTrue(located.isPresent());
		assertEquals(2, located.get().urls().length);
		assertSame(DriverOrigin.USER_DEFAULT, located.get().origin());
	}

	@Test
	void explicitJarPathReturnsSingleUrlWithUserExplicitOrigin(@TempDir Path tmp) throws IOException {
		final Path jar = tmp.resolve("acme-1.2.3.jar");
		Files.createFile(jar);

		final FilesystemDriverScanner scanner = new FilesystemDriverScanner(tmp.resolve("default-ignored"));
		final Optional<JdbcDriverJarLocator.LocatedDriverJars> located = scanner.locate("com.acme.Driver", jar.toString());
		assertTrue(located.isPresent());
		assertEquals(1, located.get().urls().length);
		assertSame(DriverOrigin.USER_EXPLICIT, located.get().origin());
		assertTrue(located.get().urls()[0].toString().endsWith("acme-1.2.3.jar"));
	}

	@Test
	void explicitJarPathMissingReturnsEmpty(@TempDir Path tmp) {
		final FilesystemDriverScanner scanner = new FilesystemDriverScanner(tmp);
		assertSame(Optional.empty(), scanner.locate("com.acme.Driver", tmp.resolve("missing.jar").toString()));
	}

	@Test
	void globMatchSingleFile(@TempDir Path tmp) throws IOException {
		Files.createFile(tmp.resolve("ojdbc11-23.5.0.24.07.jar"));
		Files.createFile(tmp.resolve("other.jar"));

		final FilesystemDriverScanner scanner = new FilesystemDriverScanner(tmp);
		final Optional<JdbcDriverJarLocator.LocatedDriverJars> located = scanner.locate(
			"oracle.jdbc.OracleDriver",
			tmp.toString() + java.io.File.separator + "ojdbc11-*.jar"
		);
		assertTrue(located.isPresent());
		assertEquals(1, located.get().urls().length);
		assertSame(DriverOrigin.USER_EXPLICIT, located.get().origin());
	}

	@Test
	void globMatchingMultipleFilesFails(@TempDir Path tmp) throws IOException {
		Files.createFile(tmp.resolve("ojdbc11-23.5.0.jar"));
		Files.createFile(tmp.resolve("ojdbc11-23.6.0.jar"));

		final FilesystemDriverScanner scanner = new FilesystemDriverScanner(tmp);
		final DriverResolutionException ex = assertThrows(DriverResolutionException.class, () ->
			scanner.locate("oracle.jdbc.OracleDriver", tmp.toString() + java.io.File.separator + "ojdbc11-*.jar")
		);
		assertTrue(ex.getMessage().contains("matched 2"), ex.getMessage());
	}

	@Test
	void globMatchingNoFileReturnsEmpty(@TempDir Path tmp) {
		final FilesystemDriverScanner scanner = new FilesystemDriverScanner(tmp);
		assertSame(
			Optional.empty(),
			scanner.locate("oracle.jdbc.OracleDriver", tmp.toString() + java.io.File.separator + "nope-*.jar")
		);
	}
}
