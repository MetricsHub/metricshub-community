package org.metricshub.agent.opentelemetry.client;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
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

import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import lombok.NoArgsConstructor;
import org.metricshub.agent.opentelemetry.LogContextSetter;

/**
 * Noop client that does nothing.
 */
@NoArgsConstructor
public class NoopClient implements IOtelClient {

	/**
	 * Does nothing.
	 * @param request          The request containing the metrics to send.
	 * @param logContextSetter The log context setter to use for logging.
	 * This method is a no-op and does not send any metrics.
	 */
	@Override
	public void send(ExportMetricsServiceRequest request, LogContextSetter logContextSetter) {
		// NO-OP
	}

	/**
	 * Does nothing.
	 */
	@Override
	public void shutdown() {
		// NO-OP
	}
}
