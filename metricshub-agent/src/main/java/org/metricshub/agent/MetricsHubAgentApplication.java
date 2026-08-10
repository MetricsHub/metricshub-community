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
import java.util.ServiceConfigurationError;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.ThreadContext;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.helper.AgentConstants;
import org.metricshub.agent.helper.ConfigHelper;
import org.metricshub.agent.opamp.OpAmpService;
import org.metricshub.agent.process.runtime.ProcessControl;
import org.metricshub.agent.service.ReloadService;
import org.metricshub.agent.service.ReloadService.ReloadResult;
import org.metricshub.agent.service.task.DirectoryWatcherTask;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.AgentContextReaderTracker;
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

			// Start the OpAMP service at application level, outside the restartable AgentContext:
			// its supervisor re-reads the opamp: configuration from the holder and keeps the
			// OpAMP connection alive across configuration reloads.
			final var opAmpService = new OpAmpService(agentContextHolder);
			opAmpService.start();
			ProcessControl.addShutdownHook(opAmpService::shutdown);

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
		// The comparison build below invokes extension providers on this watcher thread — covered
		// by neither the scheduler-termination check nor the servlet reader tracker. Lease the
		// current generation so a concurrent /restart cannot retire the reused manager's loaders
		// while this reload is still using them.
		final AgentContextReaderTracker readerTracker = MetricsHubAgentServer.getBean(AgentContextReaderTracker.class);
		final long generation = agentContextHolder.getGeneration();
		if (readerTracker != null) {
			readerTracker.acquire(generation);
		}
		try {
			doOnConfigurationChange(agentContextHolder);
		} finally {
			if (readerTracker != null) {
				readerTracker.release(generation);
			}
		}
	}

	/**
	 * Performs the configuration-change handling under the reader lease taken by
	 * {@link #onConfigurationChange(AgentContextHolder)}.
	 *
	 * @param agentContextHolder the shared holder, always read to get the freshest context
	 */
	private void doOnConfigurationChange(final AgentContextHolder agentContextHolder) {
		final AgentContext currentContext = agentContextHolder.getAgentContext();

		// Build the new agent context eagerly so we can compare configurations, reusing the
		// boot-time extension manager (extensions do not change while the agent is running).
		final AgentContext newAgentContext = loadNewAgentContext(currentContext.getExtensionManager());

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
				// A full restart reloads the extensions as well, so a file-triggered restart picks up new
				// or updated extension jars exactly like the /restart endpoint. The comparison context only
				// reused the current manager to diff the configuration and is no longer needed; the restart
				// rebuilds a context with a freshly loaded extension manager, and AgentLifecycleService
				// releases the previous loaders after a grace delay.
				newAgentContext.close();
				lifecycle.restartAsync(this::reloadExtensionsAndBuildContext);
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
	 * Reloads the extensions and builds the {@link AgentContext} served to a full restart. When the
	 * context construction fails (for example, an invalid reloaded configuration), the freshly
	 * loaded manager's isolated class loaders are closed before propagating, so repeated failed
	 * restarts cannot accumulate open jar handles.
	 *
	 * @return the new context carrying a freshly loaded {@link ExtensionManager}
	 */
	private AgentContext reloadExtensionsAndBuildContext() {
		final ExtensionManager reloadedExtensionManager = ConfigHelper.loadExtensionManager();
		try {
			return loadNewAgentContext(reloadedExtensionManager);
		} catch (Exception | ServiceConfigurationError | LinkageError e) {
			// A provider invoked during the context build can throw linkage/service errors too:
			// close the freshly loaded manager and surface a failure the lifecycle service records.
			reloadedExtensionManager.close();
			throw new IllegalStateException("Failed to build the reloaded AgentContext: " + e.getMessage(), e);
		}
	}

	/**
	 * Loads a new AgentContext which will be used in the reload service.
	 * <p>
	 * The {@link ExtensionManager} is loaded once at boot and carried forward across configuration
	 * reloads rather than rebuilt (the watcher observes the configuration directory, not the
	 * extensions directory); only a full restart supplies a freshly reloaded manager via
	 * {@link #reloadExtensionsAndBuildContext()}.
	 * </p>
	 *
	 * @param extensionManager the extension manager to carry in the new context
	 */
	private synchronized AgentContext loadNewAgentContext(final ExtensionManager extensionManager) {
		try {
			// Initialize the application context reusing the boot-time extension manager
			return new AgentContext(alternateConfigDirectory, extensionManager);
		} catch (Exception e) {
			configureGlobalErrorLogger();
			log.error("Failed to reload the Agent.", e);
			throw new IllegalStateException("Error detected during MetricsHub agent reloading.", e);
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
