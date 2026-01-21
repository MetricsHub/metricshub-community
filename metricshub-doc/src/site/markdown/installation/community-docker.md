keywords: install, community, docker, container
description: How to install MetricsHub Community on Docker.

# Installing on Docker (Community)

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

## Installing

1. Pull the latest image from Docker Hub:

   ```bash
   docker pull metricshub/metricshub-community:${communityVersion}
   ```

2. Create the local directories for configuration and logs:

   ```bash
   mkdir -p /opt/metricshub/{logs,config}
   ```

3. Download the example configuration file:

   ```bash
   cd /opt/metricshub
   wget -O ./config/metricshub.yaml https://metricshub.com/docs/latest/resources/config/linux/metricshub-example.yaml
   ```

4. Set permissions (the container runs as UID 1000):

   ```bash
   chown -R 1000:1000 /opt/metricshub && chmod -R 775 /opt/metricshub
   ```

## Next Steps

1. [Configure your resources](../configuration/configure-monitoring.html)
2. [Configure OTLP export](../configuration/send-telemetry.html)

## Starting the Container

### Using Docker Run

```bash
cd /opt/metricshub && docker run -d \
  --name=metricshub-community \
  -v $(pwd)/config:/opt/metricshub/lib/config \
  -v $(pwd)/logs:/opt/metricshub/lib/logs \
  metricshub/metricshub-community:${communityVersion}
```

### Using Docker Compose

Create a `docker-compose.yaml` file in `/opt/metricshub`:

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

Start with:

```bash
docker compose up -d
```

## Stopping the Container

```bash
docker stop metricshub-community
```

## Removing the Container

```bash
docker rm metricshub-community
```

## Upgrading

1. Stop and remove the existing container:

   ```bash
   docker stop metricshub-community
   docker rm metricshub-community
   ```

2. Pull the latest image:

   ```bash
   docker pull metricshub/metricshub-community:${communityVersion}
   ```

3. Restart with your existing configuration:

   ```bash
   cd /opt/metricshub && docker run -d \
     --name=metricshub-community \
     -v $(pwd)/config:/opt/metricshub/lib/config \
     -v $(pwd)/logs:/opt/metricshub/lib/logs \
     metricshub/metricshub-community:${communityVersion}
   ```
