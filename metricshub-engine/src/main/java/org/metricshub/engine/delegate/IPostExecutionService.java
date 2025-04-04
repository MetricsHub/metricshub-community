package org.metricshub.engine.delegate;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
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

/**
 * Interface for post-execution services in the MetricsHub engine.
 * Implementations of this interface should encapsulate the necessary steps
 * for the successful execution of a post-business operation.
 *
 * Example:
 * <pre>
 * {@code
 *   public class IHardwarePostExecutionImpl implements IPostExecutionService {
 *
 *       public void run() {
 *           // Implementation specific to the business process
 *           // This may involve calling multiple lower-level services,
 *           // handling exceptions, and performing any required business logic.
 *           // ...
 *       }
 *   }
 * }
 * </pre>
 */
public interface IPostExecutionService {
	/**
	 * Executes the designated post-execution service.
	 * The implementation of this method should encapsulate the necessary
	 * steps for the successful execution of the post-business operation.
	 */
	void run();
}
