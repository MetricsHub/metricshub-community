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

import java.util.Objects;

/**
 * Per-call selection of the JDBC driver to use for a single SQL execution.
 *
 * <p>Resolved once (typically in {@code JdbcExtension.processSource}/{@code processCriterion}) by
 * applying the resource-then-connector priority over the available {@code JdbcInfo} blocks and
 * expanding the {@code driverPath} expression with {@link JdbcPathExpression}. The expanded
 * {@code explicitJarPath} is then carried verbatim down the call chain to {@code JdbcClient}, which
 * uses it to look up a {@link LoadedDriver} from the registry and call
 * {@link java.sql.Driver#connect(String, java.util.Properties)} directly — bypassing
 * {@link java.sql.DriverManager} so two resources can simultaneously use distinct JARs that ship
 * the same driver class.
 *
 * @param driverClass     fully-qualified driver class; never {@code null} or blank.
 * @param explicitJarPath already-expanded driver path expression (may contain glob meta-characters,
 *                        may be {@code null} when the operator-default drivers directory should be
 *                        scanned).
 */
public record JdbcDriverSelection(String driverClass, String explicitJarPath) {
	/**
	 * Compact constructor: enforces that {@code driverClass} is non-blank.
	 */
	public JdbcDriverSelection {
		Objects.requireNonNull(driverClass, "driverClass");
		if (driverClass.isBlank()) {
			throw new IllegalArgumentException("driverClass must be non-blank");
		}
	}
}
