package org.metricshub.engine.connector.update;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2024 Sentry Software
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

import java.util.Map;
import java.util.regex.Pattern;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.monitor.MonitorJob;
import org.metricshub.engine.connector.model.monitor.SimpleMonitorJob;
import org.metricshub.engine.connector.model.monitor.StandardMonitorJob;
import org.metricshub.engine.connector.model.monitor.task.AbstractCollect;
import org.metricshub.engine.connector.model.monitor.task.Discovery;
import org.metricshub.engine.connector.model.monitor.task.Simple;
import org.metricshub.engine.connector.model.monitor.task.source.Source;

/**
 * Implementation of {@link SourceConnectorUpdateChain} for updating monitor task source dependencies in the connector.
 */
public class MonitorTaskSourceDepUpdate extends SourceConnectorUpdateChain {

	@Override
	void doUpdate(Connector connector) {
		for (Map.Entry<String, MonitorJob> entry : connector.getMonitors().entrySet()) {
			final String jobName = entry.getKey();
			final MonitorJob job = entry.getValue();

			if (job instanceof StandardMonitorJob standardMonitorJob) {
				final Discovery discovery = standardMonitorJob.getDiscovery();
				if (discovery != null) {
					final Map<String, Source> sources = discovery.getSources();
					discovery.setSourceDep(
						updateSourceDependency(
							sources,
							Pattern.compile(
								String.format(
									"\\s*(\\$\\{source::((?i)monitors)\\.%s\\.((?i)discovery\\.sources)\\.(%s)\\})\\s*",
									Pattern.quote(jobName),
									getSourceIdentifiersRegex(sources)
								),
								Pattern.MULTILINE
							),
							4
						)
					);
				}

				final AbstractCollect collect = standardMonitorJob.getCollect();

				if (collect != null) {
					final Map<String, Source> collectSources = collect.getSources();
					collect.setSourceDep(
						updateSourceDependency(
							collectSources,
							Pattern.compile(
								String.format(
									"\\s*(\\$\\{source::((?i)monitors)\\.%s\\.((?i)collect\\.sources)\\.(%s)\\})\\s*",
									Pattern.quote(jobName),
									getSourceIdentifiersRegex(collectSources)
								),
								Pattern.MULTILINE
							),
							4
						)
					);
				}
			}

			if (job instanceof SimpleMonitorJob simpleMonitorJob) {
				final Simple simple = simpleMonitorJob.getSimple();
				if (simple != null) {
					final Map<String, Source> simpleSources = simple.getSources();
					simple.setSourceDep(
						updateSourceDependency(
							simpleSources,
							Pattern.compile(
								String.format(
									"\\s*(\\$\\{source::((?i)monitors)\\.%s\\.((?i)simple\\.sources)\\.(%s)\\})\\s*",
									Pattern.quote(jobName),
									getSourceIdentifiersRegex(simpleSources)
								),
								Pattern.MULTILINE
							),
							4
						)
					);
				}
			}
		}
	}
}
