package org.metricshub.web.dto;

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

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO describing the current or last-known status of an agent restart request.
 */
@Schema(description = "Status of the last (or current) MetricsHub Agent restart request")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RestartStatus {

	/**
	 * Restart lifecycle states exposed to REST clients.
	 */
	public enum State {
		/** No restart has ever been requested. */
		IDLE,
		/** A restart is currently running in the background. */
		IN_PROGRESS,
		/** The last restart completed successfully. */
		SUCCEEDED,
		/** The last restart failed with an error. */
		FAILED
	}

	@Schema(description = "Current restart state")
	private State state;

	@Schema(description = "Human-readable message associated with the current state")
	private String message;

	@Schema(description = "Instant at which the current or last restart was started")
	private Instant startedAt;

	@Schema(description = "Instant at which the last restart ended (null while IN_PROGRESS)")
	private Instant endedAt;

	@Schema(
		description = "Generation of the currently active AgentContext. Increments on every context swap; useful to " +
			"confirm from a client that a restart actually took effect."
	)
	private long contextGeneration;

	@Schema(
		description = "Identifier of the restart request this status refers to, matching the requestId returned by " +
			"POST /api/agent/restart. Monotonically increasing. Clients should treat a terminal state (SUCCEEDED or " +
			"FAILED) as the outcome of their own request only when this value is greater than or equal to their own " +
			"requestId — a smaller value belongs to an earlier restart that finished while theirs was still queued. " +
			"Null until the first restart request."
	)
	private Long requestId;
}
