package org.metricshub.agent;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
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

import java.util.Locale;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.ThreadContext;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.helper.AgentConstants;
import org.metricshub.agent.helper.ConfigHelper;
import org.metricshub.agent.service.OtelCollectorProcessService;
import org.metricshub.agent.service.TaskSchedulingService;
import org.metricshub.agent.service.task.FileWatcherTask;
import org.metricshub.engine.extension.ExtensionManager;
import picocli.CommandLine;
import picocli.CommandLine.Option;

/**
 * MetricsHub Agent application entry point.
 */
@Data
@Slf4j
public class MetricsHubAgentApplication implements Runnable {
	static {
		Locale.setDefault(Locale.US);
	}

	@Option(names = { "-h", "-?", "--help" }, usageHelp = true, description = "Shows this help message and exits")
	private boolean usageHelpRequested;

	@Option(
		names = { "-c", "--config" },
		usageHelp = false,
		required = false,
		description = "Alternate MetricsHub's configuration file"
	)
	private String alternateConfigFile;

	/**
	 * The main entry point for the MetricsHub Agent application.
	 * Creates an instance of MetricsHubAgentApplication and executes it using CommandLine.
	 *
	 * @param args The command-line arguments passed to the application.
	 */
	public static void main(String[] args) {
		new CommandLine(new MetricsHubAgentApplication()).execute(args);
	}

	@Override
	public void run() {
		try {
			// Initialize the extension loader to load all the extensions which will be handled
			// by the ExtensionManager
			final ExtensionManager extensionManager = ConfigHelper.loadExtensionManager();

			// Initialize the application context
			final AgentContext agentContext = new AgentContext(alternateConfigFile, extensionManager);

			// Start OpenTelemetry Collector process
			agentContext.getOtelCollectorProcessService().launch();

			// Start the Scheduler
			agentContext.getTaskSchedulingService().start();

			FileWatcherTask
				.builder()
				.file(agentContext.getConfigFile())
				.filter(event ->
					event.context() != null && agentContext.getConfigFile().getName().equals(event.context().toString())
				)
				.checksum(ConfigHelper.calculateMD5Checksum(agentContext.getConfigFile()))
				.await(500)
				.onChange(() -> resetContext(agentContext, alternateConfigFile))
				.build()
				.start();
		} catch (Exception e) {
			configureGlobalErrorLogger();
			log.error("Failed to start MetricsHub Agent.", e);
			throw new IllegalStateException("Error dectected during MetricsHub agent startup.", e);
		}
	}

	/**
	 * Resets the agent context by restarting {@link TaskSchedulingService} and {@link OtelCollectorProcessService}:
	 * @param agentContext The agent context
	 * @param alternateConfigFile Alternation configuration file passed by the user
	 */
	private synchronized void resetContext(final AgentContext agentContext, String alternateConfigFile) {
		try {
			agentContext.getTaskSchedulingService().stop();
			agentContext.getOtelCollectorProcessService().stop();

			agentContext.build(alternateConfigFile, false);
			agentContext.getOtelCollectorProcessService().launch();
			agentContext.getTaskSchedulingService().start();
		} catch (Exception e) {
			configureGlobalErrorLogger();
			log.error("Failed to start MetricsHub Agent.", e);
			throw new IllegalStateException("Error detected during MetricsHub agent startup.", e);
		}
	}

	/**
	 * Configure the global error logger to be able to log startup fatal errors
	 * preventing the application from starting
	 */
	static void configureGlobalErrorLogger() {
		ThreadContext.put("logId", "metricshub-agent-global-error");
		ThreadContext.put("loggerLevel", Level.ERROR.toString());
		ThreadContext.put("outputDirectory", AgentConstants.DEFAULT_OUTPUT_DIRECTORY.toString());
	}
}
