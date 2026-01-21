keywords: configuration, tuning, collect period, connectors, filters, discovery, attributes
description: How to fine-tune MetricsHub monitoring settings including collection frequency, connector selection, monitor filters, and resource attributes.

# Fine-Tuning Monitoring

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

This page covers configuration options for customizing how **MetricsHub** collects and processes metrics. Settings can be applied globally, per resource group, or per resource.

For basic setup, see [Monitoring Configuration](./configure-monitoring.html).

## Scheduling and Timing

### Collection Period

**MetricsHub** collects metrics every minute by default. Use `collectPeriod` to change this:

```yaml
# Global
collectPeriod: 2m

# Per resource
resources:
  myHost1:
    collectPeriod: 1m30s
```

> **Warning**: Collecting too frequently increases CPU load.

### Discovery Cycle

**MetricsHub** runs discovery every 30 collection cycles by default. Use `discoveryCycle` to change this:

```yaml
# Global
discoveryCycle: 15

# Per resource
resources:
  myHost1:
    discoveryCycle: 5
```

> **Warning**: Discovering too frequently increases CPU load.

### Duration Format

All timeout and period values support these units:

| Unit | Description | Examples      |
| ---- | ----------- | ------------- |
| s    | seconds     | `120s`        |
| m    | minutes     | `2m`, `1m30s` |
| h    | hours       | `1h`, `1h30m` |
| d    | days        | `1d`          |

## Filtering

### Monitor Filters

Include or exclude specific monitor types to control telemetry volume:

```yaml
# Include only specific monitors
monitorFilters: [ +enclosure, +fan, +power_supply ]

# Exclude volumes
monitorFilters: [ "!volume" ]
```

Can be set globally, per resource group, or per resource.

> **Warning**: Don't exclude critical monitors (batteries, power supplies, CPUs, fans, memory).

## Resource Identity

### Custom Hostname

Separate the hostname used for collection from the one reported in metrics:

```yaml
resources:
  myHost1:
    attributes:
      host.name: reported-hostname # In metrics
    protocols:
      snmp:
        hostname: actual-ip-address # For collection
```

### FQDN Resolution

Resolve hostnames to fully qualified domain names:

```yaml
# Global
resolveHostnameToFqdn: true

# Per resource
resources:
  myHost1:
    resolveHostnameToFqdn: true
```

> **Warning**: Resolution failures may change `host.name`, affecting metric identity.

### Custom Attributes

Add labels to extend [Host Resource](https://opentelemetry.io/docs/specs/semconv/resource/host/) attributes:

```yaml
resources:
  myHost1:
    attributes:
      host.name: my-host-01
      host.type: win
      app: Jenkins # Custom
      environment: prod # Custom
```

## Performance

### Parallel Jobs

Control concurrent job execution (default: 20):

```yaml
jobPoolSize: 40
```

> **Warning**: Too many parallel jobs may cause OutOfMemory errors.

### Sequential Mode

Force serial execution for sensitive targets:

```yaml
resources:
  myHost1:
    sequential: true
```

> **Warning**: Significantly slower. Use only when required by target system limitations.

### StateSet Compression

Control how health status metrics are reported:

```yaml
stateSetCompression: suppressZeros  # Default: only report non-zero states
stateSetCompression: none           # Report all states
```

### Self-Monitoring

Track MetricsHub's own performance:

```yaml
enableSelfMonitoring: true
```
