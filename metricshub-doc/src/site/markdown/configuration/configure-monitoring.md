keywords: agent, configuration, getting started, yaml, resources, sites
description: How to configure the MetricsHub Agent to collect metrics from your infrastructure resources.

# Monitoring Configuration

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

**MetricsHub** extracts metrics from the resources defined in your configuration file(s). These **resources** can be hosts, applications, or any components running in your IT infrastructure.

Each **resource** is typically associated with a **site** (a physical or logical location like a data center or business unit). In highly distributed infrastructures, resources can be organized into **resource groups**.

> **Tip**: We recommend using the [Web Interface](../operating-web-ui.html) for most configuration tasks. Use this guide when you need to edit YAML directly or automate configuration.

## Locating Configuration Files

Store your `.yaml` or `.yml` configuration files in:

| Platform | Path                               |
| -------- | ---------------------------------- |
| Windows  | `C:\ProgramData\MetricsHub\config` |
| Linux    | `./metricshub/lib/config`          |

> **Important**: We recommend using an editor supporting the [Schemastore](https://www.schemastore.org/metricshub.json) to edit **MetricsHub**'s configuration YAML files (Example: [Visual Studio Code](https://code.visualstudio.com/download) with [RedHat's YAML extension](https://marketplace.visualstudio.com/items?itemName=redhat.vscode-yaml)).

> **Note**: A configuration example, `metricshub-config-example.yaml`, is provided in the installation directory.

## Creating Your First Configuration

Here's the minimal configuration to monitor a single Linux host via SSH:

```yaml
# metricshub.yaml
attributes:
  site: my-datacenter # Where your resources are located

resources:
  my-first-host: # <-- Change: unique identifier for this resource
    attributes:
      host.name: 192.168.1.100 # <-- Change: IP address or hostname
      host.type: linux # <-- Change: win, linux, network, storage, etc.
    protocols:
      ssh: # <-- Change: protocol to use (see Protocols below)
        username: admin # <-- Change: your credentials
        password: changeme # <-- Change: your password
        timeout: 120s
```

**To edit this configuration:**

1. Copy the example above (or `metricshub-config-example.yaml`)
2. Change `host.name` to your server's IP address or hostname
3. Change `host.type` to match your system (see [Supported Platforms](../supported-platforms.html))
4. Update the protocol section with your credentials
5. Save as `metricshub.yaml` in the config directory
6. Restart **MetricsHub** or use the Web Interface to validate

### Supported Host Types

| Type      | Description                  |
| --------- | ---------------------------- |
| `win`     | Microsoft Windows systems    |
| `linux`   | Linux systems                |
| `network` | Network devices              |
| `oob`     | Out-of-band management cards |
| `storage` | Storage systems              |
| `aix`     | IBM AIX systems              |
| `hpux`    | HP-UX systems                |
| `solaris` | Oracle Solaris systems       |

Check the [Supported Platforms](../supported-platforms.html) to find the correct type for your system.

### Common Protocol Examples

**Windows via WMI:**

```yaml
resources:
  windows-server:
    attributes:
      host.name: win-server-01
      host.type: win
    protocols:
      wmi:
        username: administrator
        password: changeme
        timeout: 120s
```

**Network device via SNMP:**

```yaml
resources:
  network-switch:
    attributes:
      host.name: switch-01.example.com
      host.type: network
    protocols:
      snmp:
        version: v2c
        community: public
        port: 161
        timeout: 120s
```

**Storage via HTTP REST API:**

```yaml
resources:
  storage-array:
    attributes:
      host.name: storage-01.example.com
      host.type: storage
    protocols:
      http:
        https: true
        port: 443
        username: admin
        password: changeme
```

For the complete list of protocol parameters, see [Protocols and Credentials](./protocols-and-credentials.html).

## Customizing What Gets Monitored

Connectors are auto-selected based on `host.type` and protocols. To override, use the `connectors` property as in the example below:

```yaml
resources:
  my-server:
    attributes:
      host.name: server-01
      host.type: linux
    protocols:
      ssh:
        username: admin
        password: changeme
    connectors: ["#hardware", "!MIB2"] # Hardware connectors, exclude MIB2
```

Use the below syntax for the `connectors` property to force or exclude specific connectors or categories of connectors:

| Prefix | Meaning                                                                                    |
| ------ | ------------------------------------------------------------------------------------------ |
| `+`    | Force connector (e.g. `+MySQL` to force the MySQL connector)                               |
| `!`    | Exclude connector (e.g. `!IpmiTool` to disable the IpmiTool connector)                     |
| `#`    | Include category (see [available categories](../connectors-directory.html#connector-tags)) |

See [Connectors Directory](../connectors-directory.html) for the full listing of available connectors. Alternatively, you can also use the below command line:

```bash
metricshub -l
```

## Choosing a Configuration Structure

### Using a Single File (Small Environments)

For environments with fewer than ~50 resources, use a single `metricshub.yaml`:

```yaml
attributes:
  site: my-datacenter

resources:
  server-1:
    attributes:
      host.name: server-01
      host.type: linux
    protocols:
      ssh:
        username: root
        password: changeme

  server-2:
    attributes:
      host.name: server-02
      host.type: win
    protocols:
      wmi:
        username: administrator
        password: changeme
```

### Using Multiple Files (Large Environments)

For larger environments, split configuration across multiple files:

```
config/
├── global-settings.yaml         # Collect period, logging, etc.
├── license.yaml                 # Enterprise license
├── paris-resources.yaml         # Resources at Paris site
├── london-resources.yaml        # Resources at London site
└── ...
```

**`global-settings.yaml`**

```yaml
collectPeriod: 1m
loggerLevel: info
enableSelfMonitoring: true
```

**`paris-resources.yaml`**

```yaml
resourceGroups:
  paris:
    attributes:
      site: paris-dc
    resources:
      server-1:
        attributes:
          host.name: paris-server-01
          host.type: linux
        protocols:
          ssh:
            username: root
            password: changeme
```

> **Note**: If migrating from a single file to multiple files, see [Upgrade Notes](../upgrade.html#support-for-multiple-configuration-files).

> **Tip**: Move backup or example files to subfolders (e.g., `config/examples/`) to prevent conflicts.

## Using Resource Groups

For distributed infrastructures with multiple sites:

```yaml
resourceGroups:
  boston:
    attributes:
      site: boston-dc
    resources:
      myBostonHost1:
        attributes:
          host.name: my-boston-host-01
          host.type: storage
        protocols:
          http:
            https: true
            username: admin
            password: changeme

  chicago:
    attributes:
      site: chicago-dc
    resources:
      myChicagoHost1:
        attributes:
          host.name: my-chicago-host-01
          host.type: linux
        protocols:
          ssh:
            username: root
            password: changeme
```

For sustainability metrics reporting within resource groups, see [Sustainability](../guides/configure-sustainability-metrics.html).

## Monitoring Multiple Similar Resources

If multiple resources share the same configuration (credentials, type), use arrays:

```yaml
resources:
  linux-servers:
    attributes:
      host.name: [server-01, server-02, server-03]
      host.type: linux
    protocols:
      ssh:
        username: admin
        password: shared-password
        timeout: 120s
```

## Using Environment Variables

Reference environment variables in your configuration:

```yaml
resources:
  my-server:
    attributes:
      host.name: ${esc.d}{env::SERVER_HOSTNAME}
      host.type: linux
    protocols:
      ssh:
        username: ${esc.d}{env::SSH_USERNAME}
        password: ${esc.d}{env::SSH_PASSWORD}
```

## Next Steps

- [Protocols and Credentials](./protocols-and-credentials.html)
- [Sending Telemetry Data](./send-telemetry.html)
- [Programmable Configuration](./programmable-configuration.html)
- [Fine-Tuning Monitoring](./fine-tuning-monitoring.html)
- [Password Encryption](../security/passwords.html)
