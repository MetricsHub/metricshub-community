package org.metricshub.extension.wbem;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Wbem Extension
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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.AUTOMATIC_NAMESPACE;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.WMI_DEFAULT_NAMESPACE;

import java.util.List;
import java.util.function.Function;
import org.metricshub.engine.common.helpers.LoggingHelper;
import org.metricshub.engine.connector.model.monitor.task.source.WbemSource;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * Processor for WBEM sources. It is responsible for executing the WBEM query and returning the resulting table.
 */
public class WbemSourceProcessor {

	private WbemRequestExecutor wbemRequestExecutor;
	private String connectorId;
	private Function<TelemetryManager, WbemConfiguration> wbemConfigurationProvider;

	private static final Function<TelemetryManager, WbemConfiguration> DEFAULT_WBEM_CONFIGURATION_PROVIDER = telemetryManager -> (WbemConfiguration) telemetryManager
		.getHostConfiguration()
		.getConfigurations()
		.get(WbemConfiguration.class);

	/**
	 * Creates a new {@link WbemSourceProcessor} with the given executor and connector ID,
	 * using the default WBEM configuration provider.
	 *
	 * @param wbemRequestExecutor The executor to perform WBEM requests.
	 * @param connectorId         The connector identifier.
	 */
	public WbemSourceProcessor(final WbemRequestExecutor wbemRequestExecutor, final String connectorId) {
		this(wbemRequestExecutor, connectorId, DEFAULT_WBEM_CONFIGURATION_PROVIDER);
	}

	/**
	 * Creates a new {@link WbemSourceProcessor} with the given executor, connector ID,
	 * and a custom WBEM configuration provider.
	 *
	 * @param wbemRequestExecutor       The executor to perform WBEM requests.
	 * @param connectorId               The connector identifier.
	 * @param wbemConfigurationProvider A function that retrieves the {@link WbemConfiguration}
	 *                                  from the given {@link TelemetryManager}.
	 */
	public WbemSourceProcessor(
		final WbemRequestExecutor wbemRequestExecutor,
		final String connectorId,
		final Function<TelemetryManager, WbemConfiguration> wbemConfigurationProvider
	) {
		this.wbemRequestExecutor = wbemRequestExecutor;
		this.connectorId = connectorId;
		this.wbemConfigurationProvider = wbemConfigurationProvider;
	}

	/**
	 * Get the namespace to use for the execution of the given {@link WbemSource} instance
	 *
	 * @param wbemSource {@link WbemSource} instance from which we want to extract the namespace. Expected "automatic", null or <em>any string</em>
	 * @param telemetryManager {@link TelemetryManager} instance from which we fetch the automatic wbem namespace
	 * @return {@link String} value
	 */
	String getNamespace(final WbemSource wbemSource, final TelemetryManager telemetryManager, final String connectorId) {
		String namespace = wbemSource.getNamespace();
		if (namespace == null) {
			namespace = WMI_DEFAULT_NAMESPACE;
		} else if (AUTOMATIC_NAMESPACE.equalsIgnoreCase(namespace)) {
			namespace = telemetryManager.getHostProperties().getConnectorNamespace(connectorId).getAutomaticWbemNamespace();
		}
		return namespace;
	}

	/**
	 * Process the given {@link WbemSource} and return the resulting {@link SourceTable}
	 *
	 * @param wbemSource {@link WbemSource} instance we wish to run
	 * @param telemetryManager {@link TelemetryManager} instance from which we fetch the hostname and related configuration
	 * @return {@link SourceTable} instance
	 */
	public SourceTable process(WbemSource wbemSource, TelemetryManager telemetryManager) {
		final WbemConfiguration wbemConfiguration = wbemConfigurationProvider.apply(telemetryManager);

		// Retrieve the hostname from the WbemConfiguration, otherwise from the telemetryManager
		final String hostname = wbemConfiguration != null && wbemConfiguration.getHostname() != null
			? wbemConfiguration.getHostname()
			: telemetryManager.getHostname();

		if (wbemSource == null) {
			return SourceTable.failed("Malformed WBEM source.");
		}

		if (wbemConfiguration == null) {
			return SourceTable.failed("WBEM: WBEM is not configured on the host.");
		}

		// Get the namespace, the default one is : root/cimv2
		final String namespace = getNamespace(wbemSource, telemetryManager, connectorId);

		try {
			if (hostname == null) {
				return SourceTable.failed("WBEM: no hostname, the URL cannot be built.");
			}
			if (wbemConfiguration.getPort() == null || wbemConfiguration.getPort() == 0) {
				return SourceTable.failed("WBEM: no port to connect to the host.");
			}

			final List<List<String>> table = wbemRequestExecutor.executeWbem(
				hostname,
				wbemConfiguration,
				wbemSource.getQuery(),
				namespace,
				telemetryManager
			);

			return SourceTable.ofTable(table);
		} catch (Exception e) {
			LoggingHelper.logSourceError(
				connectorId,
				wbemSource.getKey(),
				String.format(
					"WBEM query=%s, Username=%s, Timeout=%d, Namespace=%s",
					wbemSource.getQuery(),
					wbemConfiguration.getUsername(),
					wbemConfiguration.getTimeout(),
					namespace
				),
				hostname,
				e
			);

			return SourceTable.failed(String.format("WBEM query %s in namespace %s", wbemSource.getQuery(), namespace), e);
		}
	}
}
