package org.metricshub.engine.connector.parser;

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

import com.fasterxml.jackson.databind.JsonNode;
import java.util.function.Predicate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;

/**
 * An abstract class that provides the structure for updating a {@link JsonNode}.<br>
 * Each concrete subclass represents a specific {@link JsonNode} update implementation.
 */
@Data
@AllArgsConstructor
public abstract class AbstractJsonNodeUpdater {

	@NonNull
	protected final JsonNode jsonNode;

	@NonNull
	protected final Predicate<String> predicate;

	/**
	 * Traverses a given {@link JsonNode} instance then updates some nodes values based on some conditions.
	 * This method should be implemented in subclasses.
	 */
	protected abstract void update();

	/**
	 * Run the update only if the value matches the replacement predicate
	 *
	 * @param update Runnable function, actually the function performing the update
	 * @param value  Value to check
	 */
	protected void runUpdate(final Runnable update, final String value) {
		if (predicate.test(value)) {
			update.run();
		}
	}
}
