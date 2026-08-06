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

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Metadata describing a single extension JAR, read from its {@code MANIFEST.MF}.
 *
 * <p>Three optional manifest attributes drive the isolation model:
 * <ul>
 *   <li>{@code MetricsHub-Extension-Id} — the extension's identifier, referenced by other
 *       extensions in their {@code Requires}. When absent it defaults to the JAR file name
 *       without its {@code .jar} suffix, so legacy jars still resolve.</li>
 *   <li>{@code MetricsHub-Extension-Requires} — comma-separated ids of sibling extensions whose
 *       classes this extension needs at runtime. Their class loaders become delegates of this
 *       extension's {@link ExtensionClassLoader}.</li>
 *   <li>{@code MetricsHub-Extension-Child-First} — comma-separated package prefixes to load
 *       child-first (own URLs before the parent). Unused by the community extensions today;
 *       reserved for extensions that must ship a library version conflicting with the engine.</li>
 * </ul>
 *
 * @param id                 the extension identifier (never {@code null})
 * @param jarFile            the extension JAR file (never {@code null})
 * @param requires           ids of sibling extensions this one depends on (never {@code null})
 * @param childFirstPackages package prefixes to load child-first (never {@code null})
 */
public record ExtensionDescriptor(String id, File jarFile, List<String> requires, List<String> childFirstPackages) {
	/** Manifest attribute holding the extension identifier. */
	public static final String ATTR_ID = "MetricsHub-Extension-Id";

	/** Manifest attribute holding the comma-separated ids of required sibling extensions. */
	public static final String ATTR_REQUIRES = "MetricsHub-Extension-Requires";

	/** Manifest attribute holding the comma-separated child-first package prefixes. */
	public static final String ATTR_CHILD_FIRST = "MetricsHub-Extension-Child-First";

	/**
	 * Reads the extension metadata from the given JAR file.
	 *
	 * @param jarFile the extension JAR to inspect; must not be {@code null}.
	 * @return the parsed {@link ExtensionDescriptor}.
	 * @throws IOException if the JAR cannot be opened or read.
	 */
	public static ExtensionDescriptor from(final File jarFile) throws IOException {
		try (JarFile jar = new JarFile(jarFile)) {
			final Manifest manifest = jar.getManifest();

			String id = null;
			List<String> requires = List.of();
			List<String> childFirst = List.of();

			if (manifest != null) {
				final var attributes = manifest.getMainAttributes();
				id = trimToNull(attributes.getValue(ATTR_ID));
				requires = splitCsv(attributes.getValue(ATTR_REQUIRES));
				childFirst = splitCsv(attributes.getValue(ATTR_CHILD_FIRST));
			}

			if (id == null) {
				id = defaultId(jarFile);
			}

			return new ExtensionDescriptor(id, jarFile, requires, childFirst);
		}
	}

	/**
	 * Derives a default extension id from a JAR file name by stripping the {@code .jar} suffix.
	 *
	 * @param jarFile the JAR file.
	 * @return the file name without its {@code .jar} extension.
	 */
	private static String defaultId(final File jarFile) {
		final String name = jarFile.getName();
		return name.endsWith(".jar") ? name.substring(0, name.length() - ".jar".length()) : name;
	}

	/**
	 * Splits a comma-separated attribute value into a trimmed, non-empty list.
	 *
	 * @param value the raw attribute value; may be {@code null}.
	 * @return an immutable list of trimmed, non-empty tokens (possibly empty, never {@code null}).
	 */
	private static List<String> splitCsv(final String value) {
		if (value == null || value.isBlank()) {
			return List.of();
		}
		return java.util.Arrays.stream(value.split(","))
			.map(String::trim)
			.filter(token -> !token.isEmpty())
			.toList();
	}

	/**
	 * Trims a string and returns {@code null} when the result is empty.
	 *
	 * @param value the value to trim; may be {@code null}.
	 * @return the trimmed value, or {@code null} if blank.
	 */
	private static String trimToNull(final String value) {
		if (value == null) {
			return null;
		}
		final String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
