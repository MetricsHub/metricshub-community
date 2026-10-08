package org.metricshub.extension.win;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Win Extension Common
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

import java.util.Set;
import org.metricshub.engine.common.exception.ClientException;
import org.metricshub.engine.common.helpers.FileHelper.PathPattern;
import org.metricshub.engine.connector.model.common.FileOperations;

/**
 * {@link FileOperations} on a remote Windows host, plus the resolution of a file source's path
 * patterns. One instance serves a whole file source poll and is closed at its end.
 */
public interface WinFileOperations extends FileOperations {
	/**
	 * Resolve a path pattern into the absolute paths of the files matching it on the remote host.
	 *
	 * @param pattern the parsed path pattern; {@code *} and {@code ?} match within a single path segment
	 * @return the absolute paths of the matching files, empty when nothing matches
	 * @throws ClientException when the host cannot be queried
	 */
	Set<String> resolve(PathPattern pattern) throws ClientException;
}
