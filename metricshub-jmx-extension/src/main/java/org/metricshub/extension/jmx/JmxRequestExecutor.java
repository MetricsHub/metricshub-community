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
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.ThreadHelper;
import org.metricshub.engine.extension.recorder.AbstractRecorder;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.RequestOutcome;
import org.metricshub.engine.telemetry.RequestStats;

/**
 * Helper that connects to JMX, resolves exactly one MBean, and reads zero or more attributes in one call.
 */
@Slf4j
public class JmxRequestExecutor {

	/**
	 * The result of one request attempt, computed by the worker thread, and its outcome, recorded by the thread
	 * that waits for it.
	 *
	 * @param <T>     the type of the result
	 * @param value   the result returned to the caller
	 * @param outcome the outcome of the attempt, or {@code null} when nothing was sent
	 */
	private record JmxAttempt<T>(T value, RequestOutcome outcome) {}

	/**
	 * Fetches information about JMX beans matching the specified object name pattern and attributes.<br>
	 * This entry point is used to run JMX requests in a separate thread, allowing for timeout handling.
	 *
	 * @param jmxConfiguration      the JMX configuration containing hostname, port, username, and password
	 * @param objectNamePattern     the pattern for matching MBean object names
	 * @param attributes            the list of attributes to fetch from the MBeans
	 * @param keyProperties         the list of key properties to include in the result set, used for identifying MBeans uniquely
	 * @param requestStats          the request counters of the monitored resource, or {@code null} to record nothing
	 * @param recordOutputDirectory the output directory for recording (nullable; when set, the exchange is recorded)
	 * @return a list of lists, where each inner list contains the values of the key attributes followed by the requested attributes
	 * @throws Exception if an error occurs while connecting to the JMX server or fetching attributes
	 */
	@WithSpan("JMX Fetch MBean")
	public List<List<String>> fetchMBean(
		@SpanAttribute("jmx.config") final JmxConfiguration jmxConfiguration,
		@SpanAttribute("jmx.objectName") final String objectNamePattern,
		@SpanAttribute("jmx.attributes") final Iterable<String> attributes,
		@SpanAttribute("jmx.keyProperties") final Collection<String> keyProperties,
		final RequestStats requestStats,
		final String recordOutputDirectory
	) throws Exception {
		final long timeout = jmxConfiguration.getTimeout();
		final ObjectName objectName = parseObjectName(jmxConfiguration.getHostname(), objectNamePattern);
		final List<List<String>> result;
		if (objectName == null) {
			// Nothing is sent for an invalid pattern
			result = new ArrayList<>();
		} else {
			result = executeAndRecord(
				() -> runJmxRequest(jmxConfiguration, objectName, attributes, keyProperties),
				timeout,
				requestStats
			);
		}

		// Record the JMX exchange if recording is enabled
		if (recordOutputDirectory != null && !recordOutputDirectory.isBlank()) {
			final List<String> attrList = new ArrayList<>();
			if (attributes != null) {
				attributes.forEach(attrList::add);
			}
			final List<String> keyPropList = keyProperties != null ? new ArrayList<>(keyProperties) : List.of();
			final String csv = SourceTable.tableToCsv(result, AbstractRecorder.CSV_SEPARATOR, true);
			JmxRecorder.getInstance(recordOutputDirectory).record(objectNamePattern, attrList, keyPropList, csv);
		}

		return result;
	}

	/**
	 * Parses the object name pattern locally, before anything is sent.
	 *
	 * @param hostname          the hostname of the JMX server, for the logs
	 * @param objectNamePattern the pattern for matching MBean object names
	 * @return the {@link ObjectName}, or {@code null} when the pattern is missing or malformed
	 */
	private static ObjectName parseObjectName(final String hostname, final String objectNamePattern) {
		if (objectNamePattern == null) {
			log.debug("Hostname {} - No JMX object name pattern.", hostname);
			return null;
		}
		try {
			return new ObjectName(objectNamePattern);
		} catch (MalformedObjectNameException e) {
			log.debug("Hostname {} - Invalid JMX object name pattern {}. → ", hostname, objectNamePattern, e);
			return null;
		}
	}

	/**
	 * Runs one request attempt with a timeout and records its outcome in the calling thread, so that an attempt
	 * that completes after its timeout records nothing more.
	 *
	 * @param <T>          the type of the result
	 * @param request      the attempt, run in a worker thread
	 * @param timeout      the timeout of the attempt, in seconds
	 * @param requestStats the request counters of the monitored resource, or {@code null} to record nothing
	 * @return the result of the attempt
	 * @throws Exception the {@link ThreadHelper} exception when the attempt timed out, was interrupted or crashed
	 */
	private static <T> T executeAndRecord(
		final Callable<JmxAttempt<T>> request,
		final long timeout,
		final RequestStats requestStats
	) throws Exception {
		// A thread already interrupted (an expired deadline of its job) fails before sending anything
		final boolean interruptedBefore = Thread.currentThread().isInterrupted();
		final JmxAttempt<T> attempt;
		try {
			attempt = ThreadHelper.execute(request, timeout);
		} catch (Exception e) {
			// TimeoutException and InterruptedException count as timeout, an ExecutionException as failed
			final RequestOutcome outcome = RequestOutcome.of(e);
			if (!(interruptedBefore && outcome == RequestOutcome.TIMEOUT)) {
				record(requestStats, outcome);
			}
			throw e;
		}
		record(requestStats, attempt.outcome());
		return attempt.value();
	}

