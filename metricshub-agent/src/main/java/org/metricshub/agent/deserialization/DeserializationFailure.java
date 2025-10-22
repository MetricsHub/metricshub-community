package org.metricshub.agent.deserialization;

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

import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Container for deserialization errors detected while reading configuration files.
 *
 * <p>This class collects {@link Error} instances that capture the human-readable message along with
 * optional line and column information.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeserializationFailure {

	/**
	 * Collected {@link Error} entries describing the encountered deserialization issues.
	 */
	@Default
	private final Set<Error> errors = new HashSet<>();

	/**
	 * Register a new error message with location information.
	 *
	 * @param message description of the failure
	 * @param line the line number where the error occurred (1-based)
	 * @param column the column number where the error occurred (1-based)
	 */
	public void addError(String message, int line, int column) {
		errors.add(new Error(message, line, column));
	}

	/**
	 * Register a new error message without location details.
	 *
	 * @param message description of the failure
	 */
	public void addError(String message) {
		errors.add(new Error(message, null, null));
	}

	/**
	 * Immutable representation of a single deserialization error.
	 */
	@Data
	@AllArgsConstructor
	public static class Error {

		/** message describing the error. */
		private String message;
		/** optional line where the error occurred. */
		private Integer line;
		/** optional column where the error occurred. */
		private Integer column;
	}

	/**
	 * Check if there are no errors recorded.
	 *
	 * @return true if there are no errors, false otherwise
	 */
	public boolean isEmpty() {
		return errors == null || errors.isEmpty();
	}
}
