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

import lombok.RequiredArgsConstructor;
import org.metricshub.engine.strategy.source.SourceTable;

/**
 * {@link SourceTableLoggingProxy} for file sources: logs only a short header unless full file-source logging is enabled,
 * to avoid log flooding and excessive memory use from large file contents.
 */
@RequiredArgsConstructor
public class FileSourceTableLoggingProxy implements SourceTableLoggingProxy {

	private final SourceTableLoggingProxy delegate;
	private final boolean logFileSourceDetails;

	@Override
	public String formatForLog(
		final String operationTag,
		final String executionClassName,
		final String executionKey,
		final String connectorId,
		final SourceTable sourceTable,
		final String hostname
	) {
		if (!logFileSourceDetails) {
			final String headerOnly =
				DefaultSourceTableLoggingProxy.buildLogHeader(
					operationTag,
					executionClassName,
					executionKey,
					connectorId,
					hostname
				) +
				"\n";
			return headerOnly;
		}
		return delegate.formatForLog(operationTag, executionClassName, executionKey, connectorId, sourceTable, hostname);
	}
}
