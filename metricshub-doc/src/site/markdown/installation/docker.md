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

> **Note:** The container runs as a non-root user with UID `1000` (`metricshub`). To avoid permission issues, make sure the container has access to the directories by updating ownership and permissions:

```bash
chown -R 1000:1000 /opt/metricshub && chmod -R 775 /opt/metricshub
```

Next, download the example configuration files to help you get started:

```shell-session
cd /opt/metricshub

wget -O ./otel/otel-config.yaml https://metricshub.com/docs/latest/resources/config/otel/otel-config-example.yaml
wget -O ./config/metricshub.yaml https://metricshub.com/docs/latest/resources/config/linux/metricshub-example.yaml
```

* In the **./config/metricshub.yaml** file, configure the [resources to be monitored](../configuration/configure-monitoring.html#configure-resources).
* In the **./otel/otel-config.yaml** file, specify where the _OpenTelemetry Collector_ should [send the collected data](../configuration/send-telemetry.html#configure-the-otel-collector-28enterprise-edition-29).

### Start

To start **MetricsHub Enterprise** using the local configuration files, run the following command from **/opt/metricshub** directory:

```bash
# Run docker using local configuration files as volumes
docker run -d \
  --name=metricshub-enterprise \
  -p 24375:24375 -p 13133:13133 \
  -v $(pwd)/config/metricshub.yaml:/opt/metricshub/lib/config/metricshub.yaml \
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
      - 13133:13133                                                                      # OpenTelemetry Collector HealthCheck
      - 24375:24375                                                                      # OpenTelemetry Collector Prometheus Exporter
    volumes:
      - ./logs:/opt/metricshub/lib/logs                                                  # Mount the volume ./lib/logs into /opt/metricshub/lib/logs in the container
      - ./config/metricshub.yaml:/opt/metricshub/lib/config/metricshub.yaml              # Inject local config/metricshub.yaml into the container
      - ./otel/otel-config.yaml:/opt/metricshub/lib/otel/otel-config.yaml                # Inject local otel/otel-config.yaml into the container
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
     -v $(pwd)/config/metricshub.yaml:/opt/metricshub/lib/config/metricshub.yaml \
     -v $(pwd)/otel/otel-config.yaml:/opt/metricshub/lib/otel/otel-config.yaml \
     -v $(pwd)/logs:/opt/metricshub/lib/logs \
     --hostname=localhost \
     docker.metricshub.com/metricshub-enterprise:${enterpriseVersion}
   ```

## Community Edition

### Download

Download the Docker package, `metricshub-linux-${communityVersion}-docker.tar.gz`, from the [MetricsHub Release v${communityVersion}](https://github.com/metricshub/metricshub-community/releases/tag/v${communityVersion}) page using the following command:

```shell-session
wget -P /tmp https://github.com/metricshub/metricshub-community/releases/download/v${communityVersion}/metricshub-linux-${communityVersion}-docker.tar.gz
```

### Install

Unzip and untar the content of `metricshub-linux-${communityVersion}-docker.tar.gz` into a directory, like `/docker`.

```shell-session
sudo mkdir -p /docker
sudo tar xzf /tmp/metricshub-linux-${communityVersion}-docker.tar.gz -C /docker
```

### Configure

In the `./lib/config/metricshub.yaml` file, located under the `./metricshub` installation directory, configure:

* the [resources to be monitored.](../configuration/configure-monitoring.md#configure-resources)
* the [OpenTelemetry Protocol endpoint](../configuration/send-telemetry.html#configure-the-otlp-exporter-28community-edition-29) that will receive the MetricsHub signals.

To assist with the setup process, a configuration example `./lib/config/metricshub-example.yaml` is provided in the installation directory (`./metricshub`).

### Build the docker image

Run the following command to build the docker image:

```shell-session
cd /docker/metricshub
sudo docker build -t metricshub:latest .
```

### Start

Run the following command to start **MetricsHub** with the default configuration file, `./lib/config/metricshub.yaml`:

```shell-session
cd /docker/metricshub
sudo docker run -d --name=metricshub metricshub:latest
```

You can start **MetricsHub** with an alternate configuration file with the following command:

```shell-session
cd /docker/metricshub
sudo docker run -d --name=metricshub -v /docker/metricshub/lib/config:/opt/metricshub/lib/config -v /docker/metricshub/lib/logs:/opt/metricshub/lib/logs metricshub:latest
```

**Docker Compose Example**

You can start **MetricsHub** with docker compose:

```shell-session
sudo docker compose up -d --build
```

Example (`docker-compose.yaml`):

```yaml
version: "2.1"
services:
  metricshub:
    # for image we will use ``image: metricshub/metricshub-community:latest``
    build: .
    container_name: metricshub
    volumes:
      # Mount the volume ./lib/logs into /opt/metricshub/lib/logs in the container
      - ./lib/logs:/opt/metricshub/lib/logs
      # Mount the volume ./lib/config into /opt/metricshub/lib/config in the container
      - ./lib/config:/opt/metricshub/lib/config
    restart: unless-stopped
```

### Stop

Adjust the below commands to meet your specific requirements for stopping and removing the Docker container running **MetricsHub**.

If:

* **MetricsHub** is started as a docker container, run:

    ```shell-session
    sudo docker stop metricshub
    ```

* you are using **Docker Compose** from the `./metricshub` directory, run:

  ```shell-session
  sudo docker compose down
  ```

### Uninstall

To force-stop and remove the **MetricsHub** container, run the following commands:

```shell-session
cd /docker/metricshub
sudo docker stop -f metricshub
sudo docker rm -f metricshub
```
