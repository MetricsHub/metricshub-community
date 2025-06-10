package org.metricshub.extension.jmx;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub JMX Extension
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
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.connector.model.monitor.task.source.JmxSource;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * Processes a JmxSource by delegating attribute reads to JmxRequestExecutor.
 */
@Slf4j
@RequiredArgsConstructor
public class JmxSourceProcessor {

	// Executor instance for JMX requests
	@NonNull
	private final JmxRequestExecutor jmxExecutor;

	public SourceTable process(JmxSource src, TelemetryManager telemetryManager) {
		final JmxConfiguration jmxConfig = (JmxConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(JmxConfiguration.class);

		String pattern = src.getMbean();
		List<String> attributes = src.getAttributes();
		List<String> keysAsAttributes = src.getKeysAsAttributes();

		if (attributes == null) {
			attributes = new ArrayList<>();
		}

		if (keysAsAttributes == null) {
			keysAsAttributes = new ArrayList<>();
		}

		List<List<String>> rows = new ArrayList<>();

		try {
			// Fetch attributes; this also validates exactly one match
			rows = jmxExecutor.fetchBeanInfo(jmxConfig, pattern, attributes, keysAsAttributes);
		} catch (Exception e) {
			log.error(
				"Hostname {} - Error processing JMX source for pattern '{}': {}",
				jmxConfig.getHostname(),
				pattern,
				e.getMessage()
			);
		}

		SourceTable table = new SourceTable();
		table.setTable(rows);
		return table;
	}
}
