package org.metricshub.agent.opentelemetry;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2025 Sentry Software
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

/**
 * Functional interface used to explicitly set the logging context for asynchronous operations.
 * <p>
 * This interface should be implemented to manage the {@link org.apache.logging.log4j.ThreadContext}
 * within callbacks running on different threads, ensuring consistent context propagation in log entries.
 * </p>
 */
@FunctionalInterface
public interface LogContextSetter {
	/**
	 * Sets the logging context using {@code ThreadContext.put()} before executing log statements.
	 * <p>
	 * Implementations of this method should explicitly set all necessary context entries required
	 * for log correlation.
	 * </p>
	 */
	void setContext();
}
