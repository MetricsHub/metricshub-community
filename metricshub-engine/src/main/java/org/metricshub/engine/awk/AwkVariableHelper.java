package org.metricshub.engine.awk;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright (C) 2023 - 2025 MetricsHub
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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.EMPTY;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.SOURCE_REF_PATTERN;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.TABLE_SEP;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * Resolves the <code>variables</code> declared on an Awk source or compute into values the Jawk engine can expose to
 * the script.
 * <p>
 * A value that is a source reference, such as <code>${source::monitors.disk.discovery.sources.raw}</code>, is resolved
 * to the referenced source's table and handed over as a {@link List} of rows. Jawk turns it into an AWK array of
 * arrays, so the script addresses cells as <code>myVariable[row][column]</code> with zero-based indexes, and
 * <code>length(myVariable)</code> gives the row count. Any other value is exposed as a scalar, which also covers the
 * AWK special variables such as <code>FS</code>.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AwkVariableHelper {

	/**
	 * Resolve the given Awk variables.
	 *
	 * @param variables        The variables declared in the connector, indexed by variable name. Can be
	 *                         <code>null</code> or empty.
	 * @param telemetryManager The current {@link TelemetryManager} instance wrapping the connector namespace where the
	 *                         source tables are located.
	 * @param connectorId      The connector's identifier.
	 * @param operationKey     The unique key of the operation, used for logging.
	 * @return An unmodifiable map of variable name to value, never <code>null</code>.
	 */
	public static Map<String, Object> resolveVariables(
		final Map<String, String> variables,
		final TelemetryManager telemetryManager,
		final String connectorId,
		final Object operationKey
	) {
		if (variables == null || variables.isEmpty()) {
			return Collections.emptyMap();
		}

		final Map<String, Object> resolved = new LinkedHashMap<>();

		variables.forEach((name, value) ->
			resolved.put(name, resolveValue(name, value, telemetryManager, connectorId, operationKey))
		);

		return Collections.unmodifiableMap(resolved);
	}

	/**
	 * Resolve one variable value.
	 *
	 * @param name             The variable name, used for logging.
	 * @param value            The variable value as declared in the connector.
	 * @param telemetryManager The current {@link TelemetryManager} instance.
	 * @param connectorId      The connector's identifier.
	 * @param operationKey     The unique key of the operation, used for logging.
	 * @return The table of the referenced source, or the value itself when it is not a source reference.
	 */
	private static Object resolveValue(
		final String name,
		final String value,
		final TelemetryManager telemetryManager,
		final String connectorId,
		final Object operationKey
	) {
		// A variable declared without a value, such as "myVariable:" in YAML, is exposed as the AWK uninitialized value
		if (value == null) {
			return EMPTY;
		}

		// Not a source reference? Expose the value as a scalar
		if (!SOURCE_REF_PATTERN.matcher(value).find()) {
			return value;
		}

		final String hostname = telemetryManager.getHostname();

		final SourceTable sourceTable = SourceTable.lookupSourceTable(value, connectorId, telemetryManager).orElse(null);

		if (sourceTable == null) {
			log.error(
				"Hostname {} - The source table is not available. Couldn't extract {} referenced by the Awk variable" +
					" {} in {}. The variable will be exposed as an empty array.",
				hostname,
				value,
				name,
				operationKey
			);
			return Collections.emptyList();
		}

		final List<List<String>> table = sourceTable.getTable();

		if (table != null && !table.isEmpty()) {
			return table;
		}

		// Fall back to the raw data when the source holds no table. E.g. an HTTP source returning a body
		final String rawData = sourceTable.getRawData();

		if (rawData != null && !rawData.isEmpty()) {
			return SourceTable.csvToTable(rawData, TABLE_SEP);
		}

		log.debug(
			"Hostname {} - The source {} referenced by the Awk variable {} in {} is empty." +
				" The variable will be exposed as an empty array.",
			hostname,
			value,
			name,
			operationKey
		);

		return Collections.emptyList();
	}
}
