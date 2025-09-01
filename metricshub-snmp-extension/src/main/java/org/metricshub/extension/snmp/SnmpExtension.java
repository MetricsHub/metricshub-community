package org.metricshub.extension.snmp;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub SNMP Extension
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

import com.fasterxml.jackson.databind.JsonNode;
import java.util.function.UnaryOperator;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.configuration.IConfiguration;

/**
 * This class extends {@link AbstractSnmpExtension}, reports the supported features,
 * and processes SNMP sources and criteria.
 */
@Slf4j
@AllArgsConstructor
public class SnmpExtension extends AbstractSnmpExtension {

	/**
	 * The identifier for the Snmp protocol.
	 */
	private static final String IDENTIFIER = "snmp";

	@NonNull
	private SnmpRequestExecutor snmpRequestExecutor;

	/**
	 * Creates a new instance of the {@link SnmpExtension} implementation.
	 */
	public SnmpExtension() {
		snmpRequestExecutor = new SnmpRequestExecutor();
	}

	@Override
	public boolean isValidConfiguration(IConfiguration configuration) {
		return configuration instanceof SnmpConfiguration;
	}

	@Override
	protected Class<SnmpConfiguration> getConfigurationClass() {
		return SnmpConfiguration.class;
	}

	@Override
	public IConfiguration buildConfiguration(
		@NonNull String configurationType,
		@NonNull JsonNode jsonNode,
		UnaryOperator<char[]> decrypt
	) throws InvalidConfigurationException {
		try {
			final SnmpConfiguration snmpConfiguration = newObjectMapper().treeToValue(jsonNode, SnmpConfiguration.class);

			if (decrypt != null) {
				char[] community = snmpConfiguration.getCommunity();
				if (community != null) {
					// Decrypt the community
					snmpConfiguration.setCommunity(decrypt.apply(community));
				}
			}

			return snmpConfiguration;
		} catch (Exception e) {
			final String errorMessage = String.format("Error while reading SNMP Configuration. Error: %s", e.getMessage());
			log.error(errorMessage);
			log.debug("Error while reading SNMP Configuration. Stack trace:", e);
			throw new InvalidConfigurationException(errorMessage, e);
		}
	}

	@Override
	public String getIdentifier() {
		return IDENTIFIER;
	}

	@Override
	public AbstractSnmpRequestExecutor getRequestExecutor() {
		return snmpRequestExecutor;
	}
}
