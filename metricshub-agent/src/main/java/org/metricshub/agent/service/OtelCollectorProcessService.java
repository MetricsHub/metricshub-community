package org.metricshub.agent.service;

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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.ThreadContext;
import org.metricshub.agent.config.AgentConfig;
import org.metricshub.agent.config.otel.OtelCollectorConfig;
import org.metricshub.agent.config.otel.OtelCollectorOutput;
import org.metricshub.agent.fleet.EnrollmentCoordinator;
import org.metricshub.agent.process.config.ProcessOutput;
import org.metricshub.agent.process.io.LineReaderProcessor;
import org.metricshub.agent.process.io.ProcessorHelper;
import org.metricshub.agent.process.runtime.AbstractProcess;

/**
 * OpenTelemetry Collector process service
 */
@Slf4j
public class OtelCollectorProcessService extends AbstractProcess {

	/**
	 * The environment variable that gives the collector the path of the file holding the agent's current token.
	 */
	public static final String TOKEN_FILE_VARIABLE = "METRICSHUB_AGENT_TOKEN_FILE";

	private AgentConfig agentConfig;

	private EnrollmentCoordinator enrollment = EnrollmentCoordinator.global();

	/** The token file the collector reads, {@code null} without {@code central.auth}. */
	private volatile Path tokenFile;

	/**
	 * Set by {@link #stop()}, cleared by {@link #launch()}: a launch that waited for the token file never starts a
	 * collector stopped meanwhile. Read and written under this object's lock, as {@code start()} and {@code stop()}.
	 */
	private boolean stopped;

	/**
	 * Creates a new instance of OtelCollectorProcessService.
	 *
	 * @param agentConfig The configuration for the MetricsHub Agent.
	 */
	public OtelCollectorProcessService(final AgentConfig agentConfig) {
		super(agentConfig.getOtelCollector().toProcessConfig());
		this.agentConfig = agentConfig;
	}

	@Override
	protected void onBeforeProcess() {
		// Not implemented
	}

	@Override
	protected void onBeforeProcessStart(ProcessBuilder processBuilder) {
		final Path file = tokenFile;
		// Only the path: the token, the agent uid and its secret never reach the collector's environment
		if (file != null) {
			processBuilder.environment().put(TOKEN_FILE_VARIABLE, file.toString());
		}
	}

	@Override
	protected void onAfterProcessStart() {
		final ProcessOutput outputConfig = processConfig.getOutput();

		// Is there an output configuration defining output processors?
		if (outputConfig != null) {
			// Connect the reader to the output processor
			ProcessorHelper.connect(getReader(), outputConfig.getOutputProcessor(), LineReaderProcessor::new);

			// Connect the error reader to the error processor
			// If the error processor is not present then the error stream is redirected to output stream
			ProcessorHelper.connect(getError(), outputConfig.getErrorProcessor(), LineReaderProcessor::new);
		}
	}

	@Override
	protected void onBeforeProcessStop() {
		// Not implemented
	}

	@Override
	public synchronized void start() throws IOException {
		if (stopped) {
			log.debug("The OpenTelemetry Collector was stopped before its deferred launch: it is not started.");
			return;
		}
		super.start();
	}

	@Override
	public synchronized void stop() {
		stopped = true;
		super.stop();
	}

	/**
	 * @param enrollment the enrollment coordinator (tests)
	 */
	void setEnrollment(final EnrollmentCoordinator enrollment) {
		this.enrollment = enrollment;
	}

	@Override
	protected void stopInternal() {
		super.stopProcess();
	}

	@Override
	protected void onAfterProcessStop() {
		// Not implemented
	}

