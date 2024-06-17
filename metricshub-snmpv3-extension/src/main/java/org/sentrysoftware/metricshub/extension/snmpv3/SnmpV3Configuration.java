package org.sentrysoftware.metricshub.extension.snmpv3;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub SNMP V3 Extension
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.sentrysoftware.metricshub.engine.common.exception.InvalidConfigurationException;
import org.sentrysoftware.metricshub.engine.common.helpers.StringHelper;
import org.sentrysoftware.metricshub.engine.deserialization.TimeDeserializer;
import org.sentrysoftware.metricshub.extension.snmp.ISnmpConfiguration;

/**
 * The SnmpV3Configuration class represents the configuration for SNMP v3 in the
 * MetricsHub engine. It implements the ISnmpConfiguration interface and
 * includes settings such as SNMP version, port, timeout, context
 * name, authentication type, privacy, privacy password, username, and password.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SnmpV3Configuration implements ISnmpConfiguration {

	private static final int V3 = 3;
	private static final String INVALID_AUTH_TYPE_EXCEPTION_MESSAGE = "Invalid authentication type: ";
	private static final String INVALID_PRIVACY_VALUE_EXCEPTION_MESSAGE = "Invalid privacy value: ";

	@Default
	@JsonSetter(nulls = SKIP)
	private Integer port = 161;

	@Default
	@JsonSetter(nulls = SKIP)
	@JsonDeserialize(using = TimeDeserializer.class)
	private Long timeout = 120L;

	private String contextName;

	@JsonDeserialize(using = AuthTypeDeserializer.class)
	private AuthType authType;

	@JsonDeserialize(using = PrivacyDeserializer.class)
	private Privacy privacy;

	private String username;
	private char[] privacyPassword;
	private char[] password;
	private int[] retryIntervals;

	@Override
	public String toString() {
		String description = "SNMP V3";
		if (username != null) {
			description = description + " as " + username;
		}
		if (privacy != null && privacy != Privacy.NO_ENCRYPTION) {
			description = description + " (" + privacy + "-encrypted)";
		}
		return description;
	}

	@Override
	public void validateConfiguration(final String resourceKey) throws InvalidConfigurationException {
		StringHelper.validateConfigurationAttribute(
			port,
			attr -> attr == null || attr < 1 || attr > 65535,
			() ->
				String.format(
					"Resource %s - Invalid port configured: %s. Please verify the configured port value.",
					resourceKey,
					port
				)
		);

		StringHelper.validateConfigurationAttribute(
			timeout,
			attr -> attr == null || attr < 0L,
			() ->
				String.format(
					"Resource %s - Timeout value is invalid: %s. Please verify the configured timeout value.",
					resourceKey,
					timeout
				)
		);

		StringHelper.validateConfigurationAttribute(
			username,
			attr -> attr == null || attr.isEmpty(),
			() ->
				String.format(
					"Resource %s - No username configured for protocol %s." +
					" This resource will not be monitored. Please verify the configured username.",
					resourceKey,
					username
				)
		);

		StringHelper.validateConfigurationAttribute(
			authType,
			attr -> attr == null,
			() ->
				String.format(
					"Resource %s - No username configured for protocol %s." +
					" This resource will not be monitored. Please verify the configured authtype.",
					resourceKey,
					authType
				)
		);
	}

	/**
	 * Enum of authentication types for SNMP v3.
	 */
	@AllArgsConstructor
	public enum AuthType {
		NO_AUTH,
		MD5,
		SHA;

		/**
		 * Interpret the specified label and returns corresponding value.
		 *
		 * @param authType String to be interpreted
		 * @return Corresponding {@link AuthType} value
		 */
		public static AuthType interpretValueOf(@NonNull final String authType) {
			final String lowerCaseAuthType = authType.toLowerCase();

			if (lowerCaseAuthType.contains("no") && lowerCaseAuthType.contains("auth")) {
				return NO_AUTH;
			} else if (lowerCaseAuthType.contains("md5")) {
				return MD5;
			} else if (lowerCaseAuthType.contains("sha")) {
				return SHA;
			}

			throw new IllegalArgumentException(INVALID_AUTH_TYPE_EXCEPTION_MESSAGE + authType);
		}
	}

	/**
	 * SNMP v3 Privacy (encryption type). Represents the encryption algorithm to be
	 * used in SNMP v3 connections.
	 */
	@AllArgsConstructor
	public enum Privacy {
		NO_ENCRYPTION,
		AES,
		DES;

		/**
		 * Interpret the specified label and returns corresponding value.
		 *
		 * @param privacy String to be interpreted
		 * @return Corresponding {@link Privacy} value
		 */
		public static Privacy interpretValueOf(@NonNull final String privacy) {
			final String lowerCasePrivacy = privacy.toLowerCase();

			if (lowerCasePrivacy.equals("none") || lowerCasePrivacy.equals("no")) {
				return NO_ENCRYPTION;
			} else if (lowerCasePrivacy.equals("aes")) {
				return AES;
			} else if (lowerCasePrivacy.equals("des")) {
				return DES;
			}

			throw new IllegalArgumentException(INVALID_PRIVACY_VALUE_EXCEPTION_MESSAGE + privacy);
		}
	}

	@Override
	public int getIntVersion() {
		return V3;
	}
}
