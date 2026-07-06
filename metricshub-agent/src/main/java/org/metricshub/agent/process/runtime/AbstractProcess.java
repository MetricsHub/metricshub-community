package org.metricshub.agent.process.runtime;

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
import java.io.Reader;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.process.config.ProcessConfig;
import org.metricshub.agent.process.config.ProcessOutput;

/**
 * Extends this abstract class to create your own process implementation.<br>
 * Create a new instance using the {@link ProcessConfig} then invoke the <code>start()</code> method to start the process.
 * You can override the default behavior of this abstract class by implementing the following methods:
 * <ol>
 *   <li><code>onBeforeProcess()</code>: override this method to execute additional operations before the <code>start</code> procedure begins its executions.</li>
 *   <li><code>onBeforeProcessStart()</code>: override this method to update the process builder or to add additional actions before starting the process.</li>
 *   <li><code>onAfterProcessStart()</code>: override this method to execute additional operations after the process is started.</li>
 *   <li><code>onBeforeProcessStop()</code>: override this method to execute additional operations before terminating the process.</li>
 *   <li><code>onAfterProcessStop()</code>: override this method to execute additional operations after terminating the process.</li>
 *   <li><code>stopInternal()</code>: You MUST implement this method in order to provide your own stop strategy.
 *   					 You can call the <code>stopProcess()</code> method which destroys the process.
 *   					 But, for instance, you might want to terminate your process with a SIGTERM or SIGKILL.</li>
 * </ol>
 */
@Slf4j
public abstract class AbstractProcess implements IStoppable {

	@Getter
	protected final ProcessConfig processConfig;

	@Getter
	protected ProcessControl processControl;

	/**
	 * Whether the process is currently started. Writes happen inside the synchronized
	 * {@link #start()} / {@link #stop()} methods; volatile so unsynchronized readers
	 * (e.g. status reporting) always see the current value.
	 */
	@Getter
	protected volatile boolean started;

	/**
	 * The virtual-machine shutdown hook that stops the process when the JVM shuts down.
	 * Tracked so we can unregister it on {@link #stop()} and avoid accumulating a hook
	 * for every {@link #start()} call (e.g. when the process is restarted repeatedly).
	 */
	private Thread shutdownHook;

	protected AbstractProcess(final ProcessConfig processConfig) {
		this.processConfig = processConfig;
	}

	/**
	 * Starts the process.
	 * <p>
	 * Synchronized with {@link #stop()} so a concurrent stop (e.g. the JVM shutdown hook
	 * firing while a restart is launching the process) cannot interleave with the start
	 * sequence.
	 * </p>
	 *
	 * @throws IOException is thrown if the process cannot be started
	 */
	public synchronized void start() throws IOException {
		try {
			onBeforeProcess();

			// Get the process output to check if we should redirect STDERR
			final ProcessOutput output = processConfig.getOutput();

			// Create a new process builder
			final ProcessBuilder processBuilder = ProcessControl.newProcessBuilder(
				processConfig.getCommandLine(),
				processConfig.getEnvironment(),
				processConfig.getWorkingDir(),
				output != null && output.getErrorProcessor() == null
			);

			onBeforeProcessStart(processBuilder);

			// Start the process
			processControl = ProcessControl.start(processBuilder);

			// Register a new virtual-machine shutdown hook to stop the process.
			// Keep a reference so we can unregister it on stop() and avoid leaking
			// a hook per restart.
			shutdownHook = ProcessControl.addShutdownHook(this::stop);

			onAfterProcessStart();

			started = true;

			log.info("Started process with command line: {}", processConfig.getCommandLine());
		} catch (Exception e) {
			log.error("Cannot start the process due to an exception: {}", e.getMessage());
			log.debug("Exception: ", e);
			stop();
			throw e;
		}
	}

	/**
	 * Stops the process.
	 * <p>
	 * Synchronized so concurrent callers — the restart thread (via
	 * {@code OtelCollectorProcessService.stop()} / {@code AgentContext.close()}) and the JVM
	 * shutdown hook registered in {@link #start()} — serialize instead of both passing the
	 * {@code started} check and running the teardown twice. The second caller then sees
	 * {@code started == false} and returns without side effects (idempotent).
	 * </p>
	 */
	@Override
	public synchronized void stop() {
		if (started) {
			onBeforeProcessStop();

			stopInternal();

			started = false;

			onAfterProcessStop();

			log.info("Stopped process previously launched with command line: {}", processConfig.getCommandLine());
		}

		// Unregister the shutdown hook (if any) so we don't accumulate one per restart
		// and so the wrapper can be garbage-collected.
		unregisterShutdownHook();
	}

	/**
	 * Unregisters the shutdown hook associated with this process, if it was previously
	 * registered and the JVM is not already shutting down.
	 */
	private void unregisterShutdownHook() {
		final Thread hook = shutdownHook;
		if (hook == null) {
			return;
		}
		shutdownHook = null;
		try {
			Runtime.getRuntime().removeShutdownHook(hook);
		} catch (IllegalStateException e) {
			// JVM is already shutting down; nothing to unregister.
			log.debug("Could not unregister shutdown hook (JVM is shutting down): {}", e.getMessage());
		} catch (Exception e) {
			log.debug("Failed to unregister shutdown hook: {}", e.getMessage());
		}
	}

	/**
	 * Stop the process
	 */
	protected final void stopProcess() {
		if (processControl != null) {
			processControl.stop();
		}
	}

	/**
	 * Executes additional operations before starting the process
	 */
	protected abstract void onBeforeProcess();

	/**
	 * Updates the process builder before starting the process
	 *
	 * @param processBuilder This object is used to create operating system processes.
	 */
	protected abstract void onBeforeProcessStart(ProcessBuilder processBuilder);

	/**
	 * Executes additional operations after the process is started
	 */
	protected abstract void onAfterProcessStart();

	/**
	 * Executes operations before stopping the process
	 */
	protected abstract void onBeforeProcessStop();

	/**
	 * Execute operations after the process is stopped
	 */
	protected abstract void onAfterProcessStop();

	/**
	 * Stop the process
	 */
	protected abstract void stopInternal();

	/**
	 * Get the STDOUT reader
	 *
	 * @return {@link Reader} instance
	 */
	protected Reader getReader() {
		return processControl.getReader();
	}

	/**
	 * Get the STDERR reader
	 *
	 * @return {@link Reader} instance
	 */
	protected Reader getError() {
		return processControl.getError();
	}
}
