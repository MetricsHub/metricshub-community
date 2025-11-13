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

### Install

You can install **MetricsHub Community Edition** in two ways:

1. **Automatically** using the official installation script (recommended)
2. **Manually** by downloading and extracting the `.tar.gz` package

> Note: Both methods that are outlined below will install **MetricsHub** to the `${esc.d}HOME/metricshub` directory.

#### Option 1: Automatic (Recommended)

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

#### Option 2: Manual

If you prefer a manual setup, follow these steps:

##### Download

Download the Linux package `metricshub-community-linux-${communityVersion}.tar.gz` from the [MetricsHub Release v${communityVersion}](https://github.com/metricshub/metricshub-community/releases/tag/v${communityVersion}) page:

```shell-session
wget -P /tmp https://github.com/metricshub/metricshub-community/releases/download/v${communityVersion}/metricshub-community-linux-${communityVersion}.tar.gz
```

##### Unpack

Unzip and untar the content of `metricshub-community-linux-${communityVersion}.tar.gz` into a program directory, like `${esc.d}HOME`. There is no need to create a specific subdirectory for `metricshub` as the archive already contains a `metricshub` directory.

```shell-session
cd ${esc.d}HOME
tar xzf /tmp/metricshub-community-linux-${communityVersion}.tar.gz
```

### Configure

After installing **MetricsHub**, you need to:

* [structure your configuration](../configuration/configure-monitoring.md#step-1-structure-your-configuration) by creating either one single or multiple configuration file
* [configure your resource groups](../configuration/configure-monitoring.md#step-2-configure-resource-groups) and [resources to be monitored.](../configuration/configure-monitoring.md#step-3-configure-resources)
* [define the OpenTelemetry Protocol endpoint](../configuration/send-telemetry.html#configure-the-otlp-exporter-28community-edition-29) that will receive the **MetricsHub** signals.

To assist with the setup process, the configuration example `./lib/config/metricshub-example.yaml` is provided for guidance in the installation directory (`./metricshub`).

### Start

To start **MetricsHub** in an interactive terminal, run the command below:

```shell-session
cd ${esc.d}HOME/metricshub/bin
./service
```

To start **MetricsHub** with an alternate configuration file, run the command below:

```shell-session
cd ${esc.d}HOME/metricshub/bin
./service --config <PATH>
```

Example:

```shell-session
cd ${esc.d}HOME/metricshub/bin
./service --config config/my-metricshub.yaml
```

To start **MetricsHub** as a **Linux service**, follow the steps below:

* **Create a systemd service file**

  Create a file (for example: `/etc/systemd/system/metricshub-service.service`) and define the **MetricsHub Service** configuration as follows:

  ```
  [Unit]
  Description=MetricsHub Service

  [Service]
  ExecStart=${esc.d}HOME/metricshub/bin/service
  Restart=on-failure

  [Install]
  WantedBy=multi-user.target
  ```
* **Reload systemd**

  After creating the Linux service file, reload `systemd` to recognize the new service.

  ```shell-session
  systemctl daemon-reload
  ```
* **Start the MetricsHub Service**

  ```shell-session
  systemctl start metricshub-service
  ```

  To enable the Linux service to start on boot, run the command below:

  ```shell-session
  systemctl enable metricshub-service
  ```

* **Check status**

  To verify that the **MetricsHub Service** is running without errors, run the command below:

  ```shell-session
  systemctl status metricshub-service
  ```

  This will give you information about the current status of your service.

### Stop

**Interactive Terminal**

To manually stop the **MetricsHub Service** in an interactive terminal, use the keyboard shortcut `CTRL+C`. This will interrupt the running process and terminate the **MetricsHub Service**.

**Background Process**

If the **MetricsHub Service** is running in the background, follow these steps to stop it:

1. Run the `ps` command to get the **MetricsHub Service** PID:

   ```shell-session
   ps aux | grep service
   ```

2. Write down the PID associated with the **MetricsHub Service**.
3. Terminate the process using the `kill` command below:

   ```shell-session
   kill -9 <PID>
   ```
where `<PID>` should be replaced with the actual process ID.

**Service**

To stop the **MetricsHub Service** that is started as a **Linux service**, run the command below:

```shell-session
systemctl stop <metricshub-service>
```

where `<metricshub-service>` should be replaced with the actual service name. For example, `metricshub-service` if the `systemd` service file is `/etc/systemd/system/metricshub-service.service`

### Uninstall

1. Stop the **MetricsHub Service**.
2. Navigate to the directory where **MetricsHub** is located (e.g., `${esc.d}HOME`) and remove the entire `metricshub` directory.

   ```shell-session
   rm -rf ${esc.d}HOME/metricshub
   ```

If the **MetricsHub Service** was set up as a **Linux Service**, delete the file `/etc/systemd/system/metricshub-service.service` and run the below command to reload `systemd`:

  ```shell-session
  systemctl daemon-reload
  ```
