keywords: install, enterprise, windows
description: How to install MetricsHub Enterprise on Windows.

# Installing on Windows (Enterprise)

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

> **Supported versions:** Windows Server 2012 R2 and higher

## Installing

1. Download **metricshub-enterprise-${enterpriseVersion}.msi** from [metricshub.com/downloads](https://metricshub.com/downloads)
2. Double-click the `.msi` file to launch the Installation Wizard
3. Follow the prompts to complete installation

When complete:
- Files are deployed to `C:\Program Files\MetricsHub`
- The **MetricsHub Enterprise Agent** service is registered and started
- Configuration is stored in `C:\ProgramData\MetricsHub`

## Next Steps

1. [Configure your resources](../configuration/configure-monitoring.html)
2. [Set up telemetry export](../configuration/send-telemetry.html)
3. [Access the Web UI](../operating-web-ui.html)

## Managing the Service

### Starting the Service

Open **services.msc** and start the **MetricsHub Enterprise** service.

### Stopping the Service

Open **services.msc** and stop the **MetricsHub Enterprise** service.

## Uninstalling

Double-click **metricshub-enterprise-${enterpriseVersion}.msi** and click **Remove** when prompted.

## Upgrading

1. Download the latest **metricshub-enterprise-${enterpriseVersion}.msi** from [metricshub.com/downloads](https://metricshub.com/downloads)
2. Stop the **MetricsHub Enterprise** service in **services.msc**
3. Double-click the new `.msi` file and follow the upgrade prompts
