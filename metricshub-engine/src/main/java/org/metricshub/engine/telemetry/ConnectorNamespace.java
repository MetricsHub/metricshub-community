package org.metricshub.engine.telemetry;

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
	private Map<String, Integer> eventLogSourceCursors = new HashMap<>();

	/**
	 * A map of log file sources. Each key represents the source, and the value
	 * is a map of cursors, where keys are the filenames, and the values are cursors.
	 */
	@Default
	private Map<String, Map<String, Long>> fileSourceCursors = new HashMap<>();

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

	/**
	 * Gets the event log cursor (RecordNumber) for the specified source.
	 *
	 * @param sourceKey the source key identifying the event log source
	 * @return the cursor value, or null if not set
	 */
	public Integer getEventLogCursor(@NonNull String sourceKey) {
		return eventLogSourceCursors.get(sourceKey);
	}

	/**
	 * Sets the event log cursor (RecordNumber) for the specified source.
	 *
	 * @param sourceKey the source key identifying the event log source
	 * @param cursor the cursor value to set, or null to clear
	 */
	public void setEventLogCursor(@NonNull String sourceKey, @NonNull Integer cursor) {
		eventLogSourceCursors.put(sourceKey, cursor);
	}

	/**
	 * Gets the file source cursors (byte offsets) for the specified source.
	 * The returned map associates each file path with its last-read position.
	 * The map is created on first access and mutations persist across polls.
	 *
	 * @param sourceKey the source key identifying the file source
	 * @return a mutable map of file path to cursor (byte offset); never null
	 */
	public Map<String, Long> getFileSourceCursors(@NonNull String sourceKey) {
		return fileSourceCursors.computeIfAbsent(sourceKey, k -> new HashMap<>());
	}
}
