package org.metricshub.agent.upgrade;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2026 MetricsHub
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
 * Failure of an upgrade step. The message is user-facing: it is persisted in the upgrade
 * transaction and reported to the OpAMP server as the package {@code error_message}.
 */
public class UpgradeException extends Exception {

	private static final long serialVersionUID = 1L;

	/**
	 * Creates an upgrade failure.
	 *
	 * @param message the user-facing failure cause
	 */
	public UpgradeException(final String message) {
		super(message);
	}

	/**
	 * Creates an upgrade failure with a cause.
	 *
	 * @param message the user-facing failure cause
	 * @param cause   the underlying exception
	 */
	public UpgradeException(final String message, final Throwable cause) {
		super(message, cause);
	}
}
