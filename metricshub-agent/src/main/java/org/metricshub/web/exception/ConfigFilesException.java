package org.metricshub.web.exception;

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

public class ConfigFilesException extends RuntimeException {

	public enum Code {
		CONFIG_DIR_UNAVAILABLE,
		FILE_NOT_FOUND,
		INVALID_FILE_NAME,
		INVALID_EXTENSION,
		INVALID_PATH,
		TARGET_EXISTS,
		VALIDATION_FAILED,
		IO_FAILURE
	}

	private final Code code;

	public ConfigFilesException(Code code) {
		super(code.name());
		this.code = code;
	}

	public ConfigFilesException(Code code, String message) {
		super(message);
		this.code = code;
	}

	public ConfigFilesException(Code code, String message, Throwable cause) {
		super(message, cause);
		this.code = code;
	}

	public Code getCode() {
		return code;
	}
}
