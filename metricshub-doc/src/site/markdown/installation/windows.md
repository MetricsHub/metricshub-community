keywords: install, enterprise, community
description: How to install MetricsHub on Windows.

# Installation

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

> MetricsHub Community and MetricsHub Enterprise support Windows 2012R2 and higher.

## MetricsHub Enterprise

### Install MetricsHub Enterprise

To install **MetricsHub Enterprise**:

1. From [MetricsHub's Web site](https://metricshub.com/downloads), download **metricshub-enterprise-${enterpriseVersion}.msi**
2. Double-click the `.msi` file. The Installation Wizard will automatically start and guide you through the installation process.

When complete, the **MetricsHub Enterprise**'s files are deployed to the destination folder (by default under `C:\Program Files\MetricsHub`) and the **MetricsHub Enterprise Agent** is started as a service and appears in `services.msc`.

You can now configure the [resources to be monitored](../configuration/configure-monitoring.md) and where to [send the collected data](../configuration/send-telemetry.html#configure-the-otel-collector-28enterprise-edition-29).

**MetricsHub Enterprise** operates using the configuration located in the `ProgramData\MetricsHub` directory.

### Start MetricsHub Enterprise

To start the **MetricsHub Enterprise** service, open **services.msc** and start the **MetricsHub Enterprise** service.

### Uninstall MetricsHub Enterprise

To uninstall **MetricsHub Enterprise**, double-click the **metricshub-enterprise-${enterpriseVersion}.msi** file and click **Remove** when prompted.

### Upgrade MetricsHub Enterprise

To upgrade **MetricsHub Enterprise** to the latest version:

1. From [MetricsHub's Web site](https://metricshub.com/downloads), download **metricshub-enterprise-${enterpriseVersion}.msi**.
2. Open **services.msc** and stop the **MetricsHub Enterprise** service.
3. Double-click the `.msi` file you previously downloaded. The Installation Wizard will automatically start and guide you through the upgrade process.

## MetricsHub Community

### Install MetricsHub Community

To install **MetricsHub Community**:

1. From [MetricsHub's Web site](https://metricshub.com/downloads), download **metricshub-community-${communityVersion}.msi**.
2. Double-click the `.msi` file. The Installation Wizard will automatically start and guide you through the installation process.

When complete, the **MetricsHub Community**'s files are deployed to the destination folder (by default under `C:\Program Files\MetricsHub`) and the **MetricsHub Community Agent** is started as a service and appears in `services.msc`.

You can now configure the [resources to be monitored](../configuration/configure-monitoring.md) and the [OTLP exporter](../configuration/configure-monitoring.md#otlp-exporter-settings).

**MetricsHub Community** operates using the configuration located in the `ProgramData\MetricsHub` directory.

### Start MetricsHub Community

To start the **MetricsHub Community** service, open **services.msc** and start the **MetricsHub Community** service.

### Uninstall MetricsHub Community

To uninstall **MetricsHub Community**, double-click the **metricshub-community-${communityVersion}.msi** file and click **Remove** when prompted.

### Upgrade MetricsHub Community

To upgrade **MetricsHub Community** to the latest version:

1. From [MetricsHub's Web site](https://metricshub.com/downloads), download **metricshub-community-${communityVersion}.msi**.
2. Open **services.msc** and stop the **MetricsHub Community** service.
3. Double-click the `.msi` file you previously downloaded. The Installation Wizard will automatically start and guide you through the upgrade process.
