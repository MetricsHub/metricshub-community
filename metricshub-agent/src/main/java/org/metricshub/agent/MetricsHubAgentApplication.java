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
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.ThreadContext;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.helper.AgentConstants;
import org.metricshub.agent.helper.ConfigHelper;
import org.metricshub.agent.service.ReloadService;
import org.metricshub.agent.service.ReloadService.ReloadResult;
import org.metricshub.agent.service.task.DirectoryWatcherTask;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.MetricsHubAgentServer;
import org.metricshub.web.service.AgentLifecycleService;
import picocli.CommandLine;
import picocli.CommandLine.Option;

/**
 * MetricsHub Agent application entry point.
 */
@Data
@Slf4j
public class MetricsHubAgentApplication implements Runnable {

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

			// Initialize the bootstrap agent context
			final var bootAgentContext = new AgentContext(alternateConfigDirectory, extensionManager);

			// Create the single source of truth for the running AgentContext. All subsequent
			// reads (Spring services, DirectoryWatcher, AgentLifecycleService, ...) go through
			// this holder, never through a closure-captured AgentContext.
			final var agentContextHolder = new AgentContextHolder(bootAgentContext);

			// Start OpenTelemetry Collector process on the bootstrap context
			bootAgentContext.getOtelCollectorProcessService().launch();

			// Start the Scheduler on the bootstrap context
			bootAgentContext.getTaskSchedulingService().start();

			// Start the Spring server on a separate thread, handing it the holder so the
			// AgentContextHolder singleton bean is our own instance (used by every service).
			new Thread(() -> MetricsHubAgentServer.startServer(agentContextHolder)).start();

			// Start the DirectoryWatcherTask to watch for changes in the configuration directory
			final Path configDirectory = bootAgentContext.getConfigDirectory();

			DirectoryWatcherTask.builder()
				.directory(configDirectory)
				.filter((WatchEvent<?> event) -> {
					final Object context = event.context();
					log.info("RELOAD - Directory Watcher Task event triggered.\nContext: " + context.toString());
					// CHECKSTYLE:OFF
					return (
						context != null &&
						agentContextHolder
							.getAgentContext()
							.getExtensionManager()
							.findConfigurationFileExtensions()
							.stream()
							.anyMatch(fileExtension -> context.toString().endsWith(fileExtension))
					);
					// CHECKSTYLE:ON
				})
				.await(CONFIG_WATCHER_AWAIT_MS)
				.checksumSupplier(() ->
					buildChecksum(agentContextHolder.getAgentContext().getExtensionManager(), configDirectory)
				)
				.onChange(() -> onConfigurationChange(agentContextHolder))
				.build()
				.start();
		} catch (Exception e) {
			configureGlobalErrorLogger();
			log.error("Failed to start MetricsHub Agent.", e);
			throw new IllegalStateException("Error dectected during MetricsHub agent startup.", e);
		}
	}

	/**
	 * Handles a configuration directory change detected by the {@link DirectoryWatcherTask}.
	 * <p>
	 * Compares the currently active {@link AgentContext} (obtained from the shared
	 * {@link AgentContextHolder}) with a freshly built one. On resource-only changes the
	 * {@link ReloadService} applies them in place. On global configuration changes the
	 * reload is delegated to {@link AgentLifecycleService#restartAsync(java.util.function.Supplier)}
	 * so both restart triggers (file edit and API call) share the same concurrency guard,
	 * status tracking and old-context disposal path.
	 * </p>
	 *
	 * @param agentContextHolder the shared holder, always read to get the freshest context
	 */
	void onConfigurationChange(final AgentContextHolder agentContextHolder) {
		final AgentContext currentContext = agentContextHolder.getAgentContext();

		// Build the new agent context eagerly so we can compare configurations
		final AgentContext newAgentContext = loadNewAgentContext();

		final ReloadService reloadService = ReloadService.builder()
			.withRunningAgentContext(currentContext)
			.withReloadedAgentContext(newAgentContext)
			.build();

		final ReloadResult result = reloadService.reload();

		switch (result) {
			case GLOBAL_RESTART_REQUIRED -> {
				// Route through the lifecycle service so the file-triggered restart shares
				// the same queue, coalescing policy and status tracking as the API-triggered
				// one.
				final AgentLifecycleService lifecycle = MetricsHubAgentServer.getBean(AgentLifecycleService.class);
				if (lifecycle == null) {
					log.warn("AgentLifecycleService is not available yet; discarding the freshly built context.");
					newAgentContext.close();
					return;
				}
				// The lifecycle service always accepts the request (SCHEDULED, QUEUED or
				// COALESCED). Ownership of newAgentContext is now with the service — do NOT
				// close it here. On COALESCED the service itself closes the previously queued
				// context.
				lifecycle.restartAsync(() -> newAgentContext);
			}
			case LOCAL_ONLY ->
				// ReloadService already grafted the required TelemetryManagers from newAgentContext
				// into the running one. The remaining state on newAgentContext is no longer needed.
				newAgentContext.close();
			case NO_CHANGE ->
				// The freshly built context is not needed
				newAgentContext.close();
			default -> log.warn("Unknown reload result: {}", result);
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
		return ConfigHelper.calculateDirectoryMD5ChecksumSafe(configDirectory, path ->
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
