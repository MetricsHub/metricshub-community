package org.metricshub.agent.m8b;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2026 MetricsHub
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
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.config.M8bConfig;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.fleet.AgentInstanceUid;
import org.metricshub.agent.fleet.FleetHeaders;
import org.metricshub.agent.m8b.protocol.AgentDescriptor;
import org.metricshub.agent.m8b.protocol.HostDescriptor;
import org.metricshub.agent.m8b.protocol.M8bMessage;
import org.metricshub.agent.m8b.protocol.M8bMessage.AgentRegister;
import org.metricshub.agent.m8b.protocol.M8bMessage.AgentRegistered;
import org.metricshub.agent.m8b.protocol.M8bMessage.HostsUpdated;
import org.metricshub.agent.m8b.protocol.M8bMessage.ToolInvoke;
import org.metricshub.agent.m8b.tunnel.M8bTunnelClient;
import org.metricshub.agent.m8b.tunnel.M8bTunnelListener;
import org.metricshub.agent.m8b.tunnel.M8bTunnelSettings;
import org.metricshub.web.AgentContextHolder;
import org.springframework.ai.tool.ToolCallbackProvider;

/**
 * Supervises the M8B tunnel for the lifetime of the JVM, outside the restartable
 * {@link AgentContext}: a periodic tick reads the current {@code m8b:} configuration, (re)builds the
 * tunnel client when it changes, retries a failed startup on the next tick, and pushes
 * {@code hosts.updated} when a configuration reload changed the monitored hosts.
 * <p>
 * The tool set is static for the JVM, so the registry snapshot is built once per configuration and
 * re-advertised on every (re)connection.
 * </p>
 */
@Slf4j
public class M8bService {

	static final long SUPERVISOR_PERIOD_SECONDS = 30;

	/**
	 * Loads the persistent agent uid; a seam for tests, which must not touch the security directory.
	 */
	@FunctionalInterface
	interface UidSupplier {
		String load() throws IOException;
	}

	private final AgentContextHolder agentContextHolder;
	private final ToolCallbackProvider toolCallbackProvider;
	private final BiFunction<M8bTunnelSettings, M8bTunnelListener, M8bTunnelClient> clientFactory;
	private final UidSupplier uidSupplier;
	private final ExecutorService toolWorkers;
	private final ScheduledExecutorService supervisor;

	private M8bTunnelClient client;
	private M8bToolBridge bridge;
	private ToolRegistrySnapshot snapshot;
	private M8bConfig activeConfig;
	// Written by the supervisor and by the tunnel thread building a registration. Volatile rather
	// than guarded: the tunnel thread must never wait on this service's monitor, which the
	// supervisor holds while stop() waits for the tunnel thread to run its closing task.
	private volatile long advertisedGeneration;
	private volatile List<HostDescriptor> advertisedHosts = List.of();
	/**
	 * The identity the current session was registered with.
	 *
	 * <p>Kept because the protocol has no way to amend it: {@code agent.register} is the only frame
	 * that carries a descriptor, so a reload that renames the host or adds an attribute can only
	 * reach the Governor through a new registration.
	 */
	private volatile AgentDescriptor advertisedDescriptor;

	/**
	 * @param agentContextHolder   the running agent context
	 * @param toolCallbackProvider the tools the agent exposes
	 */
	public M8bService(final AgentContextHolder agentContextHolder, final ToolCallbackProvider toolCallbackProvider) {
		this(agentContextHolder, toolCallbackProvider, M8bTunnelClient::new, AgentInstanceUid::loadOrCreate);
	}

	M8bService(
		final AgentContextHolder agentContextHolder,
		final ToolCallbackProvider toolCallbackProvider,
		final BiFunction<M8bTunnelSettings, M8bTunnelListener, M8bTunnelClient> clientFactory,
		final UidSupplier uidSupplier
	) {
		this.agentContextHolder = agentContextHolder;
		this.toolCallbackProvider = toolCallbackProvider;
		this.clientFactory = clientFactory;
		this.uidSupplier = uidSupplier;
		this.supervisor = Executors.newSingleThreadScheduledExecutor(runnable -> {
			final Thread thread = new Thread(runnable, "metricshub-m8b-supervisor");
			thread.setDaemon(true);
			return thread;
		});
		// One pool for the process, not one per bridge. A callback that ignores its interruption
		// keeps its thread through a reconfiguration too, so a ceiling that reset on every
		// configuration change would bound nothing: reload often enough with calls stuck and the
		// agent accumulates a fresh poolful of threads each time.
		this.toolWorkers = M8bToolBridge.newWorkerPool();
	}

