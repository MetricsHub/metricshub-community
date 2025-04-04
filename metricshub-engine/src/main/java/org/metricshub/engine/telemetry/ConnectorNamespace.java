package org.metricshub.engine.telemetry;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2024 Sentry Software
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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.metricshub.engine.strategy.source.SourceTable;

/**
 * Represents a namespace for a connector, containing information about source tables and related settings.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConnectorNamespace {

	@Default
	private Map<String, SourceTable> sourceTables = new HashMap<>();

	private String automaticWmiNamespace;
	private String automaticWbemNamespace;
	private boolean isStatusOk;

	@Default
	private ReentrantLock forceSerializationLock = new ReentrantLock(true);

	/**
	 * Add a source in the current sourceTables map
	 *
	 * @param key sourceTable key
	 * @param sourceTable sourceTable instance
	 */
	public void addSourceTable(@NonNull String key, @NonNull SourceTable sourceTable) {
		sourceTables.put(key, sourceTable);
	}

	/**
	 * Get the {@link SourceTable} identified with the given key
	 *
	 * @param key sourceTable key
	 * @return return existing {@link SourceTable} object
	 */
	public SourceTable getSourceTable(@NonNull String key) {
		return sourceTables.get(key);
	}
}
