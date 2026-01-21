keywords: install, enterprise, docker, container
description: How to install MetricsHub Enterprise on Docker.

# Installing on Docker (Enterprise)

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

## Installing

1. Authenticate to the MetricsHub Docker registry using the credentials from your onboarding email:

   ```bash
   docker login docker.metricshub.com
   ```

2. Pull the latest image:

   ```bash
   docker pull docker.metricshub.com/metricshub-enterprise:${enterpriseVersion}
   ```

3. Create the local directories for configuration and logs:

   ```bash
   mkdir -p /opt/metricshub/{logs,config,otel}
   ```

4. Download the example configuration files:

   ```bash
   cd /opt/metricshub
   wget -O ./otel/otel-config.yaml https://metricshub.com/docs/latest/resources/config/otel/otel-config-example.yaml
   wget -O ./config/metricshub.yaml https://metricshub.com/docs/latest/resources/config/linux/metricshub-example.yaml
   ```

5. Set permissions (the container runs as UID 1000):

   ```bash
   chown -R 1000:1000 /opt/metricshub && chmod -R 775 /opt/metricshub
   ```

## Next Steps

1. [Configure your resources](../configuration/configure-monitoring.html)
2. [Set up telemetry export](../configuration/send-telemetry.html)

## Starting the Container

### Using Docker Run

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

### Using Docker Compose

Create a `docker-compose.yaml` file in `/opt/metricshub`:

```yaml
services:
  metricshub:
    image: docker.metricshub.com/metricshub-enterprise:${enterpriseVersion}
    container_name: metricshub-enterprise
    hostname: localhost
    ports:
      - 13133:13133    # OpenTelemetry Collector HealthCheck
      - 24375:24375    # OpenTelemetry Collector Prometheus Exporter
    volumes:
      - ./logs:/opt/metricshub/lib/logs
      - ./config:/opt/metricshub/lib/config
      - ./otel/otel-config.yaml:/opt/metricshub/lib/otel/otel-config.yaml
    restart: unless-stopped
```

Start with:

```bash
docker compose up -d
```

## Stopping the Container

```bash
docker stop metricshub-enterprise
```

## Removing the Container

```bash
docker rm metricshub-enterprise
```

## Upgrading

1. Stop and remove the existing container:

   ```bash
   docker stop metricshub-enterprise
   docker rm metricshub-enterprise
   ```

2. Pull the latest image:

   ```bash
   docker pull docker.metricshub.com/metricshub-enterprise:${enterpriseVersion}
   ```

3. Restart with your existing configuration:

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
