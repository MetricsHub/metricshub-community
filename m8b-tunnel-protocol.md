# M8B Tunnel Protocol — version 1

**Audience:** maintainers of the MetricsHub Agent and of the M8B Governor (`MetricsHub/metricshub-fleet`).
**Status:** authoritative specification. The fleet repository mirrors this file as `docs/tunnel-protocol.md`; the golden fixtures under `metricshub-agent/src/test/resources/m8b/` are copied there unchanged.

The tunnel is a persistent **outbound** WebSocket opened by every MetricsHub Agent toward the M8B Governor. Over it the agent registers its identity, the tools it exposes and the hosts it monitors, and executes the tools the Governor invokes. It complements OpAMP (status and upgrades) and never replaces it.

| Section | Content |
|---|---|
| [1. Connection](#1-connection) | Handshake, identity, authentication |
| [2. Envelope](#2-envelope) | Frame format and discriminator |
| [3. Messages](#3-messages) | Field-by-field reference |
| [4. Lifecycle](#4-lifecycle) | Registration, heartbeats, reconnection, close codes |
| [5. Tool invocation](#5-tool-invocation) | Correlation, timeouts, error codes |
| [6. Compatibility rules](#6-compatibility-rules) | How both sides evolve independently |
| [7. Security](#7-security) | Trust model |

---

## 1. Connection

```
Agent                                              M8B Governor
  │  GET /ws/agent  (Upgrade: websocket)                 │
  │  Authorization: Bearer <agent secret>                │
  │  X-MetricsHub-Agent-Uid: <uuid>                      │
  │ ────────────────────────────────────────────────────► │
  │  101 Switching Protocols  |  401  |  400             │
  │ ◄──────────────────────────────────────────────────── │
  │  {"type":"agent.register", ...}                      │
  │ ────────────────────────────────────────────────────► │
  │  {"type":"agent.registered", ...}                    │
  │ ◄──────────────────────────────────────────────────── │
```

* **Transport:** WebSocket over TLS (`wss://`). Plain `ws://` is tolerated for loopback development only.
* **Authentication:** `Authorization: Bearer <secret>`; the secret is one of the fleet's configured agent secrets (`m8b.headers` on the agent side). A bad or missing secret is refused at the handshake with `401`.
* **Identity:** `X-MetricsHub-Agent-Uid` carries the agent's persistent instance uid — the same UUIDv7 persisted for OpAMP in the MetricsHub `security` directory (`opamp-instance-uid`) — so the fleet sees one agent whichever channel reports. A value that is not a canonical UUID is refused with `400`.
* **First frame:** the agent must send `agent.register` within 10 s of the upgrade; otherwise the server closes with code `4002`.

## 2. Envelope

Every WebSocket **text** frame carries exactly one JSON object. The `type` property selects the message:

```json
{ "type": "heartbeat.ping" }
```

Rules:

* `type` is a dotted lowercase string (`<subject>.<verb|noun>`).
* Properties whose value is `null` are omitted.
* Unknown properties are ignored by both sides.
* A frame whose `type` is unknown is answered with `{"type":"error","code":"UNKNOWN_MESSAGE_TYPE"}` and otherwise ignored; the connection stays open.
* Binary frames are not used. A binary frame is a protocol error: the server closes `1003`, the agent closes `1000` carrying the reason `Binary frames are not supported` — a WebSocket client may only send `1000` or a code in `[3000, 4999]`, so it states the cause in the reason instead.

## 3. Messages

| Direction | `type` | Purpose |
|---|---|---|
| agent → M8B | `agent.register` | Identity, tool registry and host inventory |
| M8B → agent | `agent.registered` | Acknowledgement and limits |
| agent → M8B | `heartbeat.ping` | Liveness |
| M8B → agent | `heartbeat.pong` | Liveness answer |
| agent → M8B | `hosts.updated` | Full replacement of the host inventory |
| M8B → agent | `tool.invoke` | Run a tool |
| agent → M8B | `tool.result` | Tool succeeded |
| agent → M8B | `tool.error` | Tool failed |
| both | `error` | Protocol-level error |

### 3.1 `agent.register` (agent → M8B)

```json
{
  "type": "agent.register",
  "protocolVersion": 1,
  "agent": {
    "name": "MetricsHub Agent",
    "version": "3.9.07",
    "edition": "Community",
    "hostName": "server-01",
    "osType": "linux",
    "arch": "amd64",
    "buildNumber": "abcdef12",
    "attributes": { "host.name": "server-01", "service.name": "MetricsHub Agent", "version": "3.9.07" }
  },
  "toolRegistryRevision": "sha256:…",
  "tools": [
    { "name": "ListHosts", "description": "…", "inputSchema": { "type": "object", "properties": {} } }
  ],
  "hosts": [
    {
      "resourceKey": "paris-host1",
      "resourceGroup": "paris",
      "hostnames": { "ssh": "paris-host1.example.com" },
      "attributes": { "host.name": "paris-host1.example.com", "host.type": "linux" }
    }
  ]
}
```

| Field | Notes |
|---|---|
| `protocolVersion` | Version of this document implemented by the agent. Always `1` today |
| `agent.name` | Service name (`MetricsHub Agent`, `MetricsHub Enterprise Agent`, …) |
| `agent.version` | MetricsHub version; an explicitly configured `service.version` attribute wins over the built-in version |
| `agent.edition` | `Community` or `Enterprise` |
| `agent.hostName`, `agent.osType`, `agent.arch`, `agent.buildNumber` | Agent host identity; any may be absent |
| `agent.attributes` | Every resolved attribute: pre-built agent attributes, then the agent-level `attributes:`, then the fleet-level `opamp.attributes` which win |
| `toolRegistryRevision` | `sha256:` + hex SHA-256 of the canonical JSON (sorted keys, no nulls) of `tools`. Equal tool sets give equal revisions |
| `tools[]` | Advertised tools, sorted by `name`. `name` is unique. `inputSchema` is the JSON schema of the arguments object exactly as generated by the agent (Spring AI). A tool without arguments advertises `{"type":"object","properties":{}}` |
| `hosts[]` | Hosts the agent actually monitors (configured resources that failed validation are not advertised), sorted by `resourceGroup` then `resourceKey`. A host is identified by the pair (`resourceGroup`, `resourceKey`): a resource key is unique only within its group. `hostnames` maps a protocol name (`ssh`, `snmp`, `wmi`, …) to the host name configured for it; `attributes` are the resource attributes |

The tool list is the **only** source of truth for what M8B may invoke on this agent. Tools listed in the agent's `m8b.excludedTools` are never advertised.

### 3.2 `agent.registered` (M8B → agent)

```json
{ "type": "agent.registered", "heartbeatIntervalSeconds": 30, "maxPayloadBytes": 8388608, "maxInFlight": 4 }
```

| Field | Notes |
|---|---|
| `heartbeatIntervalSeconds` | Interval the agent must use for `heartbeat.ping`; overrides the agent configuration. Accepted range **1..3600**; a value outside it is **clamped to the nearest bound**, not replaced by the agent's configuration — a governor cannot see what an agent configured, so falling back to it would make two agents answer the same registration at different rates. `0` or absent means "not specified", and only then does the agent's own `heartbeatInterval` apply (clamped to the same range) |
| `maxPayloadBytes` | Largest text frame the server accepts. A `tool.result` that would exceed it is replaced by `tool.error` `RESULT_TOO_LARGE`. It bounds what the agent *sends*; the agent also keeps a fixed 8 MiB bound on what it *buffers*, and this value can only lower that one — a server saying a larger number is describing its own buffers, not raising the agent's |
| `maxInFlight` | Maximum number of concurrent `tool.invoke` the agent executes; additional ones are refused with `TOO_MANY_INFLIGHT` |

### 3.3 `heartbeat.ping` / `heartbeat.pong`

No payload. Sent by the agent every `heartbeatIntervalSeconds`; the server answers immediately. Both sides treat **any** inbound frame as proof of liveness.

### 3.4 `hosts.updated` (agent → M8B)

```json
{ "type": "hosts.updated", "hosts": [ … ] }
```

Full replacement of the inventory, same element shape as `agent.register.hosts`. Sent when the agent configuration changed and the resulting inventory differs. Tools cannot change while the agent runs; the tool registry is refreshed on reconnection only (`tools.updated` is reserved for a future version).

### 3.5 `tool.invoke` (M8B → agent)

```json
{ "type": "tool.invoke", "requestId": "b1e4…", "tool": "PingHost", "arguments": { "hostname": ["server-01"] }, "timeoutMs": 60000 }
```

| Field | Notes |
|---|---|
| `requestId` | Opaque correlation id, unique per invocation, echoed in the answer |
| `tool` | An advertised tool name, matched exactly (case-sensitive) |
| `arguments` | JSON object matching the advertised `inputSchema`; `{}` for a tool without arguments |
| `timeoutMs` | Deadline enforced by **both** sides: the agent aborts the tool and answers `TIMEOUT`; the server stops waiting and discards a late answer |

### 3.6 `tool.result` (agent → M8B)

```json
{ "type": "tool.result", "requestId": "b1e4…", "result": { … }, "durationMs": 812 }
```

`result` is the JSON the tool returned, unmodified.

### 3.7 `tool.error` (agent → M8B)

```json
{ "type": "tool.error", "requestId": "b1e4…", "code": "TOOL_NOT_AVAILABLE", "message": "…" }
```

| `code` | Meaning |
|---|---|
| `TOOL_NOT_AVAILABLE` | Not advertised by this agent (excluded, or unknown) |
| `INVALID_ARGUMENTS` | Arguments rejected by the tool binding |
| `EXECUTION_ERROR` | The tool threw |
| `TIMEOUT` | `timeoutMs` elapsed |
| `RESULT_TOO_LARGE` | Serialized result larger than `maxPayloadBytes` |
| `TOO_MANY_INFLIGHT` | `maxInFlight` invocations already running |

### 3.8 `error` (both directions)

```json
{ "type": "error", "code": "UNKNOWN_MESSAGE_TYPE", "message": "…" }
```

Informational; never closes the connection by itself.

## 4. Lifecycle

```
          ┌──────────┐  open + register   ┌────────────┐  agent.registered  ┌───────────┐
 start ──►│CONNECTING│───────────────────►│REGISTERING │───────────────────►│ CONNECTED │
          └────┬─────┘                    └─────┬──────┘                    └─────┬─────┘
               │ failure                        │ close / timeout                 │ close / idle
               ▼                                ▼                                 ▼
          ┌──────────┐  backoff with jitter (RetrySchedule), capped at 10 min
          │ BACKOFF  │◄─────────────────────────────────────────────────────────────┘
          └──────────┘
```

* **One session per agent.** When a second connection registers the same uid, the server adopts it and closes the previous one with `4001`. An agent receiving `4001` must not reconnect immediately: another instance owns the identity (duplicate uid file, cloned VM); it retries with backoff and logs a warning.
* **Heartbeat.** The agent pings every `heartbeatIntervalSeconds`. Either side closes a connection idle for more than 2.5 × the interval with `4003`.
* **Reconnection.** After any close the agent re-registers from scratch: the server treats every `agent.register` as the complete current state (tools and hosts). This is how an upgraded agent replaces its previous tool registry.
* **Shutdown.** The agent closes with `1000`; the server closes with `1001` when it shuts down.

| Close code | Sent by | Meaning |
|---|---|---|
| `1000` | agent | Normal shutdown |
| `1001` | server | Server going away |
| `1002` | server | Protocol version the server does not implement, after `error` `UNSUPPORTED_PROTOCOL_VERSION` |
| `1003` | server | Unsupported data (binary frame); the agent closes `1000` with the reason instead |
| `1009` | server | Frame larger than `maxPayloadBytes` |
| `4001` | server | Superseded by a newer session for the same uid |
| `4002` | server | No `agent.register` within 10 s |
| `4003` | either | Heartbeat timeout |
| `4004` | server | The agent secret this tunnel presented is no longer configured |

## 5. Tool invocation

Server-side checks happen **before** any frame is sent, in this order: agent known → session connected → tool present in the *runtime* registry of that session → arguments valid against the advertised schema → policy (deny / approval lists). Only then does `tool.invoke` leave the server. The agent re-validates: exact tool name, in-flight cap, timeout, payload cap.

Both sides bound the invocation with the same `timeoutMs`; there is no cancel message. A `tool.result` or `tool.error` whose `requestId` is unknown (already timed out, or from a superseded session) is logged and dropped.

## 6. Compatibility rules

1. Adding a message type, a property or an error code is a **minor** change: peers ignore what they do not know and answer unknown types with `error`. `protocolVersion` stays `1`.
2. Removing or renaming a property, or changing its type, is a **major** change and increments `protocolVersion`. A server refusing a version closes with `1002` after sending `error` `UNSUPPORTED_PROTOCOL_VERSION`.
3. Tool contracts belong to the agent: a tool's `inputSchema` may differ between agents. The server must validate against the schema advertised by the target agent, never against a fleet-wide definition.
4. The golden fixtures in `metricshub-agent/src/test/resources/m8b/` pin the wire shape; changing them requires the same change in the fleet repository.

## 7. Security

| Decision | Rationale |
|---|---|
| The agent always dials out; the server never connects to the agent | No inbound firewall rule on monitored sites |
| Only advertised tools are invokable, and `excludedTools` removes a tool from the advertisement itself | The tool registry is the security boundary; an operator can withdraw a capability without touching the fleet |
| Existing tool kill switches (`metricshub.mcp.tool.ssh.enabled`, `metricshub.mcp.tool.win.remote.enabled`) still apply inside the tools | A remotely invoked tool never has more rights than a locally invoked one |
| Limits (`timeoutMs`, `maxPayloadBytes`, `maxInFlight`) are enforced on both sides | A misbehaving peer cannot exhaust the other |
| Credentials travel only in the handshake headers, over TLS, to the configured endpoint | Same convention as `opamp.headers`; values may be keystore-encrypted |
| Approvals for sensitive tools are decided in M8B, between M8B and its users | The agent cannot tell an approved call from any other; the policy lives where the users are |
