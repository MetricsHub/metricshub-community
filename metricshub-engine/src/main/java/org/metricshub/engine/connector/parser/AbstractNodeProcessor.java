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
import java.io.IOException;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Abstract base class for implementing a chain of responsibility pattern in processing JsonNodes.
 * Each concrete subclass represents a specific processing step in the chain.
 */
@AllArgsConstructor
@Data
public abstract class AbstractNodeProcessor {

	/**
	 * Next node processor
	 */
	protected AbstractNodeProcessor next;

	/**
	 * Process the provided {@link JsonNode} with the remaining chain of processors.
	 *
	 * @param node The JsonNode to be processed.
	 * @return An instance of {@link JsonNode} representing the result of the processing.
	 * @throws IOException If an I/O error occurs during the processing.
	 */
	public JsonNode process(final JsonNode node) throws IOException {
		final JsonNode processedNode = processNode(node);

		if (next != null) {
			return next.process(processedNode);
		}

		return processedNode;
	}

	/**
	 * Process one {@link JsonNode}.
	 *
	 * @param node The JsonNode to be processed.
	 * @return An instance of {@link JsonNode} representing the result of the processing.
	 * @throws IOException If an I/O error occurs during the processing.
	 */
	protected abstract JsonNode processNode(JsonNode node) throws IOException;
}
