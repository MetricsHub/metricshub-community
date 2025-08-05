keywords: ai, mcp, integration
description: How to configure AI assistants such as Claude or OpenAI to connect to the MetricsHub MCP Server to interact in real-time with its internal knowledge and tools.

# AI Agents Integration

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

You can configure AI-based assistants that support the MCP SSE transport, such as [Claude](https://claude.ai/) or [OpenAI](https://openai.com/), to interact in real time with **MetricsHub**'s internal knowledge and tools.

![OpenAI Prompt - MetricsHub MCP](../images/metricshub-mcp-openai.png)

The following tools are currently available:

| Tool Name                        | Parameters                                                                                                                                            | Description                                                                                                                                                                                                   |
| -------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `CheckProtocol`                  | <ul><li>`hostname` (string, **required**)</li> <li>`protocol` (string, **required**)</li> <li>`timeout` (integer, _optional_, default: 10s)</li></ul> | Determines if the specified host is accessible using a given protocol. Supported protocols include: http, ipmi, jdbc, jmx, snmp, snmpv3, ssh, wbem, winrm, and wmi.                                           |
| `CollectMetricsForHost`          | <ul><li>`hostname` (string, **required**)</li> <li>`connectorId` (string, _optional_)</li></ul>                                                       | Fetch and collect metrics for the specified host using the configured protocols and credentials, and the applicable MetricsHub connectors (MIB2, Linux, Windows, Dell, RedFish, etc.)                         |
| `GetMetricsFromCacheForHost`     | <ul><li>`hostname` (string, **required**)</li></ul>                                                                                                   | Retrieve metrics from the cache for the specified host                                                                                                                                                        |
| `ListConnectors`                 | No parameters.                                                                                                                                        | Lists all connectors supported by MetricsHub including their identifiers and information.                                                                                                                     |
| `ListHosts`                      | No parameters.                                                                                                                                        | Retrieves all configured hosts (Resources) in this MetricsHub Agent instance for which we will be able to execute MetricsHub connectors and collect metrics.                                                                                                                            |
| `PingHost`                       | <ul><li>`hostname` (string, **required**)</li> <li>`timeout` (integer, _optional_, default: 4s)</li></ul>                                             | Checks if a host is reachable via ping and returns its response time and status.                                                                                                                              |
| `TestAvailableConnectorsForHost` | <ul><li>`hostname` (string, **required**)</li> <li>`connectorId` (string, _optional_)</li></ul>                                                       | Test all applicable MetricsHub connectors (MIB2, Linux, Windows, Dell, RedFish, etc.) against the specified host using the configured credentials and return the list of connectors that work with this host. |

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
