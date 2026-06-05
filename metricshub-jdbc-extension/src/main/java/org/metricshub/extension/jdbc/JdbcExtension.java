package org.metricshub.extension.jdbc;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub JDBC Extension
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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.UnaryOperator;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.common.helpers.KnownMonitorType;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
import org.metricshub.engine.common.helpers.StringHelper;
import org.metricshub.engine.common.helpers.TextTableHelper;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.identity.ConnectorIdentity;
import org.metricshub.engine.connector.model.identity.DriverInfo;
import org.metricshub.engine.connector.model.identity.JdbcInfo;
import org.metricshub.engine.connector.model.identity.criterion.Criterion;
import org.metricshub.engine.connector.model.identity.criterion.SqlCriterion;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.SqlSource;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.jdbc.driver.DriverResolutionException;
import org.metricshub.extension.jdbc.driver.JdbcDriverRegistryHolder;
import org.metricshub.extension.jdbc.driver.JdbcDriverSelection;
import org.metricshub.extension.jdbc.driver.LoadedDriver;

/**
 * This class implements the {@link IProtocolExtension} contract, reports the supported features,
 * processes SQL sources and criteria.
 */
@Slf4j
public class JdbcExtension implements IProtocolExtension {

	/**
	 * The identifier for jdbc.
	 */
	public static final String IDENTIFIER = "jdbc";

	private SqlRequestExecutor sqlRequestExecutor;

	/**
	 * Creates a new instance of the {@link JdbcExtension} implementation.
	 */
	public JdbcExtension() {
		sqlRequestExecutor = new SqlRequestExecutor();
	}

	@Override
	public boolean isValidConfiguration(IConfiguration configuration) {
		return configuration instanceof JdbcConfiguration;
	}

	@Override
	public Set<Class<? extends Source>> getSupportedSources() {
		return Set.of(SqlSource.class);
	}

	@Override
	public Map<Class<? extends IConfiguration>, Set<Class<? extends Source>>> getConfigurationToSourceMapping() {
		return Map.of(JdbcConfiguration.class, Set.of(SqlSource.class));
	}

	@Override
	public Set<Class<? extends Criterion>> getSupportedCriteria() {
		return Set.of(SqlCriterion.class);
	}

