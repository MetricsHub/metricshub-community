keywords: ipmi, cli
description: How to execute IPMI-over-LAN queries to monitor and manage remote servers with MetricsHub IPMI CLI.

# IPMI CLI Documentation

IPMI (Intelligent Platform Management Interface) is a protocol used for managing servers independently of the operating system. It enables out-of-band monitoring, diagnostics, and management, providing administrators with access to system health, event logs, and power control. Refer to [Intelligent Plaform Management Interface](https://en.wikipedia.org/wiki/Intelligent_Platform_Management_Interface) for more details.

The **MetricsHub IPMI CLI** allows users to execute IPMI-over-LAN queries to monitor and manage remote servers. It supports authentication via username/password and BMC keys.

Before using the CLI, ensure your platform supports IPMI monitoring by checking the [Supported Platforms](../../supported-platforms.html).

## Syntax

```bash
ipmicli <HOSTNAME> --username <USERNAME> --password <PASSWORD> --bmc-key <KEY> --timeout <TIMEOUT> --skip-auth <BOOLEAN>
```

## Options

| Option        | Description                                                                                     | Default Value |
| ------------- | ----------------------------------------------------------------------------------------------- | ------------- |
| `HOSTNAME`    | Hostname or IP address of the system to monitor.                                                | None          |
| `--username`  | Username for IPMI-over-LAN authentication.                                                      | None          |
| `--password`  | Password for IPMI-over-LAN authentication. If not provided, you will be prompted interactively. | None          |
| `--bmc-key`   | BMC key for two-key authentication in hexadecimal format.                                       | None          |
| `--skip-auth` | Set to `true` to skip authentication for IPMI queries.                                          | `false`       |
| `--timeout`   | Timeout in seconds for IPMI operations.                                                         | `120`         |
| `-v`          | Enables verbose mode. Use `-v` for basic logs, `-vv` for detailed logs.                         | None          |
| `-h, --help`  | Displays detailed help information about available options.                                     | None          |

## Examples

### Example 1: Basic IPMI Query with Authentication

```bash
ipmicli dev-01 --username admin --password secret --timeout 60
```

### Example 2: Basic IPMI Query with BMC Key

```bash
ipmicli dev-01 --username admin --password secret --bmc-key AE4C7AB47FD --timeout 120
```

### Example 3: IPMI Query Skipping Authentication

```bash
ipmicli dev-01 --skip-auth true --timeout 90
```

### Example 4: IPMI Query with Interactive Password Input

```bash
ipmicli dev-01 --username admin
```

The CLI prompts for the password if not provided.
