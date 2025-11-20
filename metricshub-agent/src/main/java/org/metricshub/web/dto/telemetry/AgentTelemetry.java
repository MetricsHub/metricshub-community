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
 * Telemetry root node representing the Agent.
 * <p>
 * Contains typed collections for {@code resource-groups} and top-level
 * {@code resources}.
 * </p>
 */
public class AgentTelemetry extends AbstractBaseTelemetry {

	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	private List<ResourceGroupTelemetry> resourceGroups = new ArrayList<>();

	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	private List<ResourceTelemetry> resources = new ArrayList<>();

	/**
	 * Creates an Agent telemetry node.
	 *
	 * @param name           the agent display name
	 * @param attributes     optional agent attributes
	 * @param metrics        optional agent metrics
	 * @param resourceGroups resource-group children
	 * @param resources      top-level resources
	 */
	@Builder
	public AgentTelemetry(
		String name,
		Map<String, String> attributes,
		Map<String, Object> metrics,
		List<ResourceGroupTelemetry> resourceGroups,
		List<ResourceTelemetry> resources
	) {
		super(
			name,
			"agent",
			attributes != null ? attributes : new HashMap<>(),
			metrics != null ? metrics : new HashMap<>()
		);
		this.resourceGroups = resourceGroups != null ? resourceGroups : new ArrayList<>();
		this.resources = resources != null ? resources : new ArrayList<>();
	}

	@Override
	@JsonInclude(JsonInclude.Include.ALWAYS)
	public Map<String, String> getAttributes() {
		return super.getAttributes();
	}

	@Override
	@JsonInclude(JsonInclude.Include.ALWAYS)
	public Map<String, Object> getMetrics() {
		return super.getMetrics();
	}
}
