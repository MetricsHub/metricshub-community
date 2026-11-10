package org.metricshub.agent.config;

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

import static com.fasterxml.jackson.annotation.Nulls.SKIP;

import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * How the agent authenticates to Central: it enrolls once into Central's issuer with the temporary key of an
 * installation session, then obtains its tokens with the identity it received, kept in the {@code security}
 * directory. Once the agent is enrolled the key is ignored, and the agent never removes it from this file.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CentralAuthConfig {

	/**
	 * Central's issuer URL, an origin without a path; required.
	 */
	@JsonSetter(nulls = SKIP)
	private String issuerUrl;

	/**
	 * The installation session's temporary enrollment key, used once at the first start. Sensitive: never shown.
	 */
	@ToString.Exclude
	@JsonSetter(nulls = SKIP)
	private String enrollmentKey;
}
