package org.metricshub.engine.strategy;

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

import org.metricshub.engine.strategy.source.SourceTable;

/**
 * Proxy that formats a {@link SourceTable} result for logging.
 * Allows source-type-specific handling (e.g. FileSource: summary only; others: full table).
 */
public interface SourceTableLoggingProxy {
	/**
	 * Produces the log message for this source table result.
	 *
	 * @param operationTag       e.g. "source" or "compute"
	 * @param executionClassName e.g. "FileSource"
	 * @param executionKey       source or compute key
	 * @param connectorId        connector identifier
	 * @param sourceTable        the result (may be large for file sources)
	 * @param hostname           hostname
	 * @return the message to log (never null)
	 */
	String formatForLog(
		String operationTag,
		String executionClassName,
		String executionKey,
		String connectorId,
		SourceTable sourceTable,
		String hostname
	);
}
