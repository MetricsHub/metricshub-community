keywords: integrations, prometheus, grafana, datadog, bmc helix, alertmanager, ai agent, mcp
description: Integrate MetricsHub with observability platforms, alerting systems, and AI assistants.

# Integrations

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

MetricsHub exports metrics in OpenTelemetry format, making it compatible with any observability platform that supports OTLP. This section covers specific integrations with popular platforms.

## Observability Platforms

| Platform | Description |
|----------|-------------|
| [Prometheus](prometheus.html) | Push metrics using Remote Write Protocol |
| [Datadog](datadog.html) | Export metrics directly to Datadog |
| [BMC Helix](bmc-helix.html) | Integrate with BMC Helix Operations Management |

## Alerting

| Platform | Description |
|----------|-------------|
| [Alertmanager](alertmanager.html) | Configure Prometheus alert rules for hardware failures and performance issues |

## Visualization

| Platform | Description |
|----------|-------------|
| [Grafana Dashboards](grafana.html) | Pre-built dashboards for hardware and sustainability metrics |

## AI & Automation

| Platform | Description |
|----------|-------------|
| [AI Agent (MCP)](ai-agent-mcp.html) | Connect MetricsHub to AI assistants via the Model Context Protocol |

## Other Platforms

MetricsHub works with any platform that accepts OpenTelemetry data via OTLP, including:

* Splunk Observability Cloud
* New Relic
* Elastic APM
* Dynatrace
* Azure Monitor
* Google Cloud Operations

For these platforms, configure the OTLP exporter in your [telemetry settings](../configuration/send-telemetry.html).
