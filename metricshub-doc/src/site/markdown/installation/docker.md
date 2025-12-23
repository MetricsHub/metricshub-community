keywords: install, upgrade, firewalls
description: How to install MetricsHub on Linux, Windows, and Docker.

# Installation

<!-- MACRO{toc|fromDepth=1|toDepth=1|id=toc} -->

## Enterprise Edition

### Download

First, authenticate to the MetricsHub Docker registry using the credentials provided in your onboarding email:

```bash
docker login docker.metricshub.com
```

Once logged in, download the latest **MetricsHub Enterprise** image:

```bash
docker pull docker.metricshub.com/metricshub-enterprise:${enterpriseVersion}
```

### Configure

Create the required local directories for configuration and logs:

```bash
mkdir -p /opt/metricshub/{logs,config,otel}
```

Next, download the example configuration files to help you get started:

```shell-session
cd /opt/metricshub

wget -O ./otel/otel-config.yaml https://metricshub.com/docs/latest/resources/config/otel/otel-config-example.yaml
wget -O ./config/metricshub.yaml https://metricshub.com/docs/latest/resources/config/linux/metricshub-example.yaml
```

* [Structure your configuration](../configuration/configure-monitoring.md#step-1-structure-your-configuration) by creating either one single or multiple configuration file
* [Configure your resource groups](../configuration/configure-monitoring.md#step-2-configure-resource-groups) and [resources to be monitored.](../configuration/configure-monitoring.md#step-3-configure-resources)
* In the **./otel/otel-config.yaml** file, specify where the _OpenTelemetry Collector_ should [send the collected data](../configuration/send-telemetry.html#configure-the-otel-collector-28enterprise-edition-29).

> **Note:** The container runs as a non-root user with UID `1000` (`metricshub`). To avoid permission issues, make sure the container has access to the directories by updating ownership and permissions:

```bash
chown -R 1000:1000 /opt/metricshub && chmod -R 775 /opt/metricshub
```

### Start

To start **MetricsHub Enterprise** using the local configuration files, run the following command from **/opt/metricshub** directory:

```bash
# Run docker using local configuration files as volumes
cd /opt/metricshub && docker run -d \
  --name=metricshub-enterprise \
  -p 24375:24375 -p 13133:13133 \
  -v $(pwd)/config:/opt/metricshub/lib/config \
  -v $(pwd)/otel/otel-config.yaml:/opt/metricshub/lib/otel/otel-config.yaml \
  -v $(pwd)/logs:/opt/metricshub/lib/logs \
  --hostname=localhost \
  docker.metricshub.com/metricshub-enterprise:${enterpriseVersion}
```

**Docker Compose Example**

Alternatively, you can launch **MetricsHub Enterprise** using Docker Compose:

```shell-session
sudo docker compose up -d
```

Here’s an example of docker-compose.yaml file located under **/opt/metricshub**:

```yaml
services:
  metricshub:
    image: docker.metricshub.com/metricshub-enterprise:${enterpriseVersion}
    container_name: metricshub-enterprise
    hostname: localhost
    ports:
      - 13133:13133                                                        # OpenTelemetry Collector HealthCheck
      - 24375:24375                                                        # OpenTelemetry Collector Prometheus Exporter
    volumes:
      - ./logs:/opt/metricshub/lib/logs                                    # Mount the volume ./logs into /opt/metricshub/lib/logs in the container
      - ./config:/opt/metricshub/lib/config                                # Inject the local ./config directory into the container
      - ./otel/otel-config.yaml:/opt/metricshub/lib/otel/otel-config.yaml  # Inject local ./otel/otel-config.yaml into the container
    restart: unless-stopped
```

### Stop

To stop the container, run:

```bash
docker stop metricshub-enterprise
```

### Remove

To remove the container, run:

```bash
docker rm metricshub-enterprise
```

### Upgrade

To upgrade to a newer version of **MetricsHub Enterprise**:

1. **Stop and remove** the existing container:

   ```bash
   docker stop metricshub-enterprise
   docker rm metricshub-enterprise
   ```

2. **Pull the latest image**:

   ```bash
   docker pull docker.metricshub.com/metricshub-enterprise:${enterpriseVersion}
   ```

3. **Restart the container** with your existing configuration and volume mounts:

   ```bash
   cd /opt/metricshub

   docker run -d \
     --name=metricshub-enterprise \
     -p 24375:24375 -p 13133:13133 \
     -v $(pwd)/config:/opt/metricshub/lib/config \
     -v $(pwd)/otel/otel-config.yaml:/opt/metricshub/lib/otel/otel-config.yaml \
     -v $(pwd)/logs:/opt/metricshub/lib/logs \
     --hostname=localhost \
     docker.metricshub.com/metricshub-enterprise:${enterpriseVersion}
   ```

## Community Edition

### Download

Download the latest **MetricsHub Community** image from Docker Hub:

```bash
docker pull metricshub/metricshub-community:${communityVersion}
```

### Configure

Create the required local directories for configuration and logs:

```bash
mkdir -p /opt/metricshub/{logs,config}
```

Next, download the example configuration file to help you get started:

```shell-session
cd /opt/metricshub

wget -O ./config/metricshub.yaml https://metricshub.com/docs/latest/resources/config/linux/metricshub-example.yaml
```

1. [structure your configuration](../configuration/configure-monitoring.md#step-1-structure-your-configuration) by creating either one single or multiple configuration file(s)
2. [configure your resource groups](../configuration/configure-monitoring.md#step-2-configure-resource-groups) and [resources to be monitored.](../configuration/configure-monitoring.md#step-3-configure-resources)
3. [define the OpenTelemetry Protocol endpoint](../configuration/configure-monitoring.md#otlp-exporter-settings) that will receive the MetricsHub signals.

> **Note:** The container runs as a non-root user with UID `1000` (`metricshub`). To avoid permission issues, make sure the container has access to the directories by updating ownership and permissions:

```bash
chown -R 1000:1000 /opt/metricshub && chmod -R 775 /opt/metricshub
```

### Start

To start **MetricsHub Community** using the local configuration files, run the following command from **/opt/metricshub** directory:

```bash
# Run docker using local configuration files as volumes
cd /opt/metricshub && docker run -d \
  --name=metricshub-community \
  -v $(pwd)/config:/opt/metricshub/lib/config \
  -v $(pwd)/logs:/opt/metricshub/lib/logs \
  metricshub/metricshub-community:${communityVersion}
```

**Docker Compose Example**

Alternatively, you can launch **MetricsHub Community** using Docker Compose:

```shell-session
sudo docker compose up -d
```

Here’s an example of docker-compose.yaml file located under **/opt/metricshub**:

```yaml
services:
  metricshub:
    image: metricshub/metricshub-community:${communityVersion}
    container_name: metricshub-community
    volumes:
      - ./logs:/opt/metricshub/lib/logs                                    # Mount the volume ./logs into /opt/metricshub/lib/logs in the container
      - ./config:/opt/metricshub/lib/config                                # Inject the local ./config directory into the container
    restart: unless-stopped
```

### Stop

To stop the container, run:

```bash
docker stop metricshub-community
```

### Remove

To remove the container, run:

```bash
docker rm metricshub-community
```

### Upgrade

To upgrade to a newer version of **MetricsHub Community**:

1. **Stop and remove** the existing container:

   ```bash
   docker stop metricshub-community
   docker rm metricshub-community
   ```

2. **Pull the latest image**:

   ```bash
   docker pull metricshub/metricshub-community:${communityVersion}
   ```

3. **Restart the container** with your existing configuration and volume mounts:

   ```bash
   cd /opt/metricshub

   docker run -d \
     --name=metricshub-community \
     -v $(pwd)/config:/opt/metricshub/lib/config \
     -v $(pwd)/logs:/opt/metricshub/lib/logs \
     metricshub/metricshub-community:${communityVersion}
   ```

