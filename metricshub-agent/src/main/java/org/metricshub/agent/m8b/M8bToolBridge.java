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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.m8b.protocol.M8bJson;
import org.metricshub.agent.m8b.protocol.M8bMessage;
import org.metricshub.agent.m8b.protocol.M8bMessage.AgentRegistered;
import org.metricshub.agent.m8b.protocol.M8bMessage.ToolError;
import org.metricshub.agent.m8b.protocol.M8bMessage.ToolInvoke;
import org.metricshub.agent.m8b.protocol.M8bMessage.ToolResult;
import org.metricshub.agent.m8b.protocol.ToolErrorCode;
import org.springframework.ai.tool.ToolCallback;

/**
 * Executes the tools the M8B Governor invokes through the tunnel, using the very Spring AI
 * {@link ToolCallback}s the agent already exposes to its local AI features.
 * <p>
 * Every invocation is checked against the advertised snapshot (unknown tool), the server-imposed
 * concurrency cap and payload cap, and bounded by the request deadline: exactly one
 * {@code tool.result} or {@code tool.error} answers each request.
 * </p>
 */
@Slf4j
public class M8bToolBridge {

	/** Limits applied until the server sends its own. */
	static final AgentRegistered DEFAULT_LIMITS = new AgentRegistered(30, 8L * 1024 * 1024, 1);

	private final ToolRegistrySnapshot snapshot;
	private final Consumer<M8bMessage> sender;
	private final ExecutorService workers;
	private final ScheduledThreadPoolExecutor timer;
	private final AtomicInteger inFlight = new AtomicInteger();
	/**
	 * Bumped whenever the tunnel session ends. An invocation answers only while its own epoch is
	 * current: the server discarded the request when the connection dropped, so a late answer would
	 * land on the next session under a request id that means nothing there.
	 */
	private final AtomicInteger epoch = new AtomicInteger();
	private volatile AgentRegistered limits = DEFAULT_LIMITS;

	/**
	 * @param snapshot the advertised tools and their callbacks
	 * @param sender   where answers go, typically {@code client::send}
	 */
	public M8bToolBridge(final ToolRegistrySnapshot snapshot, final Consumer<M8bMessage> sender) {
		this.snapshot = snapshot;
		this.sender = sender;
		this.workers = Executors.newCachedThreadPool(daemonThreads("metricshub-m8b-tool"));
		this.timer = new ScheduledThreadPoolExecutor(1, daemonThreads("metricshub-m8b-tool-timer"));
		// Cancelling a deadline must drop it from the queue at once: otherwise an answered request
		// keeps its arguments alive until the timeout would have fired
		this.timer.setRemoveOnCancelPolicy(true);
	}

	/**
	 * @param limits the limits the server imposed at registration
	 */
	public void setLimits(final AgentRegistered limits) {
		this.limits = limits == null ? DEFAULT_LIMITS : limits;
	}

	/**
	 * @return the number of invocations currently running
	 */
	public int inFlight() {
		return inFlight.get();
	}

	/**
	 * @return the number of deadline tasks still queued; zero once every answered invocation has
	 *         released its timer slot
	 */
	int pendingDeadlines() {
		return timer.getQueue().size();
	}

