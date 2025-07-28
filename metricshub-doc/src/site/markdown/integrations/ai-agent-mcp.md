keywords: ai, mcp, integration
description: How to configure AI assistants such as Claude or OpenAI to connect to the MetricsHub MCP Server to interact in real-time with its internal knowledge and tools.

# AI Agents Integration

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

You can configure AI-based assistants that support the MCP SSE transport, such as [Claude](https://claude.ai/) or [OpenAI](https://openai.com/), to interact in real time with **MetricsHub**'s internal knowledge and tools.

![OpenAI Prompt - MetricsHub MCP](../images/metricshub-mcp-openai.png)

The following tools are currently available:

| Tool Name             | Parameters                                                                                                 | Description                                                                      |
| --------------------- | ---------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------- |
| `CheckHttpProtocol`   | <ul><li>`hostname` (string, **required**)</li> <li>`timeout` (integer, _optional_, default: 10s)</li></ul> | Checks if a host is reachable using the HTTP protocol.                           |
| `CheckIpmiProtocol`   | <ul><li>`hostname` (string, **required**)</li> <li>`timeout` (integer, _optional_, default: 10s)</li></ul> | Checks if a host is reachable using the IPMI protocol.                           |
| `CheckJdbcProtocol`   | <ul><li>`hostname` (string, **required**)</li> <li>`timeout` (integer, _optional_, default: 10s)</li></ul> | Checks if a host is reachable using the JDBC protocol.                           |
| `CheckJmxProtocol`    | <ul><li>`hostname` (string, **required**)</li> <li>`timeout` (integer, _optional_, default: 10s)</li></ul> | Checks if a host is reachable using the JMX protocol.                            |
| `CheckSnmpProtocol`   | <ul><li>`hostname` (string, **required**)</li> <li>`timeout` (integer, _optional_, default: 10s)</li></ul> | Checks if a host is reachable using the SNMP protocol.                           |
| `CheckSnmpV3Protocol` | <ul><li>`hostname` (string, **required**)</li> <li>`timeout` (integer, _optional_, default: 10s)</li></ul> | Checks if a host is reachable using the SNMPv3 protocol.                         |
| `CheckSshProtocol`    | <ul><li>`hostname` (string, **required**)</li> <li>`timeout` (integer, _optional_, default: 10s)</li></ul> | Checks if a host is reachable using the SSH protocol.                            |
| `CheckWbemProtocol`   | <ul><li>`hostname` (string, **required**)</li> <li>`timeout` (integer, _optional_, default: 10s)</li></ul> | Checks if a host is reachable using the WBEM protocol.                           |
| `CheckWinrmProtocol`  | <ul><li>`hostname` (string, **required**)</li> <li>`timeout` (integer, _optional_, default: 10s)</li></ul> | Checks if a host is reachable using the WinRM protocol.                          |
| `CheckWmiProtocol`    | <ul><li>`hostname` (string, **required**)</li> <li>`timeout` (integer, _optional_, default: 10s)</li></ul> | Checks if a host is reachable using the WMI protocol.                            |
| `PingHost`            | <ul><li>`hostname` (string, **required**)</li> <li>`timeout` (integer, _optional_, default: 4s)</li></ul>  | Checks if a host is reachable via ping and returns its response time and status. |

To get started, simply connect your AI assistant to the **MetricsHub MCP Server** using the [Model Context Protocol (MCP)](https://modelcontextprotocol.io). Once the connection is established, the available tools will be automatically published and ready for use.

## Prerequisites

Before configuring the AI agents integration, make sure that:

* your AI assistant supports **MCP SSE transport**
* **MetricsHub is installed and running**
* you have network access to the machine where **MetricsHub** is running
* the `31888` port is accessible from your machine or MCP client
* you have generated an [API key](../security/api-keys.md) to authenticate against the **MetricsHub MCP Server**.

> **Important**: Some MCP clients may require **HTTPS with a valid TLS certificate**. In such cases, you must enable HTTPS on your **MetricsHub** instance. One common approach is to place **MetricsHub** behind a reverse proxy (e.g., NGINX or Apache) with TLS termination, and ensure that the certificate is trusted by the client.

## Configuring the integration

Configure your AI assistant to connect to `http://<hostname>:31888/sse`. Make sure to replace `<hostname>` with the actual hostname or IP address of the machine where **MetricsHub** is running.

Each request to the **MetricsHub MCP Server** must include the [API key](../security/api-keys.md) in the `Authorization` header as follows:

```
Authorization: Bearer <your_api_key>
```

## Using the MCP tools

Once the connection is established, your AI assistant can call the `PingHost` tool as follows:

```
{
  "method": "tools/call",
  "params": {
    "name": "PingHost",
    "arguments": {
      "arg0": "server-01"
    }
  }
}
```

The command will return this type of response:

```
{
  "hostname": "server-01",
  "responseTime": 27,
  "reachable": true
}
```
