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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
	private Map<String, SourceTable> sourceTables = new ConcurrentHashMap<>();

	/**
	 * Keys of the sources that did not answer the last time they ran: they had data before and came back empty after the
	 * retry, or their table was null. A job reading such a source through a reference does not remove monitors.
	 */
	@Default
	private Set<String> unansweredSourceKeys = ConcurrentHashMap.newKeySet();

	private String automaticWmiNamespace;
	private String automaticWbemNamespace;
	private boolean isStatusOk;

	@Default
	private Map<String, Integer> eventLogSourceCursors = new ConcurrentHashMap<>();

	/**
	 * A map of log file sources. Each key represents the source, and the value
	 * is a map of cursors, where keys are the filenames, and the values are cursors.
	 */
	@Default
	private Map<String, Map<String, Long>> fileSourceCursors = new ConcurrentHashMap<>();

	/**
	 * The cursor of each incremental source handled by an extension (the ipmiSel source, for one), by source key.
	 * The extension owns the type of the cursor.
	 */
	@Default
	private Map<String, Object> sourceCursors = new ConcurrentHashMap<>();

	@Default
	private ReentrantLock forceSerializationLock = new ReentrantLock(true);

	/**
	 * Tracks the number of consecutive detection re-validation failures.
	 * When this counter reaches the configured threshold, the connector is considered
	 * to no longer match the host and its collect/discovery job is stopped.
	 * The counter is reset to zero on each successful detection re-validation.
	 */
	@Default
	private int consecutiveDetectionFailures = 0;

	/**
	 * Increments the consecutive detection failure counter and returns the new value.
	 *
	 * @return the incremented failure count
	 */
	public int incrementDetectionFailures() {
		return ++consecutiveDetectionFailures;
	}

	/**
	 * Resets the consecutive detection failure counter to zero.
	 */
	public void resetDetectionFailures() {
		consecutiveDetectionFailures = 0;
	}

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
	 * @param cursor the cursor value to set.
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
		return fileSourceCursors.computeIfAbsent(sourceKey, _ -> new ConcurrentHashMap<>());
	}

	/**
	 * Gets the cursor an extension stored for the specified incremental source.
	 *
	 * @param sourceKey the source key
	 * @return the cursor, or null before the first poll
	 */
	public Object getSourceCursor(@NonNull String sourceKey) {
		return sourceCursors.get(sourceKey);
	}

	/**
	 * Stores the cursor of the specified incremental source for its next poll.
	 *
	 * @param sourceKey the source key
	 * @param cursor    the cursor, owned by the extension
	 */
	public void setSourceCursor(@NonNull String sourceKey, @NonNull Object cursor) {
		sourceCursors.put(sourceKey, cursor);
	}

	/**
	 * The cursors of an incremental source at one point in time: its event log cursor, its file cursors and the
	 * cursor its extension owns.
	 *
	 * @param eventLog The event log cursor, null if none
	 * @param files    A copy of the file cursors, by file
	 * @param source   The cursor the extension owns, null if none
	 */
	public record SourceCursors(Integer eventLog, Map<String, Long> files, Object source) {}

	/**
	 * Gets a snapshot of the cursors of the specified incremental source.
	 *
	 * @param sourceKey the source key
	 * @return the cursors as they are now
	 */
	public SourceCursors getSourceCursors(@NonNull String sourceKey) {
		return new SourceCursors(
			eventLogSourceCursors.get(sourceKey),
			Map.copyOf(getFileSourceCursors(sourceKey)),
			sourceCursors.get(sourceKey)
		);
	}

	/**
	 * Rolls back a poll of the specified incremental source whose entries were not mapped, so that the next poll
	 * returns them again: each cursor the poll moved goes back to where it was. A cursor that changed since the poll
	 * (moved by another run) is left as it is.
	 *
	 * @param sourceKey the source key
	 * @param before    the cursors before the poll
	 * @param after     the cursors right after the poll
	 */
	public void rollBackSourceCursors(
		@NonNull String sourceKey,
		@NonNull SourceCursors before,
		@NonNull SourceCursors after
	) {
		rollBack(eventLogSourceCursors, sourceKey, before.eventLog(), after.eventLog());
		rollBack(sourceCursors, sourceKey, before.source(), after.source());
		final Map<String, Long> files = getFileSourceCursors(sourceKey);
		after.files().forEach((file, cursor) -> rollBack(files, file, before.files().get(file), cursor));
	}

	/**
	 * Puts the given key of the given concurrent map back to its value before a poll, atomically and only if it still
	 * holds the value the poll left.
	 *
	 * @param map    the concurrent map of cursors
	 * @param key    the key
	 * @param before the value before the poll, null if absent
	 * @param after  the value the poll left, null if absent
	 */
	private static <K, V> void rollBack(final Map<K, V> map, final K key, final V before, final V after) {
		if (after == null || after.equals(before)) {
			return;
		}
		if (before == null) {
			map.remove(key, after);
		} else {
			map.replace(key, after, before);
		}
	}
}
