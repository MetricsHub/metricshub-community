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

import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * Snapshot of resources and resource groups stored in metricshub-ui.yaml.
 */
@Data
@Builder
public class UiConfigSnapshotDto {

	@Builder.Default
	private Map<String, Object> resources = new HashMap<>();

	@Builder.Default
	private Map<String, Object> resourceGroups = new HashMap<>();

	/**
	 * Standalone resources present in the running configuration (other YAML files) but not in
	 * metricshub-ui.yaml. They are shown in the UI as read-only and are not editable here.
	 */
	@Builder.Default
	private Map<String, Object> externalResources = new HashMap<>();

	/**
	 * Resource groups (or, for a group also defined in metricshub-ui.yaml, only its extra
	 * resources) present in the running configuration but not in metricshub-ui.yaml. Read-only.
	 */
	@Builder.Default
	private Map<String, Object> externalResourceGroups = new HashMap<>();
}
