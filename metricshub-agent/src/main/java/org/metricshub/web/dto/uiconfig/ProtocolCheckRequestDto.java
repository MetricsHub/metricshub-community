package org.metricshub.web.dto.uiconfig;

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

import jakarta.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.web.deserialization.ProtocolCheckRequestDtoDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * Request body for an on-demand protocol health check from the guided configuration UI.
 */
@Data
@JsonDeserialize(using = ProtocolCheckRequestDtoDeserializer.class)
public class ProtocolCheckRequestDto {

	@NotBlank(message = "Field 'hostname' is required.")
	private String hostname;

	@NotBlank(message = "Field 'protocol' is required.")
	private String protocol;

	/**
	 * Inline protocol configurations keyed by protocol id (e.g. {@code ssh}).
	 */
	private Map<String, IConfiguration> protocolConfig = new HashMap<>();
}
