keywords: install, community, redhat, rhel, centos, linux
description: How to install MetricsHub Community on Red Hat Enterprise Linux.

# Installing on Red Hat Linux (Community)

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

> **Supported versions:** Red Hat Enterprise Linux / CentOS 8 and higher

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
   - **metricshub-community-${communityVersion}-1.x86_64.rpm** (x86_64)
   - **metricshub-community-${communityVersion}-1.aarch64.rpm** (aarch64)

2. Copy the package to `/usr/local`

3. Install with `rpm`:

   ```shell-session
   cd /usr/local
   sudo rpm -i metricshub-community-${communityVersion}-1.x86_64.rpm
   ```

   For aarch64 systems, use the `.aarch64.rpm` package instead.

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

For x86_64 systems:

```shell-session
sudo rpm -e metricshub-${communityVersion}-1.x86_64
```

For aarch64 systems:

```shell-session
sudo rpm -e metricshub-${communityVersion}-1.aarch64
```

## Upgrading

1. Download the latest package from [metricshub.com/downloads](https://metricshub.com/downloads)

2. Stop the service:

   ```shell-session
   systemctl stop metricshub-community-service
   ```

3. Upgrade with `rpm`:

   ```shell-session
   cd /usr/local
   sudo rpm -U metricshub-community-${communityVersion}-1.x86_64.rpm
   ```
