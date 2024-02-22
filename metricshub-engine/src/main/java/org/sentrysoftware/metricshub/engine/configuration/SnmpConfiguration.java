package org.sentrysoftware.metricshub.engine.configuration;

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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * The SnmpConfiguration class represents the configuration for SNMP in the MetricsHub engine.
 * It implements the IConfiguration interface and includes settings such as SNMP version, community,
 * port, timeout, context name, privacy, privacy password, username, and password.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SnmpConfiguration implements IConfiguration {

	private static final String INVALID_SNMP_VERSION_EXCEPTION_MESSAGE = "Invalid SNMP version: ";

	@Builder.Default
	private final SnmpVersion version = SnmpVersion.V1;

	@Builder.Default
	private final String community = "public";

	@Builder.Default
	private Integer port = 161;

	@Builder.Default
	private final Long timeout = 120L;

	@Override
	public String toString() {
		return version.getDisplayName() + " (" + community + ")";
	}

	/**
	 * Enum of SNMP versions and authentication types.
	 */
	@AllArgsConstructor
	public enum SnmpVersion {
		/**
		 * SNMP version 1 (v1) without authentication.
		 */
		V1(1, "SNMP v1"),
		/**
		 * SNMP version 2 (v2c) without authentication.
		 */
		V2C(2, "SNMP v2c");

		@Getter
		private final int intVersion;

		@Getter
		private final String displayName;

		/**
		 * Interpret the specified label and returns corresponding value.
		 *
		 * @param version String to be interpreted
		 * @return Corresponding {@link SnmpVersion} value
		 * @throws IllegalArgumentException If the provided SNMP version label is invalid.
		 */
		public static SnmpVersion interpretValueOf(@NonNull final String version) {
			final String lowerCaseVersion = version.toLowerCase();

			if ("1".equals(lowerCaseVersion) || "v1".equals(lowerCaseVersion)) {
				return SnmpConfiguration.SnmpVersion.V1;
			}

			if ("2".equals(lowerCaseVersion) || "v2".equals(lowerCaseVersion) || "v2c".equals(lowerCaseVersion)) {
				return SnmpConfiguration.SnmpVersion.V2C;
			}

			throw new IllegalArgumentException(INVALID_SNMP_VERSION_EXCEPTION_MESSAGE + version);
		}
	}
}
