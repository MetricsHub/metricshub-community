keywords: install, enterprise, community
description: How to install MetricsHub on Windows.

# Installation

<!-- MACRO{toc|fromDepth=1|toDepth=1|id=toc} -->

> MetricsHub supports Windows 2012R2 and higher.

## Enterprise Edition

### Download

From [MetricsHub's Web site](https://metricshub.com/downloads), download **metricshub-enterprise-${enterpriseVersion}.msi**.

### Install
Double-click the `.msi` file you previously downloaded. The Installation Wizard will automatically start and guide you through the installation process.

When complete, the MetricsHub's files are deployed to the destination folder (by default under `C:\Program Files\MetricsHub`) and the MetricsHub Enterprise Agent is started as a service and appears in services.msc.

MetricsHub operates using the configuration located in the `ProgramData\MetricsHub` directory

### Configure

After installing **MetricsHub**, you need to:

* [structure your configuration](../configuration/configure-monitoring.md#step-1-structure-your-configuration) by creating either one single or multiple configuration file
* [configure your resource groups](../configuration/configure-monitoring.md#step-2-configure-resource-groups) and [resources to be monitored.](../configuration/configure-monitoring.md#step-3-configure-resources)
* [define the OpenTelemetry Protocol endpoint](../configuration/send-telemetry.html#configure-the-otlp-exporter-28community-edition-29) that will receive the MetricsHub signals.

To assist with the setup process, two configuration examples are provided for guidance in the installation directory (`C:\Program Files\MetricsHub`):

* `.\config\metricshub-config-example.yaml`, a configuration example of the MetricsHub agent.
* `.\otel\otel-config-example.yaml`, a configuration example of the OpenTelemetry Collector.

### Start

To start the **MetricsHub Enterprise** service, open **services.msc** and start the **MetricsHub Enterprise** service.

### Uninstall

To uninstall **MetricsHub Enterprise**, double-click the **metricshub-enterprise-${enterpriseVersion}.msi** file and click **Remove** when prompted.

### Upgrade

If you have installed a previous version of **MetricsHub Enterprise** and want to upgrade to the latest version **${enterpriseVersion}**, follow these steps:

1. From [MetricsHub's Web site](https://metricshub.com/downloads), download **metricshub-enterprise-${enterpriseVersion}.msi**.
2. Open **services.msc** and stop the **MetricsHub Enterprise** service.
3. Double-click the `.msi` file you previously downloaded. The Installation Wizard will automatically start and guide you through the upgrade process.

## Community Edition

### Download

From [MetricsHub's Web site](https://metricshub.com/downloads), download **metricshub-community-${communityVersion}.msi**.

### Install

Double-click the `.msi` file you previously downloaded. The Installation Wizard will automatically start and guide you through the installation process.

When complete, the MetricsHub's files are deployed to the destination folder (by default under `C:\Program Files\MetricsHub`) and the MetricsHub Community Agent is started as a service and appears in services.msc.

MetricsHub operates using the configuration located in the `ProgramData\MetricsHub` directory

### Configure

After installing **MetricsHub**, you need to:

1. [structure your configuration](../configuration/configure-monitoring.md#step-1-structure-your-configuration) by creating either one single or multiple configuration file(s)
2. [configure your resource groups](../configuration/configure-monitoring.md#step-2-configure-resource-groups) and [resources to be monitored.](../configuration/configure-monitoring.md#step-3-configure-resources)
3. [define the OpenTelemetry Protocol endpoint](../configuration/configure-monitoring.md#otlp-exporter-settings) that will receive the MetricsHub signals.

To assist with the setup process, a configuration example (`.\config\metricshub-config-example.yaml`) is provided for guidance in the installation directory (`C:\Program Files\MetricsHub`).

### Start

To start the **MetricsHub Community** service, open **services.msc** and start the **MetricsHub Community** service.

### Uninstall

To uninstall **MetricsHub Community**, double-click the **metricshub-community-${communityVersion}.msi** file and click **Remove** when prompted.

### Upgrade

If you have installed a previous version of **MetricsHub Community** and want to upgrade to the latest version **${communityVersion}**, follow these steps:

1. From [MetricsHub's Web site](https://metricshub.com/downloads), download **metricshub-community-${communityVersion}.msi**.
2. Open **services.msc** and stop the **MetricsHub Community** service.
3. Double-click the `.msi` file you previously downloaded. The Installation Wizard will automatically start and guide you through the upgrade process.
