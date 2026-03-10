package org.metricshub.web.dto.mcp;

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

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Value object representing a single monitor with its attributes, metrics, and text parameters.
 * Null or empty fields are excluded during serialization.
 * Serializes with "type": "monitor" as the Jackson discriminator.
 *
 * <p>For Counter metrics (metricType == "Counter"), the key is renamed to
 * "rate(originalKey)" and the value is the computed rate (units per second)
 * rather than the raw cumulative value. If the rate is null (first collect
 * cycle), the Counter metric is omitted entirely.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MonitorVo implements MonitorTypeItem {

	/**
	 * Key-value pairs of monitor attributes (e.g., device name, mount point).
	 */
	private Map<String, String> attributes;

	/**
	 * Key-value pairs of monitor metrics.
	 * Values can be numeric (Double) or state strings (e.g., "ok", "degraded", "failed")
	 * for StateSet metrics following OpenTelemetry conventions.
	 */
	private Map<String, Object> metrics;

	/**
	 * Key-value pairs of text parameters associated with the monitor.
	 */
	private Map<String, String> textParams;
}
