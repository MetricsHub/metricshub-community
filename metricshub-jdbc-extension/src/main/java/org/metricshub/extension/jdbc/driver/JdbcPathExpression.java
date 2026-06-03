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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the {@link org.metricshub.engine.connector.model.identity.DriverInfo#getJarPath()}
 * expression into an absolute path string.
 *
 * <p>The resolver expands placeholders, accepts absolute paths as-is, and preserves glob
 * patterns. {@code ..} segments are rejected after expansion. Connector-level and
 * resource-level expressions share the same rules.
 *
 * <p>Supported placeholders:
 *
 * <table border="1">
 *   <caption>Placeholders</caption>
 *   <tr><th>Token</th><th>Resolves to</th></tr>
 *   <tr><td>{@code $INSTALL_DIR}</td><td>MetricsHub install root (parent of {@code lib/app/}).</td></tr>
 *   <tr><td>{@code $USER_HOME}</td><td>{@code System.getProperty("user.home")}.</td></tr>
 *   <tr><td>{@code $WORKING_DIR}</td><td>{@code System.getProperty("user.dir")}.</td></tr>
 * </table>
 *
 * <p>Unknown placeholders raise an {@link IllegalArgumentException} naming the offending token.
 */
public final class JdbcPathExpression {

	/** Placeholder token: install root (parent of {@code lib/app/}). */
	public static final String INSTALL_DIR = "$INSTALL_DIR";

	/** Placeholder token: current user home. */
	public static final String USER_HOME = "$USER_HOME";

	/** Placeholder token: current working directory. */
	public static final String WORKING_DIR = "$WORKING_DIR";

	/** Matches {@code $UPPER_SNAKE_CASE} placeholders. */
	private static final Pattern PLACEHOLDER = Pattern.compile("\\$[A-Z][A-Z0-9_]*");

	private final Supplier<Path> installDirSupplier;
	private final Supplier<Path> userHomeSupplier;
	private final Supplier<Path> workingDirSupplier;

	/**
	 * Production constructor; wires the install dir against {@link JdbcInstallDir}.
	 */
	public JdbcPathExpression() {
		this(
			() -> JdbcInstallDir.resolveSubPath("."),
			() -> Paths.get(System.getProperty("user.home")),
			() -> Paths.get(System.getProperty("user.dir"))
		);
	}

	/**
	 * Test constructor.
	 *
	 * @param installDirSupplier supplier for {@code $INSTALL_DIR}; never {@code null}.
	 * @param userHomeSupplier   supplier for {@code $USER_HOME}; never {@code null}.
	 * @param workingDirSupplier supplier for {@code $WORKING_DIR}; never {@code null}.
	 */
	public JdbcPathExpression(
		final Supplier<Path> installDirSupplier,
		final Supplier<Path> userHomeSupplier,
		final Supplier<Path> workingDirSupplier
	) {
		this.installDirSupplier = Objects.requireNonNull(installDirSupplier, "installDirSupplier");
		this.userHomeSupplier = Objects.requireNonNull(userHomeSupplier, "userHomeSupplier");
		this.workingDirSupplier = Objects.requireNonNull(workingDirSupplier, "workingDirSupplier");
	}

	/**
	 * Resolves {@code expression}.
	 *
	 * <p>The returned string is the expression with every placeholder substituted. Globs are
	 * <em>not</em> evaluated here; that is delegated to {@link FilesystemDriverScanner} (using
	 * {@link java.nio.file.FileSystem#getPathMatcher(String)}). Keeping the result as a string
	 * lets callers pass expressions like {@code .../ojdbc11-*.jar}, which {@link Path} cannot
	 * represent on Windows.
	 *
	 * @param expression the raw value, e.g. {@code $INSTALL_DIR/lib/extensions/jdbc/jt400.jar} or
	 *                   {@code /opt/oracle/instantclient/ojdbc11.jar}; required, non-blank.
	 * @return the expanded path expression; never {@code null}. Forward slashes inside the
	 *         original expression are preserved.
	 * @throws IllegalArgumentException when an unknown placeholder is used or when the expression
	 *                                  contains {@code ..}.
	 */
	public String resolve(final String expression) {
		if (expression == null || expression.isBlank()) {
			throw new IllegalArgumentException("jarPath expression must be non-blank");
		}
		final String trimmed = expression.trim();
		final String expanded = expand(trimmed);
		// Forbid traversal after expansion (matches a literal segment so we don't catch '..' inside a
		// filename like 'foo..bar.jar').
		for (final String segment : expanded.split("[/\\\\]")) {
			if ("..".equals(segment)) {
				throw new IllegalArgumentException("jarPath must not contain '..' segments (got: '" + expression + "')");
			}
		}
		return expanded;
	}

	/**
	 * Substitutes every recognized placeholder in {@code expression}.
	 *
	 * @param expression the trimmed user input.
	 * @return the expanded path string.
	 */
	private String expand(final String expression) {
		final Matcher matcher = PLACEHOLDER.matcher(expression);
		final StringBuilder out = new StringBuilder();
		while (matcher.find()) {
			final String token = matcher.group();
			final String replacement = resolveToken(token);
			matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(out);
		return out.toString();
	}

	/**
	 * Returns the directory replacement for {@code token}.
	 *
	 * @param token an upper-snake placeholder including the leading {@code $}.
	 * @return the absolute directory string used as a replacement.
	 * @throws IllegalArgumentException when the token is unknown.
	 */
	private String resolveToken(final String token) {
		switch (token) {
			case INSTALL_DIR:
				return installDirSupplier.get().toAbsolutePath().normalize().toString();
			case USER_HOME:
				return userHomeSupplier.get().toAbsolutePath().normalize().toString();
			case WORKING_DIR:
				return workingDirSupplier.get().toAbsolutePath().normalize().toString();
			default:
				throw new IllegalArgumentException("jarPath uses unknown placeholder " + token);
		}
	}
}
