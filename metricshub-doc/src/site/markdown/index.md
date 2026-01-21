keywords: monitoring, observability, opentelemetry, metrics, infrastructure
description: MetricsHub is a universal metrics collection agent for OpenTelemetry that monitors servers, storage, networks, and applications — pushing data to any observability platform.

# MetricsHub Documentation

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

**MetricsHub** is a universal metrics collector for [OpenTelemetry](https://opentelemetry.io/docs). It monitors your entire infrastructure — servers, storage systems, network devices, databases, and applications — and exports metrics to any observability platform.

## Why MetricsHub?

| Challenge            | How MetricsHub Helps                                                                      |
| -------------------- | ----------------------------------------------------------------------------------------- |
| **Vendor lock-in**   | OpenTelemetry-native — export to Prometheus, Datadog, Splunk, New Relic, or 30+ platforms |
| **Complex setups**   | Single agent monitors hundreds of systems via SNMP, WMI, SSH, REST, IPMI, and more        |
| **Limited coverage** | 200+ built-in connectors for enterprise hardware and software                             |
| **Custom needs**     | Extend monitoring with simple YAML — no coding required                                   |
| **Sustainability**   | Track energy consumption and carbon footprint out of the box                              |

## How It Works

**MetricsHub** runs as an agent on your infrastructure. It collects metrics from local and remote systems using standard protocols, then pushes data to your observability platform via OpenTelemetry (OTLP).

![MetricsHub Architecture](./images/otel-metricshub.png)

The collection logic is defined in **connectors** — YAML files that describe what to monitor and how. MetricsHub Enterprise includes connectors for [200+ platforms](supported-platforms.html), from HPE servers to Cisco switches to Oracle databases.

## Key Features

- **Multi-protocol collection** — HTTP, IPMI, JMX, SNMP, SSH, WBEM, WinRM, WMI
- **OpenTelemetry native** — Semantic conventions, OTLP export, collector integration
- **200+ connectors** — Servers, storage, network, databases, applications
- **Extensible** — Add custom monitoring with YAML, no code required
- **Sustainability metrics** — Power consumption, carbon intensity, electricity costs
- **Enterprise-ready** — Web UI, password encryption, 24×7 support

## Get Started

1. **[Choose your edition](getting-started/editions.html)** — Community (free) or Enterprise
2. **Follow a quick start guide:**
   - [Quick Start — Community Edition](getting-started/quick-start-community-prometheus.html)
   - [Quick Start — Enterprise Edition](getting-started/quick-start-enterprise.html)
3. **[Configure monitoring](configuration/configure-monitoring.html)** for your resources

## Learn More

- [Key Concepts](concepts.html) — Understand resources, monitors, connectors, and metrics
- [Supported Platforms](supported-platforms.html) — See what MetricsHub monitors out of the box
- [Integrations](integrations/index.html) — Connect to Prometheus, Grafana, Datadog, and more