	@Override
	public Optional<Boolean> checkProtocol(TelemetryManager telemetryManager) {
		// Retrieve SQL Configuration from the telemetry manager host configuration
		final JdbcConfiguration jdbcConfiguration = (JdbcConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(JdbcConfiguration.class);

		// Stop the health check if there is not an SQL configuration
		if (jdbcConfiguration == null) {
			return Optional.empty();
		}

		// Retrieve the hostname from the SqlConfiguration
		final String hostname = jdbcConfiguration.getHostname();
		log.info("Hostname {} - Performing {} health check.", hostname, getIdentifier());

		// Retrieve connection details
		final char[] url = jdbcConfiguration.getUrl();
		final String jdbcUrl = url != null ? String.valueOf(url) : null;
		final String username = jdbcConfiguration.getUsername();
		final char[] password = jdbcConfiguration.getPassword();
		final long timeout = jdbcConfiguration.getTimeout();

		// Pre-resolve a driver selection so the health check uses the same driver as collection:
		// resource-level configuration first, then any connector monitor advertising a driver.
		final JdbcDriverSelection selection = resolveHealthCheckSelection(jdbcConfiguration, telemetryManager);

		// Check database availability using Connection.isValid()
		boolean isAlive = isDatabaseAlive(jdbcUrl, username, password, timeout, selection);

		if (!isAlive) {
			log.warn("Hostname {} - Database health check failed", hostname);
		}

		return Optional.of(isAlive);
	}

	@Override
	public CriterionTestResult processCriterion(
		Criterion criterion,
		String connectorId,
		TelemetryManager telemetryManager,
		boolean logMode
	) {
		if (criterion instanceof SqlCriterion sqlCriterion) {
			final JdbcDriverSelection selection = ensureDeclaredDriver(connectorId, telemetryManager);
			return new SqlCriterionProcessor(sqlRequestExecutor, logMode, selection).process(sqlCriterion, telemetryManager);
		}

		throw new IllegalArgumentException(
			String.format(
				"Hostname %s - Cannot process criterion %s.",
				telemetryManager.getHostname(),
				criterion != null ? criterion.getClass().getSimpleName() : "<null>"
			)
		);
	}

	@Override
	public SourceTable processSource(Source source, String connectorId, TelemetryManager telemetryManager) {
		if (source instanceof SqlSource sqlSource) {
			final JdbcDriverSelection selection = ensureDeclaredDriver(connectorId, telemetryManager);
			return new SqlSourceProcessor(sqlRequestExecutor, connectorId, selection).process(sqlSource, telemetryManager);
		}
		throw new IllegalArgumentException(
			String.format(
				"Hostname %s - Cannot process source %s.",
				telemetryManager.getHostname(),
				source != null ? source.getClass().getSimpleName() : "<null>"
			)
		);
	}

	/**
	 * Resolves the JDBC driver selection to use for SQL execution.
	 *
	 * <p>Priority order:
	 * <ol>
	 *   <li>Resource-level {@link JdbcConfiguration#getDriver()} configuration driver block, when set.</li>
	 *   <li>Connector-level {@link ConnectorIdentity#getJdbc()} identity jdbc block, when set.</li>
	 *   <li>Best-effort URL-based lookup via {@link JdbcDriverRegistryHolder#findSelectionForUrl(String)}.</li>
	 * </ol>
	 *
	 * @param connectorId      identifier of the connector whose source or criterion is being
	 *                         processed; may be {@code null} or unknown to the store.
	 * @param telemetryManager telemetry manager carrying host configuration and connector store;
	 *                         may be {@code null}.
	 * @return the resolved {@link JdbcDriverSelection}, or {@code null} when {@code telemetryManager}
	 *         is {@code null} or when no suitable driver can be determined.
	 */
	private JdbcDriverSelection ensureDeclaredDriver(final String connectorId, final TelemetryManager telemetryManager) {
		if (telemetryManager == null) {
			return null;
		}

		// First priority: resource-level JdbcConfiguration.driver.
		final DriverInfo driverInfo = resolveConfigurationJdbcInfo(telemetryManager);
		if (driverInfo != null) {
			return JdbcDriverRegistryHolder.resolveSelection(driverInfo);
		}

		// Second priority: connector-level ConnectorIdentity.jdbc.
		final JdbcDriverSelection connectorSelection = JdbcDriverRegistryHolder.resolveSelection(
			resolveConnectorJdbcInfo(connectorId, telemetryManager)
		);
		if (connectorSelection != null) {
			return connectorSelection;
		}

		// Final fallback: pick a registered descriptor whose driver accepts this resource's URL.
		// Catches enterprise distributions where the driver JAR is on the agent classpath but the
		// connector and the resource configuration both omit jdbc.driver.
		return JdbcDriverRegistryHolder.findSelectionForUrl(resolveConfigurationUrl(telemetryManager));
	}

	/**
	 * Reads the JDBC URL from the resource-level {@link JdbcConfiguration}, returning {@code null}
	 * when no JDBC configuration is registered or the URL is missing.
	 *
	 * @param telemetryManager telemetry manager carrying the host configuration; may be {@code null}.
	 * @return the JDBC URL as a string, or {@code null} when absent.
	 */
	private String resolveConfigurationUrl(final TelemetryManager telemetryManager) {
		if (telemetryManager == null || telemetryManager.getHostConfiguration() == null) {
			return null;
		}
		final Map<Class<? extends IConfiguration>, IConfiguration> configurations = telemetryManager
			.getHostConfiguration()
			.getConfigurations();
		if (configurations == null) {
			return null;
		}
		if (configurations.get(JdbcConfiguration.class) instanceof JdbcConfiguration jdbcConfig) {
			final char[] url = jdbcConfig.getUrl();
			return url == null ? null : String.valueOf(url);
		}
		return null;
	}

	/**
	 * Reads the {@link JdbcConfiguration#getDriver() driver} block from the host's JDBC configuration,
	 * if any.
	 *
	 * @param telemetryManager telemetry manager carrying the host configuration; must not be {@code null}.
	 * @return the configuration-level {@link DriverInfo} or {@code null} when absent.
	 */
	private DriverInfo resolveConfigurationJdbcInfo(final TelemetryManager telemetryManager) {
		if (telemetryManager.getHostConfiguration() == null) {
			return null;
		}
		final Map<Class<? extends IConfiguration>, IConfiguration> configurations = telemetryManager
			.getHostConfiguration()
			.getConfigurations();
		if (configurations == null) {
			return null;
		}
		final IConfiguration raw = configurations.get(JdbcConfiguration.class);
		if (raw instanceof JdbcConfiguration jdbcConfig) {
			return jdbcConfig.getDriver();
		}
		return null;
	}

	/**
	 * Reads the {@link ConnectorIdentity#getJdbc() jdbc} block declared by the connector identified
	 * by {@code connectorId}, if any.
	 *
	 * @param connectorId      identifier of the connector; may be {@code null} or unknown.
	 * @param telemetryManager telemetry manager carrying the connector store; must not be {@code null}.
	 * @return the connector-level {@link DriverInfo} or {@code null} when absent.
	 */
	private DriverInfo resolveConnectorJdbcInfo(final String connectorId, final TelemetryManager telemetryManager) {
		if (connectorId == null) {
			return null;
		}
		final ConnectorStore store = telemetryManager.getConnectorStore();
		if (store == null || store.getStore() == null) {
			return null;
		}
		final Connector connector = store.getStore().get(connectorId);
		if (connector == null) {
			return null;
		}
		final ConnectorIdentity identity = connector.getConnectorIdentity();
		if (identity == null) {
			return null;
		}
		final JdbcInfo jdbcInfo = identity.getJdbc();
		return jdbcInfo == null ? null : jdbcInfo.getDriver();
	}

	@Override
	public boolean isSupportedConfigurationType(String configurationType) {
		return IDENTIFIER.equalsIgnoreCase(configurationType);
	}

	@Override
	public IConfiguration buildConfiguration(String configurationType, JsonNode jsonNode, UnaryOperator<char[]> decrypt)
		throws InvalidConfigurationException {
		try {
			final JdbcConfiguration jdbcConfiguration = newObjectMapper().treeToValue(jsonNode, JdbcConfiguration.class);

			if (decrypt != null) {
				char[] password = jdbcConfiguration.getPassword();
				char[] url = jdbcConfiguration.getUrl();
				if (password != null) {
					jdbcConfiguration.setPassword(decrypt.apply(password));
				}
				if (url != null) {
					jdbcConfiguration.setUrl(decrypt.apply(url));
				}
			}
			return jdbcConfiguration;
		} catch (Exception e) {
			final String errorMessage = String.format(
				"Error while reading JDBC Configuration: %s. Error: %s",
				jsonNode,
				e.getMessage()
			);
			log.error(errorMessage);
			log.debug("Error while reading JDBC Configuration: {}. Stack trace:", jsonNode, e);
			throw new InvalidConfigurationException(errorMessage, e);
		}
	}

	/**
	 * Creates and configures a new instance of the Jackson ObjectMapper for handling YAML data.
	 *
	 * @return A configured ObjectMapper instance.
	 */
	public static JsonMapper newObjectMapper() {
		return JsonMapper.builder(new YAMLFactory())
			.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
			.enable(SerializationFeature.INDENT_OUTPUT)
			.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
			.configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)
			.build();
	}

