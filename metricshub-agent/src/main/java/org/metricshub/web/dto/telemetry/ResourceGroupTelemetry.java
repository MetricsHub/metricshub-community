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
 * Telemetry node representing a resource-group.
 * <p>
 * Holds a typed collection of {@code resources} that belong to this group.
 * </p>
 */
public class ResourceGroupTelemetry extends AbstractBaseTelemetry {

	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	private List<ResourceTelemetry> resources = new ArrayList<>();

	/**
	 * Creates a Resource-Group telemetry node.
	 *
	 * @param name       the group key/name
	 * @param attributes optional group attributes
	 * @param metrics    optional group metrics
	 * @param resources  resources belonging to this group
	 */
	@Builder
	public ResourceGroupTelemetry(
		String name,
		Map<String, String> attributes,
		Map<String, Object> metrics,
		List<ResourceTelemetry> resources
	) {
		super(
			name,
			"resource-group",
			attributes != null ? attributes : new HashMap<>(),
			metrics != null ? metrics : new HashMap<>()
		);
		this.resources = resources != null ? resources : new ArrayList<>();
	}

	@Override
	@JsonInclude(JsonInclude.Include.ALWAYS)
	public Map<String, String> getAttributes() {
		return super.getAttributes();
	}
}
