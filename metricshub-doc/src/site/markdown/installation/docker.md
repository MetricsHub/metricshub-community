keywords: install, upgrade, firewalls
description: How to install MetricsHub on Docker.

# Installation

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

## MetricsHub Enterprise

### Install MetricsHub Enterprise

1. Authenticate to the MetricsHub Docker registry using the credentials provided in your onboarding email:

    ```bash
    docker login docker.metricshub.com
    ```

2. Once logged in, download the latest **MetricsHub Enterprise** image:

    ```bash
      docker pull docker.metricshub.com/metricshub-enterprise:${enterpriseVersion}
    ```

3. Create the required local directories for configuration and logs:

    ```bash
    mkdir -p /opt/metricshub/{logs,config,otel}
    ```

4. Download the example configuration files to help you get started:

    ```shell-session
    cd /opt/metricshub

    wget -O ./otel/otel-config.yaml https://metricshub.com/docs/latest/resources/config/otel/otel-config-example.yaml
    wget -O ./config/metricshub.yaml https://metricshub.com/docs/latest/resources/config/linux/metricshub-example.yaml
     ```

 > **IMPORTANT:** The container runs as a non-root user with UID `1000` (`metricshub`). To avoid permission issues, make sure the container has access to the directories by updating ownership and permissions:

 ```bash
 chown -R 1000:1000 /opt/metricshub && chmod -R 775 /opt/metricshub
 ```

### Start MetricsHub Enterprise

To start **MetricsHub Enterprise** using the local configuration files, run the following command from **/opt/metricshub** directory:

```bash
# Run docker using local configuration files as volumes
cd /opt/metricshub && docker run -d \
  --name=metricshub-enterprise \
  -p 24375:24375 -p 13133:13133 -p 31888:31888 \
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
      - 31888:31888                                                        # MetricsHub Web UI
      - 13133:13133                                                        # OpenTelemetry Collector HealthCheck
      - 24375:24375                                                        # OpenTelemetry Collector Prometheus Exporter
    volumes:
      - ./logs:/opt/metricshub/lib/logs                                    # Mount the volume ./logs into /opt/metricshub/lib/logs in the container
      - ./config:/opt/metricshub/lib/config                                # Inject the local ./config directory into the container
      - ./otel/otel-config.yaml:/opt/metricshub/lib/otel/otel-config.yaml  # Inject local ./otel/otel-config.yaml into the container
    restart: unless-stopped
```

### Stop MetricsHub Enterprise

To stop **MetricsHub Enterprise**, run the following command:

```bash
docker stop metricshub-enterprise
```

### Remove MetricsHub Enterprise

To remove **MetricsHub Enterprise**, run the following command:

```bash
docker rm metricshub-enterprise
```

### Upgrade MetricsHub Enterprise

To upgrade to the latest version:

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
     -p 24375:24375 -p 13133:13133 -p 31888:31888 \
     -v $(pwd)/config:/opt/metricshub/lib/config \
     -v $(pwd)/otel/otel-config.yaml:/opt/metricshub/lib/otel/otel-config.yaml \
     -v $(pwd)/logs:/opt/metricshub/lib/logs \
     --hostname=localhost \
     docker.metricshub.com/metricshub-enterprise:${enterpriseVersion}
   ```

## MetricsHub Community

### Install MetricsHub Community

To install **MetricsHub Community**:

1. Download the latest **MetricsHub Community** image from Docker Hub:

    ```bash
    docker pull metricshub/metricshub-community:${communityVersion}
    ```

2. Create the required local directories for configuration and logs:

    ```bash
    mkdir -p /opt/metricshub/{logs,config}
    ```

3. Download the example configuration file to help you get started:

    ```shell-session
      cd /opt/metricshub

      wget -O ./config/metricshub.yaml https://metricshub.com/docs/latest/resources/config/linux/metricshub-example.yaml
    ```

    > **IMPORTANT:** The container runs as a non-root user with UID `1000` (`metricshub`). To avoid permission issues, make sure the container has access to the directories by updating ownership and permissions:

    ```bash
    chown -R 1000:1000 /opt/metricshub && chmod -R 775 /opt/metricshub
    ```

### Start MetricsHub Community

To start **MetricsHub Community** using the local configuration files, run the following command from **/opt/metricshub** directory:

```bash
# Run docker using local configuration files as volumes
cd /opt/metricshub && docker run -d \
  --name=metricshub-community \
  -p 31888:31888 \
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
    ports:
      - 31888:31888                                                        # MetricsHub Web UI
    volumes:
      - ./logs:/opt/metricshub/lib/logs                                    # Mount the volume ./logs into /opt/metricshub/lib/logs in the container
      - ./config:/opt/metricshub/lib/config                                # Inject the local ./config directory into the container
    restart: unless-stopped
```

### Stop MetricsHub Community

To stop MetricsHub Community, run the following command:

```bash
docker stop metricshub-community
```

### Remove MetricsHub Community

To remove MetricsHub Community, run the following command:

```bash
docker rm metricshub-community
```

### Upgrade MetricsHub Community

To upgrade to the latest version:

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
     -p 31888:31888 \
     -v $(pwd)/config:/opt/metricshub/lib/config \
     -v $(pwd)/logs:/opt/metricshub/lib/logs \
     metricshub/metricshub-community:${communityVersion}
   ```
