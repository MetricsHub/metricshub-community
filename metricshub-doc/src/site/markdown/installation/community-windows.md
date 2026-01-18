keywords: install, community, windows
description: How to install MetricsHub Community on Windows.

# Installing on Windows (Community)

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

> **Supported versions:** Windows Server 2012 R2 and higher

## Installing

1. Download **metricshub-community-${communityVersion}.msi** from [metricshub.com/downloads](https://metricshub.com/downloads)
2. Double-click the `.msi` file to launch the Installation Wizard
3. Follow the prompts to complete installation

When complete:
- Files are deployed to `C:\Program Files\MetricsHub`
- The **MetricsHub Community Agent** service is registered and started
- Configuration is stored in `C:\ProgramData\MetricsHub`

## Next Steps

1. [Configure your resources](../configuration/configure-monitoring.html)
2. [Configure OTLP export](../configuration/send-telemetry.html)

## Managing the Service

### Starting the Service

Open **services.msc** and start the **MetricsHub Community** service.

### Stopping the Service

Open **services.msc** and stop the **MetricsHub Community** service.

## Uninstalling

Double-click **metricshub-community-${communityVersion}.msi** and click **Remove** when prompted.

## Upgrading

1. Download the latest **metricshub-community-${communityVersion}.msi** from [metricshub.com/downloads](https://metricshub.com/downloads)
2. Stop the **MetricsHub Community** service in **services.msc**
3. Double-click the new `.msi` file and follow the upgrade prompts
