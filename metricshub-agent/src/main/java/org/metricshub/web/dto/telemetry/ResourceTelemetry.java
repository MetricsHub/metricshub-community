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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
/**
 * Telemetry node representing a resource.
 * <p>
 * Holds a typed collection of {@code connectors} configured for this resource.
 * </p>
 */
public class ResourceTelemetry extends AbstractBaseTelemetry {

	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	private List<ConnectorTelemetry> connectors = new ArrayList<>();

	/**
	 * Creates a Resource telemetry node.
	 *
	 * @param name       the resource key/name
	 * @param attributes optional resource attributes (e.g., host metadata)
	 * @param metrics    optional resource metrics (flattened values)
	 * @param connectors connectors configured for this resource
	 */
	@Builder
	public ResourceTelemetry(
		String name,
		Map<String, String> attributes,
		Map<String, Object> metrics,
		List<ConnectorTelemetry> connectors
	) {
		super(
			name,
			"resource",
			attributes != null ? attributes : new HashMap<>(),
			metrics != null ? metrics : new HashMap<>()
		);
		this.connectors = connectors != null ? connectors : new ArrayList<>();
	}

	@Override
	@JsonInclude(JsonInclude.Include.ALWAYS)
	public Map<String, String> getAttributes() {
		return super.getAttributes();
	}
}