	/**
	 * Launch the OpenTelemetry Collector. If the configuration specifies that the collector must
	 * be disabled, the collector startup will not be initiated. With {@code central.auth}, the launch waits until
	 * the agent is enrolled and its first token is written to the token file the collector authenticates with.
	 */
	public void launch() {
		if (agentConfig.getOtelCollector().isDisabled()) {
			log.info("The MetricsHub Agent is configured to not start the OpenTelemetry Collector.");
			return;
		}

		// Ensure that the OpenTelemetry Collector Contrib executable is installed
		if (!isExecutableInstalled()) {
			log.info(
				"The MetricsHub Agent will not start the OpenTelemetry Collector because the executable is not present."
			);
			return;
		}

		synchronized (this) {
			stopped = false;
		}

		if (!EnrollmentCoordinator.isConfigured(agentConfig.getCentral())) {
			tokenFile = null;
			launchNow();
			return;
		}

		final boolean deferred = enrollment.whenTokenFileWritten(agentConfig.getCentral(), file -> {
			tokenFile = file;
			launchNow();
		});
		if (deferred) {
			log.info(
				"The OpenTelemetry Collector starts once the agent is enrolled into MetricsHub Central and holds its first token."
			);
		}
	}

	/**
	 * Starts the collector process and waits for it, at most its startup delay.
	 */
	private void launchNow() {
		final OtelCollectorConfig otelCollectorConfig = agentConfig.getOtelCollector();

		// The subprocess must be ran using a separate thread so that it creates a log context for himself only.
		// The process output will be redirected to a dedicated file, see how log4j2.xml is configured using the
		// thread context pattern.
		// Use a daemon thread so a leaked executor never keeps the JVM alive.
		final ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor(runnable -> {
			final Thread thread = new Thread(runnable, "otelcol-launcher");
			thread.setDaemon(true);
			return thread;
		});

		try {
			// Run the execution
			singleThreadExecutor.submit(() -> {
				// Configure logger
				ThreadContext.put("logId", OtelCollectorConfig.EXECUTABLE_OUTPUT_ID);

				// By default otelcol debug is enabled
				String loggerLevel = Level.DEBUG.name();

				// User might want to disable logging for the process
				if (otelCollectorConfig.getOutput() != OtelCollectorOutput.LOG) {
					loggerLevel = Level.OFF.name();
				}

				ThreadContext.put("loggerLevel", loggerLevel);

				final String outputDirectory = agentConfig.getOutputDirectory();
				if (outputDirectory != null) {
					ThreadContext.put("outputDirectory", outputDirectory);
				}

				// Start the executable
				try {
					start();
				} catch (Exception e) {
					log.error("Could not start process using command line: {}.", processConfig.getCommandLine());
					log.debug("Error: ", e);
				}
			});

			// Signal the executor that no more tasks will be submitted so that
			// awaitTermination can return as soon as the launch task completes,
			// instead of always blocking for the full startupDelay.
			singleThreadExecutor.shutdown();

			try {
				if (!singleThreadExecutor.awaitTermination(otelCollectorConfig.getStartupDelay(), TimeUnit.SECONDS)) {
					log.warn(
						"OpenTelemetry Collector did not finish starting within {} seconds. Command line: {}.",
						otelCollectorConfig.getStartupDelay(),
						processConfig.getCommandLine()
					);
				}
			} catch (InterruptedException e) {
				log.error(
					"Startup process has been interrupted after {} seconds. Command line: {}.",
					otelCollectorConfig.getStartupDelay(),
					processConfig.getCommandLine()
				);
				log.debug("Error: ", e);
				Thread.currentThread().interrupt();
			}
		} finally {
			// Ensure the executor is not leaked; the launched process itself
			// keeps running because it is a separate OS process.
			if (!singleThreadExecutor.isTerminated()) {
				singleThreadExecutor.shutdownNow();
			}
		}
	}

	/**
	 * Checks whether the executable is installed or not.
	 *
	 * @return true if installed, false otherwise.
	 */
	public boolean isExecutableInstalled() {
		final List<String> commandLine = processConfig.getCommandLine();
		return !commandLine.isEmpty() && Files.exists(Paths.get(commandLine.get(0)));
	}
}
