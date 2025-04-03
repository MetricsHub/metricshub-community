package org.metricshub.extension.snmpv3;

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

import java.io.IOException;
import java.util.Optional;

import org.metricshub.extension.snmp.AbstractSnmpRequestExecutor;
import org.metricshub.extension.snmp.ISnmpConfiguration;
import org.metricshub.snmp.client.SnmpClient;

/**
 * The SnmpRequestExecutor class extends {@link AbstractSnmpRequestExecutor} and provides utility methods
 * for executing various SNMP V3 requests on local or remote hosts.
 */
public class SnmpV3RequestExecutor extends AbstractSnmpRequestExecutor {

	@Override
	protected SnmpClient createSnmpClient(ISnmpConfiguration protocol, String hostname) throws IOException {
		final SnmpV3Configuration snmpConfig = (SnmpV3Configuration) protocol;
		final String password = Optional.ofNullable(snmpConfig.getPassword()).map(String::valueOf).orElse(null);
		final String privacyPassword = Optional
			.ofNullable(snmpConfig.getPrivacyPassword())
			.map(String::valueOf)
			.orElse(null);
		return new SnmpClient(
			hostname,
			snmpConfig.getPort(),
			snmpConfig.getIntVersion(),
			snmpConfig.getRetryIntervals(),
			null,
			SnmpV3Configuration.AuthType.NO_AUTH.equals(snmpConfig.getAuthType())
				? null
				: snmpConfig.getAuthType().toString(),
			snmpConfig.getUsername(),
			password,
			SnmpV3Configuration.Privacy.NO_ENCRYPTION.equals(snmpConfig.getPrivacy())
				? null
				: snmpConfig.getPrivacy().toString(),
			privacyPassword,
			snmpConfig.getContextName(),
			null
		);
	}
}
