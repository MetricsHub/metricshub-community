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
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Summary object appended as the last element of each monitor type list.
 * Provides aggregated statistics so the AI assistant can answer questions
 * like "how many disks are degraded?" without examining every monitor.
 *
 * Serializes with "type": "summary" as the Jackson discriminator.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MonitorTypeSummaryVo implements MonitorTypeItem {

	/** Total number of monitors of this type. */
	private int totalMonitors;

	/**
	 * Aggregated stats for each numeric metric (NumberMetric only).
	 * Key = metric name, Value = { avg, min, max, sum, count }.
	 */
	private Map<String, NumericMetricStatsVo> numericMetrics;

	/**
	 * Distribution of state values for each StateSet metric.
	 * Key = metric name, Value = list of { value, count } entries.
	 * Example: "hw.status" -&gt; [{ "value": "ok", "count": 195 }, { "value": "degraded", "count": 5 }]
	 */
	private Map<String, List<StateSetCountVo>> stateSetMetrics;
}
