package org.metricshub.extension.snmp;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub SNMP Extension
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
import java.util.Arrays;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.common.helpers.StringHelper;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.deserialization.MultiValueDeserializer;
import org.metricshub.engine.deserialization.TimeDeserializer;

/**
 * The SnmpConfiguration class represents the configuration for SNMP in the MetricsHub engine.
 * It implements the ISnmpConfiguration interface and includes settings such as SNMP version, community,
 * port, timeout, context name, privacy, privacy password, username, and password.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SnmpConfiguration implements ISnmpConfiguration {

	private static final String INVALID_SNMP_VERSION_EXCEPTION_MESSAGE = "Invalid SNMP version: ";

	@Default
	@JsonSetter(nulls = SKIP)
	@JsonDeserialize(using = SnmpVersionDeserializer.class)
	private final SnmpVersion version = SnmpVersion.V1;

	@Default
	@JsonSetter(nulls = SKIP)
	private char[] community = new char[] { 'p', 'u', 'b', 'l', 'i', 'c' };

	@Default
	@JsonSetter(nulls = SKIP)
	private Integer port = 161;

	@Default
	@JsonSetter(nulls = SKIP)
	@JsonDeserialize(using = TimeDeserializer.class)
	private final Long timeout = 120L;

	@JsonSetter(nulls = SKIP)
	@JsonDeserialize(using = MultiValueDeserializer.class)
	private String hostname;

	@JsonSetter(nulls = SKIP)
	private int[] retryIntervals;

	@Override
	public String toString() {
		return version.getDisplayName() + " (" + new String(community) + ")";
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
				return V1;
			}

			if (
				"2".equals(lowerCaseVersion) ||
				"v2".equals(lowerCaseVersion) ||
				"v2c".equals(lowerCaseVersion) ||
				"2c".equals(lowerCaseVersion)
			) {
				return V2C;
			}

			throw new IllegalArgumentException(INVALID_SNMP_VERSION_EXCEPTION_MESSAGE + version);
		}
	}

	@Override
	public void validateConfiguration(final String resourceKey) throws InvalidConfigurationException {
		final SnmpVersion snmpVersion = version;

		final String displayName = snmpVersion.getDisplayName();

		StringHelper.validateConfigurationAttribute(
			community,
			attr -> attr == null || attr.length == 0,
			() ->
				String.format(
					"Resource %s - No community string configured for %s. This resource will not be monitored.",
					resourceKey,
					displayName
				)
		);

		StringHelper.validateConfigurationAttribute(
			port,
			attr -> attr == null || attr < 1 || attr > 65535,
			() ->
				String.format(
					"Resource %s - Invalid port configured for protocol %s. Port value returned: %s." +
					" This resource will not be monitored. Please verify the configured port value.",
					resourceKey,
					displayName,
					port
				)
		);

		StringHelper.validateConfigurationAttribute(
			timeout,
			attr -> attr == null || attr < 0L,
			() ->
				String.format(
					"Resource %s - Timeout value is invalid for protocol %s." +
					" Timeout value returned: %s. This resource will not be monitored. Please verify the configured timeout value.",
					resourceKey,
					displayName,
					timeout
				)
		);

		StringHelper.validateConfigurationAttribute(
			retryIntervals,
			attr -> Objects.nonNull(attr) && Arrays.stream(attr).allMatch(value -> value < 1),
			() ->
				String.format(
					"Resource %s - retryIntervals value is invalid for protocol %s." +
					" retryIntervals value returned: %s. This resource will not be monitored. Please verify the configured retryIntervals value.",
					resourceKey,
					displayName,
					retryIntervals
				)
		);
	}

	@Override
	public int getIntVersion() {
		return version.intVersion;
	}

	@Override
	public IConfiguration copy() {
		return SnmpConfiguration
			.builder()
			.community(community)
			.port(port)
			.timeout(timeout)
			.retryIntervals(retryIntervals)
			.version(version)
			.hostname(hostname)
			.build();
	}
}
