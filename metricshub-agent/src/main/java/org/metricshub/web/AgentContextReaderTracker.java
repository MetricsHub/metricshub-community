package org.metricshub.web;

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

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Tracks request-side readers of the active {@link org.metricshub.agent.context.AgentContext},
 * per {@link AgentContextHolder#getGeneration() context generation}.
 * <p>
 * HTTP/MCP operations run on request threads — not on the context's task scheduler — and may
 * invoke extension providers synchronously (e.g. Velocity template rendering through the
 * programmable configuration extension). This servlet filter takes a lease on the generation
 * active when the request enters and releases it when the request completes, so
 * {@link org.metricshub.web.service.AgentLifecycleService} can wait for every reader of a
 * replaced context before closing its extension class loaders.
 * </p>
 */
@Component
public class AgentContextReaderTracker implements Filter {

	/**
	 * The shared holder, used to stamp each incoming request with the active context generation.
	 */
	private final AgentContextHolder agentContextHolder;

	/**
	 * Active reader count per context generation. Entries are few (one per restart) and tiny,
	 * so drained generations are kept rather than pruned to stay race-free.
	 */
	private final Map<Long, AtomicLong> readersByGeneration = new ConcurrentHashMap<>();

	/**
	 * Constructor.
	 *
	 * @param agentContextHolder the shared {@link AgentContextHolder} injected by Spring
	 */
	public AgentContextReaderTracker(final AgentContextHolder agentContextHolder) {
		this.agentContextHolder = agentContextHolder;
	}

	@Override
	public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
		throws IOException, ServletException {
		final long generation = agentContextHolder.getGeneration();
		acquire(generation);
		boolean releaseNow = true;
		try {
			chain.doFilter(request, response);
		} finally {
			if (request.isAsyncStarted()) {
				// The request went asynchronous (e.g. an SseEmitter whose work continues on another
				// thread): hand the lease over to an AsyncListener so it is released only when the
				// asynchronous processing actually completes, errors out or times out.
				try {
					request.getAsyncContext().addListener(new LeaseReleaseListener(generation));
					releaseNow = false;
				} catch (IllegalStateException e) {
					// The asynchronous cycle completed concurrently: release the lease right away.
				}
			}
			if (releaseNow) {
				release(generation);
			}
		}
	}

	/**
	 * Releases a request's lease when its asynchronous processing completes. The release happens
	 * exactly once, on {@link #onComplete(AsyncEvent)} — the servlet container fires it after
	 * errors and timeouts as well. A new asynchronous cycle started on the same request clears the
	 * listeners, so {@link #onStartAsync(AsyncEvent)} re-registers this one to keep the lease.
	 */
	private final class LeaseReleaseListener implements AsyncListener {

		private final long generation;
		private final AtomicBoolean released = new AtomicBoolean();

		private LeaseReleaseListener(final long generation) {
			this.generation = generation;
		}

		@Override
		public void onComplete(final AsyncEvent event) {
			if (released.compareAndSet(false, true)) {
				release(generation);
			}
		}

		@Override
		public void onError(final AsyncEvent event) {
			// onComplete follows; release there exactly once.
		}

		@Override
		public void onTimeout(final AsyncEvent event) {
			// onComplete follows; release there exactly once.
		}

		@Override
		public void onStartAsync(final AsyncEvent event) {
			// A restarted asynchronous cycle clears the listeners: keep holding the lease.
			event.getAsyncContext().addListener(this);
		}
	}

	/**
	 * Takes a reader lease on the given generation.
	 *
	 * @param generation the context generation active when the reader entered
	 */
	public void acquire(final long generation) {
		readersByGeneration.computeIfAbsent(generation, g -> new AtomicLong()).incrementAndGet();
	}

	/**
	 * Releases a reader lease on the given generation.
	 *
	 * @param generation the context generation the lease was taken on
	 */
	public void release(final long generation) {
		final AtomicLong readers = readersByGeneration.get(generation);
		if (readers != null) {
			readers.decrementAndGet();
		}
	}

	/**
	 * Tests whether any reader that entered at or before the given generation is still active.
	 *
	 * @param generation the (outgoing) context generation to check
	 * @return {@code true} while at least one such reader has not completed
	 */
	public boolean hasReadersAtOrBefore(final long generation) {
		for (final Map.Entry<Long, AtomicLong> entry : readersByGeneration.entrySet()) {
			if (entry.getKey() <= generation && entry.getValue().get() > 0) {
				return true;
			}
		}
		return false;
	}
}
