# Technical Notes

Detailed behavior and configuration notes for MetricsHub Community.

- [MCP Server](#mcp-server)
- [File log capture](#file-log-capture)
- [OS Command and SSH health checks](#os-command-and-ssh-health-checks)

## MCP Server

The agent exposes its tools to AI assistants through an MCP server on the web port (`31888` by default, HTTPS, authenticated with an API key created by `metricshub-api-key create <alias>` and passed as `Authorization: Bearer <key>`):

| Transport | Endpoint | Status |
| --- | --- | --- |
| Streamable HTTP (recommended) | `https://<host>:31888/mcp` | Default since 3.9.07 (`spring.ai.mcp.server.protocol: STREAMABLE`) |
| HTTP with SSE (legacy) | `https://<host>:31888/sse` then `POST /mcp/message` | Served alongside for existing clients; hardened against clients that reconnect their event stream without re-initializing |

The legacy transport can be tuned or disabled under the `web:` section of `metricshub.yaml`:

| Setting | Default | Description |
| --- | --- | --- |
| `mcp.sse.enabled` | `true` | Serve the legacy SSE endpoints next to `/mcp` (ignored when `spring.ai.mcp.server.protocol` is `SSE`, where they are the only endpoints) |
| `mcp.sse.message-timeout` | `5m` | Maximum wait for the handling of one message posted on `/mcp/message` (answered with HTTP 504 afterwards; the handling itself is not interrupted) |
| `mcp.sse.ping-timeout` | `5m` | A session whose keep-alive ping is not answered within this delay is closed |
| `mcp.sse.initialization-timeout` | `2m` | A session that never completes the `initialize` handshake is closed after this delay |
| `spring.ai.mcp.server.keep-alive-interval` | `30s` | Keep-alive ping interval of the legacy SSE transport (`spring.ai.mcp.server.streamable-http.keep-alive-interval` for Streamable HTTP) |

## File log capture

When a file source uses wildcards or multiple paths, LOG mode includes an empty
`<<<LOG:file="...">>>` / `<<<END_LOG>>>` block for each accessible file on its first
poll and on subsequent polls with no new content. The first poll initializes the
cursor without reading existing content. A single literal path retains its
content-only output format.

## OS Command and SSH health checks

### Local execution

A local resource identifies the machine running MetricsHub, whether through `localhost`, a loopback address, or a hostname/IP address resolving to a local network interface. The hostname does not need to be the literal `localhost`.

SSH and OS Command share an extension, but their configurations are distinct. The guided configuration UI and MCP checks validate that the configuration matches the requested protocol before executing it: an OS Command configuration alone cannot satisfy an SSH check, and vice versa.

For SSH-configured local resources, MetricsHub preserves the local command execution optimization used during collection. A successful local check confirms that commands can execute through this collection path; it does not validate the SSH daemon or credentials. Requesting an SSH check does not force a network connection when local execution is used.

### Collection metrics

| Configuration and execution path | Health check | Protocol label |
| --- | --- | --- |
| OS Command only, local resource | Local shell command | `oscommand` |
| SSH configured, local command execution | Local shell command | `ssh` |
| SSH configured, remote command execution | Command over SSH | `ssh` |

The protocol label identifies the configured protocol being checked, not necessarily the transport used by the local execution optimization. When both SSH and OS Command are configured for collection, the shared extension follows the SSH health-check path.

A performed check reports `metricshub.host.up{protocol="oscommand"}` or `metricshub.host.up{protocol="ssh"}` as `1` on success and `0` on failure. A successful OS Command check therefore contributes to `metricshub.host.observed=1`, just like any other successful protocol check. The observed metric is `0` if no protocol check succeeds.

For a remote resource configured only with OS Command, the health check is skipped: no OS Command host-up metric is emitted, and the skipped check does not count as a successful observation. Running a command on the MetricsHub machine cannot establish the health of a remote host.

### OS Command timeout

The local OS Command health check uses the configured OS Command timeout, including an override supplied by the guided UI or MCP. If the configured timeout is absent or not positive, it defaults to 30 seconds.
