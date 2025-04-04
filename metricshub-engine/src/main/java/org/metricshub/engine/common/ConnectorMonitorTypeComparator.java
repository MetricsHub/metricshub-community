package org.metricshub.engine.common;

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

import java.util.Comparator;
import org.metricshub.engine.common.helpers.KnownMonitorType;
import org.metricshub.engine.connector.model.Connector;

/**
 * Comparator for sorting {@link Connector} instances based on the monitor type.
 * <p>
 * This comparator prioritizes connectors with monitor types HOST and ENCLOSURE.
 * Connectors with these monitor types will be ordered first, and then the remaining connectors
 * will be sorted based on their compiled filenames.
 * </p>
 */
public class ConnectorMonitorTypeComparator implements Comparator<Connector> {

	@Override
	public int compare(Connector firstConnector, Connector secondConnector) {
		if (
			firstConnector.getMonitors().get(KnownMonitorType.HOST.getKey()) != null &&
			secondConnector.getMonitors().get(KnownMonitorType.ENCLOSURE.getKey()) != null
		) {
			return 1;
		} else if (
			firstConnector.getMonitors().get(KnownMonitorType.ENCLOSURE.getKey()) != null &&
			secondConnector.getMonitors().get(KnownMonitorType.HOST.getKey()) != null
		) {
			return -1;
		} else {
			return firstConnector
				.getConnectorIdentity()
				.getCompiledFilename()
				.compareTo(secondConnector.getConnectorIdentity().getCompiledFilename());
		}
	}
}
