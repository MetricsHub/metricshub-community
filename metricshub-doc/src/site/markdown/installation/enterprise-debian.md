keywords: install, enterprise, debian, linux
description: How to install MetricsHub Enterprise on Debian Linux.

# Installing on Debian Linux (Enterprise)

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

> **Supported versions:** Debian 10 and higher

## Installing

1. Download the package for your architecture from [metricshub.com/downloads](https://metricshub.com/downloads):
   - **metricshub-enterprise_${enterpriseVersion}_amd64.deb** (x86_64)
   - **metricshub-enterprise_${enterpriseVersion}_arm64.deb** (aarch64)

2. Copy the package to `/usr/local`

3. Install with `dpkg`:

   ```shell-session
   cd /usr/local
   sudo dpkg -i metricshub-enterprise_${enterpriseVersion}_amd64.deb
   ```

   For arm64 systems, use the `_arm64.deb` package instead.

When complete:
- Files are deployed to `/opt/metricshub`
- The **MetricsHub Enterprise Agent** service is started automatically

## Next Steps

1. [Configure your resources](../configuration/configure-monitoring.html)
2. [Set up telemetry export](../configuration/send-telemetry.html)
3. [Access the Web UI](../operating-web-ui.html)

## Managing the Service

### Starting the Service

```shell-session
systemctl start metricshub-enterprise-service
```

### Stopping the Service

```shell-session
systemctl stop metricshub-enterprise-service
```

## Uninstalling

```shell-session
sudo dpkg -r metricshub
```

## Upgrading

1. Download the latest package from [metricshub.com/downloads](https://metricshub.com/downloads)

2. Stop the service:

   ```shell-session
   systemctl stop metricshub-enterprise-service
   ```

3. Install the new package:

   ```shell-session
   cd /usr/local
   sudo dpkg -i metricshub-enterprise_${enterpriseVersion}_amd64.deb
   ```
