keywords: ssh, cli
description: How to execute remote commands on SSH-enabled devices with the MetricsHub SSH CLI.

# SSH CLI Documentation

The **MetricsHub SSH CLI** allows you to execute commands on SSH-enabled devices. When running the CLI, you can choose to authenticate yourself with credentials or a public key and configure additional settings, such as timeout and port.

Before using the CLI, ensure your platform supports SSH monitoring by checking the [Supported Platforms](../../supported-platforms.html).

## Syntax

### SSH with Username and Password

```bash
sshcli <HOSTNAME> --username <USERNAME> --password <PASSWORD> --command <COMMAND> --timeout <TIMEOUT> --port <PORT>
```

### SSH with Public Key Authentication

```bash
sshcli <HOSTNAME> --public-key <PATH> --command <COMMAND> --timeout <TIMEOUT> --port <PORT>
```

## Options

| Option         | Description                                                                           | Default Value |
| -------------- | ------------------------------------------------------------------------------------- | ------------- |
| `HOSTNAME`     | Hostname or IP address of the SSH-enabled device. **This option is required.**        | None          |
| `--username`   | Username for SSH authentication.                                                      | None          |
| `--password`   | Password for SSH authentication. If not provided, you will be prompted interactively. | None          |
| `--public-key` | Path to the public key file for SSH authentication.                                   | None          |
| `--command`    | Command to execute on the remote device.                                              | `sudo`        |
| `--timeout`    | Timeout in seconds for the SSH operation.                                             | 30            |
| `--port`       | Port for the SSH connection.                                                          | 22            |
| `-v`           | Enables verbose mode. Use `-v` for basic logs, `-vv` for detailed logs.               | None          |
| `-h, --help`   | Displays detailed help information about available options.                           | None          |

## Examples

### Example 1: Basic Authentication with Username and Password

```bash
sshcli dev-01 --username admin --password secret --command="echo Hello, World!" --timeout 30 --port 22
```

### Example 2: Authentication with Public Key

```bash
sshcli dev-01 --public-key="/opt/ssh-rsa.txt" --command="ls /home/admin" --timeout 30 --port 22
```

### Example 3: SSH Command with Interactive Password Input

```bash
sshcli dev-01 --username admin --command="whoami"
```

The CLI prompts for the password if not provided.
