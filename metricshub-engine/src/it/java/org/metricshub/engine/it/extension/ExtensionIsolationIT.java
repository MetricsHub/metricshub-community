package org.metricshub.engine.it.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.engine.extension.ExtensionLoader;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.extension.IProtocolExtension;

/**
 * Proves that a {@code META-INF/services} registration bundled by one extension does not leak into
 * another once each extension is loaded in its own class loader. This is the regression test for the
 * Oracle {@code xmlparserv2} class of bug: {@code it-extension-a} ships a fake JAXP
 * {@link DocumentBuilderFactory} service file, {@code it-extension-b} ships none.
 *
 * <p>Both fixture jars are built by the maven-invoker-plugin into
 * {@code target/it/it-extension-{a,b}/target} before this IT runs.
 */
class ExtensionIsolationIT {

	private static final String FAKE_FACTORY = "org.metricshub.it.a.FakeDocumentBuilderFactory";

	@TempDir
	Path extensionsDir;

	@Test
	void testServiceFileIsolationBetweenExtensions() throws Exception {
		// Place both conflicting extension jars in a single extensions directory.
		copyFixture("it-extension-a", "it-extension-a.jar");
		copyFixture("it-extension-b", "it-extension-b.jar");

		final ExtensionManager extensionManager = new ExtensionLoader(extensionsDir.toFile()).load();
		try {
			final List<IProtocolExtension> protocolExtensions = extensionManager.getProtocolExtensions();
			assertEquals(2, protocolExtensions.size(), "Both isolated extensions must load");

			final IProtocolExtension extensionA = findByIdentifier(protocolExtensions, "it-a");
			final IProtocolExtension extensionB = findByIdentifier(protocolExtensions, "it-b");

			// Control: the host thread resolves the JDK default factory (it cannot see A's fake).
			final String hostDefault = DocumentBuilderFactory.newInstance().getClass().getName();

			// Extension A, under its own class loader, resolves the fake factory it bundles.
			assertEquals(FAKE_FACTORY, extensionA.executeQuery(null, null), "Extension A must resolve its own JAXP factory");

			// Extension B must NOT see A's service file: it resolves the same default as the host.
			final String resolvedByB = extensionB.executeQuery(null, null);
			assertNotEquals(FAKE_FACTORY, resolvedByB, "Extension A's JAXP factory must not leak into extension B");
			assertEquals(hostDefault, resolvedByB, "Extension B must resolve the JDK default JAXP factory");
		} finally {
			// Close the isolated loaders so the temp jars are released (and exercise close()).
			extensionManager.close();
		}
	}

	@Test
	void testCrossExtensionClassDelegation() throws Exception {
		// A provides classes; C declares "Requires: it-extension-a"; D declares nothing.
		copyFixture("it-extension-a", "it-extension-a.jar");
		copyFixture("it-extension-c", "it-extension-c.jar");
		copyFixture("it-extension-d", "it-extension-d.jar");

		final ExtensionManager extensionManager = new ExtensionLoader(extensionsDir.toFile()).load();
		try {
			final List<IProtocolExtension> protocolExtensions = extensionManager.getProtocolExtensions();
			assertEquals(3, protocolExtensions.size(), "A, C and D must load");

			final IProtocolExtension extensionC = findByIdentifier(protocolExtensions, "it-c");
			final IProtocolExtension extensionD = findByIdentifier(protocolExtensions, "it-d");

			// C requires A, so it resolves A's class through delegation.
			assertEquals("found", extensionC.executeQuery(null, null), "C (requires A) must load A's class");

			// D declares no dependency, so it cannot reach A's class.
			assertEquals("not-found", extensionD.executeQuery(null, null), "D (no requires) must not load A's class");
		} finally {
			extensionManager.close();
		}
	}

	/**
	 * Copies a fixture jar built by the invoker plugin into the shared extensions directory.
	 */
	private void copyFixture(final String fixture, final String targetName) throws IOException {
		final Path source = Path.of("target", "it", fixture, "target", fixture + "-1-SNAPSHOT.jar");
		Files.copy(source, extensionsDir.resolve(targetName), StandardCopyOption.REPLACE_EXISTING);
	}

	/**
	 * Finds a protocol extension by its identifier.
	 */
	private static IProtocolExtension findByIdentifier(final List<IProtocolExtension> extensions, final String id) {
		return extensions
			.stream()
			.filter(extension -> id.equals(extension.getIdentifier()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("Extension '" + id + "' was not loaded"));
	}
}
