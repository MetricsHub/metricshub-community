package org.metricshub.web.service.openai;

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

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Thread-local holder for the {@link ContextBudgetManager} during a single
 * chat request. The budget manager is set after receiving the initial OpenAI
 * response (with tool calls) and before executing the tools.
 *
 * <p>Must be cleared after each request to prevent thread-local leaks.</p>
 *
 * <p>Usage pattern:</p>
 * <pre>{@code
 * try {
 *     ContextBudgetHolder.set(budgetManager);
 *     // execute tools...
 * } finally {
 *     ContextBudgetHolder.clear();
 * }
 * }</pre>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ContextBudgetHolder {

	private static final ThreadLocal<ContextBudgetManager> HOLDER = new ThreadLocal<>();

	/**
	 * Sets the budget manager for the current thread/request.
	 *
	 * @param budgetManager the budget manager to use
	 */
	public static void set(final ContextBudgetManager budgetManager) {
		HOLDER.set(budgetManager);
	}

	/**
	 * Gets the budget manager for the current thread/request.
	 *
	 * @return the budget manager, or null if not set
	 */
	public static ContextBudgetManager get() {
		return HOLDER.get();
	}

	/**
	 * Clears the budget manager for the current thread/request.
	 * Must be called in a finally block to prevent thread-local leaks.
	 */
	public static void clear() {
		HOLDER.remove();
	}
}
