keywords: install, linux, debian, redhat, rhel, centos, ubuntu, enterprise, community
description: How to install MetricsHub on Linux (Debian, Ubuntu, Red Hat, CentOS).

# Installing on Linux

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

This guide covers installing MetricsHub on Debian-based and Red Hat-based Linux distributions.

> **Supported versions:** Debian 10+, Ubuntu 20.04+, RHEL/CentOS 8+

## Automatic Install (Community Only)

The fastest way to get started with MetricsHub Community is the one-line installer script, which works on any supported distribution:

```shell-session
curl -fsSL https://get.metricshub.com | bash
```

Once complete, verify the installation:

```shell-session
$HOME/metricshub/bin/metricshub --version
```

## Manual Install (Community & Enterprise)

If you prefer manual control over the installation, download the appropriate package from [metricshub.com/downloads](https://metricshub.com/downloads) and follow the instructions for your edition and platform.

### Enterprise

**Debian / Ubuntu (x86_64):**

```shell-session
sudo dpkg -i metricshub-enterprise_${enterpriseVersion}_amd64.deb
```

**Debian / Ubuntu (ARM64):**

```shell-session
sudo dpkg -i metricshub-enterprise_${enterpriseVersion}_arm64.deb
```

**Red Hat / CentOS (x86_64):**

```shell-session
sudo rpm -i metricshub-enterprise-${enterpriseVersion}-1.x86_64.rpm
```

**Red Hat / CentOS (ARM64):**

```shell-session
sudo rpm -i metricshub-enterprise-${enterpriseVersion}-1.aarch64.rpm
```

### Community

**Debian / Ubuntu (x86_64):**

```shell-session
sudo dpkg -i metricshub-community_${communityVersion}_amd64.deb
```

**Debian / Ubuntu (ARM64):**

```shell-session
sudo dpkg -i metricshub-community_${communityVersion}_arm64.deb
```

**Red Hat / CentOS (x86_64):**

```shell-session
sudo rpm -i metricshub-community-${communityVersion}-1.x86_64.rpm
```

**Red Hat / CentOS (ARM64):**

```shell-session
sudo rpm -i metricshub-community-${communityVersion}-1.aarch64.rpm
```

When complete, files are deployed to `/opt/metricshub` and the service starts automatically.

## Next Steps

Once installed, you're ready to configure MetricsHub:

1. [Configure your resources](../configuration/configure-monitoring.html) — define what to monitor
2. [Set up telemetry export](../configuration/send-telemetry.html) — choose where to send metrics
3. [Access the Web UI](../operating-web-ui.html) — manage and monitor from your browser

## Managing the Service

MetricsHub runs as a systemd service. Use these commands to control it:

```shell-session
# Start the service
systemctl start metricshub-enterprise-service   # Enterprise
systemctl start metricshub-community-service    # Community

# Stop the service
systemctl stop metricshub-enterprise-service    # Enterprise
systemctl stop metricshub-community-service     # Community
```

## Uninstalling

To remove MetricsHub from your system:

**Debian / Ubuntu:**

```shell-session
sudo dpkg -r metricshub
```

**Red Hat / CentOS:**

```shell-session
sudo rpm -e metricshub-${enterpriseVersion}-1.x86_64    # x86_64
sudo rpm -e metricshub-${enterpriseVersion}-1.aarch64   # ARM64
```

## Upgrading

To upgrade to a newer version:

1. Download the latest package from [metricshub.com/downloads](https://metricshub.com/downloads)
2. Stop the service
3. Install the new package using `dpkg -i` (Debian/Ubuntu) or `rpm -U` (Red Hat/CentOS)

Your configuration files are preserved during the upgrade.
