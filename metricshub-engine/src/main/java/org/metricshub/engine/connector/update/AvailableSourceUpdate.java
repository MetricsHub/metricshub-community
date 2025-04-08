package org.metricshub.engine.connector.update;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.monitor.MonitorJob;
import org.metricshub.engine.connector.model.monitor.SimpleMonitorJob;
import org.metricshub.engine.connector.model.monitor.StandardMonitorJob;
import org.metricshub.engine.connector.model.monitor.task.AbstractCollect;
import org.metricshub.engine.connector.model.monitor.task.Discovery;
import org.metricshub.engine.connector.model.monitor.task.Simple;
import org.metricshub.engine.connector.model.monitor.task.source.Source;

/**
 * Implementation of {@link AbstractConnectorUpdateChain} for updating available sources in the connector.
 */
public class AvailableSourceUpdate extends AbstractConnectorUpdateChain {

	@Override
	void doUpdate(Connector connector) { // NOSONAR
		Set<Class<? extends Source>> sourceTypes = new HashSet<>();

		for (MonitorJob monitor : connector.getMonitors().values()) {
			if (monitor instanceof StandardMonitorJob standardMonitorJob) {
				processDiscoverySources(sourceTypes, standardMonitorJob.getDiscovery());

				processCollectSources(sourceTypes, standardMonitorJob.getCollect());
			}

			if (monitor instanceof SimpleMonitorJob simpleMonitorJob) {
				final Simple simple = simpleMonitorJob.getSimple();
				processSimpleSources(sourceTypes, simple);
			}

			// Add beforeAll source types to sourcesTypes
			final Map<String, Source> beforeAll = connector.getBeforeAll();
			if (beforeAll != null) {
				beforeAll.values().forEach(source -> sourceTypes.add(source.getClass()));
			}

			// Add afterAll source types to sourcesTypes
			final Map<String, Source> afterAll = connector.getAfterAll();
			if (afterAll != null) {
				afterAll.values().forEach(source -> sourceTypes.add(source.getClass()));
			}

			connector.setSourceTypes(sourceTypes);
		}
	}

	/**
	 * Process all at once job sources
	 *
	 * @param sourceTypes Types of the sources to be updated
	 * @param simple {@link Simple} source job
	 */
	private void processSimpleSources(final Set<Class<? extends Source>> sourceTypes, final Simple simple) {
		if (simple != null) {
			simple.getSources().values().forEach(source -> sourceTypes.add(source.getClass()));
		}
	}

	/**
	 * Process collect job sources
	 *
	 * @param sourceTypes Types of the sources to be updated
	 * @param collect {@link AbstractCollect} source job
	 */
	private void processCollectSources(final Set<Class<? extends Source>> sourceTypes, final AbstractCollect collect) {
		if (collect != null) {
			collect.getSources().values().forEach(source -> sourceTypes.add(source.getClass()));
		}
	}

	/**
	 * Process discovery job sources
	 *
	 * @param sourceTypes Types of the sources to be updated
	 * @param discovery {@link Discovery} source job
	 */
	private void processDiscoverySources(final Set<Class<? extends Source>> sourceTypes, final Discovery discovery) {
		if (discovery != null) {
			discovery.getSources().values().forEach(source -> sourceTypes.add(source.getClass()));
		}
	}
}
