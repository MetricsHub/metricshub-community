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

import java.util.List;
import org.metricshub.engine.common.helpers.TextTableHelper;
import org.metricshub.engine.strategy.source.SourceTable;

/**
 * Default {@link SourceTableLoggingProxy}: logs the full raw data and/or table representation.
 */
public class DefaultSourceTableLoggingProxy implements SourceTableLoggingProxy {

	private static final String RAW_RESULT_LABEL = "Raw result:\n";
	private static final String TABLE_RESULT_LABEL = "Table result:\n";

	@Override
	public String formatForLog(
		final String operationTag,
		final String executionClassName,
		final String executionKey,
		final String connectorId,
		final SourceTable sourceTable,
		final String hostname
	) {
		return buildFullMessage(operationTag, executionClassName, executionKey, connectorId, sourceTable, hostname);
	}

	static String buildLogHeader(
		final String operationTag,
		final String executionClassName,
		final String executionKey,
		final String connectorId,
		final String hostname
	) {
		return String.format(
			"Hostname %s - End of %s [%s %s] for connector [%s].",
			hostname,
			operationTag,
			executionClassName,
			executionKey,
			connectorId
		);
	}

	private static String buildFullMessage(
		final String operationTag,
		final String executionClassName,
		final String executionKey,
		final String connectorId,
		final SourceTable sourceTable,
		final String hostname
	) {
		final String header = buildLogHeader(operationTag, executionClassName, executionKey, connectorId, hostname) + "\n";
		final String rawData = sourceTable.getRawData();
		final List<List<String>> table = sourceTable.getTable();
		final boolean hasRaw = rawData != null;
		final boolean hasTable = table != null && !table.isEmpty();

		if (hasRaw && !hasTable) {
			return header + RAW_RESULT_LABEL + rawData + "\n";
		}
		if (!hasRaw) {
			String tableStr = hasTable ? TextTableHelper.generateTextTable(table) : "<empty>";
			return header + TABLE_RESULT_LABEL + tableStr + "\n";
		}
		// CHECKSTYLE:OFF
		return (
			header + RAW_RESULT_LABEL + rawData + "\n" + TABLE_RESULT_LABEL + TextTableHelper.generateTextTable(table) + "\n"
		);
		// CHECKSTYLE:ON
	}
}
