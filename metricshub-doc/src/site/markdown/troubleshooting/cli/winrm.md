keywords: winrm, cli
description: How to execute WinRM queries on remote systems with MetricsHub WinRM CLI.

# WinRM CLI Documentation

Windows Remote Management (WinRM) is a protocol designed for remote management and command execution on Windows systems. Built on the WS-Management protocol, it enables secure communication between systems, streamlining administrative tasks and facilitating data retrieval. Refer to [Installation and Configuration for Windows Remote Management](https://learn.microsoft.com/en-us/windows/win32/winrm/installation-and-configuration-for-windows-remote-management) for more details.

The **MetricsHub WinRM CLI** allows users to execute WinRM queries on remote systems. It supports querying namespaces, retrieving specific data, and authenticating using protocols like NTLM and Kerberos.

Before using the CLI, ensure your platform supports WinRM monitoring by checking the [Supported Platforms](../../supported-platforms.html).

## Syntax

```bash
winrmcli <HOSTNAME> --username <USERNAME> --password <PASSWORD> --namespace <NAMESPACE> --query <QUERY> --transport <PROTOCOL> --port <PORT> --timeout <TIMEOUT> --authentications <AUTH1>,<AUTH2>,...
```

## Options

| Option              | Description                                                                             | Default Value     |
| ------------------- | --------------------------------------------------------------------------------------- | ----------------- |
| `HOSTNAME`          | Hostname or IP address of the WinRM-enabled device. **This option is required.**        | None              |
| `--transport`       | Transport protocol for WinRM. Possible values: `HTTP` or `HTTPS`.                       | `HTTP`            |
| `--username`        | Username for WinRM authentication.                                                      | None              |
| `--password`        | Password for WinRM authentication. If not provided, you will be prompted interactively. | None              |
| `--port`            | Port for the WinRM service. By default: `5985` for HTTP, `5986` for HTTPS.              | Based on protocol |
| `--timeout`         | Timeout in seconds for WinRM operations.                                                | 30                |
| `--authentications` | Comma-separated list of authentication schemes. Possible values: `NTLM`, `KERBEROS`.    | `NTLM`            |
| `--query`           | WinRM query to execute. **This option is required**                                     | None              |
| `--namespace`       | Namespace for the query. **This option is required**                                    | None              |
| `-v`                | Enables verbose mode. Use `-v` for basic logs, `-vv` for detailed logs.                 | None              |
| `-h, --help`        | Displays detailed help information about available options.                             | None              |

## Examples

### Example 1: Basic WinRM Query

```bash
winrmcli dev-01 --username admin --password secret --namespace "root/cimv2" --query "SELECT * FROM Win32_OperatingSystem" \
--transport https --port 5986 --timeout 30 --authentications NTLM
```

### Example 2: WinRM Query with Multiple Authentication Schemes

```bash
winrmcli dev-01 --username admin --password secret --namespace "root/cimv2" --query "SELECT * FROM Win32_LogicalDisk" \
--transport http --port 5985 --timeout 60 --authentications NTLM,KERBEROS
```

### Example 3: WinRM Query with Interactive Password Input

```bash
winrmcli dev-01 --username admin --namespace "root/cimv2" --query "SELECT * FROM CIM_ManagedElement"
```

The CLI prompts for the password if not provided.
