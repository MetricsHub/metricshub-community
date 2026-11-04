package org.metricshub.engine.connector.model.identity;

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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Connector-side {@code jdbc} block. Wraps a {@link DriverInfo} under a {@code driver} subkey
 * so that the connector YAML mirrors the resource YAML shape:
 *
 * <pre>
 * connector:
 *   jdbc:
 *     driver:
 *       className: com.acme.Driver
 *       jarPath:   $APP_DIR/extensions/jdbc/acme.jar
 * </pre>
 *
 * <p>The same {@link DriverInfo} type is reused on the resource side under {@code jdbc.driver}.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JdbcInfo implements Serializable {

	private static final long serialVersionUID = 1L;

	/** Driver coordinates declared by the connector. */
	private DriverInfo driver;

	/**
	 * Jackson constructor.
	 *
	 * @param driver the driver coordinates; may be {@code null} when the connector declares
	 *               an empty {@code jdbc} block.
	 */
	@JsonCreator
	public static JdbcInfo create(@JsonProperty("driver") final DriverInfo driver) {
		return new JdbcInfo(driver);
	}
}
