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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ObjLongConsumer;
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

	/**
	 * How much of a failure's detail travels back. A result is measured against the server's payload
	 * cap and refused when it exceeds it; an error has no such fallback — refusing to report a
	 * failure because the report is too long leaves the Governor waiting on nothing. So the detail is
	 * cut instead, small enough that an error frame cannot approach any cap worth configuring.
	 */
	static final int MAX_ERROR_DETAIL_CHARS = 1000;

	/**
	 * The hard ceiling on threads this bridge may ever hold at once.
	 *
	 * <p>The server's {@code maxInFlight} bounds LIVE invocations, and a timed-out one stops being
	 * live the moment the Governor is told so — but its thread does not stop: a callback blocked in
	 * a socket read does not observe an interruption, and nothing in Java can take a thread back.
	 * Without a second bound, a run of timeouts would admit invocation after invocation while every
	 * abandoned one kept its thread, its connection and its work on the monitored host. So the pool
	 * is bounded, and an invocation that cannot get a thread is refused as
	 * {@link ToolErrorCode#TOO_MANY_INFLIGHT} rather than queued behind work that may never end.
	 */
	static final int MAX_WORKER_THREADS = 32;

	private final ToolRegistrySnapshot snapshot;
	private final ObjLongConsumer<M8bMessage> sender;
	private final ExecutorService workers;
	private final ScheduledThreadPoolExecutor timer;
	private final AtomicInteger inFlight = new AtomicInteger();
	/**
	 * The executions this bridge started and has not accounted for yet, keyed by the flag that says
	 * whether their slot has gone back -- the one object both the worker and the deadline hold.
	 *
	 * <p>They are tracked because the worker pool is shared and outlives this bridge: shutting the
	 * deadline timer down discards the cancellations it was holding, and a callback that WOULD have
	 * stopped on interruption would instead keep a process-wide thread for as long as it liked.
	 */
	private final ConcurrentHashMap<AtomicBoolean, Future<?>> running = new ConcurrentHashMap<>();
	private volatile AgentRegistered limits = DEFAULT_LIMITS;

	/**
	 * @param snapshot the advertised tools and their callbacks
	 * @param sender   where answers go, carrying the connection each one answers
	 * @param workers  the pool tool executions run on, owned by the caller so that its ceiling
	 *                 survives this bridge being replaced
	 */
	public M8bToolBridge(
		final ToolRegistrySnapshot snapshot,
		final ObjLongConsumer<M8bMessage> sender,
		final ExecutorService workers
	) {
		this.snapshot = snapshot;
		this.sender = sender;
		this.workers = workers;
		this.timer = new ScheduledThreadPoolExecutor(1, daemonThreads("metricshub-m8b-tool-timer"));
		// Cancelling a deadline must drop it from the queue at once: otherwise an answered request
		// keeps its arguments alive until the timeout would have fired
		this.timer.setRemoveOnCancelPolicy(true);
	}

	/**
	 * Builds the pool tool executions run on. One per agent process, not one per bridge: a callback
	 * that ignores its interruption keeps its thread through a reconfiguration too, so a ceiling
	 * that reset on every configuration change would bound nothing at all.
	 *
	 * @return a bounded pool of daemon threads
	 */
	public static ExecutorService newWorkerPool() {
		// SynchronousQueue, not an unbounded one: a request that finds every thread taken must be
		// refused now, while the Governor can still act on it, rather than queued behind an
		// invocation that has already outlived its deadline.
		return new ThreadPoolExecutor(
			0,
			MAX_WORKER_THREADS,
			60,
			TimeUnit.SECONDS,
			new SynchronousQueue<>(),
			daemonThreads("metricshub-m8b-tool")
		);
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
	 * @param invoke     the request
	 * @param generation the tunnel connection it arrived on. Every answer is bound to it, so work
	 *                   that outlives its session cannot reply to the next one under a request id
	 *                   that session never issued
	 */
	public void invoke(final ToolInvoke invoke, final long generation) {
		final ToolCallback callback = snapshot.callbacks().get(invoke.tool());
		if (callback == null) {
			sender.accept(
				new ToolError(
					invoke.requestId(),
					ToolErrorCode.TOOL_NOT_AVAILABLE,
					"Tool not advertised: " + brief(invoke.tool())
				),
				generation
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
				),
				generation
			);
			return;
		}

		final AtomicBoolean answered = new AtomicBoolean();
		// The slot is given back exactly once, by whichever of the worker and the deadline gets
		// there first: a worker cancelled before it ran never reaches its own finally.
		final AtomicBoolean released = new AtomicBoolean();
		// Holds the deadline task so the worker can drop it as soon as it answered: an uncancelled
		// task keeps the request and its arguments in the timer queue for the whole timeout.
		final AtomicReference<ScheduledFuture<?>> deadline = new AtomicReference<>();
		final long startedAt = System.nanoTime();
		final Future<?> execution;
		try {
			execution = workers.submit(() -> {
				final M8bMessage answer;
				try {
					answer = execute(invoke, callback, currentLimits, startedAt);
				} finally {
					// Free the slot before the answer leaves: the server may invoke again right away
					release(released);
				}
				answerOnce(answered, generation, answer);
				cancel(deadline.getAndSet(null));
			});
		} catch (RejectedExecutionException e) {
			release(released);
			sender.accept(
				new ToolError(
					invoke.requestId(),
					ToolErrorCode.TOO_MANY_INFLIGHT,
					"No worker available: " + MAX_WORKER_THREADS + " invocation(s) are still running, some past their deadline"
				),
				generation
			);
			return;
		}
		running.put(released, execution);
		if (released.get()) {
			// It finished before the handle was stored, and its own release found nothing to remove
			running.remove(released);
		}
		final long timeoutMs = invoke.timeoutMs() > 0 ? invoke.timeoutMs() : TimeUnit.SECONDS.toMillis(300);
		final ScheduledFuture<?> scheduled = timer.schedule(
			() -> {
				if (
					answerOnce(
						answered,
						generation,
						new ToolError(invoke.requestId(), ToolErrorCode.TIMEOUT, "Timed out after " + timeoutMs + " ms")
					)
				) {
					log.warn("M8B tool '{}' (request {}) timed out after {} ms.", invoke.tool(), invoke.requestId(), timeoutMs);
				}
				// Both of these run whether or not the answer was sent. A session that ended
				// suppresses the reply, never the deadline: otherwise work outliving its session
				// would hold its slot through every session that follows, until nothing new could
				// be invoked at all.
				execution.cancel(true);
				release(released);
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

	/**
	 * Gives an invocation's concurrency slot back, once. A callback that ignores its interruption
	 * keeps its thread, which nothing can take away — but not the slot, which the server is
	 * entitled to reuse the moment it has been told the invocation is over.
	 */
	private void release(final AtomicBoolean released) {
		if (released.compareAndSet(false, true)) {
			inFlight.decrementAndGet();
		}
		// Either it finished, or the deadline has just cancelled it: nothing left for shutdown to do
		running.remove(released);
	}

	private static void cancel(final ScheduledFuture<?> future) {
		if (future != null) {
			future.cancel(false);
		}
	}

	/**
	 * Releases what this bridge owns: its deadlines. The worker pool belongs to the caller and
	 * outlives every bridge, because the threads a stuck callback holds outlive one too.
	 */
	public void shutdown() {
		// Before the timer goes, because the timer is what was holding these cancellations.
		running.values().forEach(execution -> execution.cancel(true));
		running.clear();
		timer.shutdownNow();
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

	private boolean answerOnce(final AtomicBoolean answered, final long generation, final M8bMessage answer) {
		if (answered.compareAndSet(false, true)) {
			sender.accept(answer, generation);
			return true;
		}
		return false;
	}

	private static String messageOf(final Exception e) {
		return e.getMessage() == null ? e.getClass().getSimpleName() : brief(e.getMessage());
	}

	/**
	 * @param detail whatever a tool, or the server, put in front of us
	 * @return it, trimmed to what an error frame may carry
	 */
	private static String brief(final String detail) {
		if (detail == null || detail.length() <= MAX_ERROR_DETAIL_CHARS) {
			return detail;
		}
		return detail.substring(0, MAX_ERROR_DETAIL_CHARS) + "... (truncated)";
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
