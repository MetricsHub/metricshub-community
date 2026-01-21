keywords: install, community, debian, linux
description: How to install MetricsHub Community on Debian Linux.

# Installing on Debian Linux (Community)

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

> **Supported versions:** Debian 10 and higher

## Installing

### Automatic Install (Recommended)

Run the official installation script:

```shell-session
curl -fsSL https://get.metricshub.com | bash
```

Verify the installation:

```shell-session
$HOME/metricshub/bin/metricshub --version
```

### Manual Install

1. Download the package for your architecture from [metricshub.com/downloads](https://metricshub.com/downloads):
   - **metricshub-community_${communityVersion}_amd64.deb** (x86_64)
   - **metricshub-community_${communityVersion}_arm64.deb** (aarch64)

2. Copy the package to `/usr/local`

3. Install with `dpkg`:

   ```shell-session
   cd /usr/local
   sudo dpkg -i metricshub-community_${communityVersion}_amd64.deb
   ```

   For arm64 systems, use the `_arm64.deb` package instead.

When complete:
- Files are deployed to `/opt/metricshub`
- The **MetricsHub Community Agent** service is started automatically

## Next Steps

1. [Configure your resources](../configuration/configure-monitoring.html)
2. [Configure OTLP export](../configuration/send-telemetry.html)

## Managing the Service

### Starting the Service

```shell-session
systemctl start metricshub-community-service
```

### Stopping the Service

```shell-session
systemctl stop metricshub-community-service
```

## Uninstalling

```shell-session
sudo dpkg -r metricshub
```

## Upgrading

1. Download the latest package from [metricshub.com/downloads](https://metricshub.com/downloads)

2. Stop the service:

   ```shell-session
   systemctl stop metricshub-community-service
   ```

3. Install the new package:

   ```shell-session
   cd /usr/local
   sudo dpkg -i metricshub-community_${communityVersion}_amd64.deb
   ```
