keywords: ai, mcp, integration
description: How to configure AI assistants such as Claude or OpenAI to connect to the MetricsHub MCP Server to interact in real-time with the its internal knowledge and tools.

# AI Agents Integration

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

You can configure **AI-based assistants**, **automated scripts**, or **observability platforms** to interact in real time with **MetricsHub**'s internal knowledge and tools, provided that they support the **MCP SSE transport** (e.g., [Claude](https://claude.ai) or [OpenAI](https://openai.com)).

The following tool is currently available:

| Tool Name  | Parameters                                                                    | Description                                                                      |
| ---------- | ----------------------------------------------------------------------------- | -------------------------------------------------------------------------------- |
| `PingHost` | <ul><li>`hostname` (string, **required**)</li> <li>`timeout` (integer, *optional*, default: 4s)</li></ul> | Checks if a host is reachable via ping and returns its response time and status. |

To get started, simply connect your AI assistant to the **MetricsHub MCP Server** using the [Model Context Protocol (MCP)](https://modelcontextprotocol.io). Once the connection is established, the available tools will be automatically published and ready for use.

## Prerequisites

Before configuring the AI agents integration, make sure that:

* your AI assistant supports **MCP SSE transport**
* **MetricsHub is installed and running**
* you have network access to the machine where **MetricsHub** is running
* the `31888` port is accessible from your machine or MCP client.

> **Important**: Some MCP clients may require **HTTPS with a valid TLS certificate**. In such cases, you must enable HTTPS on your **MetricsHub** instance. One common approach is to place **MetricsHub** behind a reverse proxy (e.g., NGINX or Apache) with TLS termination, and ensure that the certificate is trusted by the client.

## Configuring the integration

Configure your AI assistant to connect to `http://<hostname>:31888/sse`. Make sure to replace `<hostname>` with the actual hostname or IP address of the machine where **MetricsHub** is running.

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
  "duration": 27,
  "reachable": true
}
```
