package org.metricshub.web.dto;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic DTO describing a node in the agent telemetry hierarchy.
 * Exposing metrics, attributes and children
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTelemetry {

	private String name;

	/**
	 * One of: "resource", "connector", "monitor", "resource-group", "agent",
	 * "resource-groups", "resources", "connectors", "monitors".
	 */
	private String type;

	@Builder.Default
	private Map<String, String> attributes = new HashMap<>();

	@Builder.Default
	private Map<String, Object> metrics = new HashMap<>();

	@Builder.Default
	private List<AgentTelemetry> children = new ArrayList<>();
}
