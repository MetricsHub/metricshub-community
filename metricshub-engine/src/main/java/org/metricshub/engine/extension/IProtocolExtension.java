package org.metricshub.engine.extension;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
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

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.connector.model.identity.criterion.Criterion;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * Defines the contract for protocol extensions within the system. Protocol extensions are responsible for
 * executing network queries for data exchange such as HTTP, SNMP, WBEM, WMI, SSH, etc
 * standards. Implementations of this interface must provide mechanisms to validate configurations, determine
 * support for specific sources and criteria, perform protocol checks, and execute operations related to sources
 * and criteria.
 */
public interface IProtocolExtension {
	/**
	 * Checks if the given configuration is valid for this extension. This method
	 * is intended to validate the configuration credentials to ensure they meet
	 * the expectations of the specific protocol extension.
	 *
	 * @param configuration Represents the generic configuration that's used and
	 *                      implemented by various protocol configurations.
	 * @return {@code true} if the configuration is valid, otherwise {@code false}.
	 */
	boolean isValidConfiguration(IConfiguration configuration);

	/**
	 * Retrieves the set of source classes that this extension supports. These sources
	 * define the protocol query this extension uses to fetch data.
	 *
	 * @return A set of classes extending {@link Source}, representing the supported sources.
	 */
	Set<Class<? extends Source>> getSupportedSources();

	/**
	 * Provides a mapping between configuration classes and their corresponding sets
	 * of source classes.<br>
	 * This method must provide a strict mapping between the configuration
	 * and the evident source to process.
	 *
	 * @return A map where the keys are classes extending {@link IConfiguration}
	 *         representing different types of protocol configurations, and the
	 *         values are sets of classes extending {@link Source}, indicating the
	 *         sources that are compatible and can be utilized with each
	 *         configuration type for data exchange operations.
	 */
	Map<Class<? extends IConfiguration>, Set<Class<? extends Source>>> getConfigurationToSourceMapping();

	/**
	 * Retrieves the set of criterion classes that this extension supports. Criteria
	 * represent specific conditions or checks that the engine execute to match connectors.
	 *
	 * @return A set of classes extending {@link Criterion}, representing the supported criteria.
	 */
	Set<Class<? extends Criterion>> getSupportedCriteria();

	/**
	 * Performs a protocol check based on the given telemetry manager and its strategy time. This method
	 * is used to verify if a specific protocol or condition is met for the hostname defined by the
	 * telemetry manager.
	 *
	 * @param telemetryManager The telemetry manager to use for monitoring.
	 * @return Optional.of(true) if the protocol check succeeds, Optional.of(false) otherwise.
	 */
	Optional<Boolean> checkProtocol(TelemetryManager telemetryManager);

	/**
	 * Executes a source operation based on the given source and configuration within the telemetry manager.
	 *
	 * @param source           The source to execute.
	 * @param connectorId      The unique identifier of the connector.
	 * @param telemetryManager The telemetry manager to use for monitoring.
	 * @return A {@link SourceTable} object representing the result of the source execution.
	 */
	SourceTable processSource(Source source, String connectorId, TelemetryManager telemetryManager);

	/**
	 * Executes a criterion check based on the given criterion and configuration within the telemetry manager.
	 *
	 * @param criterion        The criterion to execute.
	 * @param connectorId      The unique identifier of the connector.
	 * @param telemetryManager The telemetry manager to use for monitoring.
	 * @return A {@link CriterionTestResult} object representing the result of the criterion execution.
	 */
	CriterionTestResult processCriterion(Criterion criterion, String connectorId, TelemetryManager telemetryManager);

	/**
	 * Whether the configuration type expressed in the {@code configurationType} argument is supported or not
	 * @param configurationType A string representing the type of configuration to be checked. This type is used
	 *                          to select the appropriate configuration constructor through the {@code buildConfiguration}
	 *                          method.
	 * @return <code>true</code> if the configuration type is supported otherwise <code>false</code>.
	 */
	boolean isSupportedConfigurationType(String configurationType);

	/**
	 * Creates and returns a configuration object of the specified type based on the provided JSON node.
	 * This method is designed to parse and construct a configuration instance specific to a protocol (e.g., HTTP, SNMP)
	 * using the provided JSON structure. If the provided JSON node contains valid data for constructing a configuration,
	 * an {@link IConfiguration} configuration instance is returned. If the JSON node does not
	 * contain valid data or if the specified configuration type is not supported, the {@link InvalidConfigurationException} should
	 * be thrown.
	 *
	 * @param configurationType A string representing the type of configuration to be checked. This type is used
	 *                          to select the appropriate configuration constructor. This type should be used when several
	 *                          configurations are managed by the same extension.
	 * @param jsonNode          A {@link JsonNode} containing the configuration data in JSON format. This data is parsed
	 *                          to construct the configuration object.
	 * @param decrypt           Decrypt function.
	 * @return An {@link Optional} containing the created {@link IConfiguration} object if the construction is successful
	 *         and the JSON data is valid; otherwise, the {@link InvalidConfigurationException} should be thrown.
	 * @throws InvalidConfigurationException if the provided {@link JsonNode} is invalid and cannot be parsed.
	 */
	IConfiguration buildConfiguration(String configurationType, JsonNode jsonNode, UnaryOperator<char[]> decrypt)
		throws InvalidConfigurationException;

	/**
	 * Returns the identifier for protocol extension.
	 *
	 * @return The protocol identifier as a string.
	 */
	String getIdentifier();

	/**
	 * Executes a query based on the provided configuration and query parameters.
	 *
	 * @param configuration the IConfiguration object containing the configuration details.
	 * @param queryNode     a JsonNode representing the query to be executed.
	 * @throws Exception if the query execution fails due to an error or unexpected condition.
	 */
	String executeQuery(IConfiguration configuration, JsonNode queryNode) throws Exception;
}
