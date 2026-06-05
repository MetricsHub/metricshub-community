package org.metricshub.extension.jdbc.driver;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub JDBC Extension
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
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/**
 * Filesystem-backed {@link JdbcDriverJarLocator}.
 *
 * <p>Two modes:
 *
 * <ul>
 *   <li>{@code explicitJarPath != null} → resolve a specific JAR file already produced by
 *       {@link JdbcPathExpression}. The path may contain glob characters
 *       ({@code *}, {@code ?}, {@code [}); when it does, exactly one match must exist in the
 *       parent directory, otherwise resolution fails fast. Returned with origin
 *       {@link DriverOrigin#USER_EXPLICIT}.</li>
 *   <li>{@code explicitJarPath == null} → return every JAR directly under the configured
 *       default directory in lexicographic order, with origin {@link DriverOrigin#USER_DEFAULT}.</li>
 * </ul>
 *
 * <p>The locator does not inspect JAR contents. It assumes the operator placed exactly the JARs
 * required by the requested {@code driverClass}; the {@link IsolatedDriverClassLoader} built from
 * those URLs then resolves the actual driver class.
 */
@Slf4j
public final class FilesystemDriverScanner implements JdbcDriverJarLocator {

	/** Glob meta-characters; presence in a path triggers glob resolution. */
	private static final String GLOB_META_CHARS = "*?[";

	/**
	 * Hard ceiling applied when a glob uses the {@code **} recursive wildcard. Realistic JDBC
	 * driver layouts are 2&ndash;4 directories deep; capping at 10 leaves comfortable headroom
	 * while preventing an accidentally broad expression (for instance one rooted at
	 * {@code $USER_HOME}) from triggering an unbounded directory walk.
	 */
	private static final int DEEP_GLOB_MAX_DEPTH = 10;

	private final Path defaultDir;

	/**
	 * Creates a new scanner whose default directory (scanned when no explicit JAR path is supplied)
	 * is {@code defaultDir}.
	 *
	 * @param defaultDir the operator-default drivers directory (typically
	 *                   {@code $APP_DIR/extensions/jdbc/}). The directory does not need to
	 *                   exist at construction time; missing/non-directory paths simply produce
	 *                   {@link Optional#empty()} when {@code explicitJarPath == null}.
	 */
	public FilesystemDriverScanner(final Path defaultDir) {
		this.defaultDir = Objects.requireNonNull(defaultDir, "defaultDir");
	}

	@Override
	public Optional<LocatedDriverJars> locate(final String driverClass, final String explicitJarPath) {
		Objects.requireNonNull(driverClass, "driverClass");
		if (explicitJarPath != null) {
			return locateExplicit(driverClass, explicitJarPath);
		}
		return locateDefault(driverClass);
	}

	/**
	 * Resolves an explicit JAR path expression, supporting both literal files and single-match
	 * globs.
	 *
	 * @param driverClass     fully-qualified driver class, for logging.
	 * @param explicitJarPath absolute, expanded path expression (may contain glob characters).
	 * @return {@link Optional#empty()} when the file (or glob match) cannot be found; otherwise a
	 *         {@link LocatedDriverJars} carrying a single URL with origin
	 *         {@link DriverOrigin#USER_EXPLICIT}.
	 * @throws DriverResolutionException when a glob matches multiple files.
	 */
	private Optional<LocatedDriverJars> locateExplicit(final String driverClass, final String explicitJarPath) {
		if (containsGlobMeta(explicitJarPath)) {
			return resolveGlob(driverClass, explicitJarPath);
		}
		final Path jar = Paths.get(explicitJarPath);
		if (!Files.isRegularFile(jar)) {
			log.debug("Explicit JDBC driver JAR {} does not exist (driverClass={}).", jar, driverClass);
			return Optional.empty();
		}
		return toLocated(driverClass, jar, DriverOrigin.USER_EXPLICIT);
	}

