# Technical Notes

Detailed behavior and configuration notes for MetricsHub Community.

- [MCP Server](#mcp-server)
- [File log capture](#file-log-capture)
- [File source over SSH](#file-source-over-ssh)
- [OS Command and SSH health checks](#os-command-and-ssh-health-checks)
- [Job timeout](#job-timeout)
- [Protocol requests counter](#protocol-requests-counter)

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

### Result of a connector or protocol tool

On both transports, a connector tool (`<connector>_<tool>`) or a protocol tool (`<extension>_<tool>`) answers with one text content block per host, in the order of the `hostname` argument, and no longer with one JSON document. A client that parsed that JSON must read the text instead:

- Each block starts with `## <hostname>`.
- A table is a GitHub-flavored Markdown table. Its header is the column names, `Column N` for a column without a name. In a cell, `|` is written `\|` and a line break `<br>`.
- A text is the text as it is.
- An error on a host is `Error: <message>`.
- An error of the whole call (no hostname, an invalid argument) is one block with the message, flagged `isError`.

The other tools (`ListHosts`, `PingHost`, `GetMetricsFromCacheForHost`...) still answer with their JSON.

## File log capture

When a file source uses wildcards or multiple paths, LOG mode includes an empty
`<<<LOG:file="...">>>` / `<<<END_LOG>>>` block for each accessible file on its first
poll and on subsequent polls with no new content. The first poll initializes the
cursor without reading existing content. A single literal path retains its
content-only output format.

## File source over SSH

On a remote host reached over SSH, a file source resolves its `paths` and reads the files through the
SFTP subsystem of the SSH server (`Subsystem sftp` in `sshd_config`, enabled by default); no command is
run on the host. `*` and `?` match within a single path segment, symbolic links are followed, and only
regular files are returned. On Linux and UNIX, names are case-sensitive and a wildcard in a directory
segment does not match hidden (dot-prefixed) directories, as in a shell glob. On Windows (OpenSSH), names
are case-insensitive.

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

## Job timeout

When a detection, discovery or collect exceeds `jobTimeout`:

- No new request is sent to the resource until the next job. A request already in progress is abandoned at once and counts as `timeout`.
- The metrics collected before the timeout are kept. The remaining sources and monitors are skipped for this cycle.
- The session that the job opened on the resource is closed.
- The log reports `operation timed out`, and the abandoned requests are counted as `timeout` in `metricshub.host.requests`.

## Protocol requests counter

`metricshub.host.requests` (self-monitoring) is a cumulative counter per resource, with `operation_type` and `state` attributes.

### Operation types

`http` (HTTP and HTTPS), `ipmi`, `jdbc`, `jmx`, `oscommand` (local commands), `ping`, `snmp` (v1, v2c and v3), `ssh` (remote commands and remote files over SFTP), `wbem`, `winrm` or `wmi`.

### States

| State | The request... |
| --- | --- |
| `completed` | was answered, including with an empty result (no matching process, empty table, no SNMP value at this OID) or with a command that exits with a non-zero code |
| `timeout` | got no answer before the configured `timeout`, or was interrupted when its job ran out of time |
| `failed` | could not be sent (unknown host, connection refused), was rejected (authentication), or was answered with an error: HTTP status 400 or above, WBEM, WMI or WinRM error, SQL error, IPMI completion code other than `00`, SFTP error |

### Counting rules

- **One count per request sent**: every retry is counted (HTTP retries, a new vCenter ticket after the previous one expired). Nothing is counted when no request is sent: a cached result (such as a valid vCenter ticket), a query or URL rejected before sending, a wait for an SSH connection slot, or a request not started because its job had already run out of time.
- **Composite requests count once**: an SNMP `walk` or `table`, a JMX fetch (connection, query and attributes), a JDBC query (connection, query and results), a WMI query (connection and query) or an IPMI session (sensors, SEL). When the protocol library returns a partial result (for example, a sensor that could not be read), the request counts as `completed`.
- **Health checks count**: a health-check request can be `failed` while the protocol is still reported up (for example, an HTTP `401` answer).
- **One outcome per request**: a request that answers after its timeout is counted once, as `timeout`.
- **Resource attribution**: requests are counted for the configured resource, even when the protocol configuration overrides the `hostname` or goes through a vCenter server.
- **Counter lifecycle**: the counters start at `0` when MetricsHub starts, when the resource is added or its configuration changes, or when a global setting changes (the agent restarts internally). A protocol appears after its first request, then with its three states.
- **Not counted**: requests sent by the CLI tools, the Web UI or the MCP tools, and emulated resources.

### Protocol limits

Some protocols do not tell every outcome apart:

- **Ping**: an unreachable host and a host that does not answer both return no answer: the request counts as `timeout` when it took the whole timeout, as `failed` otherwise.
- **SNMP v1 and v2c**: a wrong community or an unreachable agent gets no answer and counts as `timeout`.
- **SNMP** `walk` and `table`: an agent that stops answering after the first request returns a partial result, which counts as `completed`.
- **JDBC**: a health check whose connection validation times out counts as `failed`.
- **IPMI**: a BMC that does not answer the session setup counts as `failed`.
- **JMX** and **WBEM**: a TCP connection that times out at the operating system level counts as `failed`.
- **WMI**: a WMI provider that times out while the results of a query are read counts as `failed`.
