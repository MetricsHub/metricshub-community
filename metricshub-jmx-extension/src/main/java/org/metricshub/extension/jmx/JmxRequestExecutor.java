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

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.ThreadHelper;

/**
 * Helper that connects to JMX, resolves exactly one MBean, and reads zero or more attributes in one call.
 */
@Slf4j
public class JmxRequestExecutor {

	/**
	 * Fetches information about JMX beans matching the specified object name pattern and attributes.<br>
	 * This entry point is used to run JMX requests in a separate thread, allowing for timeout handling.
	 *
	 * @param jmxConfiguration  the JMX configuration containing hostname, port, username, and password
	 * @param objectNamePattern the pattern for matching MBean object names
	 * @param attributes        the list of attributes to fetch from the MBeans
	 * @param keyProperties     the list of key properties to include in the result set, used for identifying MBeans uniquely
	 * @return a list of lists, where each inner list contains the values of the key attributes followed by the requested attributes
	 * @throws Exception if an error occurs while connecting to the JMX server or fetching attributes
	 */
	@WithSpan("JMX Fetch MBean")
	public List<List<String>> fetchMBean(
		@SpanAttribute("jmx.config") final JmxConfiguration jmxConfiguration,
		@SpanAttribute("jmx.objectName") final String objectNamePattern,
		@SpanAttribute("jmx.attributes") final Iterable<String> attributes,
		@SpanAttribute("jmx.keyProperties") final Collection<String> keyProperties
	) throws Exception {
		return ThreadHelper.execute(
			() -> runJmxRequest(jmxConfiguration, objectNamePattern, attributes, keyProperties),
			jmxConfiguration.getTimeout()
		);
	}

	/**
	 * Executes a JMX request to fetch information about MBeans matching the specified object name pattern and attributes.
	 * @param jmxConfiguration  the JMX configuration containing hostname, port, username, and password
	 * @param objectNamePattern the pattern for matching MBean object names
	 * @param attributes        the list of attributes to fetch from the MBeans
	 * @param keyProperties     the list of key properties to include in the result set, used for identifying MBeans uniquely
	 * @return
	 */
	private List<List<String>> runJmxRequest(
		final JmxConfiguration jmxConfiguration,
		final String objectNamePattern,
		final Iterable<String> attributes,
		final Collection<String> keyProperties
	) {
		final List<List<String>> results = new ArrayList<>();

		final String hostname = jmxConfiguration.getHostname();

		final String url = buildJmxRmiUrl(hostname, jmxConfiguration.getPort());
		log.debug("Hostname {} - Fetching JMX bean info for {} at {}", hostname, objectNamePattern, url);
		try (var jmxConnector = connect(hostname, url, jmxConfiguration.getUsername(), jmxConfiguration.getPassword())) {
			final var mBeanServerConnection = jmxConnector.getMBeanServerConnection();

			final var objectName = new ObjectName(objectNamePattern);
			final Set<ObjectName> matches = mBeanServerConnection.queryNames(objectName, null);

			if (matches.isEmpty()) {
				return results;
			}

			for (ObjectName matchedObjectName : matches) {
				final List<String> keyPropertyValues = keyProperties
					.stream()
					.map(matchedObjectName::getKeyProperty)
					.map(value -> value == null ? EMPTY : value)
					.toList();

				final List<String> row = new ArrayList<>(keyPropertyValues);

				// Build the row with the requested attributes
				attributes.forEach((String requestedAttribute) ->
					row.add(getAttributeValue(hostname, mBeanServerConnection, matchedObjectName, requestedAttribute).toString())
				);

				results.add(row);
			}

			return results;
		} catch (Exception e) {
			log.debug("Hostname {} - Error connecting to JMX. {} → ", hostname, url, e);
			return results;
		}
	}

	/**
	 * Fetches the value of a specific attribute for a given MBean.
	 *
	 * @param hostname              the hostname of the JMX server
	 * @param mBeanServerConnection the MBean server connection
	 * @param objectName            the ObjectName of the MBean
	 * @param requestedAttribute    the attribute key to fetch from the MBean
	 *
	 * @return the value of the requested attribute, or EMPTY if an error occurs
	 */
	private Object getAttributeValue(
		final String hostname,
		final MBeanServerConnection mBeanServerConnection,
		final ObjectName objectName,
		final String requestedAttribute
	) {
		try {
			return mBeanServerConnection.getAttribute(objectName, requestedAttribute);
		} catch (Exception e) {
			log.debug(
				"Hostname {} - Error fetching attribute {} for MBean {}. Exception: ",
				hostname,
				requestedAttribute,
				objectName,
				e
			);
		}
		return EMPTY;
	}

	/**
	 * Checks if a JMX connection can be established with the given configuration.
	 *
	 * @param configuration the JMX configuration containing hostname, port, username, and password
	 * @return true if the connection is successful, false otherwise
	 * @throws Exception if an error occurs while connecting to the JMX server or if the connection times out
	 */
	@WithSpan("JMX Connection Check")
	public boolean checkConnection(@SpanAttribute("jmx.config") JmxConfiguration configuration) throws Exception {
		return ThreadHelper.execute(() -> runConnectionCheck(configuration), configuration.getTimeout());
	}

	/**
	 * Runs a connection check to verify if the JMX server is reachable and the
	 * credentials are correct.
	 *
	 * @param configuration the JMX configuration containing hostname, port,
	 *                      username, and password
	 * @return true if the connection is successful, false otherwise
	 */
	private boolean runConnectionCheck(final JmxConfiguration configuration) {
		final String hostname = configuration.getHostname();
		final int port = configuration.getPort();

		final var url = buildJmxRmiUrl(hostname, port);

		try (JMXConnector jmxConnector = connect(hostname, url, configuration.getUsername(), configuration.getPassword())) {
			return true;
		} catch (Exception e) {
			log.debug("Hostname {} - JMX health check failed. {} → ", hostname, url, e);
			return false;
		}
	}

	/**
	 * Builds the JMX RMI URL based on the hostname and port.
	 *
	 * @param hostname the hostname of the JMX server
	 * @param port     the port on which the JMX server is listening
	 * @return the formatted JMX RMI URL
	 */
	private static String buildJmxRmiUrl(final String hostname, final int port) {
		return String.format("service:jmx:rmi:///jndi/rmi://%s:%d/jmxrmi", hostname, port);
	}

	/**
	 * Connects to the JMX server using the provided hostname, URL, username, and password.
	 *
	 * @param hostname the hostname of the JMX server
	 * @param url      the JMX service URL
	 * @param username the username for authentication (can be null)
	 * @param password the password for authentication (can be null)
	 * @return a JMXConnector instance if the connection is successful
	 * @throws Exception if an error occurs while connecting to the JMX server
	 */
	private static JMXConnector connect(
		final String hostname,
		final String url,
		final String username,
		final char[] password
	) throws Exception {
		log.debug("Hostname {} - Connecting to JMX at {}.", hostname, url);

		final Map<String, String[]> env = new HashMap<>();

		if (username != null && password != null) {
			env.put(JMXConnector.CREDENTIALS, new String[] { username, String.valueOf(password) });
		}

		return JMXConnectorFactory.connect(new JMXServiceURL(url), env);
	}
}
