package org.metricshub.extension.jdbc.driver;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub JDBC Extension
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2025 MetricsHub
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

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Objects;

/**
 * A {@link URLClassLoader} that isolates a single JDBC driver from the engine classpath.
 *
 * <p>Loading strategy:
 * <ul>
 *   <li><b>Parent-first by default</b> — guarantees that {@code java.*}, {@code javax.sql.*},
 *       {@code org.metricshub.*}, SLF4J, Jackson and every other engine dependency always resolve
 *       to the same {@link Class} the engine already loaded. Vendor JARs that fat-pack duplicates
 *       cannot shadow them.</li>
 *   <li><b>Child-first for declared driver packages</b> — when a class name starts with any prefix
 *       in {@link JdbcDriverDescriptor#driverPackages()}, this loader tries its own URLs first,
 *       then falls back to the parent. This is what lets two versions of the same driver class
 *       (e.g. {@code com.mysql.cj.jdbc.Driver}) coexist in separate loaders without colliding.</li>
 * </ul>
 *
 * <p>Loaders are created lazily by {@link JdbcDriverRegistry} on first use of a
 * {@code (driverClass, explicitJarPath)} pair, then cached for the lifetime of the JVM and
 * {@link #close()}d on shutdown.
 */
public class IsolatedDriverClassLoader extends URLClassLoader {

	static {
		// Improves concurrent class loading throughput; safe with parent-first delegation.
		ClassLoader.registerAsParallelCapable();
	}

	private final List<String> childFirstPackages;

	/**
	 * Creates a new isolated loader.
	 *
	 * @param name               diagnostic name (visible in stack traces and logs); typically
	 *                           {@code "<driverClass>@<variant|default>"}.
	 * @param urls               classpath entries (the driver JAR plus any dependencies).
	 * @param parent             parent classloader, normally the extension's own loader.
	 * @param childFirstPackages package prefixes that should be loaded child-first.
	 */
	public IsolatedDriverClassLoader(
		final String name,
		final URL[] urls,
		final ClassLoader parent,
		final List<String> childFirstPackages
	) {
		super(name, urls, parent);
		this.childFirstPackages = childFirstPackages == null ? List.of() : List.copyOf(childFirstPackages);
	}

	/**
	 * Loads {@code name}, applying parent-first delegation by default and child-first delegation
	 * for class names matched by {@link #isChildFirst(String)}. Child-first lookup falls back to
	 * the parent when the class is not found in this loader's URLs.
	 *
	 * @param name    fully-qualified class name; never {@code null}.
	 * @param resolve {@code true} to link the class after loading.
	 * @return the resolved {@link Class}; never {@code null}.
	 * @throws ClassNotFoundException when neither this loader nor the parent can load the class.
	 * @throws NullPointerException   when {@code name} is {@code null}.
	 */
	@Override
	protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
		Objects.requireNonNull(name, "name");
		if (!isChildFirst(name)) {
			return super.loadClass(name, resolve);
		}
		synchronized (getClassLoadingLock(name)) {
			Class<?> c = findLoadedClass(name);
			if (c == null) {
				try {
					c = findClass(name);
				} catch (ClassNotFoundException ignored) {
					// Fall through to parent.
					c = super.loadClass(name, false);
				}
			}
			if (resolve) {
				resolveClass(c);
			}
			return c;
		}
	}

	/**
	 * Tests whether {@code className} should be loaded child-first by this loader.
	 *
	 * @param className fully-qualified class name to test; must not be {@code null}.
	 * @return {@code true} if {@code className} starts with one of the configured child-first
	 *         prefixes; {@code false} otherwise.
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
