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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.EMPTY;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import lombok.extern.slf4j.Slf4j;

/**
 * Helper that connects to JMX, resolves exactly one MBean, and reads zero or more attributes in one call.
 */
@Slf4j
public class JmxRequestExecutor {

	public List<List<String>> fetchBeanInfo(
		JmxConfiguration jmxConfiguration,
		String objectNamePattern,
		List<String> attributes,
		List<String> keyAttributes
	) throws Exception {
		final List<List<String>> results = new ArrayList<>();
		String host = jmxConfiguration.getHostname();
		int port = jmxConfiguration.getPort();
		String url = String.format("service:jmx:rmi:///jndi/rmi://%s:%d/jmxrmi", host, port);
		try (JMXConnector jmxc = JMXConnectorFactory.connect(new JMXServiceURL(url))) {
			MBeanServerConnection mbsc = jmxc.getMBeanServerConnection();

			ObjectName pattern = new ObjectName(objectNamePattern);
			Set<ObjectName> matches = mbsc.queryNames(pattern, null);

			if (matches.isEmpty()) {
				return results;
			}

			for (ObjectName objectName : matches) {
				List<String> keyAttributesValues = keyAttributes
					.stream()
					.map(objectName::getKeyProperty)
					.map(value -> value == null ? EMPTY : value)
					.toList();

				List<String> row = new ArrayList<>(keyAttributesValues);

				attributes.forEach(requestedAttribute -> {
					Object value = null;
					try {
						value = mbsc.getAttribute(objectName, requestedAttribute);
					} catch (Exception e) {
						log.error(
							"Hostname {} - Error fetching attribute {} for MBean {}: {}",
							host,
							requestedAttribute,
							objectName,
							e.getMessage()
						);
					}
					row.add(value == null ? EMPTY : value.toString());
				});
				results.add(row);
			}

			return results;
		} catch (IOException e) {
			log.error("Hostname {} - I/O error connecting to JMX:{} → {}", host, port, e.getMessage());
			return results;
		}
	}
}
