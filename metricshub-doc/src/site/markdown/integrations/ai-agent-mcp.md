keywords: ai, mcp, integration
description: How to configure AI assistants such as Claude or OpenAI to connect to the MetricsHub MCP Server to interact in real-time with its internal knowledge and tools.

# AI Agent Integration

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

You can configure AI-based assistants that support the MCP SSE transport, such as [Claude](https://claude.ai/) or [OpenAI](https://openai.com/), to interact in real time with **MetricsHub**'s internal knowledge and tools.

![OpenAI Prompt - MetricsHub MCP](../images/metricshub-mcp-openai.png)

The following tools are currently available:

| Tool Name                        | Parameters                                                                                                                                                                                                                                                                                                                                                                        | Description                                                                                                                                                                                                             |
| -------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `CheckProtocol`                  | <ul><li>`hostname` (list&lt;string&gt;, **required**)</li> <li>`protocol` (string, **required**)</li> <li>`timeout` (integer, _optional_, default: _10s_)</li> <li>`poolSize` (integer, _optional_, default: _60 threads_)</li></ul>                                                                                                                                              | Determines if the specified hosts are accessible using a given protocol. Supported protocols include: http, ipmi, jdbc, jmx, snmp, snmpv3, ssh, wbem, winrm, and wmi.                                                   |
| `CollectMetricsForHost`          | <ul><li>`hostname` (list&lt;string&gt;, **required**)</li> <li>`connectorId` (string, _optional_)</li> <li>`poolSize` (integer, _optional_, default: _20 threads_)</li></ul>                                                                                                                                                                                                      | Fetch and collect metrics for the specified hosts using the configured protocols and credentials, and the applicable MetricsHub connectors (MIB2, Linux, Windows, Dell, RedFish, etc.)                                  |
| `ExecuteHttpQueryService`        | <ul><li>`url` (list&lt;string&gt;, **required**)</li> <li>`method` (string, _optional_, default: *GET*)</li> <li>`headers` (string, _optional_)</li> <li>`body` (string, _optional_)</li> <li>`timeout` (integer, _optional_, default: _10s_)</li> <li>`poolSize` (integer, _optional_, default: _60 threads_)</li></ul>                                                          | Executes HTTP requests for one or more URLs and optionally includes custom headers, a request body, a timeout value, and concurrent execution settings. Except HTTP GET, all the other methods are disabled by default. |
| `ExecuteIpmiQueryService`        | <ul><li>`hostname` (list&lt;string&gt;, **required**)</li> <li>`timeout` (integer, _optional_, default: _10s_)</li> <li>`poolSize` (integer, _optional_, default: _60 threads_)</li></ul>                                                                                                                                                                                         | Executes an IPMI query on the given hosts with the given timeout.                                                                                                                                                       |
| `ExecuteSnmpQueryService`        | <ul><li>`hostname` (list&lt;string&gt;, **required**)</li> <li>`queryType` (string, **required**)</li> <li>`oid` (string, **required**)</li> <li>`columns` (string, _optional_)</li> <li>`timeout` (integer, _optional_, default: _10s_)</li> <li>`poolSize` (integer, _optional_, default: _60 threads_)</li></ul>                                                               | Executes an SNMP query (Get, GetNext, Walk, or Table) on the given hosts using the specified OID. For `table` queries, comma-separated column indexes need to be provided.                                              |
| `ExecuteSshCommandlineService`   | <ul><li>`hostname` (list&lt;string&gt;, **required**)</li> <li>`commandline` (string, **required**)</li> <li>`timeout` (integer, _optional_, default: _10s_)</li> <li>`poolSize` (integer, _optional_, default: _60 threads_)</li></ul>                                                                                                                                           | Executes the specified commandline through SSH on the given hosts. This service is disabled by default.                                                                                                                 |
| `ExecuteWqlQueryService`         | <ul><li>`hostname` (list&lt;string&gt;, **required**)</li> <li>`protocol` (string, **required**)</li> <li>`query` (string, **required**)</li> <li>`namespace` (string, _optional_, default: _root/cimv2_ for WBEM and _root\cimv2_ otherwise)</li> <li>`timeout` (integer, _optional_, default: _10s_)</li> <li>`poolSize` (integer, _optional_, default: _60 threads_)</li></ul> | Executes a WQL/CIM query on Windows or WBEM-compatible hosts using the specified protocol (WBEM, WMI, or WinRM) and namespace.                                                                                          |
| `GetHostDetails`                 | <ul><li>`hostname` (list&lt;string&gt;, **required**)</li> <li>`poolSize` (integer, _optional_, default: _60 threads_)</li></ul>                                                                                                                                                                                                                                                  | Fetch, for the given hosts, the configured protocols used to check their reachability, the connectors successfully detected as working, and the collectors available to perform requests on the hosts.                  |
| `GetMetricsFromCacheForHost`     | <ul><li>`hostname` (list&lt;string&gt;, **required**)</li> <li>`poolSize` (integer, _optional_, default: _20 threads_)</li></ul>                                                                                                                                                                                                                                                  | Retrieve metrics from the cache for the specified hosts                                                                                                                                                                 |
| `ListConnectors`                 | No parameters.                                                                                                                                                                                                                                                                                                                                                                    | Lists all connectors supported by MetricsHub including their identifiers and information.                                                                                                                               |
| `ListHosts`                      | No parameters.                                                                                                                                                                                                                                                                                                                                                                    | Retrieves all configured hosts (Resources) in the MetricsHub Agent instance for which we will be able to execute MetricsHub connectors and collect metrics.                                                             |
| `PingHost`                       | <ul><li>`hostname` (list&lt;string&gt;, **required**)</li> <li>`timeout` (integer, _optional_, default: 4s)</li> <li>`poolSize` (integer, _optional_, default: _60 threads_)</li></ul>                                                                                                                                                                                            | Checks if one or more hosts are reachable via ping and returns their response time and status.                                                                                                                          |
| `TestAvailableConnectorsForHost` | <ul><li>`hostname` (list&lt;string&gt;, **required**)</li> <li>`connectorId` (string, _optional_)</li> <li>`poolSize` (integer, _optional_, default: _20 threads_)</li></ul>                                                                                                                                                                                                      | Test all applicable MetricsHub connectors (MIB2, Linux, Windows, Dell, RedFish, etc.) against the specified hosts using the configured credentials and return the list of connectors that work with these hosts.        |


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
      "arg0": ["server-01", "server-02"]
    }
  }
}
```

The command will return this type of response:

```
{
  "hosts": [
    {
      "hostname": "server-01",
      "response": {
        "hostname": "server-01",
        "responseTime": 27,
        "reachable": true
      }
    },
    {
      "hostname": "server-02",
      "response": {
        "hostname": "server-02",
        "responseTime": 42,
        "reachable": false,
        "errorMessage": "Timed out"
      }
    }
  ]
}
```
