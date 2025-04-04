package org.metricshub.engine.connector.model.identity;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2024 Sentry Software
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

import static com.fasterxml.jackson.annotation.Nulls.SKIP;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.metricshub.engine.connector.deserializer.custom.PlatformsDeserializer;

/**
 * Represents the identity information of a connector.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConnectorIdentity implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * The compiled filename of the connector.
	 */
	private String compiledFilename;

	/**
	 * The display name of the connector.
	 */
	private String displayName;

	/**
	 * The platforms on which the connector operates.
	 */
	@JsonDeserialize(using = PlatformsDeserializer.class)
	@JsonSetter(nulls = SKIP)
	@Default
	private Set<String> platforms = new HashSet<>();

	/**
	 * The dependencies or components that the connector relies on.
	 */
	private String reliesOn;

	/**
	 * The version of the connector.
	 */
	private String version;

	/**
	 * The project version of the connector.
	 */
	private String projectVersion;

	/**
	 * Additional information about the connector.
	 */
	private String information;

	/**
	 * The detection information of the connector.
	 */
	private Detection detection;

	/**
	 * The connector default variables that can be specified.
	 */
	@Default
	private Map<String, ConnectorDefaultVariable> variables = new HashMap<>();
}
