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

import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.Locale;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.ThreadContext;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.helper.AgentConstants;
import org.metricshub.agent.helper.ConfigHelper;
import org.metricshub.agent.service.ReloadService;
import org.metricshub.agent.service.task.DirectoryWatcherTask;
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

	/**
	 * Default milliseconds await delay for the DirectoryWatcherTask.
	 */
	private static final long CONFIG_WATCHER_AWAIT_MS = 500L;

	@Option(names = { "-h", "-?", "--help" }, usageHelp = true, description = "Shows this help message and exits")
	private boolean usageHelpRequested;

	@Option(
		names = { "-c", "--config" },
		usageHelp = false,
		required = false,
		description = "Alternate MetricsHub's configuration directory"
	)
	private String alternateConfigDirectory;

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
			final var extensionManager = ConfigHelper.loadExtensionManager();

			// Initialize the application context
			final var agentContext = new AgentContext(alternateConfigDirectory, extensionManager);

			// Start OpenTelemetry Collector process
			agentContext.getOtelCollectorProcessService().launch();

			// Start the Scheduler
			agentContext.getTaskSchedulingService().start();

			// Start the DirectoryWatcherTask to watch for changes in the configuration directory
			final Path configDirectory = agentContext.getConfigDirectory();

			DirectoryWatcherTask
				.builder()
				.directory(configDirectory)
				.filter((WatchEvent<?> event) -> {
					final Object context = event.context();
					log.info("RELOAD - Directory Watcher Task event triggered.\nContext: " + context.toString());
					// CHECKSTYLE:OFF
					return (
						context != null &&
						extensionManager
							.findConfigurationFileExtensions()
							.stream()
							.anyMatch(fileExtension -> context.toString().endsWith(fileExtension))
					);
					// CHECKSTYLE:ON
				})
				.await(CONFIG_WATCHER_AWAIT_MS)
				.checksumSupplier(() -> buildChecksum(extensionManager, configDirectory))
				.onChange(() -> {
					// Create a new Agent Context to use in the reload service
					final AgentContext newAgentContext = loadNewAgentContext();

					// Reload the agent according to the new Agent Context
					ReloadService
						.builder()
						.withRunningAgentContext(agentContext)
						.withReloadedAgentContext(newAgentContext)
						.build()
						.reload();
				})
				.build()
				.start();
		} catch (Exception e) {
			configureGlobalErrorLogger();
			log.error("Failed to start MetricsHub Agent.", e);
			throw new IllegalStateException("Error dectected during MetricsHub agent startup.", e);
		}
	}

	/**
	 * Loads a new AgentContext which will be used in the reload service
	 */
	private synchronized AgentContext loadNewAgentContext() {
		try {
			// Initialize the application context
			return new AgentContext(alternateConfigDirectory, ConfigHelper.loadExtensionManager());
		} catch (Exception e) {
			configureGlobalErrorLogger();
			log.error("Failed to reload the Agent.", e);
			throw new IllegalStateException("Error dectected during MetricsHub agent reloading.", e);
		}
	}

	/**
	 * Builds the checksum of the configuration directory.
	 *
	 * @param extensionManager The extension manager
	 * @param configDirectory  The agent configuration directory
	 * @return The checksum of the configuration directory
	 */
	private static String buildChecksum(final ExtensionManager extensionManager, final Path configDirectory) {
		return ConfigHelper.calculateDirectoryMD5ChecksumSafe(
			configDirectory,
			path ->
				extensionManager
					.findConfigurationFileExtensions()
					.stream()
					.anyMatch(fileExtension -> path.toString().endsWith(fileExtension))
		);
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
