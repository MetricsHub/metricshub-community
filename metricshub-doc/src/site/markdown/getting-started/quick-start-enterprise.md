keywords: quick start, getting started, enterprise
description: Step-by-step instructions for installing and configuring MetricsHub Enterprise Edition.

# Quick Start - Enterprise Edition

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

This quick start guide provides step-by-step instructions for deploying **MetricsHub Enterprise Edition** in your environment. The Enterprise Edition includes a bundled OpenTelemetry Collector, making it easier to send metrics to your observability platform.

After completing this quick start, you will have:

- **MetricsHub Enterprise** installed on your machine
- The **MetricsHub Agent** configured to collect metrics from your local host
- Metrics flowing to your chosen observability platform

## Prerequisites

- Administrator/root access on the target system
- Network access to the systems you want to monitor
- A valid license key (or use the 30-day trial)

## Step 1: Install MetricsHub Enterprise

Download and install **MetricsHub Enterprise** for your platform:

| Platform       | Installation                                                                               |
| -------------- | ------------------------------------------------------------------------------------------ |
| Windows        | [Download](https://metricshub.com/download) the `.msi` installer and run the wizard        |
| Debian/Ubuntu  | [Download](https://metricshub.com/download) the `.deb` package and install via apt or dpkg |
| Red Hat/CentOS | [Download](https://metricshub.com/download) the `.rpm` package and install via yum or rpm  |
| Docker         | Pull from `docker.metricshub.com`                                                          |

> **Note**: Registered customers have access to private package repositories. See [Installation](../installation/index.html) for detailed instructions.

After installation:

- **Windows**: MetricsHub is installed to `C:\Program Files\MetricsHub` with configuration in `C:\ProgramData\MetricsHub`
- **Linux**: MetricsHub is installed to `/opt/metricshub`

## Step 2: Configure Your License

Edit the license configuration file:

- **Windows**: `C:\ProgramData\MetricsHub\config\license.yaml`
- **Linux**: `/opt/metricshub/lib/config/license.yaml`

```yaml
license:
  product: MetricsHub Enterprise
  organization: YOUR_ORGANIZATION
  expiresOn: EXPIRATION_DATE
  resources: NUMBER_OF_RESOURCES
  key: YOUR_LICENSE_KEY
```

> **Note**: If you don't have a license key yet, MetricsHub Enterprise includes a 30-day trial. Contact [sales@metricshub.com](mailto:sales@metricshub.com) to obtain your license.

## Step 3: Configure Your First Resource

Create or edit the main configuration file:

- **Windows**: `C:\ProgramData\MetricsHub\config\metricshub.yaml`
- **Linux**: `/opt/metricshub/lib/config/metricshub.yaml`

### Monitoring the Local Windows Host

```yaml
attributes:
  site: my-datacenter

resources:
  localhost:
    attributes:
      host.name: localhost
      host.type: win
    protocols:
      wmi:
        timeout: 120s
```

### Monitoring the Local Linux Host

```yaml
attributes:
  site: my-datacenter

resources:
  localhost:
    attributes:
      host.name: localhost
      host.type: linux
    protocols:
      osCommand:
        timeout: 120s
        useSudo: true
```

## Step 4: Configure the OpenTelemetry Exporter

The Enterprise Edition includes a bundled OpenTelemetry Collector. Configure your destination in:

- **Windows**: `C:\ProgramData\MetricsHub\otel\otel-config.yaml`
- **Linux**: `/opt/metricshub/lib/otel/otel-config.yaml`

### Example: Sending to Prometheus

```yaml
exporters:
  prometheus:
    endpoint: "0.0.0.0:9464"

service:
  pipelines:
    metrics:
      receivers: [otlp]
      processors: [memory_limiter, batch]
      exporters: [prometheus]
```

### Example: Sending to Datadog

```yaml
exporters:
  datadog:
    api:
      key: ${DD_API_KEY}
      site: datadoghq.com

service:
  pipelines:
    metrics:
      receivers: [otlp]
      processors: [memory_limiter, batch]
      exporters: [datadog]
```

See [Sending Telemetry Data](../configuration/send-telemetry.html) for more exporter configurations.

## Step 5: Start MetricsHub

### On Windows

Start the service from the Services console or run:

```powershell
Start-Service "MetricsHub Enterprise"
```

### On Linux

```shell
sudo systemctl start metricshub
sudo systemctl enable metricshub  # Start on boot
```

### Verify It's Running

Check the service status:

- **Windows**: Open Services and verify "MetricsHub Enterprise" is running
- **Linux**: `sudo systemctl status metricshub`

Check the health endpoint:

```shell
curl http://localhost:13133
```

Expected response:

```json
{ "status": "Server available", "upSince": "...", "uptime": "..." }
```

## Step 6: Access the Web Interface

Open your browser and navigate to:

```
https://localhost:31888
```

1. Create a user account (see [Operating the Web UI](../operating-web-ui.html))
2. Log in to view your monitored resources and metrics
3. Use the interface to add more resources or adjust configuration

## Next Steps

- [Monitoring Configuration](../configuration/configure-monitoring.html)
- [Protocols and Credentials](../configuration/protocols-and-credentials.html)
- [Datadog Integration](../integrations/datadog.html)
- [Prometheus Alertmanager](../integrations/alertmanager.html)
- [Troubleshooting](../troubleshooting/index.html)

## Getting Help

- **Enterprise Support**: [Support Desk](https://support.metricshub.com)
- **Community**: [MetricsHub Slack](https://metricshub.slack.com)
- **Documentation**: [metricshub.com/docs](https://metricshub.com/docs)
