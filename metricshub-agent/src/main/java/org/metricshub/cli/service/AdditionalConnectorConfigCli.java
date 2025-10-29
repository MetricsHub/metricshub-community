package org.metricshub.cli.service;

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

import java.util.Map;
import lombok.Data;
import org.metricshub.engine.configuration.AdditionalConnector;
import picocli.CommandLine.Option;

/**
 * Represents the CLI configuration for an additional connector.
 * <p>
 * Includes the connector ID, an optional reference to another connector via {@code --uses},
 * and optional variables passed with {@code -F key=value}.
 * </p>
 */
@Data
public class AdditionalConnectorConfigCli {

	@Option(names = { "--additionalConnector", "--additional-connector" }, required = true)
	private String connectorId;

	@Option(names = "--uses")
	private String uses;

	@Option(names = { "-F", "--variable" }, description = "Connector variable in key=value format")
	Map<String, String> variables;

	/**
	 * Converts this CLI configuration to an {@link AdditionalConnector} instance.
	 *
	 * If {@code uses} is not specified, defaults to {@code connectorId}.
	 *
	 * @return the constructed {@link AdditionalConnector}
	 */
	AdditionalConnector toAdditionalConnector() {
		return AdditionalConnector
			.builder()
			.force(true)
			.uses(uses != null ? uses : connectorId)
			.variables(variables)
			.build();
	}
}