	/**
	 * Expands a glob expression rooted at its non-glob ancestor directory.
	 *
	 * <p>Multi-segment globs are walked with a bounded depth: the depth implied by the
	 * pattern's segment count, or {@link #DEEP_GLOB_MAX_DEPTH} when the pattern uses the
	 * recursive {@code **} wildcard. This prevents an overly broad expression from walking
	 * a deep tree such as {@code $USER_HOME}.
	 *
	 * @param driverClass driver class, for logging.
	 * @param globPath    absolute path expression whose final segment(s) contain glob
	 *                    meta-characters.
	 * @return the located JAR, or {@link Optional#empty()} when no file matches.
	 * @throws DriverResolutionException when more than one file matches.
	 */
	private Optional<LocatedDriverJars> resolveGlob(final String driverClass, final String globPath) {
		final String parentString = firstNonGlobAncestor(globPath);
		if (parentString == null) {
			log.debug("Glob {} has no non-glob ancestor (driverClass={}).", globPath, driverClass);
			return Optional.empty();
		}
		final Path parent = Paths.get(parentString);
		if (!Files.isDirectory(parent)) {
			log.debug("Glob {} has no existing parent directory (driverClass={}).", globPath, driverClass);
			return Optional.empty();
		}
		// Compute the glob portion relative to the parent directory. If it still contains a
		// path separator, we walk recursively; otherwise we use a single-directory stream.
		final String remainder = globPath.substring(parentString.length()).replace('\\', '/').replaceFirst("^/+", "");
		final List<Path> matches = new ArrayList<>();
		if (remainder.indexOf('/') < 0) {
			// Single-directory glob, e.g. "ojdbc11-*.jar".
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(parent, remainder)) {
				for (final Path p : stream) {
					if (Files.isRegularFile(p)) {
						matches.add(p.toAbsolutePath().normalize());
					}
				}
			} catch (IOException e) {
				log.warn("Failed to enumerate {} for glob {}: {}", parent, globPath, e.getMessage());
				return Optional.empty();
			}
		} else {
			// Multi-segment glob: walk and match relative paths using forward-slash form so the
			// glob syntax is uniform across operating systems. The walk is depth-bounded by the
			// pattern itself (or DEEP_GLOB_MAX_DEPTH when '**' is used) to guard against an
			// expression accidentally rooted at a deep tree such as $USER_HOME.
			final PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + remainder);
			final int maxDepth = computeMaxDepth(remainder);
			try (Stream<Path> walk = Files.walk(parent, maxDepth)) {
				walk
					.filter(Files::isRegularFile)
					.filter(p -> {
						final Path rel = parent.relativize(p);
						return matcher.matches(Paths.get(rel.toString().replace('\\', '/')));
					})
					.map(p -> p.toAbsolutePath().normalize())
					.forEach(matches::add);
			} catch (IOException e) {
				log.warn("Failed to walk {} while resolving glob {}: {}", parent, globPath, e.getMessage());
				return Optional.empty();
			}
		}
		if (matches.isEmpty()) {
			return Optional.empty();
		}
		if (matches.size() > 1) {
			throw new DriverResolutionException(
				"""
				Driver path glob '%s' matched %d files for %s; expected exactly one. Matches: %s\
				""".formatted(globPath, matches.size(), driverClass, matches)
			);
		}
		return toLocated(driverClass, matches.get(0), DriverOrigin.USER_EXPLICIT);
	}

	/**
	 * Scans the default directory for {@code *.jar} entries.
	 *
	 * @param driverClass driver class, for logging.
	 * @return all JARs found at the root of {@link #defaultDir}, lexicographically sorted.
	 */
	private Optional<LocatedDriverJars> locateDefault(final String driverClass) {
		if (!Files.isDirectory(defaultDir)) {
			log.debug("JDBC driver default directory {} does not exist (driverClass={}).", defaultDir, driverClass);
			return Optional.empty();
		}
		final URL[] urls = listJarUrls(defaultDir);
		if (urls.length == 0) {
			log.debug("JDBC driver default directory {} contains no JARs (driverClass={}).", defaultDir, driverClass);
			return Optional.empty();
		}
		log.info("Located {} JAR(s) for driver {} in {} (origin=USER_DEFAULT).", urls.length, driverClass, defaultDir);
		return Optional.of(new LocatedDriverJars(urls, DriverOrigin.USER_DEFAULT));
	}

	/**
	 * Wraps a single JAR file into a {@link LocatedDriverJars}.
	 *
	 * @param driverClass driver class, for logging.
	 * @param jar         the resolved JAR file (must exist and be regular).
	 * @param origin      the {@link DriverOrigin} to tag the result with.
	 * @return a populated {@link LocatedDriverJars}, or {@link Optional#empty()} when the URL
	 *         conversion fails (effectively never).
	 */
	private static Optional<LocatedDriverJars> toLocated(
		final String driverClass,
		final Path jar,
		final DriverOrigin origin
	) {
		try {
			final URL url = jar.toUri().toURL();
			log.info("Located driver {} at {} (origin={}).", driverClass, jar, origin);
			return Optional.of(new LocatedDriverJars(new URL[] { url }, origin));
		} catch (MalformedURLException e) {
			log.warn("Skipping unreadable JAR {}: {}", jar, e.getMessage());
			return Optional.empty();
		}
	}

	/**
	 * Computes the maximum directory depth required to satisfy a relative glob pattern.
	 *
	 * <p>When {@code remainder} contains the recursive {@code **} wildcard the depth is capped
	 * at {@link #DEEP_GLOB_MAX_DEPTH}. Otherwise the depth is exactly the number of path
	 * segments in the pattern (one more than the count of {@code /} separators), because no
	 * deeper file could possibly match.
	 *
	 * @param remainder the glob portion expressed relative to its non-glob parent, using
	 *                  forward-slash separators (never {@code null}).
	 * @return the {@code maxDepth} argument to pass to {@link Files#walk(Path, int, java.nio.file.FileVisitOption...)}.
	 */
	private static int computeMaxDepth(final String remainder) {
		if (remainder.contains("**")) {
			return DEEP_GLOB_MAX_DEPTH;
		}
		int segments = 1;
		for (int i = 0; i < remainder.length(); i++) {
			if (remainder.charAt(i) == '/') {
				segments++;
			}
		}
		return segments;
	}

	/**
	 * @param pathString a path expression.
	 * @return {@code true} when {@code pathString} contains any glob meta-character.
	 */
	private static boolean containsGlobMeta(final String pathString) {
		for (int i = 0; i < pathString.length(); i++) {
			if (GLOB_META_CHARS.indexOf(pathString.charAt(i)) >= 0) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Walks up from {@code globPath} until a path prefix without glob meta-characters is found.
	 *
	 * @param globPath the path expression to scan.
	 * @return the deepest ancestor prefix with no glob meta-characters, or {@code null} when no
	 *         such ancestor exists (degenerate input).
	 */
	private static String firstNonGlobAncestor(final String globPath) {
		String current = globPath;
		while (current != null && containsGlobMeta(current)) {
			final int slash = lastSeparator(current);
			if (slash < 0) {
				return null;
			}
			current = current.substring(0, slash);
			if (current.isEmpty()) {
				return null;
			}
		}
		return current;
	}

	/**
	 * @param s the path expression.
	 * @return the index of the last path separator ({@code /} or {@code \}), or -1.
	 */
	private static int lastSeparator(final String s) {
		int idx = -1;
		for (int i = s.length() - 1; i >= 0; i--) {
			final char c = s.charAt(i);
			if (c == '/' || c == '\\') {
				idx = i;
				break;
			}
		}
		return idx;
	}

	/**
	 * Lists every {@code *.jar} file directly under {@code dir}, converted to {@link URL}.
	 * Non-jar entries and subdirectories are ignored; entries are returned in lexicographic order.
	 *
	 * @param dir the directory to scan. Must exist and be a directory.
	 * @return the JAR URLs found; never {@code null}, possibly empty.
	 */
	private static URL[] listJarUrls(final Path dir) {
		final List<URL> urls = new ArrayList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.jar")) {
			final List<Path> sorted = new ArrayList<>();
			stream.forEach(sorted::add);
			sorted.sort(null);
			for (final Path jar : sorted) {
				if (Files.isRegularFile(jar)) {
					addJarUrl(urls, jar);
				}
			}
		} catch (IOException e) {
			log.warn("Failed to enumerate JDBC driver directory {}: {}", dir, e.getMessage());
		}
		return urls.toArray(URL[]::new);
	}

	/**
	 * Converts {@code jar} to URL and adds it to {@code urls}, logging and skipping on failure.
	 *
	 * @param urls the list to add to; never {@code null}.
	 * @param jar  the JAR file to convert; must exist and be a regular file.
	 */
	private static void addJarUrl(final List<URL> urls, final Path jar) {
		try {
			urls.add(jar.toUri().toURL());
		} catch (MalformedURLException e) {
			log.warn("Skipping unreadable JAR {}: {}", jar, e.getMessage());
		}
	}
}
