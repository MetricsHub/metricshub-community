keywords: install, windows, enterprise, community
description: How to install MetricsHub on Windows.

# Installing on Windows

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

This guide covers installing MetricsHub on Windows Server using the MSI installer.

> **Supported versions:** Windows Server 2012 R2 and higher

## Installing

1. Download the installer for your edition from [metricshub.com/downloads](https://metricshub.com/downloads):
   - **metricshub-enterprise-${enterpriseVersion}.msi** (Enterprise)
   - **metricshub-community-${communityVersion}.msi** (Community)

2. Double-click the `.msi` file and follow the Installation Wizard

When complete:

- Files are deployed to `C:\Program Files\MetricsHub`
- The MetricsHub Agent service is registered and started
- Configuration is stored in `C:\ProgramData\MetricsHub`

## Next Steps

Once installed, you're ready to configure MetricsHub:

1. [Configure your resources](../configuration/configure-monitoring.html) — define what to monitor
2. [Set up telemetry export](../configuration/send-telemetry.html) — choose where to send metrics
3. [Access the Web UI](../operating-web-ui.html) — manage and monitor from your browser

## Managing the Service

MetricsHub runs as a Windows service. Open **services.msc** and locate the **MetricsHub** service:

- To **start**: Right-click the service and select **Start**
- To **stop**: Right-click the service and select **Stop**

## Uninstalling

To remove MetricsHub from your system, double-click the original `.msi` file and click **Remove** when prompted.

## Upgrading

To upgrade to a newer version:

1. Download the latest `.msi` from [metricshub.com/downloads](https://metricshub.com/downloads)
2. Stop the MetricsHub service in **services.msc**
3. Double-click the new `.msi` file and follow the upgrade prompts

Your configuration files are preserved during the upgrade.
