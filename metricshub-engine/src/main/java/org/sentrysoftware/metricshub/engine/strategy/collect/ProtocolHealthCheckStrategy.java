package org.sentrysoftware.metricshub.engine.strategy.collect;

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

import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.sentrysoftware.metricshub.engine.client.ClientsExecutor;
import org.sentrysoftware.metricshub.engine.client.http.HttpRequest;
import org.sentrysoftware.metricshub.engine.configuration.HttpConfiguration;
import org.sentrysoftware.metricshub.engine.configuration.SnmpConfiguration;
import org.sentrysoftware.metricshub.engine.connector.model.common.ResultContent;
import org.sentrysoftware.metricshub.engine.strategy.AbstractStrategy;
import org.sentrysoftware.metricshub.engine.telemetry.MetricFactory;
import org.sentrysoftware.metricshub.engine.telemetry.Monitor;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;

/**
 * A strategy that aims to perform health check over a hostname on each protocol (HTTP, SNMP, IPMI, ...).
 *
 * <p>
 * This aims to report the responsiveness of configured protocols on a resource. The health check
 * is performed at the beginning of each data collection cycle, and a metric is generated for each protocol
 * indicating whether it is responding or not.
 * </p>
 */
@Slf4j
public class ProtocolHealthCheckStrategy extends AbstractStrategy {

	/**
	 * Protocol up status value '1.0'
	 */
	public static final Double UP = 1.0;

	/**
	 * Protocol down status value '0.0'
	 */
	public static final Double DOWN = 0.0;

	/**
	 * Up metric name format that will be saved by the metric factory
	 */
	private static final String UP_METRIC_FORMAT = "metricshub.host.up{protocol=\"%s\"}";

	/**
	 * HTTP Up metric
	 */
	public static final String HTTP_UP_METRIC = String.format(UP_METRIC_FORMAT, "http");

	/**
	 * SNMP Up metric
	 */
	public static final String SNMP_UP_METRIC = String.format(UP_METRIC_FORMAT, "snmp");

	/**
	 * The SNMP OID value to use in the health check test
	 */
	public static final String SNMP_OID = "1.3.6.1";

	/**
	 * Constructs a new {@code HealthCheckStrategy} using the provided telemetry manager, strategy time, and
	 * clients executor.
	 *
	 * @param telemetryManager The telemetry manager responsible for managing telemetry-related operations.
	 * @param strategyTime     The time when the strategy is executed.
	 * @param clientsExecutor  The executor for managing clients used in the strategy.
	 */
	@Builder
	public ProtocolHealthCheckStrategy(
		@NonNull final TelemetryManager telemetryManager,
		@NonNull final Long strategyTime,
		@NonNull final ClientsExecutor clientsExecutor
	) {
		super(telemetryManager, strategyTime, clientsExecutor);
	}

	@Override
	public void run() {
		// Retrieve the hostname
		final String hostname = telemetryManager.getHostConfiguration().getHostname();

		// Retrieve the host endpoint monitor
		final Monitor hostMonitor = telemetryManager.getEndpointHostMonitor();

		// If the host monitor does not exist, the strategy is aborted
		if (hostMonitor == null) {
			return;
		}

		log.info("Hostname {} - Performing protocol health check.", hostname);

		// Create a metric factory
		final MetricFactory metricFactory = new MetricFactory(hostname);

		// Check the hostname protocols health
		checkHttpHealth(hostname, hostMonitor, metricFactory);
		checkSnmpHealth(hostname, hostMonitor, metricFactory);
	}

	@Override
	public long getStrategyTimeout() {
		return telemetryManager.getHostConfiguration().getStrategyTimeout();
	}

	@Override
	public Long getStrategyTime() {
		return telemetryManager.getStrategyTime();
	}

	/**
	 * Check HTTP protocol health on the hostname for the host monitor
	 * Criteria: The HTTP GET request to "/" must return a result.
	 *
	 * @param hostMonitor   An endpoint host monitor
	 * @param hostname      The hostname on which we perform health check
	 * @param metricFactory The metric factory used to collect the health check metric
	 */
	public void checkHttpHealth(String hostname, Monitor hostMonitor, MetricFactory metricFactory) {
		// Create and set the HTTP result to null
		String httpResult = null;

		// Retrieve HTTP configuration from the telemetry manager
		final HttpConfiguration httpConfiguration = (HttpConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(HttpConfiguration.class);

		// Stop the HTTP health check if there is not an HTTP configuration
		if (httpConfiguration == null) {
			return;
		}

		log.info("Hostname {} - Checking HTTP protocol status. Sending GET request to '/'.", hostname);

		// Execute HTTP test request
		try {
			// Create an Http request
			final HttpRequest request = HttpRequest
				.builder()
				.hostname(hostname)
				.path("/")
				.httpConfiguration(httpConfiguration)
				.resultContent(ResultContent.ALL)
				.build();

			// Execute Http test request
			httpResult = clientsExecutor.executeHttp(request, true);
		} catch (Exception e) {
			log.debug(
				"Hostname {} - Checking HTTP protocol status. HTTP exception when performing a GET request to '/': ",
				hostname,
				e
			);
		}

		// Generate a metric from the Http result
		metricFactory.collectNumberMetric(hostMonitor, HTTP_UP_METRIC, httpResult != null ? UP : DOWN, strategyTime);
	}

	/**
	 * Check SNMP protocol health on the hostname for the host monitor
	 * Criteria: SNMP Get Next on '1.3.6.1' SNMP OID must be successful.
	 *
	 * @param hostMonitor   An endpoint host monitor
	 * @param hostname      The hostname on which we perform health check
	 * @param metricFactory The metric factory used to collect the health check metric
	 */
	public void checkSnmpHealth(String hostname, Monitor hostMonitor, MetricFactory metricFactory) {
		// Create and set the SNMP result to null
		String snmpResult = null;

		// Retrieve SNMP Configuration from the telemetry manager host configuration
		final SnmpConfiguration snmpConfiguration = (SnmpConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(SnmpConfiguration.class);

		// Stop the SNMP health check if there is not an SNMP configuration
		if (snmpConfiguration == null) {
			return;
		}

		log.info("Hostname {} - Checking SNMP protocol status. Sending Get Next request on {}.", hostname, SNMP_OID);

		// Execute SNMP test command
		try {
			snmpResult = clientsExecutor.executeSNMPGetNext(SNMP_OID, snmpConfiguration, hostname, true);
		} catch (Exception e) {
			log.debug(
				"Hostname {} - Checking SNMP protocol status. SNMP exception when performing a test SNMP Get Next on {}: ",
				hostname,
				SNMP_OID,
				e
			);
		}

		// Generate a metric from the Snmp result
		metricFactory.collectNumberMetric(hostMonitor, SNMP_UP_METRIC, snmpResult != null ? UP : DOWN, strategyTime);
	}
}
