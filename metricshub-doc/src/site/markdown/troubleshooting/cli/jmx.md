keywords: jmx, cli
description: How to execute JMX queries with MetricsHub JMX CLI.

# JMX CLI Documentation

[JMX](https://www.oracle.com/technical-resources/articles/javase/jmx.html) (Java Management Extensions) is a standard API for monitoring and managing resources such as applications, devices, and services. It is widely used for accessing Java Virtual Machine (JVM) metrics and MBeans (Managed Beans).

The **MetricsHub JMX CLI** allows users query MBeans from a JMX server to fetch attributes, filter key properties, and extract JVM metrics.

## Syntax

```bash
jmxcli <HOSTNAME> --object-name <OBJECT_NAME> [--port <PORT>] [--username <USERNAME>] [--password <PASSWORD>] [--timeout <TIMEOUT>] [--attributes <ATTRIBUTES>] [--key-properties <KEY_PROPERTIES>] [-v]
```

## Options

| Option             | Description                                                                      | Default Value   |
| ------------------ | -------------------------------------------------------------------------------- | --------------- |
| `HOSTNAME`         | Hostname or IP address of the JMX server.                                        | None (required) |
| `--object-name`    | JMX MBean object name or pattern (e.g., `java.lang:type=Memory`).                | None (required) |
| `--port`           | Port number for the JMX server.                                                  | `1099`          |
| `--username`       | Username for JMX authentication.                                                 | None            |
| `--password`       | Password for JMX authentication. If not provided, the CLI prompts interactively. | None            |
| `--timeout`        | Timeout in seconds for the JMX request.                                          | `30`            |
| `--attributes`     | Comma-separated list of attributes to fetch from the MBean.                      | None            |
| `--key-properties` | Comma-separated list of key properties to include in the result set.             | None            |
| `-v`               | Enables verbose mode. Repeat (`-vv`) for increased verbosity.                    | None            |
| `-h, -?, --help`   | Displays detailed help information.                                              | None            |

## Examples

### Example 1: Querying Cassandra Metrics with JMX

```bash
jmxcli cassandra-01 --port 7199 --timeout 60s \
  --object-name org.apache.cassandra.metrics:type=Table,keyspace=system,scope=*,name=TotalDiskSpaceUsed \
  --key-properties scope \
  --attributes Count
```

### Example 2: Querying a JVM's Runtime Attributes

```bash
jmxcli jvm-host --object-name java.lang:type=Runtime --attributes Uptime,StartTime
```

### Example 3: JMX Query with Authentication and Interactive Password Prompt

```bash
jmxcli app-host --username monitor --object-name java.lang:type=Threading --attributes ThreadCount,PeakThreadCount --timeout 45
```

If the `--password` option is omitted, the CLI prompts securely for the password.
