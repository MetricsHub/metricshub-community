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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the {@link org.metricshub.engine.connector.model.identity.JdbcInfo#getDriverPath()}
 * expression into an absolute {@link Path}.
 *
 * <p>Two scopes drive the security boundary:
 *
 * <ul>
 *   <li>{@link Scope#CONNECTOR} — third-party content. Only {@code $INSTALL_DIR} and
 *       {@code $USER_HOME} placeholders are allowed. Absolute paths and {@code $WORKING_DIR}
 *       are rejected. Connectors must anchor their paths under operator- or user-owned
 *       directories.</li>
 *   <li>{@link Scope#RESOURCE} — operator content ({@code metricshub.yaml}). All placeholders
 *       are allowed and absolute paths are accepted as-is.</li>
 * </ul>
 *
 * <p>{@code ..} segments are rejected after expansion, in every scope.
 *
 * <p>Supported placeholders:
 *
 * <table border="1">
 *   <caption>Placeholders</caption>
 *   <tr><th>Token</th><th>Resolves to</th></tr>
 *   <tr><td>{@code $INSTALL_DIR}</td><td>MetricsHub install root (parent of {@code lib/app/}).</td></tr>
 *   <tr><td>{@code $USER_HOME}</td><td>{@code System.getProperty("user.home")}.</td></tr>
 *   <tr><td>{@code $WORKING_DIR}</td><td>{@code System.getProperty("user.dir")}. Resource-only.</td></tr>
 * </table>
 *
 * <p>Unknown placeholders raise an {@link IllegalArgumentException} naming the offending token.
 */
public final class JdbcPathExpression {

	/** Placeholder token: install root (parent of {@code lib/app/}). */
	public static final String INSTALL_DIR = "$INSTALL_DIR";

	/** Placeholder token: current user home. */
	public static final String USER_HOME = "$USER_HOME";

	/** Placeholder token: current working directory; resource-scope only. */
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
	 * Resolves {@code expression} against the supplied scope.
	 *
	 * <p>The returned string is the expression with every placeholder substituted. Globs are
	 * <em>not</em> evaluated here; that is delegated to {@link FilesystemDriverScanner} (using
	 * {@link java.nio.file.FileSystem#getPathMatcher(String)}). Keeping the result as a string
	 * lets callers pass expressions like {@code .../ojdbc11-*.jar}, which {@link Path} cannot
	 * represent on Windows.
	 *
	 * @param expression the raw value, e.g. {@code $INSTALL_DIR/lib/extensions/jdbc/jt400.jar} or
	 *                   {@code /opt/oracle/instantclient/ojdbc11.jar}; required, non-blank.
	 * @param scope      security scope; required.
	 * @return the expanded path expression; never {@code null}. Forward slashes inside the
	 *         original expression are preserved.
	 * @throws IllegalArgumentException when an unknown placeholder is used, when scope rules are
	 *                                  violated, or when the expression contains {@code ..}.
	 */
	public String resolve(final String expression, final Scope scope) {
		Objects.requireNonNull(scope, "scope");
		if (expression == null || expression.isBlank()) {
			throw new IllegalArgumentException("driverPath expression must be non-blank");
		}
		final String trimmed = expression.trim();
		final String expanded = expand(trimmed, scope);
		// Forbid traversal after expansion (matches a literal segment so we don't catch '..' inside a
		// filename like 'foo..bar.jar').
		for (final String segment : expanded.split("[/\\\\]")) {
			if ("..".equals(segment)) {
				throw new IllegalArgumentException("driverPath must not contain '..' segments (got: '" + expression + "')");
			}
		}
		return expanded;
	}

	/**
	 * Substitutes every recognized placeholder in {@code expression} according to {@code scope}.
	 * Absolute paths and unsupported tokens are rejected here for connector scope.
	 *
	 * @param expression the trimmed user input.
	 * @param scope      the security scope.
	 * @return the expanded path string (still potentially relative for resource scope).
	 */
	private String expand(final String expression, final Scope scope) {
		if (scope == Scope.CONNECTOR && !startsWithKnownToken(expression)) {
			// Connector-scope expressions must anchor under an operator- or user-owned directory.
			throw new IllegalArgumentException(
				"driverPath in connector scope must start with $INSTALL_DIR or $USER_HOME (got: '" + expression + "')"
			);
		}
		final Matcher matcher = PLACEHOLDER.matcher(expression);
		final StringBuilder out = new StringBuilder();
		while (matcher.find()) {
			final String token = matcher.group();
			final String replacement = resolveToken(token, scope);
			matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(out);
		return out.toString();
	}

	/**
	 * Returns the directory replacement for {@code token}, validating scope rules.
	 *
	 * @param token an upper-snake placeholder including the leading {@code $}.
	 * @param scope the security scope.
	 * @return the absolute directory string used as a replacement.
	 * @throws IllegalArgumentException when the token is unknown or scope-forbidden.
	 */
	private String resolveToken(final String token, final Scope scope) {
		switch (token) {
			case INSTALL_DIR:
				return installDirSupplier.get().toAbsolutePath().normalize().toString();
			case USER_HOME:
				return userHomeSupplier.get().toAbsolutePath().normalize().toString();
			case WORKING_DIR:
				if (scope == Scope.CONNECTOR) {
					throw new IllegalArgumentException("driverPath placeholder " + token + " is not allowed in connector scope");
				}
				return workingDirSupplier.get().toAbsolutePath().normalize().toString();
			default:
				throw new IllegalArgumentException("driverPath uses unknown placeholder " + token);
		}
	}

	/**
	 * @param expression the trimmed user input.
	 * @return {@code true} when the expression starts with one of the known placeholder tokens.
	 */
	private static boolean startsWithKnownToken(final String expression) {
		return expression.startsWith(INSTALL_DIR) || expression.startsWith(USER_HOME) || expression.startsWith(WORKING_DIR);
	}

	/**
	 * Security scope of a {@code driverPath} expression.
	 */
	public enum Scope {
		/** Path coming from a connector header (third-party content, restricted). */
		CONNECTOR,
		/** Path coming from {@code metricshub.yaml} (operator content, unrestricted). */
		RESOURCE
	}
}
