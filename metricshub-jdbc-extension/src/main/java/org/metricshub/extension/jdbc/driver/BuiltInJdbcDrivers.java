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

import java.util.Map;
import java.util.Optional;
import lombok.NoArgsConstructor;

/**
 * Static URL-prefix to driver-class lookup for the JDBC drivers shipped inside
 * {@code metricshub-jdbc-extension}.
 *
 * <p>Used by callers that have a JDBC URL but no explicit {@code driver} declaration (typically
 * {@code metricshub.yaml} resources without a {@code driver:} block, and the programmable SQL
 * tool). External drivers (Oracle, JTOpen, etc.) are not inferred here: they must always be
 * declared explicitly through {@link org.metricshub.engine.connector.model.identity.DriverInfo}.
 */
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class BuiltInJdbcDrivers {

	private static final Map<String, String> URL_PREFIX_TO_CLASS = Map.of(
		"jdbc:mariadb:",
		"org.mariadb.jdbc.Driver",
		"jdbc:postgresql:",
		"org.postgresql.Driver",
		"jdbc:mysql:",
		"com.mysql.cj.jdbc.Driver",
		"jdbc:h2:",
		"org.h2.Driver"
	);

	/**
	 * Returns the driver class advertised by {@link BuiltInJdbcDriverProvider} whose URL prefix
	 * matches the start of {@code url}.
	 *
	 * @param url a JDBC URL; may be {@code null} or blank.
	 * @return the matching built-in driver class, or {@link Optional#empty()} when {@code url} is
	 *         blank or starts with no known built-in prefix.
	 */
	public static Optional<String> driverClassForUrl(final String url) {
		if (url == null || url.isBlank()) {
			return Optional.empty();
		}
		for (final Map.Entry<String, String> e : URL_PREFIX_TO_CLASS.entrySet()) {
			if (url.startsWith(e.getKey())) {
				return Optional.of(e.getValue());
			}
		}
		return Optional.empty();
	}
}
