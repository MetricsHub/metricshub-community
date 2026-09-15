package org.metricshub.agent.m8b.protocol;

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

import java.util.Map;

/**
 * Identity of the MetricsHub Agent as reported to the M8B Governor at registration. The instance uid
 * itself travels in the handshake header, not in this payload.
 *
 * @param name        service name (e.g. {@code MetricsHub Agent})
 * @param version     MetricsHub version
 * @param edition     {@code Community} or {@code Enterprise}
 * @param hostName    host running the agent
 * @param osType      OpenTelemetry OS type of the agent host
 * @param arch        CPU architecture of the agent host
 * @param buildNumber build number of the agent
 * @param attributes  every resolved agent attribute (pre-built, agent-level and fleet-level)
 */
public record AgentDescriptor(
	String name,
	String version,
	String edition,
	String hostName,
	String osType,
	String arch,
	String buildNumber,
	Map<String, String> attributes
) {}
