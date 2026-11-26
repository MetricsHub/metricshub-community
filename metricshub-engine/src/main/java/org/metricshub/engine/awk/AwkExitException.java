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

import java.util.Map;
import lombok.Getter;

/**
 * An AWK script exited with a code other than 0: the <code>awk</code> source or compute that runs it fails.
 * <code>exit</code> and <code>exit 0</code> end a script without an exception.
 */
public class AwkExitException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/**
	 * What an exit code that the <code>exitCodes</code> of the source or compute do not list means
	 */
	static final String UNSPECIFIED_ERROR = "Unspecified error";

	/**
	 * The exit code of the script, never 0
	 */
	@Getter
	private final int code;

	/**
	 * @param code The exit code of the script
	 */
	public AwkExitException(final int code) {
		super("The script exited with code " + code + ".");
		this.code = code;
	}

	/**
	 * The error of the source: what the exit code means, as the <code>exitCodes</code> of the <code>awk</code> source
	 * or compute tell, then the code.
	 *
	 * @param exitCodes The message of each exit code, null for none
	 * @return the message of the exit code, <code>Unspecified error</code> without one, followed by the code
	 */
	public String describe(final Map<Integer, String> exitCodes) {
		final String message = exitCodes != null ? exitCodes.get(code) : null;
		return "%s (exit code %d)".formatted(message != null ? message : UNSPECIFIED_ERROR, code);
	}
}
