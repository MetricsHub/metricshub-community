keywords: ai, mcp, integration
description: How to use the MetricsHub MCP Server to provide remote tool access via Model Context Protocol (MCP)

# MCP Server

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

**MetricsHub** supports the [Model Context Protocol (MCP)](https://modelcontextprotocol.io) to allow remote agents and AI assistants to interact with the system through standardized tools and context-aware capabilities.

The **MetricsHub** MCP Server exposes an SSE (Server-Sent Events) endpoint to allow communication between remote clients and **MetricsHub** tools.

This integration is ideal for enabling **AI-based assistants**, **automated scripts**, or **observability platforms** to interact with **MetricsHub**'s internal knowledge and tooling in real-time.

## Prerequisites

Before using the **MetricsHub** MCP Server, ensure the following:

1. **MetricsHub is installed and running**, with the MCP server enabled.
2. You are using an AI assistant (like [Claude](https://claude.ai) or [OpenAI](https://openai.com)) that supports **MCP SSE transport**.
3. You have network access to the machine where MetricsHub is running.

## Connecting to the MCP server

To connect your MCP-compatible client to the **MetricsHub** MCP server, use the following URL:

```
http://localhost:31888/sse
```

Replace `localhost` with the actual hostname or IP address where **MetricsHub** is running.

If you’re running **MetricsHub** in a container or on a remote host, make sure that port `31888` is accessible from your machine or client.

Some MCP clients may **require HTTPS with a valid certificate**. In such cases, you need to enable HTTPS on your **MetricsHub** instance, for example, by placing it
behind a reverse proxy such as NGINX or Apache with TLS termination and ensure that the certificate is trusted by your client.

## Tools available via MCP

The following tools are available via the **MetricsHub** MCP server:

| Tool Name  | Parameters                                                                    | Description                                                                      |
| ---------- | ----------------------------------------------------------------------------- | -------------------------------------------------------------------------------- |
| `PingHost` | <ul><li>`hostname` (string, required)</li> <li>`timeout` (integer, optional, default: 4s)</li></ul> | Checks if a host is reachable via ping and returns its response time and status. |

> Note: The tool names and descriptions are automatically published by the MCP server when the client connects.

## Using the MCP tools

Once connected, your AI assistant or remote agent can call the available tools like `PingHost`. For example:

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

A response will look like:

```
{
  "hostname": "server-01",
  "duration": 27,
  "reachable": true
}
```
