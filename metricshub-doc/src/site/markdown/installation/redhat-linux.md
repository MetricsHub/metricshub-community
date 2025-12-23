keywords: install, enterprise, community
description: How to install MetricsHub on RedHat Enterprise Linux.

# Installation

<!-- MACRO{toc|fromDepth=1|toDepth=1|id=toc} -->

> MetricsHub supports RedHat/CentOS v8.

## Enterprise Edition

### Download

From [MetricsHub's Web site](https://metricshub.com/downloads), download the appropriate RPM package for your system architecture and copy it into `/usr/local`:

* For **x86_64** (amd64) systems: **metricshub-enterprise-${enterpriseVersion}-1.x86_64.rpm**
* For **aarch64** (arm64) systems: **metricshub-enterprise-${enterpriseVersion}-1.aarch64.rpm**

### Install

Once you have downloaded the RPM package, run the following `rpm` command:

**For x86_64 systems:**

```shell-session
cd /usr/local
sudo rpm -i metricshub-enterprise-${enterpriseVersion}-1.x86_64.rpm
```

**For aarch64 systems:**

```shell-session
cd /usr/local
sudo rpm -i metricshub-enterprise-${enterpriseVersion}-1.aarch64.rpm
```

When complete, the **MetricsHub**'s files are deployed in `/opt/metricshub` and the **MetricsHubEnterprise Agent** is started as a service.

### Configure

* [Structure your configuration](../configuration/configure-monitoring.md#step-1-structure-your-configuration) by creating either one single or multiple configuration file
* [Configure your resource groups](../configuration/configure-monitoring.md#step-2-configure-resource-groups) and [resources to be monitored.](../configuration/configure-monitoring.md#step-3-configure-resources
* In the **./lib/otel/otel-config.yaml** file, located under the `/opt/metricshub` installation directory, specify where the _OpenTelemetry Collector_ should [send the collected data.](../configuration/send-telemetry.html#configure-the-otel-collector-28enterprise-edition-29)

To assist with the setup process, two configuration examples are provided for guidance in the installation directory (`./metricshub`):

* `./lib/config/metricshub-config-example.yaml`, a configuration example of the MetricsHub agent.
* `./lib/otel/otel-config-example.yaml`, a configuration example of the OpenTelemetry Collector.

### Start

To start the **MetricsHub Enterprise** service, run the command below:

```shell-session
systemctl start metricshub-enterprise-service
```

<p id="redhat"> You can start <strong>MetricsHub</strong> in an interactive terminal with an alternate <strong>MetricsHub Agent</strong>'s configuration file with the command below:</p>

```shell-session
cd /opt/metricshub/bin
./enterprise-service --config=<PATH>
```
Example:

```shell-session
cd /opt/metricshub/bin
./enterprise-service --config=config/my-metricshub-config.yaml
```

### Stop

To stop the **MetricsHub Enterprise** service, run the command below:

```shell-session
systemctl stop metricshub-enterprise-service
```

### Uninstall

To uninstall **MetricsHub Enterprise**, run the appropriate command below based on your architecture:

**For x86_64 systems:**

```shell-session
sudo rpm -e metricshub-${enterpriseVersion}-1.x86_64
```

**For aarch64 systems:**

```shell-session
sudo rpm -e metricshub-${enterpriseVersion}-1.aarch64
```

### Upgrade

If you have installed a previous version of **MetricsHub Enterprise** and want to upgrade to the latest version **${enterpriseVersion}**, follow these steps:

1. From [MetricsHub's Web site](https://metricshub.com/downloads), download the appropriate RPM package for your system architecture and copy the file into the `/usr/local` directory:
   * For **x86_64** (amd64) systems: **metricshub-enterprise-${enterpriseVersion}-1.x86_64.rpm**
   * For **aarch64** (arm64) systems: **metricshub-enterprise-${enterpriseVersion}-1.aarch64.rpm**

2. Run the following command to stop the **MetricsHub Enterprise** service:

   ```shell-session
   systemctl stop metricshub-enterprise-service
   ```

3. Run the following `rpm` command with the appropriate package for your architecture:

   **For x86_64 systems:**

   ```shell-session
   cd /usr/local
   sudo rpm -U metricshub-enterprise-${enterpriseVersion}-1.x86_64.rpm
   ```

   **For aarch64 systems:**

   ```shell-session
   cd /usr/local
   sudo rpm -U metricshub-enterprise-${enterpriseVersion}-1.aarch64.rpm
   ```


## Community Edition

You can install **MetricsHub Community Edition** in two ways:

1. **Automatically** using the official installation script (recommended)
2. **Manually** by downloading and installing the `metricshub-community-${communityVersion}-{arch}.rpm` package

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

1. From [MetricsHub's Web site](https://metricshub.com/downloads), download the appropriate RPM package for your system architecture and copy it into `/usr/local`:

   * For **x86_64** (amd64) systems: **metricshub-community-${communityVersion}-1.x86_64.rpm**
   * For **aarch64** (arm64) systems: **metricshub-community-${communityVersion}-1.aarch64.rpm**

2. Run the following `rpm` command:

   **For amd64 systems:**

   ```shell-session
   cd /usr/local
   sudo rpm -i metricshub-community-${communityVersion}-1.x86_64.rpm
   ```

   **For arm64 systems:**

   ```shell-session
   cd /usr/local
   sudo rpm -i metricshub-community-${communityVersion}-1.aarch64.rpm
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

To uninstall **MetricsHub Community**, run the appropriate command below based on your architecture:

**For x86_64 systems:**

```shell-session
sudo rpm -e metricshub-${communityVersion}-1.x86_64
```

**For aarch64 systems:**

```shell-session
sudo rpm -e metricshub-${communityVersion}-1.aarch64
```

#### Upgrade

If you have installed a previous version of **MetricsHub Community** and want to upgrade to the latest version **${communityVersion}**, follow these steps:

1. From [MetricsHub's Web site](https://metricshub.com/downloads), download the appropriate RPM package for your system architecture and copy the file into the `/usr/local` directory:
   * For **x86_64** (amd64) systems: **metricshub-community-${communityVersion}-1.x86_64.rpm**
   * For **aarch64** (arm64) systems: **metricshub-community-${communityVersion}-1.aarch64.rpm**

2. Run the following command to stop the **MetricsHub Community** service:

   ```shell-session
   systemctl stop metricshub-community-service
   ```

3. Run the following `rpm` command with the appropriate package for your architecture:

   **For amd64 systems:**

   ```shell-session
   cd /usr/local
   sudo rpm -U metricshub-community-${communityVersion}-1.x86_64.rpm
   ```

   **For arm64 systems:**

   ```shell-session
   cd /usr/local
   sudo rpm -U metricshub-community-${communityVersion}-1.aarch64.rpm
   ```