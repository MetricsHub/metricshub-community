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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Helper class for creating instances of NodeProcessor implementations.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class NodeProcessorHelper {

	/**
	 * Creates a new {@link ConstantsProcessor} with a {@link SourceKeyProcessor} as destination.
	 *
	 * @return A new {@link ConstantsProcessor} instance.
	 */
	private static AbstractNodeProcessor constantsProcessorWithSourceKeyProcessor() {
		return new ConstantsProcessor(new SourceKeyProcessor());
	}

	/**
	 * Creates a {@link ExtendsProcessor} with a {@link ConstantsProcessor} destination.
	 *
	 * @param connectorDirectory The directory containing connectors YAML files.
	 * @param mapper             The object mapper.
	 * @return A new {@link ExtendsProcessor} instance.
	 */
	public static AbstractNodeProcessor withExtendsAndConstantsProcessor(
		final Path connectorDirectory,
		final ObjectMapper mapper
	) {
		return ExtendsProcessor
			.builder()
			.connectorDirectory(connectorDirectory)
			.mapper(mapper)
			.next(new ReferenceResolverProcessor(constantsProcessorWithSourceKeyProcessor()))
			.build();
	}

	/**
	 * Creates a {@link ExtendsProcessor} with a {@link ConnectorVariableProcessor} destination
	 * that redirects to {@link ConstantsProcessor}.
	 *
	 * @param connectorDirectory   The directory containing connectors YAML files.
	 * @param mapper               The object mapper.
	 * @param connectorVariables   The map of connector variables.
	 * @return A new {@link ConnectorVariableProcessor} instance.
	 */
	public static AbstractNodeProcessor withExtendsAndConnectorVariableProcessor(
		final Path connectorDirectory,
		final ObjectMapper mapper,
		final Map<String, String> connectorVariables
	) {
		return ExtendsProcessor
			.builder()
			.connectorDirectory(connectorDirectory)
			.next(
				ConnectorVariableProcessor
					.builder()
					.connectorVariables(connectorVariables)
					.next(new ReferenceResolverProcessor(constantsProcessorWithSourceKeyProcessor()))
					.build()
			)
			.mapper(mapper)
			.build();
	}
}
