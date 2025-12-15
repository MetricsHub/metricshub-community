keywords: winremote, cli, windows, os command
description: How to execute Windows remote OS commands (CMD commands) on remote systems with MetricsHub Windows Remote CLI.

# Windows Remote CLI Documentation

The **MetricsHub Windows Remote CLI** allows you to execute Windows OS commands (CMD commands) on remote Windows systems via WMI or WinRM protocols. It enables administrators to run commands like `ipconfig`, `systeminfo`, `tasklist`, and other Windows command-line utilities on remote hosts.

Before using the CLI, ensure your platform supports Windows remote monitoring by checking the [Connector Directory](https://metricshub.com/docs/latest/metricshub-connectors-directory.html).

## Syntax

```bash
winremotecli <HOSTNAME> --username <USERNAME> --password <PASSWORD> --protocol <wmi|winrm> --command <COMMAND> --timeout <TIMEOUT>
```

## Options

| Option       | Description                                                                                      | Default Value |
| ------------ | ------------------------------------------------------------------------------------------------ | ------------- |
| `HOSTNAME`   | Hostname or IP address of the Windows remote-enabled device. **This option is required.**        | None          |
| `--username` | Username for Windows remote authentication.                                                      | None          |
| `--password` | Password for Windows remote authentication. If not provided, you will be prompted interactively. | None          |
| `--protocol` | Protocol to use for Windows remote command execution. Possible values: `wmi` or `winrm`.         | `wmi`         |
| `--command`  | Windows OS command (CMD command) to execute. **This option is required.**                        | None          |
| `--timeout`  | Timeout in seconds for Windows remote OS command execution.                                      | 30            |
| `-v`         | Enables verbose mode. Use `-v` for basic logs, `-vv` for detailed logs.                          | None          |
| `-h, --help` | Displays detailed help information about available options.                                      | None          |

## Examples

### Example 1: Execute Command via WMI Protocol

```bash
winremotecli dev-01 --username admin --password secret --protocol wmi --command "ipconfig /all" --timeout 30
```

### Example 2: Execute Command via WinRM Protocol

```bash
winremotecli dev-01 --username admin --password secret --protocol winrm --command "systeminfo" --timeout 30
```

### Example 3: Interactive Password Input

```bash
winremotecli dev-01 --username admin --protocol wmi --command "tasklist"
```

The CLI prompts for the password if not provided.

### Example 4: Using Default Protocol (WMI)

```bash
winremotecli dev-01 --username admin --password secret --command "dir C:\\Windows"
```

When `--protocol` is not specified, the CLI defaults to `wmi`.

