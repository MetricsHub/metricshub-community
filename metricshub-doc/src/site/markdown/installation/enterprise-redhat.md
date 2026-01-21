keywords: install, enterprise, redhat, rhel, centos, linux
description: How to install MetricsHub Enterprise on Red Hat Enterprise Linux.

# Installing on Red Hat Linux (Enterprise)

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

> **Supported versions:** Red Hat Enterprise Linux / CentOS 8 and higher

## Installing

1. Download the package for your architecture from [metricshub.com/downloads](https://metricshub.com/downloads):
   - **metricshub-enterprise-${enterpriseVersion}-1.x86_64.rpm** (x86_64)
   - **metricshub-enterprise-${enterpriseVersion}-1.aarch64.rpm** (aarch64)

2. Copy the package to `/usr/local`

3. Install with `rpm`:

   ```shell-session
   cd /usr/local
   sudo rpm -i metricshub-enterprise-${enterpriseVersion}-1.x86_64.rpm
   ```

   For aarch64 systems, use the `.aarch64.rpm` package instead.

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

### Running with a Custom Configuration

To start MetricsHub with an alternate configuration file:

```shell-session
cd /opt/metricshub/bin
./enterprise-service --config=config/my-metricshub-config.yaml
```

## Uninstalling

For x86_64 systems:

```shell-session
sudo rpm -e metricshub-${enterpriseVersion}-1.x86_64
```

For aarch64 systems:

```shell-session
sudo rpm -e metricshub-${enterpriseVersion}-1.aarch64
```

## Upgrading

1. Download the latest package from [metricshub.com/downloads](https://metricshub.com/downloads)

2. Stop the service:

   ```shell-session
   systemctl stop metricshub-enterprise-service
   ```

3. Upgrade with `rpm`:

   ```shell-session
   cd /usr/local
   sudo rpm -U metricshub-enterprise-${enterpriseVersion}-1.x86_64.rpm
   ```
