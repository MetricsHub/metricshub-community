keywords: snmp, cli
description: How to use MetricsHub SNMP CLI to interact with SNMP-enabled devices.

# SNMP CLI Documentation

The **MetricsHub SNMP CLI** allows you to interact with SNMP-enabled devices using the `GET`, `GETNEXT`, `WALK`, and `TABLE` queries. When running the CLI, you can configure the SNMP version to be used (v1 or v2c), community string, port, and retry intervals. To use SNMP v3, refer to MetricsHub [SNMPv3 CLI](snmpv3.md).

Before using the CLI, ensure your platform supports SNMP monitoring by checking the [Supported Platforms](../../supported-platforms.html).

## Syntax

### SNMP Get Request

```bash
snmpcli <HOSTNAME> --get <OID> --community <COMMUNITY> --version <VERSION> --port <PORT> --timeout <TIMEOUT> --retry <INTERVAL1>,<INTERVAL2>,...
```

### SNMP Get Next Request

```bash
snmpcli <HOSTNAME> --getNext <OID> --community <COMMUNITY> --version <VERSION> --port <PORT> --timeout <TIMEOUT> --retry <INTERVAL1>,<INTERVAL2>,...
```

### SNMP Walk Request

```bash
snmpcli <HOSTNAME> --walk <OID> --community <COMMUNITY> --version <VERSION> --port <PORT> --timeout <TIMEOUT> --retry <INTERVAL1>,<INTERVAL2>,...
```

### SNMP Table Request

```bash
snmpcli <HOSTNAME> --table <OID> --columns <COLUMN,COLUMN,...> --community <COMMUNITY> --version <VERSION> --port <PORT> --timeout <TIMEOUT> --retry <INTERVAL1>,<INTERVAL2>,...
```

## Options

| Option        | Description                                                                     | Default Value |
| ------------- | ------------------------------------------------------------------------------- | ------------- |
| `HOSTNAME`    | Hostname or IP address of the SNMP-enabled device. **This option is required.** | None          |
| `--version`   | SNMP version to use. Possible values: `1` or `2c`.                              | `2c`          |
| `--community` | Community string for SNMP authentication.                                       | `public`      |
| `--port`      | Port on which the SNMP agent is listening.                                      | `161`         |
| `--timeout`   | Timeout in seconds for SNMP operations.                                         | 5             |
| `--retry`     | Comma-separated retry intervals in milliseconds (e.g., `500,1000`).             | None          |
| `--get`       | OID for SNMP Get request.                                                       | None          |
| `--getNext`   | OID for SNMP Get Next request.                                                  | None          |
| `--walk`      | OID for SNMP Walk request.                                                      | None          |
| `--table`     | OID for SNMP Table request.                                                     | None          |
| `--columns`   | Comma-separated list of column names for SNMP Table request.                    | None          |
| `-v`          | Enables verbose mode. Use `-v` for basic logs, `-vv` for detailed logs.         | None          |
| `-h, --help`  | Displays detailed help information about available options.                     | None          |

## Examples

### Example 1: SNMP Get Request

```bash
snmpcli dev-01 --get 1.3.6.1.4.1.674.10892.5.5.1.20.130.4.1.1.1 --community public --version v2c --port 161 --timeout 60 --retry 500,1000
```

### Example 2: SNMP Get Next Request

```bash
snmpcli dev-01 --getNext 1.3.6.1.4.1.674.10892.5.5.1.20.130.4 --community public --version v2c --port 161 --timeout 60 --retry 500,1000
```

### Example 3: SNMP Walk Request

```bash
snmpcli dev-01 --walk 1.3.6.1 --community public --version v1 --port 161 --timeout 60 --retry 500,1000
```

### Example 4: SNMP Table Request

```bash
snmpcli dev-01 --table 1.3.6.1.4.1.674.10892.5.4.300.10.1 --columns 1,3,8,9,11 --community public --version v1 --port 161 --timeout 60 --retry 500,1000
```
