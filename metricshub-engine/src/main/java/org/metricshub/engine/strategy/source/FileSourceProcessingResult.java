package org.metricshub.engine.strategy.source;

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

import lombok.Builder;
import lombok.Data;

/**
 * Result of processing a single file in LOG mode for a file source.
 * Used to return the newly read content, the updated read position (cursor), and the remaining
 * size quota for the current polling cycle.
 */
@Data
@Builder
public class FileSourceProcessingResult {

	/** The content read from the file since the last cursor position. May be null if no new content was read. */
	String content;

	/** The new cursor position (byte offset) after this read. Used to track the next read start for this file. */
	long cursor;

	/**
	 * The remaining size limit (in bytes) for this polling cycle after this read.
	 * When the file source uses unlimited size per poll, this stays {@code -1}.
	 */
	long remainingSize;
}
