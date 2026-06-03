package org.metricshub.engine.connector.model.identity;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Declares which JDBC driver a connector (or a resource override) needs at runtime.
 *
 * <p>Two fields, intentionally minimal:
 *
 * <ul>
 *   <li>{@link #className} — required. Fully-qualified {@link java.sql.Driver} implementation
 *       class. Doubles as the primary cache key on the registry side.</li>
 *   <li>{@link #jarPath} — optional. Path expression pointing at a driver JAR file. May use
 *       placeholders such as {@code $INSTALL_DIR} or {@code $USER_HOME} (and, resource-scope only,
 *       {@code $WORKING_DIR}) and {@code glob:} patterns. When {@code null}, the operator-default
 *       drivers directory is scanned for a JAR exposing {@link #className}.</li>
 * </ul>
 *
 * <p>The parser accepts {@link #jarPath} syntactically; placeholder expansion and the
 * connector-vs-resource security boundary are enforced at resolution time by the JDBC extension.
 * Empty / blank values are normalized to {@code null}.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DriverInfo implements Serializable {

	private static final long serialVersionUID = 1L;

	/** Fully-qualified {@link java.sql.Driver} implementation class. */
	private String className;

	/** Optional path expression pointing at a driver JAR file. May contain placeholders. */
	private String jarPath;

	/**
	 * Jackson constructor with parse-time validation.
	 *
	 * @param className fully-qualified driver class (required, non-blank).
	 * @param jarPath   optional path expression pointing at the driver JAR; placeholders are
	 *                  accepted as-is and resolved at lookup. Blank values are normalised to
	 *                  {@code null}.
	 * @throws JsonMappingException when {@code className} is missing or blank.
	 */
	@JsonCreator
	public static DriverInfo create(
		@JsonProperty("className") final String className,
		@JsonProperty("jarPath") final String jarPath
	) throws JsonMappingException {
		if (className == null || className.isBlank()) {
			throw new JsonMappingException(null, "jdbc.driver.className is required and must be non-blank.");
		}
		final String normalizedPath = (jarPath == null || jarPath.isBlank()) ? null : jarPath.trim();
		return new DriverInfo(className.trim(), normalizedPath);
	}
}
