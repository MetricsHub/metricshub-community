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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.TABLE_SEP;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.connector.model.monitor.task.source.JmxSource;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * Processes a JmxSource by delegating attribute reads to JmxRequestExecutor.
 */
@Slf4j
public class JmxSourceProcessor {

	// Executor instance for JMX requests
	@NonNull
	private final JmxRequestExecutor jmxExecutor;

	private final Function<TelemetryManager, JmxConfiguration> jmxConfigurationProvider;

	private static final Function<TelemetryManager, JmxConfiguration> DEFAULT_JMX_CONFIGURATION_PROVIDER =
		telemetryManager ->
			(JmxConfiguration) telemetryManager.getHostConfiguration().getConfigurations().get(JmxConfiguration.class);

	/**
	 * Creates a new {@link JmxSourceProcessor} with the given executor,
	 * using the default JMX configuration provider.
	 *
	 * @param jmxExecutor The executor to perform JMX requests.
	 */
	public JmxSourceProcessor(final JmxRequestExecutor jmxExecutor) {
		this(jmxExecutor, DEFAULT_JMX_CONFIGURATION_PROVIDER);
	}

	/**
	 * Creates a new {@link JmxSourceProcessor} with the given executor
	 * and a custom JMX configuration provider.
	 *
	 * @param jmxExecutor               The executor to perform JMX requests.
	 * @param jmxConfigurationProvider A function that retrieves the {@link JmxConfiguration}
	 *                                  from the given {@link TelemetryManager}.
	 */
	public JmxSourceProcessor(
		final JmxRequestExecutor jmxExecutor,
		final Function<TelemetryManager, JmxConfiguration> jmxConfigurationProvider
	) {
		this.jmxExecutor = jmxExecutor;
		this.jmxConfigurationProvider = jmxConfigurationProvider;
	}

	/**
	 * Processes a JmxSource by fetching its attributes and returning a SourceTable.
	 *
	 * @param jmxSource        The JmxSource defining the JMX object and attributes to fetch.
	 * @param telemetryManager The TelemetryManager to access host configuration and log errors.
	 * @return SourceTable containing the fetched attributes.
	 */
	public SourceTable process(final JmxSource jmxSource, final TelemetryManager telemetryManager) {
		final JmxConfiguration jmxConfig = jmxConfigurationProvider.apply(telemetryManager);

		final String objectName = jmxSource.getObjectName();
		List<String> attributes = jmxSource.getAttributes();
		List<String> keyProperties = jmxSource.getKeyProperties();

		if (attributes == null) {
			attributes = new ArrayList<>();
		}

		if (keyProperties == null) {
			keyProperties = new ArrayList<>();
		}

		final List<List<String>> rows = new ArrayList<>();

		try {
			// Fetch attributes; this also validates exactly one match
			rows.addAll(
				jmxExecutor.fetchMBean(
					jmxConfig,
					objectName,
					attributes,
					keyProperties,
					telemetryManager.getHostname(),
					telemetryManager.getRecordOutputDirectory()
				)
			);
		} catch (Exception e) {
			String hostname = jmxConfig.getHostname();
			log.error(
				"Hostname {} - Error processing JMX source for objectName '{}': {}",
				hostname,
				objectName,
				e.getMessage()
			);
			log.debug("Hostname {} - Error processing JMX source for objectName '{}'", hostname, objectName, e);
		}

		return SourceTable.builder().table(rows).rawData(SourceTable.tableToCsv(rows, TABLE_SEP, true)).build();
	}
}
