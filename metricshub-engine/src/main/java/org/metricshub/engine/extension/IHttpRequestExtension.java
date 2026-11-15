package org.metricshub.engine.extension;

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

import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * An extension that executes the HTTP request of an {@link HttpSource} and gives the response itself, with its
 * status code, where {@link IProtocolExtension#processSource} gives the text the source asks for.
 */
public interface IHttpRequestExtension {
	/**
	 * Executes the HTTP request of the source. Its <code>resultContent</code> plays no part.
	 *
	 * @param httpSource       The request
	 * @param connectorId      The identifier of the connector, which holds the embedded files of the request
	 * @param telemetryManager The telemetry manager, which gives the host and its HTTP configuration
	 * @return The response, <code>null</code> when the request got none
	 */
	HttpResult executeHttpRequest(HttpSource httpSource, String connectorId, TelemetryManager telemetryManager);
}
