package org.metricshub.engine.extension;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
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
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A {@link URLClassLoader} that isolates a single MetricsHub extension from its siblings.
 *
 * <p>Every extension jar gets its own {@link ExtensionClassLoader}. Because the jar is no longer
 * appended to the system class loader, an extension's {@code META-INF/services} entries (JAXP/StAX
 * factories, {@code java.sql.Driver}, Jackson, security providers, …) are visible only to that
 * extension, so one extension can never change JVM-global service resolution for another.
 *
 * <p><b>Delegation order</b> for {@link #loadClass(String, boolean)}:
 * <ol>
 *   <li><b>Child-first packages</b> (opt-in, {@code MetricsHub-Extension-Child-First}) — this
 *       loader's own URLs first, so an extension may ship a library version that conflicts with the
 *       engine. Unused by community extensions.</li>
 *   <li><b>Parent (the engine/agent class loader)</b> — guarantees a single {@link Class} identity
 *       for {@code org.metricshub.engine.*}, Jackson, SLF4J/Log4j2 and OpenTelemetry, all of which
 *       cross the engine↔extension boundary. This is why the default is parent-first, not
 *       child-first: a child-loaded copy of any of those would be a {@link ClassCastException}
 *       factory.</li>
 *   <li><b>Declared dependencies</b> ({@code MetricsHub-Extension-Requires}) — the class loaders of
 *       sibling extensions this one depends on, searched via {@link #loadLocal(String)} (their own
 *       URLs, never the shared parent again). Dependency-before-self means dependents share one
 *       class identity for a dependency's classes and can never shadow them.</li>
 *   <li><b>Own URLs.</b></li>
 * </ol>
 */
public class ExtensionClassLoader extends URLClassLoader {

	static {
		// Improves concurrent class loading throughput; safe with parent-first delegation.
		ClassLoader.registerAsParallelCapable();
	}

	private final List<String> childFirstPackages;

	/**
	 * The child-first package prefixes converted to resource-path form ({@code com.acme.lib.} →
	 * {@code com/acme/lib/}), so resources under a child-first package resolve child-first too —
	 * keeping classes and their package metadata/configuration from the same library version.
	 */
	private final List<String> childFirstResourcePrefixes;

	/**
	 * The class loaders of the extensions this one declares a dependency on. Populated after
	 * construction (dependency loaders are built first, but the list is assembled by the runtime),
	 * hence a mutable-then-frozen reference held via a final field initialized in the constructor.
	 */
	private final List<ExtensionClassLoader> delegates;

	/**
	 * Creates a new isolated extension loader.
	 *
	 * @param name               diagnostic name (visible in stack traces and logs).
	 * @param urls               the extension jar (and any extra URLs) to search.
	 * @param parent             the shared engine/agent class loader.
	 * @param delegates          the loaders of declared dependency extensions; may be empty, never
	 *                           {@code null}.
	 * @param childFirstPackages package prefixes to load child-first; may be empty, never
	 *                           {@code null}.
	 */
	public ExtensionClassLoader(
		final String name,
		final URL[] urls,
		final ClassLoader parent,
		final List<ExtensionClassLoader> delegates,
		final List<String> childFirstPackages
	) {
		super(name, urls, parent);
		this.delegates = delegates == null ? List.of() : List.copyOf(delegates);
		this.childFirstPackages = childFirstPackages == null ? List.of() : List.copyOf(childFirstPackages);
		this.childFirstResourcePrefixes = this.childFirstPackages.stream()
			.map(prefix -> prefix.replace('.', '/'))
			.toList();
	}

	@Override
	protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
		Objects.requireNonNull(name, "name");
		synchronized (getClassLoadingLock(name)) {
			Class<?> c = findLoadedClass(name);

			if (c == null && isChildFirst(name)) {
				try {
					c = findClass(name);
				} catch (ClassNotFoundException _) {
					// Not in our URLs: fall through to the parent-first path below.
				}
			}

			if (c == null) {
				try {
					c = getParent().loadClass(name);
				} catch (ClassNotFoundException _) {
					c = findInDelegatesOrSelf(name);
				}
			}

			if (resolve) {
				resolveClass(c);
			}
			return c;
		}
	}

	/**
	 * Loads a class from this extension's own URLs and its declared dependencies, without ever
	 * consulting the shared parent. Used by a dependent loader to reach into this loader's subtree.
	 *
	 * @param name fully-qualified class name; never {@code null}.
	 * @return the resolved {@link Class}; never {@code null}.
	 * @throws ClassNotFoundException when neither this loader's URLs nor its dependencies define it.
	 */
	Class<?> loadLocal(final String name) throws ClassNotFoundException {
		synchronized (getClassLoadingLock(name)) {
			final Class<?> loaded = findLoadedClass(name);
			if (loaded != null) {
				return loaded;
			}
			return findInDelegatesOrSelf(name);
		}
	}

	/**
	 * Searches declared dependencies (in declared order) then this loader's own URLs.
	 *
	 * @param name fully-qualified class name.
	 * @return the resolved {@link Class}.
	 * @throws ClassNotFoundException when no dependency and not this loader defines it.
	 */
	private Class<?> findInDelegatesOrSelf(final String name) throws ClassNotFoundException {
		for (final ExtensionClassLoader delegate : delegates) {
			try {
				return delegate.loadLocal(name);
			} catch (ClassNotFoundException _) {
				// Try the next dependency.
			}
		}
		return findClass(name);
	}

	@Override
	public URL getResource(final String name) {
		// Resources under a child-first package resolve exactly like child-first class lookup: this
		// loader's own URLs first (a child-loaded library reads its own metadata/configuration),
		// then the parent, then declared dependencies — the same fallback order as loadClass, so a
		// parent-loaded class never consumes a dependency's incompatible metadata.
		if (isChildFirstResource(name)) {
			final URL own = findResource(name);
			if (own != null) {
				return own;
			}
		}
		// Parent-first, then dependencies (recursively, mirroring class lookup), then self.
		final ClassLoader parent = getParent();
		final URL url = parent == null ? null : parent.getResource(name);
		if (url != null) {
			return url;
		}
		return findResourceLocal(name);
	}

	/**
	 * Finds a resource in this extension's declared dependencies (recursively, in declared order) or
	 * in its own URLs, without ever consulting the shared parent. This is the resource counterpart of
	 * {@link #loadLocal(String)}: a dependent extension reaches the whole dependency subtree, so
	 * transitive chains such as C → A → B expose B's resources to C. The dependency graph is acyclic
	 * (cycles are disabled at load time), so the recursion terminates.
	 *
	 * @param name the resource name.
	 * @return the resource {@link URL}, or {@code null} when neither a dependency nor this loader
	 *         defines it.
	 */
	URL findResourceLocal(final String name) {
		for (final ExtensionClassLoader delegate : delegates) {
			final URL url = delegate.findResourceLocal(name);
			if (url != null) {
				return url;
			}
		}
		return findResource(name);
	}

	@Override
	public Enumeration<URL> getResources(final String name) throws IOException {
		// Deduplicate by the URL's external form (a String) rather than by URL itself: URL.equals /
		// URL.hashCode perform blocking DNS resolution and are unreliable (Sonar java:S2112).
		final Set<String> seen = new HashSet<>();
		final List<URL> urls = new ArrayList<>();
		final ClassLoader parent = getParent();
		if (isChildFirstResource(name)) {
			// Child-first ordering mirrors class lookup: this loader's own resources first (so
			// classes and their metadata come from the same jar), then the parent, then declared
			// dependencies — a parent-loaded class must not consume a dependency's metadata.
			addAll(seen, urls, findResources(name));
			if (parent != null) {
				addAll(seen, urls, parent.getResources(name));
			}
			collectResourcesLocal(name, seen, urls);
		} else {
			if (parent != null) {
				addAll(seen, urls, parent.getResources(name));
			}
			collectResourcesLocal(name, seen, urls);
		}
		return Collections.enumeration(urls);
	}

	/**
	 * Tests whether {@code resourceName} lives under a configured child-first package.
	 *
	 * @param resourceName the resource path (slash-separated); must not be {@code null}.
	 * @return {@code true} if it starts with a configured child-first path prefix.
	 */
	private boolean isChildFirstResource(final String resourceName) {
		for (final String prefix : childFirstResourcePrefixes) {
			if (resourceName.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Collects the resources of this extension's declared dependencies (recursively, in declared
	 * order) and of its own URLs into {@code target}, without consulting the shared parent. Resource
	 * counterpart of {@link #loadLocal(String)} for {@link #getResources(String)}.
	 *
	 * @param name   the resource name.
	 * @param seen   the set of already-collected URL external forms (deduplication).
	 * @param target the destination list, in insertion order.
	 * @throws IOException if the resource lookup fails.
	 */
	void collectResourcesLocal(final String name, final Set<String> seen, final List<URL> target) throws IOException {
		for (final ExtensionClassLoader delegate : delegates) {
			delegate.collectResourcesLocal(name, seen, target);
		}
		addAll(seen, target, findResources(name));
	}

	/**
	 * Drains an enumeration of URLs into {@code target}, skipping any whose external form was already
	 * seen. Deduplicating on the string form avoids {@link URL#equals}/{@link URL#hashCode}.
	 *
	 * @param seen   the set of already-collected URL external forms.
	 * @param target the destination list, in insertion order.
	 * @param source the enumeration to drain; may be {@code null}.
	 */
	private static void addAll(final Set<String> seen, final List<URL> target, final Enumeration<URL> source) {
		if (source == null) {
			return;
		}
		while (source.hasMoreElements()) {
			final URL url = source.nextElement();
			if (seen.add(url.toExternalForm())) {
				target.add(url);
			}
		}
	}

	/**
	 * Tests whether {@code className} is configured to be loaded child-first.
	 *
	 * @param className fully-qualified class name; must not be {@code null}.
	 * @return {@code true} if it starts with a configured child-first prefix.
	 */
	boolean isChildFirst(final String className) {
		for (final String prefix : childFirstPackages) {
			if (className.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}
}
