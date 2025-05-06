package org.metricshub.agent.connector;

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
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.metricshub.engine.connector.model.Connector;

/**
 * Represents the result of parsing additional connectors, containing a map of connectors
 * and a set of forced connector IDs.
 */
@Builder
@Data
@NoArgsConstructor
public class AdditionalConnectorsParsingResult {

	/**
	 * The map containing the parsed additional custom connectors.
	 */
	final Map<String, Connector> customConnectorsMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

	/**
	 * A set of connector IDs to add into the host connectors set.
	 */
	final Set<String> resourceConnectors = new HashSet<>();
}
