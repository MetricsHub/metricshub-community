keywords: automation, velocity, templates, dynamic configuration, http, sql, file
description: How to use Velocity templates to dynamically generate MetricsHub configuration from external sources like HTTP APIs, databases, or local files.

# Programmable Configuration

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

You can write simple [Velocity scripts](https://velocity.apache.org) to automatically configure your monitoring resources by pulling data from an HTTP portal (like NetBox), reading local files, SQL databases, or parsing JSON content.

This is ideal for:

* **Large environments** with hundreds or thousands of resources
* **Dynamic infrastructures** where resources are frequently added or removed
* **Integration with CMDBs** or inventory systems

## Getting Started

Place your `.vm` template files in the `/config` directory alongside your regular YAML configuration files. **MetricsHub** will process these templates and generate configuration blocks automatically.

### Available Tools

To fetch and transform data into valid configuration blocks, use [Velocity Tools](https://velocity.apache.org/tools/3.1/tools-summary.html):

| Tool | Purpose |
|------|---------|
| `${esc.d}http` | Making HTTP requests to APIs |
| `${esc.d}file` | Reading local files |
| `${esc.d}json` | Parsing JSON data |
| `${esc.d}collection` | Splitting strings or manipulating collections |
| `${esc.d}sql` | Executing SQL queries on a database |
| `${esc.d}env` | Retrieving environment variables |
| `${esc.d}date`, `${esc.d}number`, `${esc.d}esc` | Date formatting, number formatting, escaping |

> **Reminder**: In [Velocity](https://velocity.apache.org/engine/2.4/user-guide.html), use `${esc.h}` for directives, `${esc.h}${esc.h}` for comments, and `${esc.d}` for variables.

## Loading Resources from an HTTP API

### `${esc.d}http.execute` Tool Arguments

Use the `${esc.d}http.execute(...)` function to execute HTTP requests directly from templates:

| Argument | Description |
|----------|-------------|
| `url` | **(Required)** The HTTP(S) endpoint to call. |
| `method` | HTTP method (`GET`, `POST`, etc.). (Default: `GET`). |
| `username` | Username used for authentication. |
| `password` | Password used for authentication. |
| `headers` | HTTP headers, written as newline-separated `Key: Value` pairs. |
| `body` | Payload to send with the request (e.g., for `POST`). |
| `timeout` | Request timeout in seconds. (Default: `60`). |

> **Note**: Use `${esc.d}http.get(...)` or `${esc.d}http.post(...)` to quickly send `GET` or `POST` requests without specifying a `method`.

### Example: Loading Resources from an HTTP API

Suppose your API endpoint at `https://cmdb/servers` returns:

```json
[
  {"hostname":"host1","OSType":"win","adminUsername":"admin1"},
  {"hostname":"host2","OSType":"win","adminUsername":"admin2"}
]
```

You can dynamically create resource blocks using:

```velocity

resources:
${esc.h}set(${esc.d}hostList = ${esc.d}json.parse(${esc.d}http.get({ "url": "https://cmdb/servers" }).body).root())

${esc.h}foreach(${esc.d}host in ${esc.d}hostList)
  ${esc.h}if(${esc.d}host.OSType == "win")
  ${esc.d}host.hostname:
    attributes:
      host.name: ${esc.d}host.hostname
      host.type: windows
    protocols:
      ping:
      wmi:
        timeout: 120
        username: ${esc.d}host.adminUsername
        password: ${esc.d}http.get({ "url": "https://passwords/servers/${esc.d}{host.hostname}/password" }).body
  ${esc.h}end
${esc.h}end
```

## Loading Resources from a Local File

### `${esc.d}file.readAllLines` Tool Arguments

Use the `${esc.d}file.readAllLines(filePath)` function to read all lines from a local file.

| Argument | Description |
|----------|-------------|
| `filePath` | **(Required)** The path to the local file. |

### Example: Loading Resources from a CSV File

If your CSV file contains:

```csv
host1,win,wmi,user1,pass1
host2,linux,ssh,user2,pass2
```

Use this Velocity template to generate the resource block:

```velocity

${esc.h}set(${esc.d}lines = ${esc.d}file.readAllLines("/opt/data/resources.csv"))
resources:
${esc.h}foreach(${esc.d}line in ${esc.d}lines)
  ${esc.h}set(${esc.d}fields = ${esc.d}collection.split(${esc.d}line))
  ${esc.h}set(${esc.d}hostname = ${esc.d}fields.get(0))
  ${esc.h}set(${esc.d}hostType = ${esc.d}fields.get(1))
  ${esc.h}set(${esc.d}protocol = ${esc.d}fields.get(2))
  ${esc.h}set(${esc.d}username = ${esc.d}fields.get(3))
  ${esc.h}set(${esc.d}password = ${esc.d}fields.get(4))
  ${esc.d}hostname:
    attributes:
      host.name: ${esc.d}hostname
      host.type: ${esc.d}hostType
    protocols:
      ${esc.d}protocol:
        username: ${esc.d}username
        password: ${esc.d}password
${esc.h}end
```

## Loading Resources from a SQL Database

### `${esc.d}sql.query` Tool Arguments

Use the `${esc.d}sql.query(query, jdbcUrl, username, password, timeout)` function to execute SQL queries:

| Argument | Description |
|----------|-------------|
| `query` | **(Required)** SQL query to execute. |
| `jdbcUrl` | **(Required)** The JDBC connection URL to access the database. |
| `username` | Name used for database authentication. |
| `password` | Password used for database authentication. |
| `timeout` | (Optional) Query timeout in seconds (Default: 120). |

### Example: Loading Resources from an SQL Database

Consider a `hosts` table in your database with the following data:

| hostname | host_type | username | password |
|----------|-----------|----------|----------|
| storage-server-1 | storage | admin1 | pwd1 |
| windows-server-1 | windows | admin2 | pwd2 |

You can dynamically create resource blocks by querying the database:

```velocity

${esc.h}set(${esc.d}url = "jdbc:h2:mem:management_db")
${esc.h}set(${esc.d}user = "sa")
${esc.h}set(${esc.d}pass = "pwd1")
${esc.h}set(${esc.d}rows = ${esc.d}sql.query("SELECT hostname, host_type, username, password FROM hosts ORDER BY hostname", ${esc.d}url, ${esc.d}user, ${esc.d}pass, 120))
resources:
${esc.h}foreach(${esc.d}row in ${esc.d}rows)
  ${esc.h}set(${esc.d}hostname  = ${esc.d}row.get(0))
  ${esc.h}set(${esc.d}host_type = ${esc.d}row.get(1))
  ${esc.h}set(${esc.d}username  = ${esc.d}row.get(2))
  ${esc.h}set(${esc.d}password  = ${esc.d}row.get(3))
  ${esc.h}if(${esc.d}host_type.equals("storage"))
  ${esc.d}hostname:
    attributes:
      host.name: ${esc.d}hostname
      host.type: ${esc.d}host_type
    protocols:
      http:
        hostname: ${esc.d}hostname
        https: true
        port: 443
        username: ${esc.d}username
        password: ${esc.d}password
  ${esc.h}end
${esc.h}end
```

## Using Environment Variables

Use `${esc.d}env.get("<ENV_VARIABLE_NAME>")` to retrieve environment variables in your templates:

```velocity
${esc.h}set(${esc.d}apiKey = ${esc.d}env.get("CMDB_API_KEY"))
${esc.h}set(${esc.d}response = ${esc.d}http.get({
  "url": "https://cmdb/api/servers",
  "headers": "Authorization: Bearer ${esc.d}apiKey"
}))
```

This allows you to keep sensitive credentials out of your template files.
