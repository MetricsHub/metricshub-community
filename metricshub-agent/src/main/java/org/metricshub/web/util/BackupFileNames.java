package org.metricshub.web.util;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
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

import java.util.regex.Pattern;

/**
 * Utility for backup file naming conventions used by the configuration UI.
 * <p>
 * Backup files follow the pattern {@code backup-YYYYMMDD-HHMMSS__<original-name>},
 * matching the frontend {@code backup-names.js} module.
 */
public final class BackupFileNames {

	/**
	 * Pattern for valid backup filenames produced by the UI backup feature.
	 */
	private static final Pattern BACKUP_FILE_NAME_PATTERN = Pattern.compile("^backup-(\\d{8}-\\d{6})__(.+)$");

	private BackupFileNames() {}

	/**
	 * Returns {@code true} when the given filename follows the backup naming convention.
	 *
	 * @param fileName the simple file name (no path separators)
	 * @return {@code true} if the name matches the backup pattern
	 */
	public static boolean isBackupFileName(final String fileName) {
		return fileName != null && BACKUP_FILE_NAME_PATTERN.matcher(fileName).matches();
	}
}
