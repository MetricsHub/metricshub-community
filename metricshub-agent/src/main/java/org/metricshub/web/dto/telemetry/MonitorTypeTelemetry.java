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
 * Telemetry node representing a monitor type under a connector (e.g., cpu,
 * mem).
 * <p>
 * Holds a typed collection of monitor {@code instances} discovered for this
 * type.
 * </p>
 */
public class MonitorTypeTelemetry extends AbstractBaseTelemetry {

	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	private List<InstanceTelemetry> instances = new ArrayList<>();

	/**
	 * Creates a Monitor-Type telemetry node.
	 *
	 * @param name       the monitor type name (e.g., cpu, mem)
	 * @param attributes optional type-level attributes
	 * @param metrics    optional aggregated metrics at the type level
	 * @param instances  monitor instances discovered for this type
	 */
	@Builder
	public MonitorTypeTelemetry(
		String name,
		Map<String, String> attributes,
		Map<String, Object> metrics,
		List<InstanceTelemetry> instances
	) {
		super(
			name,
			"monitor",
			attributes != null ? attributes : new HashMap<>(),
			metrics != null ? metrics : new HashMap<>()
		);
		this.instances = instances != null ? instances : new ArrayList<>();
	}
}
