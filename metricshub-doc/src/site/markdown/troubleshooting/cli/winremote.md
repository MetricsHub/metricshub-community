keywords: winremote, cli, windows, os command
description: How to execute Windows remote OS commands (CMD commands) on remote systems with MetricsHub Windows Remote CLI.

# Windows Remote CLI Documentation

The **MetricsHub Windows Remote CLI** allows you to execute Windows OS commands (CMD commands) on remote Windows systems via WMI or WinRM protocols. It enables administrators to run commands like `ipconfig`, `systeminfo`, `tasklist`, and other Windows command-line utilities on remote hosts.

## Syntax

```bash
winremotecli <HOSTNAME> --command <COMMAND> [--wmi | --winrm] [WMI_OPTIONS | WINRM_OPTIONS]
```

## Options

| Option       | Description                                                                                 | Default Value |
| ------------ | ------------------------------------------------------------------------------------------- | ------------- |
| `HOSTNAME`   | Hostname or IP address of the Windows remote-enabled device. **This option is required.**   | None          |
| `--command`  | Windows OS command (CMD command) to execute. **This option is required.**                   | None          |
| `--wmi`      | Enable WMI protocol for command execution. **Either --wmi or --winrm must be specified.**   | None          |
| `--winrm`    | Enable WinRM protocol for command execution. **Either --wmi or --winrm must be specified.** | None          |
| `-v`         | Enables verbose mode. Use `-v` for basic logs, `-vv` for detailed logs.                     | None          |
| `-h, --help` | Displays detailed help information about available options.                                 | None          |

### WMI Options

When using `--wmi`, you can specify additional WMI-specific options:

| Option                  | Description                                                                                 | Default Value |
| ----------------------- | ------------------------------------------------------------------------------------------- | ------------- |
| `--wmi-username`        | Username for WMI authentication.                                                            | None          |
| `--wmi-password`        | Password for WMI authentication. If not provided, you will be prompted interactively.       | None          |
| `--wmi-timeout`         | Timeout in seconds for WMI operations.                                                      | 30            |
| `--wmi-force-namespace` | Force a specific namespace for connectors that perform namespace auto-detection (advanced). | None          |

### WinRM Options

When using `--winrm`, you can specify additional WinRM-specific options:

| Option                    | Description                                                                                  | Default Value |
| ------------------------- | -------------------------------------------------------------------------------------------- | ------------- |
| `--winrm-transport`       | Transport protocol for WinRM. Possible values: `HTTP` or `HTTPS`.                            | `HTTP`        |
| `--winrm-username`        | Username for WinRM authentication.                                                           | None          |
| `--winrm-password`        | Password for WinRM authentication. If not provided, you will be prompted interactively.      | None          |
| `--winrm-port`            | Port for WinRM service (default: 5985 for HTTP, 5986 for HTTPS).                             | Auto-detect   |
| `--winrm-timeout`         | Timeout in seconds for WinRM operations.                                                     | 30            |
| `--winrm-auth`            | Comma-separated ordered list of authentication schemes. Possible values: `NTLM`, `KERBEROS`. | `NTLM`        |
| `--winrm-force-namespace` | Force a specific namespace for connectors that perform namespace auto-detection (advanced).  | None          |

## Examples

### Example 1: Execute Command via WMI Protocol

```bash
winremotecli dev-01 --wmi --wmi-username admin --wmi-password secret --command "ipconfig /all"
```

### Example 2: Execute Command via WinRM Protocol

```bash
winremotecli dev-01 --winrm --winrm-username admin --winrm-password secret --command "systeminfo"
```

### Example 3: Interactive Password Input

```bash
winremotecli dev-01 --wmi --wmi-username admin --command "tasklist"
```

The CLI prompts for the password if `--wmi-password` or `--winrm-password` is not provided.

### Example 4: WMI with Custom Timeout

```bash
winremotecli dev-01 --wmi --wmi-username admin --wmi-password secret --command "dir C:\\Windows" --wmi-timeout 60
```

### Example 5: WinRM with HTTPS Transport

```bash
winremotecli dev-01 --winrm --winrm-username admin --winrm-password secret --command "systeminfo" --winrm-transport HTTPS --winrm-port 5986
```

### Example 6: WinRM with Custom Authentication

```bash
winremotecli dev-01 --winrm --winrm-username admin --winrm-password secret --command "tasklist" --winrm-auth NTLM,KERBEROS
```
