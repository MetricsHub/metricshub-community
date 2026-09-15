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
 * A monitored host (MetricsHub resource) reported to the M8B Governor so it can route a host name to
 * the agent that monitors it.
 *
 * @param resourceKey   MetricsHub resource key, unique within the agent configuration
 * @param resourceGroup key of the resource group holding the resource
 * @param hostnames     host name configured per protocol (protocol name to host name)
 * @param attributes    resource attributes (e.g. {@code host.name}, {@code host.type})
 */
public record HostDescriptor(
	String resourceKey,
	String resourceGroup,
	Map<String, String> hostnames,
	Map<String, String> attributes
) {}
