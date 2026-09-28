package org.metricshub.opamp.client.http;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub OpAMP Client
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

import java.time.Duration;
import java.util.Optional;

/**
 * Response of one OpAMP HTTP exchange.
 *
 * @param statusCode the HTTP status code
 * @param body       the raw response body (a serialized {@code ServerToAgent} message on success)
 * @param retryAfter the delay parsed from the {@code Retry-After} header, when present
 */
public record TransportResponse(int statusCode, byte[] body, Optional<Duration> retryAfter) {
	/**
	 * Indicates whether the exchange succeeded (HTTP 200).
	 *
	 * @return {@code true} when the status code is 200
	 */
	public boolean isSuccess() {
		return statusCode == 200;
	}
}