	@Override
	public String getIdentifier() {
		return IDENTIFIER;
	}

	/**
	 * Flushes and releases recorder resources at the end of a recording session.
	 *
	 * @param telemetryManager telemetry manager that carries the recording output directory
	 */
	@Override
	public void onRecordingSessionEnd(final TelemetryManager telemetryManager) {
		final String recordOutputDirectory = telemetryManager.getRecordOutputDirectory();
		if (recordOutputDirectory != null && !recordOutputDirectory.isBlank()) {
			JdbcRecorder.flushAndRemoveInstance(recordOutputDirectory);
		}
	}

	@Override
	public String executeQuery(final IConfiguration configuration, final JsonNode queryNode) throws Exception {
		final String hostname = configuration.getHostname();
		final String sqlQuery = queryNode.get("query").asText();
		final JdbcConfiguration jdbcConfiguration = (JdbcConfiguration) configuration;
		final List<List<String>> resultList = sqlRequestExecutor.executeSql(
			hostname,
			jdbcConfiguration,
			sqlQuery,
			false,
			null
		);
		final String[] columns = StringHelper.extractColumns(sqlQuery);
		if (columns.length == 1 && columns[0].equals("*")) {
			return TextTableHelper.generateTextTable(resultList);
		} else {
			return TextTableHelper.generateTextTable(columns, resultList);
		}
	}

