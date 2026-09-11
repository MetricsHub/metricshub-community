package org.metricshub.engine.telemetry;

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
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * Represents properties related to a host, including information about IPMI, WMI, WBEM, and connector namespaces.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
public class HostProperties {

	private boolean isLocalhost;
	private String ipmitoolCommand;
	private int ipmiExecutionCount;

	@Default
	private Set<String> possibleWmiNamespaces = new TreeSet<>();

	@Default
	private Set<String> possibleWbemNamespaces = new TreeSet<>();

	private String vCenterTicket;
	private boolean osCommandExecutesLocally;
	private boolean osCommandExecutesRemotely;
	private boolean mustCheckSshStatus;

	/**
	 * Whether the local shell must be tested for a host configured with an OS Command configuration
	 * and no SSH configuration. Only set by on-demand protocol checks: the collect strategy leaves it
	 * {@code false} so that a local shell test is never published as an SSH health metric.
	 */
	private boolean mustCheckOsCommandStatus;

	@Default
	private Map<String, ConnectorNamespace> connectorNamespaces = new ConcurrentHashMap<>();

	/**
	 * Get the connector namespace defined for the given connector identifier
	 *
	 * @param connectorId the identifier of the connector namespace
	 * @return ConnectorNamespace instance
	 */
	public ConnectorNamespace getConnectorNamespace(@NonNull final String connectorId) {
		return connectorNamespaces.computeIfAbsent(connectorId, _ -> ConnectorNamespace.builder().build());
	}
}
