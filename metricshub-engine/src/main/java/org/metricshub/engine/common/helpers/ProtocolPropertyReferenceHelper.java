package org.metricshub.engine.common.helpers;

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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * Utility class for updating and replacing protocol properties macros in a text string.
 * This class handles various protocols like HTTP, SNMP, etc
 * and properties like username, password, authentication token, etc, depending on the specified protocol.
 * Protocols and properties are case-insensitive.
 */
@Slf4j
public class ProtocolPropertyReferenceHelper {

	private static final String PROTOCOL_DOT_PROPERTY_REFERENCE_REGEX = "([^.]+).([^}]+)";

	/**
	 * Returns the property associated to the protocol from the parameter protocolDotProperty
	 * using the configurations of {@link TelemetryManager}.
	 * @param protocolDotProperty A {@link String} that is composed as "protocol.property".
	 * @param telemetryManager The {@link TelemetryManager} instance.
	 * @return The protocol's property if possible, null otherwise.
	 */
	public static Object getProtocolProperty(final String protocolDotProperty, final TelemetryManager telemetryManager) {
		if (protocolDotProperty == null || protocolDotProperty.isEmpty()) {
			return null;
		}

		final Matcher matcher = Pattern.compile(PROTOCOL_DOT_PROPERTY_REFERENCE_REGEX).matcher(protocolDotProperty);

		final String protocol;
		final String property;
		if (matcher.find()) {
			protocol = matcher.group(1).toLowerCase();
			property = matcher.group(2).toLowerCase();
		} else {
			return null;
		}

		final Optional<IConfiguration> optionalConfiguration = getProtocol(protocol, telemetryManager);
		if (optionalConfiguration.isPresent()) {
			return optionalConfiguration.get().getProperty(property);
		} else {
			return null;
		}
	}

	/**
	 * Tries to return the first corresponding {@link IConfiguration} from the {@link TelemetryManager}.
	 * @param protocol The protocol name.
	 * @param telemetryManager The {@link TelemetryManager} instance.
	 * @return The right {@link IConfiguration} or null if not possible.
	 */
	private static Optional<IConfiguration> getProtocol(final String protocol, final TelemetryManager telemetryManager) {
		final Map<Class<? extends IConfiguration>, IConfiguration> configurations = telemetryManager
			.getHostConfiguration()
			.getConfigurations();
		return configurations
			.values()
			.stream()
			.filter(Objects::nonNull) // Filter out null configurations
			.filter(config -> config.isCorrespondingProtocol(protocol)) // Filter out wrong protocols
			.findFirst();
	}
}