	/**
	 * Starts the supervision; the first tick runs immediately.
	 */
	public void start() {
		supervisor.scheduleWithFixedDelay(this::superviseSafely, 0, SUPERVISOR_PERIOD_SECONDS, TimeUnit.SECONDS);
	}

	/**
	 * Stops the supervision and closes the tunnel.
	 */
	public synchronized void shutdown() {
		supervisor.shutdownNow();
		closeClient("Agent shutting down");
		toolWorkers.shutdownNow();
	}

	private void superviseSafely() {
		try {
			supervise();
		} catch (Exception e) {
			log.error("M8B supervision failed: {}", e.getMessage());
			log.debug("M8B supervision failed:", e);
		}
	}

	synchronized void supervise() {
		final AgentContext agentContext = agentContextHolder.getAgentContext();
		if (agentContext == null || agentContext.getAgentConfig() == null) {
			return;
		}
		final M8bConfig config = agentContext.getAgentConfig().getM8b();
		if (!Objects.equals(config, activeConfig)) {
			reconfigure(config);
			return;
		}
		if (client != null && client.isConnected() && agentContextHolder.getGeneration() != advertisedGeneration) {
			// A configuration reload swapped the agent context: re-advertise what actually changed
			final ContextSnapshot current = readContextSnapshot();
			final AgentDescriptor descriptor = AgentDescriptorMapper.map(current.context());
			if (!descriptor.equals(advertisedDescriptor)) {
				// There is no frame that amends an identity: agent.register is the only one that
				// carries a descriptor, so a renamed host or a new attribute reaches the Governor by
				// registering again. Reconnecting rebuilds the registration from the current context,
				// which is what the protocol already promises a reconnection does -- and it carries
				// the hosts with it, so nothing below needs to run.
				log.info("M8B tunnel: the agent's identity changed; reconnecting to re-register.");
				client.reconnect("Agent description changed");
				advertisedDescriptor = descriptor;
				return;
			}
			final List<HostDescriptor> hosts = HostInventory.from(current.context());
			if (!hosts.equals(advertisedHosts)) {
				if (!client.send(new HostsUpdated(hosts))) {
					// Too large for this server to accept, so it was not sent. Recording it as
					// advertised anyway would leave every later tick seeing nothing to do, and the
					// Governor routing on the old inventory for as long as the process ran. Left
					// unrecorded, the next tick tries again -- which costs a log line every thirty
					// seconds and recovers by itself if hosts are removed or the server's cap raised.
					return;
				}
				log.info("M8B tunnel: host inventory updated ({} host(s)).", hosts.size());
			}
			advertisedHosts = hosts;
			advertisedGeneration = current.generation();
		}
	}

	/**
	 * A context and the generation it belongs to.
	 *
	 * @param context    the agent context
	 * @param generation the generation of that context
	 */
	private record ContextSnapshot(AgentContext context, long generation) {}

	/**
	 * Reads the current context together with its generation.
	 * <p>
	 * The generation is read <em>first</em> and re-checked afterwards: recording a generation newer
	 * than the context it was taken from would make the next tick believe the new inventory had
	 * already been advertised, and the Governor would keep routing to stale hosts until the next
	 * reload or reconnection. The reverse mistake is harmless — the hosts are compared before
	 * anything is sent.
	 * </p>
	 *
	 * @return a consistent context and generation pair
	 */
	private ContextSnapshot readContextSnapshot() {
		long generation = agentContextHolder.getGeneration();
		AgentContext context = agentContextHolder.getAgentContext();
		if (generation != agentContextHolder.getGeneration()) {
			generation = agentContextHolder.getGeneration();
			context = agentContextHolder.getAgentContext();
		}
		return new ContextSnapshot(context, generation);
	}

