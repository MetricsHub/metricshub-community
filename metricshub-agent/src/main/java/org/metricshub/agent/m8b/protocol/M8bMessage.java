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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * Envelope of the M8B tunnel protocol (version {@link #PROTOCOL_VERSION}). Every WebSocket text frame
 * carries exactly one JSON object whose {@code type} property selects one of the records below.
 * <p>
 * A message whose {@code type} is unknown deserializes as {@link Unknown} so that a newer peer can add
 * message types without breaking older agents: unknown messages are answered with a
 * {@link ProtocolError} and otherwise ignored.
 * </p>
 */
@JsonTypeInfo(
	use = Id.NAME,
	include = As.PROPERTY,
	property = "type",
	visible = true,
	defaultImpl = M8bMessage.Unknown.class
)
@JsonSubTypes(
	{
		@Type(value = M8bMessage.AgentRegister.class, name = M8bMessage.AgentRegister.TYPE),
		@Type(value = M8bMessage.AgentRegistered.class, name = M8bMessage.AgentRegistered.TYPE),
		@Type(value = M8bMessage.HeartbeatPing.class, name = M8bMessage.HeartbeatPing.TYPE),
		@Type(value = M8bMessage.HeartbeatPong.class, name = M8bMessage.HeartbeatPong.TYPE),
		@Type(value = M8bMessage.HostsUpdated.class, name = M8bMessage.HostsUpdated.TYPE),
		@Type(value = M8bMessage.ToolInvoke.class, name = M8bMessage.ToolInvoke.TYPE),
		@Type(value = M8bMessage.ToolResult.class, name = M8bMessage.ToolResult.TYPE),
		@Type(value = M8bMessage.ToolError.class, name = M8bMessage.ToolError.TYPE),
		@Type(value = M8bMessage.ProtocolError.class, name = M8bMessage.ProtocolError.TYPE)
	}
)
public sealed interface M8bMessage {
	/**
	 * Version of the tunnel protocol implemented by this agent.
	 */
	int PROTOCOL_VERSION = 1;

	/**
	 * @return the value of the {@code type} discriminator
	 */
	@JsonIgnore
	String type();

	/**
	 * First frame sent by the agent once the WebSocket is open.
	 *
	 * @param protocolVersion      protocol version implemented by the agent
	 * @param agent                agent identity
	 * @param toolRegistryRevision fingerprint of {@code tools}, stable across restarts
	 * @param tools                tools the Governor may invoke on this agent
	 * @param hosts                hosts monitored by this agent
	 */
	record AgentRegister(
		int protocolVersion,
		AgentDescriptor agent,
		String toolRegistryRevision,
		List<ToolDescriptor> tools,
		List<HostDescriptor> hosts
	) implements M8bMessage {
		/** Discriminator value. */
		public static final String TYPE = "agent.register";

		@Override
		public String type() {
			return TYPE;
		}
	}

	/**
	 * Server acknowledgement of {@link AgentRegister}; carries the limits the agent must honor.
	 *
	 * @param heartbeatIntervalSeconds interval between two heartbeats
	 * @param maxPayloadBytes          largest text frame the server accepts
	 * @param maxInFlight              maximum number of concurrent tool invocations
	 */
	record AgentRegistered(long heartbeatIntervalSeconds, long maxPayloadBytes, int maxInFlight) implements M8bMessage {
		/** Discriminator value. */
		public static final String TYPE = "agent.registered";

		@Override
		public String type() {
			return TYPE;
		}
	}

	/**
	 * Application-level heartbeat sent by the agent.
	 */
	record HeartbeatPing() implements M8bMessage {
		/** Discriminator value. */
		public static final String TYPE = "heartbeat.ping";

		@Override
		public String type() {
			return TYPE;
		}
	}

	/**
	 * Server answer to {@link HeartbeatPing}.
	 */
	record HeartbeatPong() implements M8bMessage {
		/** Discriminator value. */
		public static final String TYPE = "heartbeat.pong";

		@Override
		public String type() {
			return TYPE;
		}
	}

	/**
	 * Full replacement of the host inventory, sent when the agent configuration changed.
	 *
	 * @param hosts hosts monitored by this agent
	 */
	record HostsUpdated(List<HostDescriptor> hosts) implements M8bMessage {
		/** Discriminator value. */
		public static final String TYPE = "hosts.updated";

		@Override
		public String type() {
			return TYPE;
		}
	}

	/**
	 * Tool invocation request from the Governor.
	 *
	 * @param requestId correlation id echoed in the answer
	 * @param tool      advertised tool name
	 * @param arguments arguments object matching the advertised input schema
	 * @param timeoutMs execution deadline enforced by both sides
	 */
	record ToolInvoke(String requestId, String tool, JsonNode arguments, long timeoutMs) implements M8bMessage {
		/** Discriminator value. */
		public static final String TYPE = "tool.invoke";

		@Override
		public String type() {
			return TYPE;
		}
	}

	/**
	 * Successful answer to {@link ToolInvoke}.
	 *
	 * @param requestId  correlation id of the request
	 * @param result     JSON returned by the tool
	 * @param durationMs execution time
	 */
	record ToolResult(String requestId, JsonNode result, long durationMs) implements M8bMessage {
		/** Discriminator value. */
		public static final String TYPE = "tool.result";

		@Override
		public String type() {
			return TYPE;
		}
	}

	/**
	 * Failed answer to {@link ToolInvoke}.
	 *
	 * @param requestId correlation id of the request
	 * @param code      failure category
	 * @param message   human-readable detail
	 */
	record ToolError(String requestId, ToolErrorCode code, String message) implements M8bMessage {
		/** Discriminator value. */
		public static final String TYPE = "tool.error";

		@Override
		public String type() {
			return TYPE;
		}
	}

	/**
	 * Protocol-level error, not tied to a tool invocation.
	 *
	 * @param code    error code
	 * @param message human-readable detail
	 */
	record ProtocolError(String code, String message) implements M8bMessage {
		/** Discriminator value. */
		public static final String TYPE = "error";

		@Override
		public String type() {
			return TYPE;
		}
	}

	/**
	 * Any message whose {@code type} this agent does not know.
	 *
	 * @param unknownType the unknown discriminator value
	 */
	record Unknown(@JsonProperty("type") String unknownType) implements M8bMessage {
		@Override
		public String type() {
			return unknownType;
		}
	}
}