	/**
	 * Checks if the database is reachable using {@link Connection#isValid(int)}.
	 *
	 * <p>When {@code selection} is non-null the supplied driver is used. Otherwise the URL prefix is
	 * matched against the built-in driver inference table; this keeps zero-config liveness checks
	 * working for built-in drivers (MariaDB, PostgreSQL, MySQL, H2).
	 *
	 * @param jdbcUrl   The JDBC URL of the database
	 * @param username  The database username
	 * @param password  The database password
	 * @param timeout   The timeout duration in seconds
	 * @param selection Pre-resolved driver selection; may be {@code null} to fall back to URL
	 *                  inference.
	 * @return true if the database is reachable, false otherwise
	 */
	static boolean isDatabaseAlive(
		final String jdbcUrl,
		final String username,
		final char[] password,
		final long timeout,
		final JdbcDriverSelection selection
	) {
		final JdbcDriverSelection effective;
		if (selection != null) {
			effective = selection;
		} else {
			// Walk every registered descriptor (built-in + external via ServiceLoader) and pick
			// the first one whose Driver instance accepts this URL. Catches enterprise
			// distributions where Oracle/JTOpen/DB2/etc. ship on the agent classpath without an
			// explicit jdbc.driver declaration.
			final JdbcDriverSelection inferred = JdbcDriverRegistryHolder.findSelectionForUrl(jdbcUrl);
			if (inferred == null) {
				log.warn(
					"isDatabaseAlive: no JDBC driver accepts URL {}; declare jdbc.driver.className on the resource " +
						"or install the driver JAR (operator-default drivers directory or agent classpath).",
					jdbcUrl
				);
				return false;
			}
			effective = inferred;
		}
		final LoadedDriver loaded;
		try {
			loaded = JdbcDriverRegistryHolder.get().resolve(effective.driverClass(), effective.explicitJarPath());
		} catch (DriverResolutionException e) {
			log.warn(
				"isDatabaseAlive: failed to resolve declared JDBC driver {} (jarPath={}) for URL {}: {}",
				effective.driverClass(),
				effective.explicitJarPath(),
				jdbcUrl,
				e.getMessage()
			);
			return false;
		}
		final Properties props = new Properties();
		if (username != null) {
			props.setProperty("user", username);
		}
		if (password != null) {
			props.setProperty("password", new String(password));
		}
		try (Connection connection = loaded.driver().connect(jdbcUrl, props)) {
			return connection != null && connection.isValid((int) timeout);
		} catch (SQLException _) {
			return false;
		}
	}

