package org.metricshub.engine.extension;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2026 MetricsHub
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
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtensionRuntimeTest {

	@TempDir
	Path tempDir;

	/**
	 * Builds a descriptor carrying the given child-first prefixes (id/jar are irrelevant here).
	 */
	private static ExtensionDescriptor descriptorWithChildFirst(final List<String> childFirst) {
		return new ExtensionDescriptor("test-extension", null, List.of(), childFirst);
	}

	@Test
	void testKeepsUnrelatedChildFirstPrefixes() {
		final ExtensionDescriptor descriptor = descriptorWithChildFirst(List.of("com.acme.driver.", "org.example.lib."));
		assertEquals(
			List.of("com.acme.driver.", "org.example.lib."),
			ExtensionRuntime.sanitizeChildFirst(descriptor),
			"Prefixes that do not overlap a forced-parent namespace must be kept"
		);
	}

	@Test
	void testRejectsPrefixNestedUnderForcedParent() {
		// A more specific prefix that falls under a forced-parent prefix must be dropped.
		final ExtensionDescriptor descriptor = descriptorWithChildFirst(List.of("org.metricshub.engine.telemetry."));
		assertTrue(
			ExtensionRuntime.sanitizeChildFirst(descriptor).isEmpty(),
			"A prefix nested under a forced-parent prefix must be rejected"
		);
	}

	@Test
	void testRejectsExactForcedParentPrefix() {
		final ExtensionDescriptor descriptor = descriptorWithChildFirst(List.of("org.metricshub.engine."));
		assertTrue(
			ExtensionRuntime.sanitizeChildFirst(descriptor).isEmpty(),
			"A prefix equal to a forced-parent prefix must be rejected"
		);
	}

	@Test
	void testRejectsAncestorOfForcedParent() {
		// The regression: a broad prefix that is an ANCESTOR of a forced-parent prefix would still
		// capture the forced namespace (e.g. org.metricshub. captures org.metricshub.engine.) and
		// must be rejected too.
		final ExtensionDescriptor descriptor = descriptorWithChildFirst(List.of("org.metricshub.", "com."));
		assertTrue(
			ExtensionRuntime.sanitizeChildFirst(descriptor).isEmpty(),
			"A prefix that is an ancestor of a forced-parent prefix must be rejected"
		);
	}

	@Test
	void testFiltersOnlyOverlappingPrefixes() {
		final ExtensionDescriptor descriptor = descriptorWithChildFirst(
			List.of("com.acme.driver.", "org.metricshub.", "org.example.lib.")
		);
		assertEquals(
			List.of("com.acme.driver.", "org.example.lib."),
			ExtensionRuntime.sanitizeChildFirst(descriptor),
			"Only the overlapping prefix must be dropped; the rest kept in order"
		);
	}

	@Test
	void testMalformedServiceEntryDoesNotAbortDiscoveryOrLeakLoaders() throws IOException {
		// A jar whose IProtocolExtension service file names a class that does not exist: discovery
		// must skip it (ServiceConfigurationError handled) instead of unwinding with open loaders.
		final File jar = tempDir.resolve("broken-extension.jar").toFile();
		try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(jar))) {
			zos.putNextEntry(new ZipEntry("META-INF/services/org.metricshub.engine.extension.IProtocolExtension"));
			zos.write("does.not.Exist".getBytes(StandardCharsets.UTF_8));
			zos.closeEntry();
		}

		final ExtensionRuntime runtime = ExtensionRuntime.load(
			tempDir.toFile(),
			ExtensionRuntimeTest.class.getClassLoader()
		);
		try {
			assertTrue(
				runtime.getExtensionManager().getProtocolExtensions().isEmpty(),
				"The broken provider must be skipped, not loaded"
			);
		} finally {
			runtime.close();
		}

		// After close(), the loader no longer holds the jar: it must be deletable (Windows file lock).
		assertTrue(Files.deleteIfExists(jar.toPath()), "The extension jar must be released after close()");
	}
}
