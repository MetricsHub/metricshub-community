keywords: install, enterprise, community
description: How to install MetricsHub on Debian Linux.

# Installation

<!-- MACRO{toc|fromDepth=1|toDepth=1|id=toc} -->

   > MetricsHub Community and MetricsHub Enterprise support Debian v10.

## Enterprise Edition

### Install

To install **MetricsHub Enterprise** on Debian Linux:

1. Download from [MetricsHub's Web site](https://metricshub.com/downloads) the package corresponding to your system architecture:

   * **metricshub-enterprise_${enterpriseVersion}_amd64.deb** (for amd64 (x86_64) systems)
   * **metricshub-enterprise_${enterpriseVersion}_arm64.deb** (for arm64 (aarch64) systems)
  
2. Copy the package into `/usr/local`
3. Run the following `dpkg` command:

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

### Uninstall

To uninstall **MetricsHub Enterprise**, run the following command:

```shell-session
sudo dpkg -r metricshub
```

### Upgrade

To upgrade to the latest version:

1. Download from [MetricsHub's Web site](https://metricshub.com/downloads) the package corresponding to your system architecture:

   * **metricshub-enterprise_${enterpriseVersion}_amd64.deb** (for amd64 (x86_64) systems)
   * **metricshub-enterprise_${enterpriseVersion}_arm64.deb** (for arm64 (aarch64) systems)

2. Run the following command to stop **MetricsHub Enterprise**:

   ```shell-session
   systemctl stop metricshub-enterprise-service
   ```

3. Run the following `dpkg` command:

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

To install **MetricsHub Community**, you can either:

* run the official installation script (**recommended**)
* or download and manually install the `metricshub-community_${communityVersion}_{arch}.deb` package.

### Automatic Install (Recommended)

First, run the following command to install **MetricsHub Community**:

```shell-session
curl -fsSL https://get.metricshub.com | bash
```

Then, run the following command to ensure that the product has been successfully installed:

```shell-session
${esc.d}HOME/metricshub/bin/metricshub --version
```

### Manual Install

To manually install **MetricsHub Community**:

1. From [MetricsHub's Web site](https://metricshub.com/downloads), download the package corresponding to your system architecture:

   * **metricshub-community_${communityVersion}_amd64.deb** (for amd64 (x86_64) systems)
   * **metricshub-community_${communityVersion}_arm64.deb** (for arm64 (aarch64) systems)

2. Copy the package into `/usr/local`
3. Run the following `dpkg` command:

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

### Uninstall

To uninstall **MetricsHub Community**, run the following command:

```shell-session
sudo dpkg -r metricshub
```

### Upgrade

To upgrade to the latest version:

1. From [MetricsHub's Web site](https://metricshub.com/downloads), download the the package corresponding to your system architecture:
   
   * **metricshub-community_${communityVersion}_amd64.deb** (for amd64 (x86_64) systems)
   * **metricshub-community_${communityVersion}_arm64.deb** (for arm64 (aarch64) systems)

2. Run the following command to stop **MetricsHub Community**:

   ```shell-session
   systemctl stop metricshub-community-service
   ```

3. Run the following `dpkg` command:

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
