keywords: wbem, cli
description: How to execute WBEM queries on WBEM-enabled devices or systems with MetricsHub WBEM CLI.

# WBEM CLI Documentation

The **MetricsHub WBEM CLI** allows you to execute WBEM queries on WBEM-enabled devices or systems. It supports querying namespaces, retrieving specific information, and accessing vCenter-hosted resources.

Before using the CLI, ensure your platform supports WBEM monitoring by checking the [Supported Platforms](../../supported-platforms.html).

## Syntax

```bash
wbemcli <HOSTNAME> --namespace <NAMESPACE> --query <QUERY> --username <USERNAME> --password <PASSWORD> --vcenter <VCENTER> --transport <PROTOCOL> --port <PORT> --timeout <TIMEOUT>
```

## Options

| Option        | Description                                                                            | Default Value     |
| ------------- | -------------------------------------------------------------------------------------- | ----------------- |
| `HOSTNAME`    | Hostname or IP address of the WBEM-enabled device. **This option is required.**        | None              |
| `--query`     | WBEM query to execute.                                                                 | None (required)   |
| `--transport` | Transport protocol for WBEM: `HTTP` or `HTTPS`.                                        | `HTTPS`           |
| `--port`      | Port of the WBEM server. By default, `5988` for HTTP, `5989` for HTTPS.                | Based on protocol |
| `--username`  | Username for WBEM authentication.                                                      | None              |
| `--password`  | Password for WBEM authentication. If not provided, you will be prompted interactively. | None              |
| `--timeout`   | Timeout in seconds for WBEM operations.                                                | 30                |
| `--namespace` | WBEM namespace for the query. **This option is required.**                             | None              |
| `--vcenter`   | VCenter hostname providing the authentication ticket (if applicable).                  | None              |
| `-v`          | Enables verbose mode. Use `-v` for basic logs, `-vv` for detailed logs.                | None              |
| `-h, --help`  | Displays detailed help information about available options.                            | None              |

## Examples

### Example 1: Basic WBEM Query

```bash
wbemcli esx-01 --namespace "root/cimv2" --query "SELECT MajorVersion FROM VMware_HypervisorSoftwareIdentity" \
--username admin --password secret --vcenter hci-vcenter
```

### Example 2: emc-san Namespace Query

```bash
wbemcli emc-san --namespace "root/emc" --query "SELECT DeviceID FROM EMC_DiskDrive" \
--transport https --username admin --password secret
```

### Example 3: WBEM Query wih Interactive Password Input

```bash
wbemcli esx-01 --namespace "root/cimv2" --query "SELECT * FROM CIM_ManagedElement" --username admin
```

The CLI prompts for the password if not provided.