	/**
	 * Records the outcome of an attempt under the {@link JmxExtension#IDENTIFIER} label.
	 *
	 * @param requestStats the request counters, or {@code null} to record nothing
	 * @param outcome      the outcome, or {@code null} when nothing was sent
	 */
	private static void record(final RequestStats requestStats, final RequestOutcome outcome) {
		if (requestStats != null && outcome != null) {
			requestStats.record(JmxExtension.IDENTIFIER, outcome);
		}
	}

	/**
	 * Classifies the exception of an attempt.
	 *
	 * @param e the exception thrown while connecting, querying or closing
	 * @return {@code null} for an invalid service URL (nothing was sent), the {@link RequestOutcome} of the exception
	 *         otherwise
	 */
	private static RequestOutcome outcomeOf(final Exception e) {
		if (e instanceof MalformedURLException) {
			return null;
		}
		return RequestOutcome.of(e);
	}

	/**
	 * Executes a JMX request to fetch information about MBeans matching the specified object name pattern and attributes.
	 * @param jmxConfiguration the JMX configuration containing hostname, port, username, and password
	 * @param objectName       the pattern for matching MBean object names
	 * @param attributes       the list of attributes to fetch from the MBeans
	 * @param keyProperties    the list of key properties to include in the result set, used for identifying MBeans uniquely
	 * @return the rows (a list of lists, where each inner list contains the values of the key attributes followed by
	 *         the requested attributes, partial on error) and the outcome of the attempt
	 */
	private JmxAttempt<List<List<String>>> runJmxRequest(
		final JmxConfiguration jmxConfiguration,
		final ObjectName objectName,
		final Iterable<String> attributes,
		final Collection<String> keyProperties
	) {
		final List<List<String>> results = new ArrayList<>();
		RequestOutcome outcome = RequestOutcome.COMPLETED;

		final String hostname = jmxConfiguration.getHostname();

		final String url = buildJmxRmiUrl(hostname, jmxConfiguration.getPort());
		log.debug("Hostname {} - Fetching JMX bean info for {} at {}", hostname, objectName, url);
		try (var jmxConnector = connect(hostname, url, jmxConfiguration.getUsername(), jmxConfiguration.getPassword())) {
			final var mBeanServerConnection = jmxConnector.getMBeanServerConnection();

			final Set<ObjectName> matches = mBeanServerConnection.queryNames(objectName, null);

			for (ObjectName matchedObjectName : matches) {
				final List<String> keyPropertyValues = keyProperties.stream()
					.map(matchedObjectName::getKeyProperty)
					.map(value -> value == null ? EMPTY : value)
					.toList();

				final List<String> row = new ArrayList<>(keyPropertyValues);

				// Build the row with the requested attributes
				for (final String requestedAttribute : attributes) {
					Object value = EMPTY;
					try {
						value = mBeanServerConnection.getAttribute(matchedObjectName, requestedAttribute);
					} catch (Exception e) {
						log.debug(
							"Hostname {} - Error fetching attribute {} for MBean {}. Exception: ",
							hostname,
							requestedAttribute,
							matchedObjectName,
							e
						);
						// The server answered the other errors; a communication error fails the attempt
						if (e instanceof IOException) {
							outcome = RequestOutcome.of(e);
						}
					}
					row.add(value.toString());
				}

				results.add(row);
			}
		} catch (Exception e) {
			log.debug("Hostname {} - Error connecting to JMX. {} → ", hostname, url, e);
			return new JmxAttempt<>(results, outcomeOf(e));
		}
		return new JmxAttempt<>(results, outcome);
	}

	/**
	 * Checks if a JMX connection can be established with the given configuration.
	 *
	 * @param configuration the JMX configuration containing hostname, port, username, and password
	 * @param requestStats  the request counters of the monitored resource, or {@code null} to record nothing
	 * @return true if the connection is successful, false otherwise
	 * @throws Exception if an error occurs while connecting to the JMX server or if the connection times out
	 */
	@WithSpan("JMX Connection Check")
	public boolean checkConnection(
		@SpanAttribute("jmx.config") final JmxConfiguration configuration,
		final RequestStats requestStats
	) throws Exception {
		return executeAndRecord(() -> runConnectionCheck(configuration), configuration.getTimeout(), requestStats);
	}

	/**
	 * Runs a connection check to verify if the JMX server is reachable and the
	 * credentials are correct.
	 *
	 * @param configuration the JMX configuration containing hostname, port,
	 *                      username, and password
	 * @return true if the connection is successful, false otherwise, and the outcome of the attempt
	 */
	private JmxAttempt<Boolean> runConnectionCheck(final JmxConfiguration configuration) {
		final String hostname = configuration.getHostname();
		final int port = configuration.getPort();

		final var url = buildJmxRmiUrl(hostname, port);

		try (var _ = connect(hostname, url, configuration.getUsername(), configuration.getPassword())) {
			return new JmxAttempt<>(true, RequestOutcome.COMPLETED);
		} catch (Exception e) {
			log.debug("Hostname {} - JMX health check failed. {} → ", hostname, url, e);
			return new JmxAttempt<>(false, outcomeOf(e));
		}
	}

	/**
	 * Builds the JMX RMI URL based on the hostname and port.
	 *
	 * @param hostname the hostname of the JMX server
	 * @param port     the port on which the JMX server is listening
	 * @return the formatted JMX RMI URL
	 */
	String buildJmxRmiUrl(final String hostname, final int port) {
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
	JMXConnector connect(final String hostname, final String url, final String username, final char[] password)
		throws Exception {
		log.debug("Hostname {} - Connecting to JMX at {}.", hostname, url);

		final Map<String, String[]> env = new HashMap<>();

		if (username != null && password != null) {
			env.put(JMXConnector.CREDENTIALS, new String[] { username, String.valueOf(password) });
		}

		return JMXConnectorFactory.connect(new JMXServiceURL(url), env);
	}
}