	private void reconfigure(final M8bConfig newConfig) {
		closeClient("M8B configuration changed");
		activeConfig = newConfig;

		if (newConfig == null || !newConfig.isEnabled()) {
			log.info("M8B tunnel is disabled.");
			return;
		}
		final String endpoint = newConfig.getEndpoint();
		if (endpoint == null || endpoint.isBlank()) {
			log.warn("M8B tunnel is enabled but no endpoint is configured; the tunnel is not started.");
			return;
		}

		M8bTunnelClient newClient = null;
		try {
			snapshot = ToolRegistrySnapshot.from(toolCallbackProvider, newConfig.getExcludedTools());
			final M8bTunnelSettings settings = new M8bTunnelSettings(
				URI.create(endpoint.trim()),
				FleetHeaders.decrypt(newConfig.getHeaders(), "M8B"),
				newConfig.getCertificateFile(),
				uidSupplier.load(),
				Duration.ofSeconds(atLeastOneSecond(newConfig.getHeartbeatInterval()))
			);
			// The bridge answers on the client it was built for AND on the connection that asked.
			// Neither half is enough alone: resolving the current client at send time would let a
			// paused worker answer over a configuration that replaced its own, and pinning the
			// client would still let it answer over that client's next reconnection. Both are
			// dropped -- a stopped client discards what it is handed, and a live one compares the
			// generation on its own thread.
			final AtomicReference<M8bTunnelClient> owner = new AtomicReference<>();
			final M8bToolBridge newBridge = new M8bToolBridge(
				snapshot,
				(message, generation) -> {
					final M8bTunnelClient target = owner.get();
					if (target != null) {
						target.send(message, generation);
					}
				},
				toolWorkers
			);
			newClient = clientFactory.apply(settings, new TunnelListener(newBridge));
			owner.set(newClient);
			bridge = newBridge;
			client = newClient;
			newClient.start();
			log.info("M8B tunnel started toward {} advertising {} tool(s).", endpoint, snapshot.tools().size());
		} catch (Exception e) {
			if (newClient != null) {
				newClient.stop("M8B tunnel startup failed");
			}
			// Clear the active configuration so the next supervisor tick retries the startup: a
			// transient failure (e.g. missing CA file) must not disable the tunnel until a restart.
			activeConfig = null;
			client = null;
			closeBridge();
			log.error("Failed to start the M8B tunnel toward {}: {}", endpoint, e.getMessage());
			log.debug("Failed to start the M8B tunnel:", e);
		}
	}

	private void closeClient(final String reason) {
		if (client != null) {
			client.stop(reason);
			client = null;
		}
		closeBridge();
		advertisedHosts = List.of();
		advertisedDescriptor = null;
		advertisedGeneration = 0;
	}

	/**
	 * Releases a bridge. What its outstanding work might still answer is not this method's problem:
	 * every answer is bound to the connection that asked, and the client it was bound to is either
	 * stopped by now or on a newer generation, so a late result is dropped rather than delivered.
	 * The worker pool is deliberately untouched -- it belongs to the process.
	 */
	private void closeBridge() {
		if (bridge != null) {
			bridge.shutdown();
			bridge = null;
		}
	}

	private static long atLeastOneSecond(final long seconds) {
		if (seconds < 1) {
			log.warn(
				"Invalid m8b.heartbeatInterval {} second(s); using the default {} second(s).",
				seconds,
				M8bConfig.DEFAULT_HEARTBEAT_INTERVAL
			);
			return M8bConfig.DEFAULT_HEARTBEAT_INTERVAL;
		}
		return seconds;
	}

	/**
	 * Reactions to the tunnel: the registration payload reflects the current context, invocations go
	 * to the bridge.
	 */
	private final class TunnelListener implements M8bTunnelListener {

		private final M8bToolBridge tunnelBridge;

		private TunnelListener(final M8bToolBridge tunnelBridge) {
			this.tunnelBridge = tunnelBridge;
		}

		@Override
		public AgentRegister buildRegistration() {
			final ContextSnapshot current = readContextSnapshot();
			final List<HostDescriptor> hosts = HostInventory.from(current.context());
			final AgentDescriptor descriptor = AgentDescriptorMapper.map(current.context());
			advertisedHosts = hosts;
			advertisedDescriptor = descriptor;
			advertisedGeneration = current.generation();
			return new AgentRegister(M8bMessage.PROTOCOL_VERSION, descriptor, snapshot.revision(), snapshot.tools(), hosts);
		}

		@Override
		public void onRegistered(final AgentRegistered registered) {
			tunnelBridge.setLimits(registered);
		}

		@Override
		public void onDisconnected(final int code, final String reason, final long generation) {
			// The Governor discarded whatever it had asked for. The generation binding already stops
			// an answer from reaching the next session; this is about the slot, which the bridge
			// outliving the connection would otherwise hold until the invocation's own deadline.
			tunnelBridge.cancelGeneration(generation);
		}

		@Override
		public void onInvoke(final ToolInvoke invoke, final long generation) {
			// The generation travels with the request and comes back with the answer: the server
			// discarded what it had asked for when the connection dropped, and a result correlated
			// with a request the next session never made must never reach it.
			tunnelBridge.invoke(invoke, generation);
		}
	}
}
