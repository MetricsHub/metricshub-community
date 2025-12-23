keywords: install, enterprise, community
description: How to install MetricsHub on Debian Linux.

# Installation

<!-- MACRO{toc|fromDepth=1|toDepth=1|id=toc} -->

> MetricsHub supports Debian v10.

## Enterprise Edition

### Download

From [MetricsHub's Web site](https://metricshub.com/downloads), download the appropriate Debian package for your system architecture and copy it into `/usr/local`:

* For **amd64** (x86_64) systems: **metricshub-enterprise_${enterpriseVersion}_amd64.deb**
* For **arm64** (aarch64) systems: **metricshub-enterprise_${enterpriseVersion}_arm64.deb**

### Install

Once you have downloaded the Debian package, run the following `dpkg` command:

**For amd64 systems:**

```shell-session
cd /usr/local
sudo dpkg -i metricshub-enterprise_${enterpriseVersion}_amd64.deb
```

**For arm64 systems:**

```shell-session
cd /usr/local
sudo dpkg -i metricshub-enterprise_${enterpriseVersion}_arm64.deb
```

When complete, the **MetricsHub**'s files are deployed in `/opt/metricshub` and the **MetricsHub Enterprise Agent** is started as a service.

### Configure

* In the **./lib/config/** directory, located under the `/opt/metricshub` installation directory, create your configuration file(s) and define the [resources to be monitored.](../configuration/configure-monitoring.md#step-3-configure-resources)
* In the **./lib/otel/otel-config.yaml** file, located under the `/opt/metricshub` installation directory, specify where the _OpenTelemetry Collector_ should [send the collected data](../configuration/send-telemetry.html#configure-the-otel-collector-28enterprise-edition-29).

To assist with the setup process, two configuration examples are provided for guidance in the installation directory (`./metricshub`):

* `./lib/config/metricshub-config-example.yaml`, a configuration example of the MetricsHub agent.
* `./lib/otel/otel-config-example.yaml`, a configuration example of the OpenTelemetry Collector.

### Start

To start the **MetricsHub Enterprise** service, run the command below:

```shell-session
systemctl start metricshub-enterprise-service
```

### Stop

To stop the **MetricsHub Enterprise** service, run the command below:

```shell-session
systemctl stop metricshub-enterprise-service
```

### Uninstall

To uninstall **MetricsHub Enterprise**, run the command below:

```shell-session
sudo dpkg -r metricshub
```

### Upgrade

If you have installed a previous version of **MetricsHub Enterprise** and want to upgrade to the latest version **${enterpriseVersion}**, follow these steps:

1. From [MetricsHub's Web site](https://metricshub.com/downloads), download the appropriate Debian package for your system architecture and copy the file into the `/usr/local` directory:
   * For **amd64** systems: **metricshub-enterprise_${enterpriseVersion}_amd64.deb**
   * For **arm64** systems: **metricshub-enterprise_${enterpriseVersion}_arm64.deb**

2. Run the following command to stop the **MetricsHub Enterprise** service:

   ```shell-session
   systemctl stop metricshub-enterprise-service
   ```

3. Run the following `dpkg` command with the appropriate package for your architecture:

   **For amd64 systems:**

   ```shell-session
   cd /usr/local
   sudo dpkg -i metricshub-enterprise_${enterpriseVersion}_amd64.deb
   ```

   **For arm64 systems:**

   ```shell-session
   cd /usr/local
   sudo dpkg -i metricshub-enterprise_${enterpriseVersion}_arm64.deb
   ```

## Community Edition

You can install **MetricsHub Community Edition** in two ways:

1. **Automatically** using the official installation script (recommended)
2. **Manually** by downloading and installing the `metricshub-community_${communityVersion}_{arch}.deb` package

### Automatic Install (Recommended)

Run the following command to install **MetricsHub Community**:

```shell-session
curl -fsSL https://get.metricshub.com | bash
```

This command will:

* Download the latest version of **MetricsHub Community Edition**
* Install it to `${esc.d}HOME/metricshub`
* Run a version check to confirm successful installation

Finally, run the below command to ensure **MetricsHub Community** is properly installed:

```shell-session
${esc.d}HOME/metricshub/bin/metricshub --version
```

### Manual Install

If you prefer a manual setup, follow these steps:

1. From [MetricsHub's Web site](https://metricshub.com/downloads), download the appropriate Debian package for your system architecture and copy it into `/usr/local`:

   * For **amd64** (x86_64) systems: **metricshub-community_${communityVersion}_amd64.deb**
   * For **arm64** (aarch64) systems: **metricshub-community_${communityVersion}_arm64.deb**

2. Run the following `dpkg` command:

   **For amd64 systems:**

   ```shell-session
   cd /usr/local
   sudo dpkg -i metricshub-community_${communityVersion}_amd64.deb
   ```

   **For arm64 systems:**

   ```shell-session
   cd /usr/local
   sudo dpkg -i metricshub-community_${communityVersion}_arm64.deb
   ```

When complete, the **MetricsHub**'s files are deployed in `/opt/metricshub` and the **MetricsHub Community Agent** is started as a service.

#### Configure

In the **./lib/config/** directory, located under the `/opt/metricshub` installation directory:

1. [structure your configuration](../configuration/configure-monitoring.md#step-1-structure-your-configuration) by creating either one single or multiple configuration file(s)
2. [configure your resource groups](../configuration/configure-monitoring.md#step-2-configure-resource-groups) and [resources to be monitored.](../configuration/configure-monitoring.md#step-3-configure-resources)
3. [define the OpenTelemetry Protocol endpoint](../configuration/configure-monitoring.md#otlp-exporter-settings) that will receive the MetricsHub signals.

To assist with the setup process, a configuration example (`./lib/config/metricshub-config-example.yaml`) is provided for guidance in the installation directory (`/opt/metricshub`).

#### Start / Stop the Service

To start the **MetricsHub Community** service, run the command below:
```shell-session
systemctl start metricshub-community-service
```

To stop the **MetricsHub Community** service, run the command below:

```shell-session
systemctl stop metricshub-community-service
```

#### Uninstall

To uninstall **MetricsHub Community**, run the command below:

```shell-session
sudo dpkg -r metricshub
```

#### Upgrade

If you have installed a previous version of **MetricsHub Community** and want to upgrade to the latest version **${communityVersion}**, follow these steps:

1. From [MetricsHub's Web site](https://metricshub.com/downloads), download the appropriate Debian package for your system architecture and copy the file into the `/usr/local` directory:
   * For **amd64** systems: **metricshub-community_${communityVersion}_amd64.deb**
   * For **arm64** systems: **metricshub-community_${communityVersion}_arm64.deb**

2. Run the following command to stop the **MetricsHub Community** service:

   ```shell-session
   systemctl stop metricshub-community-service
   ```

3. Run the following `dpkg` command with the appropriate package for your architecture:

   **For amd64 systems:**

   ```shell-session
   cd /usr/local
   sudo dpkg -i metricshub-community_${communityVersion}_amd64.deb
   ```

   **For arm64 systems:**

   ```shell-session
   cd /usr/local
   sudo dpkg -i metricshub-community_${communityVersion}_arm64.deb
   ```