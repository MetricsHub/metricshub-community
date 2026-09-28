package org.metricshub.opamp.client;

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
import org.metricshub.opamp.proto.ServerErrorResponse;
import org.metricshub.opamp.proto.ServerToAgent;

/**
 * Callbacks invoked by the OpAMP client. All methods have no-op defaults; they are invoked on the
 * client's polling thread and must return quickly.
 */
public interface OpampClientCallbacks {
	/**
	 * Called on the first successful exchange with the server, and again after connectivity is
	 * re-established following failures.
	 */
	default void onConnect() {}

	/**
	 * Called when an exchange with the server fails at the transport level.
	 *
	 * @param error            the failure cause
	 * @param nextAttemptDelay the delay before the next attempt
	 */
	default void onConnectFailed(Throwable error, Duration nextAttemptDelay) {}

	/**
	 * Called when the server reports an error in response to a previously sent message.
	 *
	 * @param errorResponse the error reported by the server
	 */
	default void onErrorResponse(ServerErrorResponse errorResponse) {}

	/**
	 * Called for every successfully parsed {@code ServerToAgent} message, after the client
	 * processed the standard fields it handles itself (identification, flags, package offers).
	 * This is the extension point for future capabilities such as remote configuration.
	 *
	 * @param message the message received from the server
	 */
	default void onMessage(ServerToAgent message) {}
}
