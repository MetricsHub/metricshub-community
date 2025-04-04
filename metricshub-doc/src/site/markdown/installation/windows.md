keywords: install, enterprise, community
description: How to install MetricsHub on Windows.

# Installation

<!-- MACRO{toc|fromDepth=1|toDepth=1|id=toc} -->

> MetricsHub supports Windows 2012R2 and higher.

## Enterprise Edition

### Download

From [MetricsHub's Web site](https://metricshub.com/downloads), download **metricshub-enterprise-windows-${enterpriseVersion}.msi**.

### Install
Double-click the `.msi` file you previously downloaded. The Installation Wizard will automatically start and guide you through the installation process.

When complete, the MetricsHub's files are deployed to the destination folder (by default under `C:\Program Files\MetricsHub`) and the MetricsHubEnterprise Agent is started as a service and appears in services.msc.

MetricsHub operates using the configuration located in the `ProgramData\MetricsHub` directory

### Configure

* In the **C:\ProgramData\MetricsHub\config\metricshub.yaml** file, configure the [resources to be monitored.](../configuration/configure-monitoring.html#configure-resources)
* In the **C:\ProgramData\MetricsHub\otel\otel-config.yaml** file, specify where the _OpenTelemetry Collector_ should [send the collected data.](../configuration/send-telemetry.html#configure-the-otel-collector-28enterprise-edition-29)

To assist with the setup process, two configuration examples are provided for guidance in the installation directory (`C:\Program Files\MetricsHub`):

* .\config\metricshub-config-example.yaml, a configuration example of the MetricsHub agent.
* .\otel\otel-config-example.yaml, a configuration example of the OpenTelemetry Collector.

### Start

To start the **MetricsHub Enterprise** service, open **services.msc** and start the **MetricsHub Enterprise** service.

### Uninstall

To uninstall **MetricsHub Enterprise**, double-click the **metricshub-enterprise-windows-${enterpriseVersion}.msi** file and click **Remove** when prompted.

### Upgrade

If you have installed a previous version of **MetricsHub Enterprise** and want to upgrade to the latest version **${enterpriseVersion}**, follow these steps:

1. From [MetricsHub's Web site](https://metricshub.com/downloads), download **metricshub-enterprise-windows-${enterpriseVersion}.msi**.
2. Open **services.msc** and stop the **MetricsHub Enterprise** service.
3. Double-click the `.msi` file you previously downloaded. The Installation Wizard will automatically start and guide you through the upgrade process.

## Community Edition

### Download

Download the Windows package, `metricshub-windows-${communityVersion}.zip`, from the [MetricsHub Release v${communityVersion}](https://github.com/metricshub/metricshub-community/releases/tag/v${communityVersion}) page.

### Install

Unzip the content of `metricshub-windows-${communityVersion}.zip` into a program folder, like `C:\Program Files`. There is no need to create a specific subdirectory for `MetricsHub` as the zip archive already contains a `MetricsHub` directory.

> Note: You will need administrative privileges to unzip into `C:\Program Files`.

### Configure

In the `C:\ProgramData\MetricsHub\config\metricshub.yaml` file, configure:

* the [resources to be monitored.](../configuration/configure-monitoring.html#configure-resources)
* the [OpenTelemetry Protocol endpoint](../configuration/send-telemetry.html#configure-the-otlp-exporter-28community-edition-29) that will receive the MetricsHub signals.

To assist with the setup process, the configuration example `.\config\metricshub-example.yaml` is provided for guidance in the installation directory (typically, `C:\Program Files\MetricsHub`).

### Start

To start **MetricsHub Service** in `CMD.EXE` or [`Windows Terminal`](https://www.microsoft.com/en-us/p/windows-terminal/9n0dx20hk701?activetab=pivot:overviewtab), run the command below:

```shell-session
cd "c:\Program Files\MetricsHub"
MetricsHubServiceManager
```

> Note: Run `CMD.EXE` or `Windows Terminal` with elevated privileges (**Run As Administrator**).

This will start **MetricsHub** with the default configuration file `C:\ProgramData\MetricsHub\config\metricshub.yaml`.

Run the command below to start **MetricsHub** with an alternate configuration file:

```shell-session
cd "c:\Program Files\MetricsHub"
MetricsHubServiceManager --config <PATH>
```

Example:

```shell-session
cd "c:\Program Files\MetricsHub"
MetricsHubServiceManager --config C:\ProgramData\MetricsHub\config\my-metricshub.yaml
```

To start **MetricsHub** as a **Windows service**, run the following commands under the installation folder (assuming the product has been installed in `C:\Program Files`):

```shell-session
cd "c:\Program Files\MetricsHub"
service-installer install MetricsHub "c:\Program Files\MetricsHub\MetricsHubServiceManager.exe"
service-installer set MetricsHub AppDirectory "c:\Program Files\MetricsHub"
service-installer set MetricsHub DisplayName MetricsHub
service-installer set MetricsHub Start SERVICE_AUTO_START
```

To check MetricsHub's status, run the following command:

```shell-session
sc query MetricsHub
```

The service will appear as `MetricsHub` in the `services.msc` console.

### Stop

**Interactive Terminal**

To stop the **MetricsHub Service** manually, use the keyboard shortcut `CTRL+C`. This will interrupt the running process and terminate the **MetricsHub Service**.

**Background Process**

If the **MetricsHub Service** is running in the background, execute the `taskkill` command as follows:

```batch
taskkill /F /IM MetricsHubServiceManager.exe
```

**Service**

To stop the **MetricsHub Service** started as a **Windows service**:

1. Run `services.msc` to access all the Windows services.
2. In the Services window, locate the `MetricsHub`service you manually created.
3. Right-click the `MetricsHub` service and click **Stop**.

### Uninstall

1. Stop the **MetricsHub Service**.
2. Navigate to the folder where **MetricsHub** is installed (e.g., `C:\Program Files`) and delete the entire `MetricsHub` folder.

If the **MetricsHub Service** was set up as a **Windows Service**, run the following command to remove it:

  ```batch
  sc delete MetricsHub
  ```