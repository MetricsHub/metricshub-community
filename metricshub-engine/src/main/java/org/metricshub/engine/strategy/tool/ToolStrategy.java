package org.metricshub.engine.strategy.tool;

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

import java.util.ArrayList;
import java.util.Map;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.JobInfo;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.tool.ConnectorTool;
import org.metricshub.engine.connector.model.tool.ToolResult;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.strategy.AbstractStrategy;
import org.metricshub.engine.strategy.source.OrderedSources;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.strategy.surrounding.AfterAllStrategy;
import org.metricshub.engine.strategy.surrounding.BeforeAllStrategy;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * Runs one connector tool on a host: the connector's {@code beforeAll} sources, then the tool's sources and computes
 * with the invocation arguments substituted for {@code ${param::name}}, and keeps the table of the tool's result
 * source. The source tables land in the connector namespace of the telemetry manager, like a monitor job's, so the
 * caller runs it on a telemetry manager it owns rather than on the one being collected.
 */
@Slf4j
@EqualsAndHashCode(callSuper = true)
public class ToolStrategy extends AbstractStrategy {

	/**
	 * The job name under which the tool's sources are logged.
	 */
	public static final String JOB_NAME = "tool";

	private final Connector connector;
	private final String toolName;
	private final Map<String, String> arguments;

	/**
	 * The table of the tool's result source, empty until {@link #run()} completes.
	 */
	@Getter
	private SourceTable result = SourceTable.empty();

	/**
	 * Builds the strategy.
	 *
	 * @param telemetryManager the host to run the tool on
	 * @param strategyTime     the time of the invocation
	 * @param clientsExecutor  the executor of the protocol clients
	 * @param extensionManager the extensions that process the sources
	 * @param connector        the connector declaring the tool
	 * @param toolName         the name of the tool in the connector's {@code tools:} section
	 * @param arguments        the arguments of the invocation, by parameter name, {@code null} for none
	 */
	@Builder
	public ToolStrategy(
		@NonNull final TelemetryManager telemetryManager,
		@NonNull final Long strategyTime,
		@NonNull final ClientsExecutor clientsExecutor,
		@NonNull final ExtensionManager extensionManager,
		@NonNull final Connector connector,
		@NonNull final String toolName,
		final Map<String, String> arguments
	) {
		super(telemetryManager, strategyTime, clientsExecutor, extensionManager);
		this.connector = connector;
		this.toolName = toolName;
		this.arguments = arguments;
	}

	@Override
	public void run() {
		final String connectorId = connector.getCompiledFilename();
		final String hostname = telemetryManager.getHostname();

		final ConnectorTool tool = connector.getTools().get(toolName);
		if (tool == null) {
			throw new IllegalArgumentException("Connector %s declares no tool named %s".formatted(connectorId, toolName));
		}
		final ToolResult toolResult = tool.getResult();
		if (toolResult == null || toolResult.source() == null) {
			throw new IllegalStateException(
				"Tool %s of connector %s declares no result source".formatted(toolName, connectorId)
			);
		}

		// Defaults applied, required arguments checked
		final Map<String, String> resolvedArguments = tool.toDefinition(toolName).resolveArguments(arguments);

		// The names only: a value may be a token or a password
		log.debug(
			"Hostname {} - Running tool {} of connector {} with argument(s) {}.",
			hostname,
			toolName,
			connectorId,
			resolvedArguments.keySet()
		);

		// The tool's sources may reference the connector's beforeAll sources, like a monitor job's: a session
		// token fetched there is what a REST tool authenticates with
		BeforeAllStrategy.builder()
			.clientsExecutor(clientsExecutor)
			.strategyTime(strategyTime)
			.telemetryManager(telemetryManager)
			.connector(connector)
			.extensionManager(extensionManager)
			.build()
			.run();

		try {
			final JobInfo jobInfo = JobInfo.builder()
				.hostname(hostname)
				.connectorId(connectorId)
				.jobName(JOB_NAME)
				.monitorType(toolName)
				.build();

			final OrderedSources orderedSources = OrderedSources.builder()
				.sources(tool.getSources(), new ArrayList<>(tool.getExecutionOrder()), tool.getSourceDep(), jobInfo)
				.build();

			// The arguments ride on the attribute map: ${param::name} is replaced in sources and computes
			// exactly where ${attribute::name} is
			processSourcesAndComputes(orderedSources.getSources(), resolvedArguments, jobInfo);

			result = SourceTable.lookupSourceTable(toolResult.source(), connectorId, telemetryManager).orElseGet(
				SourceTable::empty
			);
		} finally {
			// Whatever beforeAll opened, afterAll closes: a tool must not leave a session behind on a host that
			// counts them, whether or not its sources succeeded
			AfterAllStrategy.builder()
				.clientsExecutor(clientsExecutor)
				.strategyTime(strategyTime)
				.telemetryManager(telemetryManager)
				.connector(connector)
				.extensionManager(extensionManager)
				.build()
				.run();
		}
	}
}
