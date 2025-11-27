package org.metricshub.web.dto.telemetry;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
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

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
/**
 * Base DTO for all telemetry nodes exposed by the Explorer API.
 * <p>
 * All nodes have a display {@code name}, a {@code type} discriminator and two
 * optional
 * collections for {@code attributes} and {@code metrics}.
 * </p>
 */
public abstract class AbstractBaseTelemetry {

	/** Human-readable name of the node. */
	protected String name;
	/**
	 * Node type discriminator (e.g., agent, resource-group, resource, connector,
	 * monitor, instance).
	 */
	protected String type;

	@JsonInclude(JsonInclude.Include.ALWAYS)
	protected Map<String, String> attributes = new HashMap<>();

	@JsonInclude(JsonInclude.Include.ALWAYS)
	protected Map<String, Object> metrics = new HashMap<>();
}
