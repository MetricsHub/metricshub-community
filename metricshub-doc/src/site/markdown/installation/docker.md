keywords: install, docker, container, enterprise, community
description: How to install MetricsHub on Docker.

# Installing on Docker

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

This guide covers running MetricsHub as a Docker container. You can use Docker directly or Docker Compose.

## Pulling the Image

**Community** (from Docker Hub):

```bash
docker pull metricshub/metricshub-community:${communityVersion}
```

**Enterprise** (requires registry login with credentials from your onboarding email):

```bash
docker login docker.metricshub.com
docker pull docker.metricshub.com/metricshub-enterprise:${enterpriseVersion}
```

## Preparing the Configuration

Create directories and download example configuration:

```bash
mkdir -p /opt/metricshub/{logs,config}
cd /opt/metricshub
wget -O ./config/metricshub.yaml https://metricshub.com/docs/latest/resources/config/linux/metricshub-example.yaml
```

**Enterprise only** — also create the OTel Collector config:

```bash
mkdir -p /opt/metricshub/otel
wget -O ./otel/otel-config.yaml https://metricshub.com/docs/latest/resources/config/otel/otel-config-example.yaml
```

Set permissions (container runs as UID 1000):

```bash
chown -R 1000:1000 /opt/metricshub && chmod -R 775 /opt/metricshub
```

## Starting the Container

**Community:**

```bash
cd /opt/metricshub && docker run -d \
  --name=metricshub-community \
  -v $(pwd)/config:/opt/metricshub/lib/config \
  -v $(pwd)/logs:/opt/metricshub/lib/logs \
  metricshub/metricshub-community:${communityVersion}
```

**Enterprise:**

```bash
cd /opt/metricshub && docker run -d \
  --name=metricshub-enterprise \
  -p 24375:24375 -p 13133:13133 \
  -v $(pwd)/config:/opt/metricshub/lib/config \
  -v $(pwd)/otel/otel-config.yaml:/opt/metricshub/lib/otel/otel-config.yaml \
  -v $(pwd)/logs:/opt/metricshub/lib/logs \
  --hostname=localhost \
  docker.metricshub.com/metricshub-enterprise:${enterpriseVersion}
```

## Next Steps

Once running, you're ready to configure MetricsHub:

1. [Configure your resources](../configuration/configure-monitoring.html) — define what to monitor
2. [Set up telemetry export](../configuration/send-telemetry.html) — choose where to send metrics
3. [Access the Web UI](../operating-web-ui.html) — manage and monitor from your browser

## Docker Compose

For easier management, you can use Docker Compose. Create a `docker-compose.yaml` file in `/opt/metricshub`:

**Community:**

```yaml
services:
  metricshub:
    image: metricshub/metricshub-community:${communityVersion}
    container_name: metricshub-community
    volumes:
      - ./logs:/opt/metricshub/lib/logs
      - ./config:/opt/metricshub/lib/config
    restart: unless-stopped
```

**Enterprise:**

```yaml
services:
  metricshub:
    image: docker.metricshub.com/metricshub-enterprise:${enterpriseVersion}
    container_name: metricshub-enterprise
    hostname: localhost
    ports:
      - 13133:13133 # OTel Collector HealthCheck
      - 24375:24375 # OTel Collector Prometheus Exporter
    volumes:
      - ./logs:/opt/metricshub/lib/logs
      - ./config:/opt/metricshub/lib/config
      - ./otel/otel-config.yaml:/opt/metricshub/lib/otel/otel-config.yaml
    restart: unless-stopped
```

Start with `docker compose up -d`.

## Managing Containers

Use these commands to manage your MetricsHub container:

```bash
docker stop metricshub-community   # or metricshub-enterprise
docker rm metricshub-community     # or metricshub-enterprise
docker logs metricshub-community   # or metricshub-enterprise
```

## Upgrading

To upgrade to a newer version:

1. Stop and remove the existing container:
   ```bash
   docker stop metricshub-community && docker rm metricshub-community
   ```
2. Pull the latest image:
   ```bash
   docker pull metricshub/metricshub-community:${communityVersion}
   ```
3. Start with your existing configuration (same command as initial start)

Your configuration files are preserved in the mounted volumes.
