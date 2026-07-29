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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtensionClassLoaderTest {

	@TempDir
	Path tempDir;

	/**
	 * Writes a jar containing a single text resource at {@code resourcePath} with {@code content}.
	 */
	private URL writeResourceJar(final String jarName, final String resourcePath, final String content)
		throws IOException {
		final File jar = tempDir.resolve(jarName).toFile();
		try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(jar))) {
			zos.putNextEntry(new ZipEntry(resourcePath));
			zos.write(content.getBytes(StandardCharsets.UTF_8));
			zos.closeEntry();
		}
		return jar.toURI().toURL();
	}

	private static String read(final URL url) throws IOException {
		if (url == null) {
			return null;
		}
		// Disable jar URL caching so the temp jar is not held open (Windows @TempDir cleanup).
		final java.net.URLConnection connection = url.openConnection();
		connection.setUseCaches(false);
		try (var in = connection.getInputStream()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static ExtensionClassLoader loader(final String name, final URL url, final List<ExtensionClassLoader> deps) {
		return new ExtensionClassLoader(name, new URL[] { url }, ClassLoader.getPlatformClassLoader(), deps, List.of());
	}

	@Test
	void testSiblingServiceFilesAreNotVisible() throws IOException {
		final String service = "META-INF/services/javax.xml.parsers.DocumentBuilderFactory";
		final URL urlA = writeResourceJar("a.jar", service, "org.example.FactoryA");
		final URL urlB = writeResourceJar("b.jar", service, "org.example.FactoryB");

		try (ExtensionClassLoader a = loader("A", urlA, List.of()); ExtensionClassLoader b = loader("B", urlB, List.of())) {
			assertEquals("org.example.FactoryA", read(a.getResource(service)), "A sees only its own service file");
			assertEquals("org.example.FactoryB", read(b.getResource(service)), "B sees only its own service file");
		}
	}

	@Test
	void testNonDependentCannotSeeAnotherExtensionResource() throws IOException {
		final URL urlA = writeResourceJar("a.jar", "res/only-in-a.txt", "A");
		final URL urlB = writeResourceJar("b.jar", "res/only-in-b.txt", "B");

		try (ExtensionClassLoader a = loader("A", urlA, List.of()); ExtensionClassLoader b = loader("B", urlB, List.of())) {
			assertNotNull(a.getResource("res/only-in-a.txt"), "A sees its own resource");
			assertNull(b.getResource("res/only-in-a.txt"), "B (no dependency on A) cannot see A's resource");
		}
	}

	@Test
	void testDeclaredDependencyResourceIsVisible() throws IOException {
		final URL urlA = writeResourceJar("a.jar", "res/only-in-a.txt", "A");
		final URL urlC = writeResourceJar("c.jar", "res/only-in-c.txt", "C");

		try (ExtensionClassLoader a = loader("A", urlA, List.of())) {
			try (ExtensionClassLoader c = loader("C", urlC, List.of(a))) {
				assertNotNull(c.getResource("res/only-in-c.txt"), "C sees its own resource");
				assertNotNull(c.getResource("res/only-in-a.txt"), "C (requires A) sees A's resource through delegation");
			}
		}
	}

	@Test
	void testChildFirstResourceResolvesFromOwnJar() throws IOException {
		// The same resource path exists in the parent and in the extension jar; under a child-first
		// prefix the extension's own version must win so classes and their metadata match.
		final String resource = "org/example/lib/config.txt";
		final URL parentJar = writeResourceJar("parent.jar", resource, "parent-version");
		final URL childJar = writeResourceJar("child.jar", resource, "child-version");

		try (java.net.URLClassLoader parent = new java.net.URLClassLoader(new URL[] { parentJar }, null)) {
			try (
				ExtensionClassLoader childFirst = new ExtensionClassLoader(
					"child-first",
					new URL[] { childJar },
					parent,
					List.of(),
					List.of("org.example.lib.")
				);
				ExtensionClassLoader parentFirst = new ExtensionClassLoader(
					"parent-first",
					new URL[] { childJar },
					parent,
					List.of(),
					List.of()
				)
			) {
				assertEquals("child-version", read(childFirst.getResource(resource)), "Child-first prefix wins");
				assertEquals(
					"child-version",
					read(java.util.Collections.list(childFirst.getResources(resource)).get(0)),
					"getResources must order the child's version first under a child-first prefix"
				);
				assertEquals("parent-version", read(parentFirst.getResource(resource)), "Default stays parent-first");
			}
		}
	}

	@Test
	void testChildFirstResourcePrefersOwnJarOverDependency() throws IOException {
		// Both the extension and its declared dependency ship the same resource: under a child-first
		// prefix the extension's OWN version must win, so its classes read their own metadata rather
		// than the dependency's.
		final String resource = "org/example/lib/config.txt";
		final URL dependencyJar = writeResourceJar("dep.jar", resource, "dependency-version");
		final URL ownJar = writeResourceJar("own.jar", resource, "own-version");

		try (ExtensionClassLoader dependency = loader("DEP", dependencyJar, List.of())) {
			try (
				ExtensionClassLoader own = new ExtensionClassLoader(
					"OWN",
					new URL[] { ownJar },
					ClassLoader.getPlatformClassLoader(),
					List.of(dependency),
					List.of("org.example.lib.")
				)
			) {
				assertEquals("own-version", read(own.getResource(resource)), "Own jar must win over the dependency");
				assertEquals(
					"own-version",
					read(java.util.Collections.list(own.getResources(resource)).get(0)),
					"getResources must order the own jar's version first"
				);
			}
		}
	}

	@Test
	void testTransitiveDependencyResourceIsVisible() throws IOException {
		// C -> A -> B: C must reach B's resources through A, matching class lookup's transitivity.
		final String service = "META-INF/services/org.example.Spi";
		final URL urlB = writeResourceJar("b.jar", service, "org.example.ImplB");
		final URL urlA = writeResourceJar("a.jar", "res/only-in-a.txt", "A");
		final URL urlC = writeResourceJar("c.jar", "res/only-in-c.txt", "C");

		try (ExtensionClassLoader b = loader("B", urlB, List.of())) {
			try (ExtensionClassLoader a = loader("A", urlA, List.of(b))) {
				try (ExtensionClassLoader c = loader("C", urlC, List.of(a))) {
					assertEquals(
						"org.example.ImplB",
						read(c.getResource(service)),
						"C (requires A, which requires B) sees B's service file transitively"
					);
					assertEquals(
						List.of("org.example.ImplB"),
						java.util.Collections.list(c.getResources(service))
							.stream()
							.map(url -> {
								try {
									return read(url);
								} catch (IOException e) {
									throw new java.io.UncheckedIOException(e);
								}
							})
							.toList(),
						"getResources must traverse the dependency graph transitively"
					);
				}
			}
		}
	}
}
