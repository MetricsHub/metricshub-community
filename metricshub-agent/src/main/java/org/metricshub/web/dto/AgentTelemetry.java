package org.metricshub.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic DTO describing a node in the agent telemetry hierarchy.
 * Exposing metrics, attributes and typed child collections.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTelemetry {

	private String name;

	/**
	 * One of: "resource", "connector", "monitor", "resource-group", "agent",
	 * plus type nodes that can hold typed children.
	 */
	private String type;

	@Builder.Default
	private Map<String, String> attributes = new HashMap<>();

	@Builder.Default
	private Map<String, Object> metrics = new HashMap<>();

	// Typed children relationships (only one applicable per node kind starting from
	// resource-groups level)
	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	@Builder.Default
	private List<AgentTelemetry> resourceGroups = new ArrayList<>();

	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	@Builder.Default
	private List<AgentTelemetry> resources = new ArrayList<>();

	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	@Builder.Default
	private List<AgentTelemetry> connectors = new ArrayList<>();

	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	@Builder.Default
	private List<AgentTelemetry> monitors = new ArrayList<>();

	// Children of monitor-type nodes are called "instances"
	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	@Builder.Default
	private List<AgentTelemetry> instances = new ArrayList<>();
}
