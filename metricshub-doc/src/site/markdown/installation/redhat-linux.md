keywords: install, enterprise, community
description: How to install MetricsHub on Re dHat Enterprise Linux.

# Installation

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

> MetricsHub Community and MetricsHub Enterprise support Red Hat/CentOS v8.

## MetricsHub Enterprise

### Install MetricsHub Enterprise

To install **MetricsHub Enterprise** on Red Hat Linux:

1. Download from [MetricsHub's Web site](https://metricshub.com/downloads) the package corresponding to your system architecture:

   * **metricshub-enterprise-${enterpriseVersion}-1.x86_64.rpm** (for x86_64 (amd64) systems)
   * **metricshub-enterprise-${enterpriseVersion}-1.aarch64.rpm** (for aarch64 (arm64) systems)
  
2. Copy the package into `/usr/local`
3. Run the following `rpm` command:

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

When complete, the **MetricsHub Enterprise**'s files are deployed in `/opt/metricshub` and the **MetricsHub Enterprise Agent** is started as a service.

You can now configure the [resources to be monitored](../configuration/configure-monitoring.md) and where to [send the collected data](../configuration/send-telemetry.html#configure-the-otel-collector-28enterprise-edition-29).

### Start / Stop MetricsHub Enterprise

Run the following command:

* to start **MetricsHub Enterprise**:

   ```shell-session
   systemctl start metricshub-enterprise-service
   ```

* to stop **MetricsHub Enterprise**:

   ```shell-session
   systemctl stop metricshub-enterprise-service
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

### Uninstall MetricsHub Enterprise

To uninstall **MetricsHub Enterprise**, run the following command:

**For x86_64 systems:**

```shell-session
sudo rpm -e metricshub-${enterpriseVersion}-1.x86_64
```

**For aarch64 systems:**

```shell-session
sudo rpm -e metricshub-${enterpriseVersion}-1.aarch64
```

### Upgrade MetricsHub Enterprise

To upgrade to the latest version:

1. Download from [MetricsHub's Web site](https://metricshub.com/downloads) the package corresponding to your system architecture:

   * **metricshub-enterprise-${enterpriseVersion}-1.x86_64.rpm** (for x86_64 (amd64) systems)
   * **metricshub-enterprise-${enterpriseVersion}-1.aarch64.rpm** (for aarch64 (arm64) systems)

2. Copy the package into `/usr/local`
3. Run the following command to stop **MetricsHub Enterprise**:

   ```shell-session
   systemctl stop metricshub-enterprise-service
   ```

4. Run the following `rpm` command:

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

## MetricsHub Community

### Install MetricsHub Community

To install **MetricsHub Community**, you can either:

* run the official installation script (**recommended**)
* or download and manually install the  `metricshub-community-${communityVersion}-{arch}.rpm` package

#### Automatic Install (Recommended)

First, run the following command to install **MetricsHub Community**:

```shell-session
curl -fsSL https://get.metricshub.com | bash
```

Then, run the following command to ensure that the product has been successfully installed:

```shell-session
${esc.d}HOME/metricshub/bin/metricshub --version
```

#### Manual Install

To manually install **MetricsHub Community**:

1. From [MetricsHub's Web site](https://metricshub.com/downloads), download the appropriate RPM package for your system architecture:

   * For **x86_64** (amd64) systems: **metricshub-community-${communityVersion}-1.x86_64.rpm**
   * For **aarch64** (arm64) systems: **metricshub-community-${communityVersion}-1.aarch64.rpm**

2. Copy the package into `/usr/local`
3. Run the following `rpm` command:

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

When complete, the **MetricsHub Community**'s files are deployed in `/opt/metricshub` and the **MetricsHub Community Agent** is started as a service.

You can now configure the [resources to be monitored](../configuration/configure-monitoring.md) and the [OTLP exporter](../configuration/configure-monitoring.md#otlp-exporter-settings).

### Start / Stop MetricsHub Community

Run the following command:

* to start **MetricsHub Community**:

```shell-session
systemctl start metricshub-community-service
```

* to stop **MetricsHub Community**:

```shell-session
systemctl stop metricshub-community-service
```

### Uninstall MetricsHub Community

To uninstall **MetricsHub Community**, run the following command:

**For x86_64 systems:**

```shell-session
sudo rpm -e metricshub-${communityVersion}-1.x86_64
```

**For aarch64 systems:**

```shell-session
sudo rpm -e metricshub-${communityVersion}-1.aarch64
```

### Upgrade MetricsHub Community

To upgrade to the latest version:

1. From [MetricsHub's Web site](https://metricshub.com/downloads), download the package corresponding to your system architecture:

   * **metricshub-community-${communityVersion}-1.x86_64.rpm** (for x86_64 (amd64) systems)
   * **metricshub-community-${communityVersion}-1.aarch64.rpm** (for aarch64 (arm64) systems)

2. Copy the package into `/usr/local`
3. Run the following command to stop **MetricsHub Community**:

   ```shell-session
   systemctl stop metricshub-community-service
   ```

4. Run the following `rpm` command:

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
