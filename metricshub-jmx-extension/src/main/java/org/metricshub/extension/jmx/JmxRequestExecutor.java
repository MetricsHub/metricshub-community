package org.metricshub.extension.jmx;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub OsCommand Extension
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

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
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

	/**
	 * @param host              JMX host (e.g. "localhost")
	 * @param port              JMX port (e.g. 7199)
	 * @param objectNamePattern ObjectName pattern; must match exactly one MBean
	 * @param attributes        List of attributes to fetch; may be empty/null
	 * @param timeoutSeconds    Seconds to wait for connect; ≤0 → default
	 * @return Map from attribute name → its string value (null if unreadable)
	 * @throws Exception on no match, multiple matches, or missing attribute
	 */
	public Map<String, String> fetchAttributes(
		String host,
		int port,
		String objectNamePattern,
		List<String> attributes,
		long timeoutSeconds
	) throws Exception {
		String url = String.format("service:jmx:rmi:///jndi/rmi://%s:%d/jmxrmi", host, port);

		try (JMXConnector jmxc = JMXConnectorFactory.connect(new JMXServiceURL(url))) {
			MBeanServerConnection mbsc = jmxc.getMBeanServerConnection();

			ObjectName pattern = new ObjectName(objectNamePattern);
			Set<ObjectName> matches = mbsc.queryNames(pattern, null);

			if (matches.isEmpty()) {
				throw new IllegalArgumentException(String.format("No MBeans matched pattern \"%s\"", objectNamePattern));
			}
			if (matches.size() > 1) {
				throw new IllegalArgumentException(
					String.format("Multiple MBeans matched pattern \"%s\": %s", objectNamePattern, matches)
				);
			}

			ObjectName resolved = matches.iterator().next();

			if (attributes == null || attributes.isEmpty()) {
				return Map.of();
			}

			MBeanInfo info = mbsc.getMBeanInfo(resolved);
			Map<String, MBeanAttributeInfo> attrInfoMap = new HashMap<>();
			for (MBeanAttributeInfo ai : info.getAttributes()) {
				attrInfoMap.put(ai.getName(), ai);
			}

			Map<String, String> results = new HashMap<>();
			for (String attr : attributes) {
				MBeanAttributeInfo ai = attrInfoMap.get(attr);
				if (ai == null || !ai.isReadable()) {
					results.put(attr, null);
				} else {
					try {
						Object value = mbsc.getAttribute(resolved, attr);
						results.put(attr, (value == null) ? null : value.toString());
					} catch (Exception e) {
						log.warn("Failed reading attribute \"{}\" from {}: {}", attr, resolved, e.getMessage());
						results.put(attr, null);
					}
				}
			}
			return results;
		} catch (IOException e) {
			log.error("I/O error connecting to JMX {}:{} → {}", host, port, e.getMessage());
			return Map.of();
		}
	}
}
