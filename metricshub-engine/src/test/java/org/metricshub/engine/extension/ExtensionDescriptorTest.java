package org.metricshub.engine.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtensionDescriptorTest {

	@TempDir
	Path tempDir;

	/**
	 * Writes a jar with the given manifest main attributes.
	 */
	private File writeJar(final String name, final String... keyValues) throws IOException {
		final Manifest manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		for (int i = 0; i < keyValues.length; i += 2) {
			manifest.getMainAttributes().put(new Attributes.Name(keyValues[i]), keyValues[i + 1]);
		}
		final File jar = tempDir.resolve(name).toFile();
		try (var _ = new JarOutputStream(new java.io.FileOutputStream(jar), manifest)) {
			// empty jar, manifest only
		}
		return jar;
	}

	@Test
	void testDefaultIdFromFileNameWhenNoManifestAttribute() throws IOException {
		final File jar = writeJar("metricshub-foo-extension.jar");

		final ExtensionDescriptor descriptor = ExtensionDescriptor.from(jar);

		assertEquals("metricshub-foo-extension", descriptor.id(), "Id defaults to the jar name without .jar");
		assertTrue(descriptor.requires().isEmpty(), "No requires by default");
		assertTrue(descriptor.childFirstPackages().isEmpty(), "No child-first packages by default");
	}

	@Test
	void testReadsIdRequiresAndChildFirst() throws IOException {
		final File jar = writeJar(
			"anything.jar",
			ExtensionDescriptor.ATTR_ID,
			"metricshub-emulation-extension",
			ExtensionDescriptor.ATTR_REQUIRES,
			" metricshub-http-extension , metricshub-wmi-extension ,, ",
			ExtensionDescriptor.ATTR_CHILD_FIRST,
			"com.example.foo,com.example.bar"
		);

		final ExtensionDescriptor descriptor = ExtensionDescriptor.from(jar);

		assertEquals("metricshub-emulation-extension", descriptor.id());
		assertEquals(List.of("metricshub-http-extension", "metricshub-wmi-extension"), descriptor.requires());
		assertEquals(List.of("com.example.foo", "com.example.bar"), descriptor.childFirstPackages());
	}
}
