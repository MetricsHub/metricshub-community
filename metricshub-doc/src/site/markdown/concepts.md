keywords: concepts, resources, sites, connectors, protocols, resource groups
description: Key concepts and terminology used in MetricsHub for infrastructure monitoring.

# Key Concepts

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

Before diving into **MetricsHub**, familiarize yourself with the core concepts that shape how monitoring is configured and organized.

## Resources

A **resource** is any entity you want to monitor: a server, a network switch, a storage array, a database, or an application. Each resource is identified by:

- A unique **resource ID** (e.g., `my-server-01`)
- A **hostname** or IP address (`host.name`)
- A **type** indicating what kind of system it is (`host.type`)

```yaml
resources:
  my-server-01: # Resource ID
    attributes:
      host.name: 192.168.1.100 # Hostname or IP
      host.type: linux # Type of system
```

### Resource Types

| Type      | Description                              | Common Protocols |
| --------- | ---------------------------------------- | ---------------- |
| `linux`   | Linux servers                            | SSH, SNMP        |
| `win`     | Windows servers                          | WMI, WinRM       |
| `network` | Switches, routers, firewalls             | SNMP             |
| `storage` | SAN, NAS, storage arrays                 | HTTP, WBEM, SNMP |
| `oob`     | Out-of-band management (BMC, iLO, iDRAC) | IPMI, HTTP       |
| `aix`     | IBM AIX systems                          | SSH              |
| `hpux`    | HP-UX systems                            | SSH              |
| `solaris` | Oracle Solaris systems                   | SSH              |

## Resource Groups

**Resource groups** are containers that group related resources together. They're useful for:

- Managing large, distributed infrastructures
- Applying shared settings to multiple resources
- Organizing multi-site deployments

For example, resource groups may have a **site** attribute indicating the physical or logical location (data center, cloud region, etc.):

```yaml
resourceGroups:
  paris:
    attributes:
      site: paris-dc
    resources:
      server-1: ...
      server-2: ...

  london:
    attributes:
      site: london-dc
    resources:
      server-3: ...
```

The `site` attribute is required for [sustainability](guides/configure-sustainability-metrics.html) and hardware monitoring dashboards.

> **Tip**: For small, centralized environments, you don't need resource groups; just use `resources:` directly with a top-level `site` attribute if needed.

## Protocols

**Protocols** define how **MetricsHub** communicates with your resources to collect metrics. Each protocol has its own authentication and connection parameters.

| Protocol        | Use Case                           |
| --------------- | ---------------------------------- |
| **SSH**         | Linux/Unix command execution       |
| **WMI**         | Windows local/domain queries       |
| **WinRM**       | Windows remote management          |
| **SNMP v1/v2c** | Network devices (community string) |
| **SNMP v3**     | Network devices (encrypted)        |
| **HTTP/HTTPS**  | REST APIs, web services            |
| **WBEM**        | VMware, CIM-based systems          |
| **IPMI**        | Hardware management (BMC)          |
| **JDBC**        | Database queries                   |
| **JMX**         | Java application monitoring        |

A resource can use multiple protocols simultaneously:

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
      snmp:
        version: v2c
        community: public
```

## Connectors

**Connectors** are YAML-based definitions that describe _what_ to collect and _how_ to interpret the data. They contain:

- Detection rules (how to identify compatible systems)
- Data sources (SNMP OIDs, WMI queries, CLI commands, etc.)
- Metric mappings (how to transform raw data into OpenTelemetry metrics)

**MetricsHub** includes hundreds of built-in connectors for:

- Operating systems (Linux, Windows, AIX, etc.)
- Hardware vendors (Dell, HPE, Cisco, Lenovo, etc.)
- Storage systems (NetApp, Pure Storage, EMC, etc.)
- Network devices (Cisco, Juniper, Arista, etc.)

See the [Connectors Directory](connectors-directory.html) for the full list.

### Automatic Detection

When **MetricsHub** connects to a resource, it automatically:

1. Tests which connectors are compatible
2. Selects the best matching connectors
3. Begins collecting metrics

You can override this behavior to force or exclude specific connectors. See [Fine-Tuning Monitoring](configuration/fine-tuning-monitoring.html#connector-selection).

## Monitors

**Monitors** are the individual components discovered within a resource—such as:

- CPUs, memory, disks
- Network interfaces
- Power supplies, fans, temperatures
- Processes, services
- Storage volumes, LUNs

Each monitor produces metrics that are exported to your observability platform.

## Metrics and OpenTelemetry

**MetricsHub** exports all collected data using the [OpenTelemetry](https://opentelemetry.io) standard:

- **Metrics** follow OpenTelemetry semantic conventions
- Data is exported via **OTLP** (gRPC or HTTP)
- Compatible with Prometheus, Datadog, Splunk, New Relic, and 30+ platforms

This means your metrics are portable and standardized, regardless of which backend you use.

## Next Steps

- [Quick Start (Community)](getting-started/quick-start-community-prometheus.html)
- [Quick Start (Enterprise)](getting-started/quick-start-enterprise.html)
- [Monitoring Configuration](configuration/configure-monitoring.html)
- [Supported Platforms](supported-platforms.html)