	/**
	 * Pre-resolves the JDBC driver selection used by the health check.
	 *
	 * <p>Priority order:
	 * <ol>
	 *   <li>Resource-level {@link JdbcConfiguration#getDriver()} configuration driver.</li>
	 *   <li>Any connector monitor in the {@link TelemetryManager} whose connector identity advertises a {@link DriverInfo}.</li>
	 *   <li>Best-effort URL-based lookup via {@link JdbcDriverRegistryHolder#findSelectionForUrl(String)}.</li>
	 * </ol>
	 *
	 * @param jdbcConfiguration the resource-level configuration; never {@code null}.
	 * @param telemetryManager  the telemetry manager carrying connector monitors and the connector store; never {@code null}.
	 * @return a resolved {@link JdbcDriverSelection}, or {@code null} when no suitable driver can be determined.
	 */
	private JdbcDriverSelection resolveHealthCheckSelection(
		final JdbcConfiguration jdbcConfiguration,
		final TelemetryManager telemetryManager
	) {
		final DriverInfo configurationDriver = jdbcConfiguration.getDriver();
		if (configurationDriver != null) {
			return JdbcDriverRegistryHolder.resolveSelection(configurationDriver);
		}
		final DriverInfo connectorDriver = discoverDriverFromConnectorMonitors(telemetryManager);
		if (connectorDriver != null) {
			return JdbcDriverRegistryHolder.resolveSelection(connectorDriver);
		}
		// Final fallback: pick a registered descriptor whose driver accepts this URL. Catches
		// enterprise distributions where the driver JAR is bundled on the agent classpath without
		// any explicit jdbc.driver declaration in the connector or the resource configuration.
		final char[] url = jdbcConfiguration.getUrl();
		return url == null ? null : JdbcDriverRegistryHolder.findSelectionForUrl(String.valueOf(url));
	}

	/**
	 * Walks every connector monitor in {@code telemetryManager} and returns the first non-null
	 * {@link DriverInfo} declared by the matching {@link Connector} in the
	 * {@link ConnectorStore}.
	 *
	 * <p>This makes the health check honour database connectors that ship a connector-level
	 * driver (for instance JTOpen, Oracle), at least once detection has populated the connector
	 * monitors. On the very first cycle, before detection runs, no connector monitor exists yet
	 * and this method returns {@code null}; the caller then falls back to URL-based inference.
	 * </p>
	 * @param telemetryManager the telemetry manager; may be {@code null}.
	 * @return the first non-null connector-level {@link DriverInfo} found, or {@code null}.
	 */
	private DriverInfo discoverDriverFromConnectorMonitors(final TelemetryManager telemetryManager) {
		if (telemetryManager == null) {
			return null;
		}
		final Map<String, Monitor> connectorMonitors = telemetryManager.findMonitorsByType(
			KnownMonitorType.CONNECTOR.getKey()
		);
		if (connectorMonitors == null || connectorMonitors.isEmpty()) {
			return null;
		}
		final ConnectorStore store = telemetryManager.getConnectorStore();
		if (store == null || store.getStore() == null) {
			return null;
		}
		for (final Monitor monitor : connectorMonitors.values()) {
			final String connectorId = monitor.getAttribute(MetricsHubConstants.MONITOR_ATTRIBUTE_CONNECTOR_ID);
			if (connectorId == null) {
				continue;
			}
			final Connector connector = store.getStore().get(connectorId);
			if (connector == null) {
				continue;
			}
			final ConnectorIdentity identity = connector.getConnectorIdentity();
			if (identity == null) {
				continue;
			}
			final JdbcInfo jdbcInfo = identity.getJdbc();
			if (jdbcInfo != null && jdbcInfo.getDriver() != null) {
				return jdbcInfo.getDriver();
			}
		}
		return null;
	}
}
