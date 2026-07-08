package org.metricshub.web.dto.uiconfig;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
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

import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Data;

/**
 * Request body used to create a host in metricshub-ui.yaml.
 */
@Data
public class AddHostRequestDto {

	private String resourceGroup;

	@NotBlank(message = "Field 'hostId' is required.")
	private String hostId;

	private Map<String, Object> attributes = new HashMap<>();

	private Map<String, Object> protocols = new HashMap<>();

	/**
	 * Connector identifiers to force for this resource (each entry may use MetricsHub prefixes such as {@code +}).
	 */
	private List<String> connectors = new ArrayList<>();

	/**
	 * Variable connector instances keyed by connector id (maps to {@code additionalConnectors} in YAML).
	 */
	private Map<String, UiAdditionalConnectorDto> additionalConnectors = new HashMap<>();

	private String loggerLevel;

	private String outputDirectory;

	private String collectPeriod;

	private Integer discoveryCycle;

	private UiAlertingSystemConfigDto alertingSystem;

	private Boolean sequential;

	private Boolean enableSelfMonitoring;

	private Boolean logFileSourceDetails;

	private Boolean resolveHostnameToFqdn;

	private Set<String> monitorFilters;

	private String jobTimeout;

	private Map<String, Double> metrics = new HashMap<>();

	private List<String> enrichments = new ArrayList<>();

	private String stateSetCompression;
}