	/**
	 * Runs a tool invocation asynchronously and answers through the sender.
	 *
	 * @param invoke the request
	 */
	public void invoke(final ToolInvoke invoke) {
		final ToolCallback callback = snapshot.callbacks().get(invoke.tool());
		if (callback == null) {
			sender.accept(
				new ToolError(invoke.requestId(), ToolErrorCode.TOOL_NOT_AVAILABLE, "Tool not advertised: " + invoke.tool())
			);
			return;
		}
		final AgentRegistered currentLimits = limits;
		if (inFlight.incrementAndGet() > currentLimits.maxInFlight()) {
			inFlight.decrementAndGet();
			sender.accept(
				new ToolError(
					invoke.requestId(),
					ToolErrorCode.TOO_MANY_INFLIGHT,
					"Already running " + currentLimits.maxInFlight() + " invocation(s)"
				)
			);
			return;
		}

		final AtomicBoolean answered = new AtomicBoolean();
		final int invokeEpoch = epoch.get();
		// Holds the deadline task so the worker can drop it as soon as it answered: an uncancelled
		// task keeps the request and its arguments in the timer queue for the whole timeout.
		final AtomicReference<ScheduledFuture<?>> deadline = new AtomicReference<>();
		final long startedAt = System.nanoTime();
		final Future<?> execution = workers.submit(() -> {
			final M8bMessage answer;
			try {
				answer = execute(invoke, callback, currentLimits, startedAt);
			} finally {
				// Free the slot before the answer leaves: the server may invoke again right away
				inFlight.decrementAndGet();
			}
			answerOnce(answered, invokeEpoch, answer);
			cancel(deadline.getAndSet(null));
		});
		final long timeoutMs = invoke.timeoutMs() > 0 ? invoke.timeoutMs() : TimeUnit.SECONDS.toMillis(300);
		final ScheduledFuture<?> scheduled = timer.schedule(
			() -> {
				if (
					answerOnce(
						answered,
						invokeEpoch,
						new ToolError(invoke.requestId(), ToolErrorCode.TIMEOUT, "Timed out after " + timeoutMs + " ms")
					)
				) {
					log.warn("M8B tool '{}' (request {}) timed out after {} ms.", invoke.tool(), invoke.requestId(), timeoutMs);
					execution.cancel(true);
				}
			},
			timeoutMs,
			TimeUnit.MILLISECONDS
		);
		deadline.set(scheduled);
		if (answered.get()) {
			// The tool finished before the handle was stored
			cancel(deadline.getAndSet(null));
		}
	}

	private static void cancel(final ScheduledFuture<?> future) {
		if (future != null) {
			future.cancel(false);
		}
	}

	/**
	 * Releases the worker threads. Running invocations are interrupted.
	 */
	public void shutdown() {
		timer.shutdownNow();
		workers.shutdownNow();
	}

	private M8bMessage execute(
		final ToolInvoke invoke,
		final ToolCallback callback,
		final AgentRegistered currentLimits,
		final long startedAt
	) {
		final String arguments =
			invoke.arguments() == null || invoke.arguments().isNull() ? "{}" : invoke.arguments().toString();
		final String output;
		try {
			output = callback.call(arguments);
		} catch (IllegalArgumentException e) {
			return new ToolError(invoke.requestId(), ToolErrorCode.INVALID_ARGUMENTS, messageOf(e));
		} catch (Exception e) {
			log.warn("M8B tool '{}' (request {}) failed: {}", invoke.tool(), invoke.requestId(), messageOf(e));
			log.debug("M8B tool failed:", e);
			return new ToolError(invoke.requestId(), ToolErrorCode.EXECUTION_ERROR, messageOf(e));
		}
		final long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
		final ToolResult result = new ToolResult(invoke.requestId(), toJson(output), durationMs);
		final int size = M8bJson.write(result).getBytes(StandardCharsets.UTF_8).length;
		if (size > currentLimits.maxPayloadBytes()) {
			return new ToolError(
				invoke.requestId(),
				ToolErrorCode.RESULT_TOO_LARGE,
				"Result is " + size + " bytes, above the " + currentLimits.maxPayloadBytes() + " bytes cap"
			);
		}
		return result;
	}

	/**
	 * Tool callbacks return JSON text; anything else (a plain message) is carried as a JSON string.
	 */
	private static JsonNode toJson(final String output) {
		if (output == null) {
			return M8bJson.MAPPER.nullNode();
		}
		try {
			return M8bJson.MAPPER.readTree(output);
		} catch (JsonProcessingException e) {
			return M8bJson.MAPPER.getNodeFactory().textNode(output);
		}
	}

	private boolean answerOnce(final AtomicBoolean answered, final int invokeEpoch, final M8bMessage answer) {
		if (invokeEpoch != epoch.get()) {
			return false;
		}
		if (answered.compareAndSet(false, true)) {
			sender.accept(answer);
			return true;
		}
		return false;
	}

	/**
	 * Drops the answers of everything still running: their tunnel session is gone.
	 *
	 * <p>The work itself is left to finish on its own — a tool call is a network round trip to a
	 * monitored host, and interrupting it buys nothing the deadline will not take care of.
	 */
	public void cancelSessionWork() {
		epoch.incrementAndGet();
	}

	private static String messageOf(final Exception e) {
		return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
	}

	private static ThreadFactory daemonThreads(final String prefix) {
		final AtomicInteger counter = new AtomicInteger();
		return runnable -> {
			final Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
			thread.setDaemon(true);
			return thread;
		};
	}
}
