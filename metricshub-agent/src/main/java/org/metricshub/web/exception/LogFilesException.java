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

/**
 * Exception class for log file-related errors.
 */
public class LogFilesException extends Exception {

	/**
	 * Serial version UID for serialization.
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Enumeration of error codes for log file operations.
	 */
	public enum Code {
		LOGS_DIR_UNAVAILABLE,
		FILE_NOT_FOUND,
		INVALID_FILE_NAME,
		INVALID_PATH,
		IO_FAILURE
	}

	private final Code code;

	/**
	 * Constructor for LogFilesException.
	 *
	 * @param code the error code representing the type of error.
	 */
	public LogFilesException(Code code) {
		super(code.name());
		this.code = code;
	}

	/**
	 * Constructor for LogFilesException with a custom message.
	 *
	 * @param code    the error code representing the type of error.
	 * @param message the custom error message.
	 */
	public LogFilesException(Code code, String message) {
		super(message);
		this.code = code;
	}

	/**
	 * Constructor for LogFilesException with a cause.
	 *
	 * @param code    the error code representing the type of error.
	 * @param message the custom error message.
	 * @param cause   the underlying cause of the exception.
	 */
	public LogFilesException(Code code, String message, Throwable cause) {
		super(message, cause);
		this.code = code;
	}

	/**
	 * Gets the error code associated with this exception.
	 *
	 * @return the error code.
	 */
	public Code getCode() {
		return code;
	}
}
