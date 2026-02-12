package org.metricshub.web.dto.m8b;

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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Base class for WebSocket messages between MetricsHub agent and M8B server.
 * <p>
 * The message type is determined by the "type" property in JSON and maps
 * to the appropriate subclass for deserialization.
 * </p>
 */
@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
	{
		@JsonSubTypes.Type(value = M8bWebSocketMessage.Register.class, name = "REGISTER"),
		@JsonSubTypes.Type(value = M8bWebSocketMessage.Request.class, name = "REQUEST"),
		@JsonSubTypes.Type(value = M8bWebSocketMessage.Response.class, name = "RESPONSE"),
		@JsonSubTypes.Type(value = M8bWebSocketMessage.Ping.class, name = "PING"),
		@JsonSubTypes.Type(value = M8bWebSocketMessage.Pong.class, name = "PONG"),
		@JsonSubTypes.Type(value = M8bWebSocketMessage.Error.class, name = "ERROR")
	}
)
public abstract class M8bWebSocketMessage {

	/**
	 * Agent registration message (Agent → Server).
	 * <p>
	 * Sent when the agent first connects to register itself with the M8B control plane.
	 * </p>
	 */
	@Data
	@EqualsAndHashCode(callSuper = true)
	public static class Register extends M8bWebSocketMessage {

		private String agentId;
		private String hostname;
		private String version;
		private Map<String, String> metadata;
	}

	/**
	 * Proxy request (Server → Agent).
	 * <p>
	 * Sent by the M8B server to forward an HTTP request to this agent's local REST API.
	 * </p>
	 */
	@Data
	@EqualsAndHashCode(callSuper = true)
	public static class Request extends M8bWebSocketMessage {

		private String requestId;
		private String method;
		private String path;
		private Map<String, String> headers;
		private Object body;
	}

	/**
	 * Proxy response (Agent → Server).
	 * <p>
	 * Sent by the agent containing the response from executing a proxied request.
	 * </p>
	 */
	@Data
	@EqualsAndHashCode(callSuper = true)
	public static class Response extends M8bWebSocketMessage {

		private String requestId;
		private int status;
		private Map<String, String> headers;
		private Object body;
	}

	/**
	 * Heartbeat ping (Server → Agent).
	 * <p>
	 * Sent by the server to check if the agent is still alive.
	 * </p>
	 */
	@Data
	@EqualsAndHashCode(callSuper = true)
	public static class Ping extends M8bWebSocketMessage {

		private long timestamp = System.currentTimeMillis();
	}

	/**
	 * Heartbeat pong (Agent → Server).
	 * <p>
	 * Response to a ping message with the same timestamp.
	 * </p>
	 */
	@Data
	@EqualsAndHashCode(callSuper = true)
	public static class Pong extends M8bWebSocketMessage {

		private long timestamp;
	}

	/**
	 * Error message (bidirectional).
	 * <p>
	 * Indicates an error occurred during message processing.
	 * </p>
	 */
	@Data
	@EqualsAndHashCode(callSuper = true)
	public static class Error extends M8bWebSocketMessage {

		private String requestId;
		private String code;
		private String message;
	}
}
