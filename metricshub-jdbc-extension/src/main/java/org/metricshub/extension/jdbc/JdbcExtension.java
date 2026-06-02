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
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.common.helpers.StringHelper;
import org.metricshub.engine.common.helpers.TextTableHelper;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.identity.ConnectorIdentity;
import org.metricshub.engine.connector.model.identity.JdbcInfo;
import org.metricshub.engine.connector.model.identity.criterion.Criterion;
import org.metricshub.engine.connector.model.identity.criterion.SqlCriterion;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.SqlSource;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.jdbc.driver.JdbcDriverRegistryHolder;
import org.metricshub.extension.jdbc.driver.JdbcDriverSelection;
import org.metricshub.extension.jdbc.driver.JdbcPathExpression;

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

		// Check database availability using Connection.isValid()
		boolean isAlive = isDatabaseAlive(jdbcUrl, username, password, timeout);

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
	 * Pre-resolves the JDBC driver to use, applying the following priority order:
	 * <ol>
	 *   <li>Resource-level {@link JdbcConfiguration#getDriver() configuration driver} block, when set.</li>
	 *   <li>Connector-level {@link ConnectorIdentity#getJdbc() identity jdbc} block, when set.</li>
	 * </ol>
	 * Each level is null-safe and resolution failures are swallowed at DEBUG so the eventual
	 * {@code Driver.connect} call surfaces the real {@link java.sql.SQLException}.
	 *
	 * @param connectorId      identifier of the connector whose source or criterion is being
	 *                         processed; may be {@code null} or unknown to the store.
	 * @param telemetryManager telemetry manager carrying host configuration and connector store;
	 *                         may be {@code null}.
	 * @return the resolved {@link JdbcDriverSelection}, or {@code null} when no {@link JdbcInfo}
	 *         block is declared (legacy zero-config path).
	 */
	private JdbcDriverSelection ensureDeclaredDriver(final String connectorId, final TelemetryManager telemetryManager) {
		if (telemetryManager == null) {
			return null;
		}

		// First priority: resource-level JdbcConfiguration.driver.
		final JdbcInfo configJdbc = resolveConfigurationJdbcInfo(telemetryManager);
		if (configJdbc != null) {
			return JdbcDriverRegistryHolder.resolveSelection(configJdbc, JdbcPathExpression.Scope.RESOURCE);
		}

		// Fallback: connector-level ConnectorIdentity.jdbc.
		return JdbcDriverRegistryHolder.resolveSelection(
			resolveConnectorJdbcInfo(connectorId, telemetryManager),
			JdbcPathExpression.Scope.CONNECTOR
		);
	}

	/**
	 * Reads the {@link JdbcConfiguration#getJdbc() jdbc} block from the host's JDBC configuration,
	 * if any.
	 *
	 * @param telemetryManager telemetry manager carrying the host configuration; must not be {@code null}.
	 * @return the configuration-level {@link JdbcInfo} or {@code null} when absent.
	 */
	private JdbcInfo resolveConfigurationJdbcInfo(final TelemetryManager telemetryManager) {
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
	 * @return the connector-level {@link JdbcInfo} or {@code null} when absent.
	 */
	private JdbcInfo resolveConnectorJdbcInfo(final String connectorId, final TelemetryManager telemetryManager) {
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
		return identity.getJdbc();
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
	 * Checks if the database is reachable using Connection.isValid().
	 *
	 * @param jdbcUrl  The JDBC URL of the database
	 * @param username The database username
	 * @param password The database password
	 * @param timeout  The timeout duration
	 * @return true if the database is reachable, false otherwise
	 */
	static boolean isDatabaseAlive(
		final String jdbcUrl,
		final String username,
		final char[] password,
		final long timeout
	) {
		// Ensure the appropriate driver is registered with DriverManager before connecting.
		JdbcDriverRegistryHolder.ensureDriverForUrl(jdbcUrl);

		try (
			Connection connection = (username == null || password == null)
				? DriverManager.getConnection(jdbcUrl)
				: DriverManager.getConnection(jdbcUrl, username, new String(password))
		) {
			return connection.isValid((int) timeout);
		} catch (SQLException e) {
			return false;
		}
	}
}
