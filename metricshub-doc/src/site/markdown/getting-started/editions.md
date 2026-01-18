keywords: community, enterprise, comparison, editions, pricing
description: Compare MetricsHub Community and Enterprise editions to choose the right one for your needs.

# Choosing Your Edition

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

**MetricsHub** is available in two editions: **Community** (open source) and **Enterprise** (commercial). Both share the same core monitoring engine and connector library.

## Feature Comparison

| Feature | Community | Enterprise |
|---------|:---------:|:----------:|
| **Core Monitoring** | | |
| Linux and Windows systems monitoring | ✅ | ✅ |
| Basic hardware monitoring (Redfish, SNMP, WMI) | ✅ | ✅ |
| Sustainability metrics | ✅ | ✅ |
| Remote monitoring | ✅ | ✅ |
| Custom monitoring (YAML connectors) | ✅ | ✅ |
| Programmable configuration (Velocity templates) | ✅ | ✅ |
| **Connectors** | | |
| Built-in connectors | [20+](https://metricshub.org/community-connectors/metricshub-connectors-directory.html) | [200+](../connectors-directory.html) |
| **Deployment** | | |
| Manual setup (unzip archives) | ✅ | ✅ |
| Installable packages (.deb, .rpm, .msi, Docker) | — | ✅ |
| Embedded OpenTelemetry Collector | — | ✅ |
| Integrated Prometheus | — | ✅ |
| **Support** | | |
| Community Slack channel | ✅ | ✅ |
| Premium support (24x7) | — | ✅ |
| Premium support for Prometheus | — | ✅ |

## Which Edition Should I Choose?

### Choose Community Edition if:

- You're evaluating **MetricsHub** or running a proof of concept
- You have a small environment with basic monitoring needs
- You already have an OpenTelemetry Collector deployed
- You're comfortable with manual installation and configuration
- You prefer open-source solutions

### Choose Enterprise Edition if:

- You're deploying in production with mission-critical requirements
- You need broader monitoring coverage (200+ connectors)
- You want seamless installation with executable packages
- You need the embedded OpenTelemetry Collector and Prometheus
- You require 24x7 premium support

## Getting Started

### Community Edition

1. [Download](https://metricshub.com/download) from the download page
2. Follow the [Quick Start (Community)](quick-start-community-prometheus.html)
3. Join the [MetricsHub Slack](https://join.slack.com/t/metricshub/shared_invite/zt-2acx4uglx-Gl4pQZYvoVedDXGF3~C5NQ) for community support

### Enterprise Edition

1. [Start a free trial](https://metricshub.com/trial) (30-day trial period)
2. Follow the [Quick Start (Enterprise)](quick-start-enterprise.html)
3. Access the [Support Desk](https://support.metricshub.com) for premium support

## Upgrading from Community to Enterprise

Transitioning from Community to Enterprise is straightforward:

1. **Your configuration files are compatible** — no changes needed to `metricshub.yaml`
2. Install the Enterprise Edition on the same system
3. Copy your existing configuration to the Enterprise config directory
4. Start the Enterprise service

## Pricing

Visit [metricshub.com/pricing](https://metricshub.com/pricing) for details.
