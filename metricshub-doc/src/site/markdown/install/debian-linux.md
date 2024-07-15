keywords: install, enterprise, community
description: How to install MetricsHub on Debian Linux.

# Installation

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

## Enterprise Edition

### Download

From [MetricsHub's Web site](https://metricshub.com), download **metricshub-enterprise-debian-${enterpriseVersion}-amd64.deb** and copy into `/usr/local`.

#### Install

Once you have downloaded the Debian package, run the following `dpkg` command:

```shell-session
cd /usr/local
sudo dpkg -i metricshub-enterprise-debian-${enterpriseVersion}-amd64.deb
```

When complete, the **MetricsHub**'s files are deployed in `/opt/metricshub` and the **MetricsHub Enterprise Agent** is started as a service.

### Configure

* In the [**./lib/config/metricshub.yaml**](configuration/configure-agent.md) file, located under the `/opt/metricshub` installation directory, configure the [resources to be monitored](./configuration/configure-agent.html#configure-monitored-resources)
* In the [**./lib/otel/otel-config.yaml**](configuration/configure-otel.md) file, located under the `/opt/metricshub` installation directory, specify where the _OpenTelemetry Collector_ should send the collected data.

To assist with the setup process, two configuration examples are provided for guidance in the installation directory (`./metricshub`):

* `./lib/config/metricshub-config-example.yaml`, a configuration example of the MetricsHub agent.
* `./lib/otel/otel-config-example.yaml`, a configuration example of the OpenTelemetry Collector.

### Start

To start the **MetricsHub Enterprise** service, run the command below:

```shell-session
systemctl start metricshub-enterprise-service
```
This will start **MetricsHub** with the default **MetricsHub Enterprise Agent** configuration file, **./lib/config/metricshub.yaml**.

### Stop

To stop the **MetricsHub Enterprise** service, run the command below:

```shell-session
systemctl stop metricshub-enterprise-service
```

### Uninstall

To uninstall **MetricsHub Enterprise**, run the command below:

```shell-session
sudo dpkg -r metricshub-enterprise-debian-${enterpriseVersion}-amd64.deb
```

## Community Edition

### Download

Download the Linux package, `metricshub-linux-${communityVersion}.tar.gz`, from the [MetricsHub Release v${communityVersion}](https://github.com/sentrysoftware/metricshub/releases/tag/v${communityVersion}) page using the following command:

```shell-session
wget -P /tmp https://github.com/sentrysoftware/metricshub/releases/download/v${communityVersion}/metricshub-linux-${communityVersion}.tar.gz
```

### Install

Unzip and untar the content of `metricshub-linux-${communityVersion}.tar.gz` into a program directory, like `/opt`. There is no need to create a specific subdirectory for `metricshub` as the archive already contains a `metricshub` directory.

```shell-session
cd /opt
sudo tar xzf /tmp/metricshub-linux-${communityVersion}.tar.gz
```

### Configure

In the `./lib/config/metricshub.yaml` file, located under the `./metricshub` installation directory, configure:

* the [resources to be monitored](./configuration/configure-agent.html#configure-monitored-resources)
* the [OpenTelemetry Protocol endpoint](configuration/configure-agent.md#otlp-endpoint) that will receive the MetricsHub signals.

To assist with the setup process, the configuration example `./lib/config/metricshub-example.yaml` is provided for guidance in the installation directory (`./metricshub`).

### Start

To start **MetricsHub** in an interactive terminal with the default configuration file `./lib/config/metricshub.yaml`, run the command below:

```shell-session
cd /opt/metricshub/bin
./service
```

To start **MetricsHub** with an alternate configuration file, run the command below:

```shell-session
cd /opt/metricshub/bin
./service --config <PATH>
```

Example:

```shell-session
cd /opt/metricshub/bin
./service --config config/my-metricshub.yaml
```

To start **MetricsHub** as a **Linux service**, follow the steps below:

* **Create a systemd service file**

  Create a file (for example: `/etc/systemd/system/metricshub-service.service`) and define the **MetricsHub Service** configuration as follows:

  ```
  [Unit]
  Description=MetricsHub Service

  [Service]
  ExecStart=/opt/metricshub/bin/service
  Restart=on-failure

  [Install]
  WantedBy=multi-user.target
  ```
* **Reload systemd**

  After creating the Linux service file, reload `systemd` to recognize the new service.

  ```shell-session
  systemctl daemon-reload
  ```
* **Start the MetricsHub Service**

  ```shell-session
  systemctl start metricshub-service
  ```

  To enable the Linux service to start on boot, run the command below:

  ```shell-session
  systemctl enable metricshub-service
  ```

* **Check status**

  To verify that the **MetricsHub Service** is running without errors, run the command below:

  ```shell-session
  systemctl status metricshub-service
  ```

  This will give you information about the current status of your service.

### Stop

**Interactive Terminal**

To manually stop the **MetricsHub Service** in an interactive terminal, use the keyboard shortcut `CTRL+C`. This will interrupt the running process and terminate the **MetricsHub Service**.

**Background Process**

If the **MetricsHub Service** is running in the background, follow these steps to stop it:

1. Run the `ps` command to get the **MetricsHub Service** PID:

   ```shell-session
   ps aux | grep service
   ```

2. Write down the PID associated with the **MetricsHub Service**.
3. Terminate the process using the `kill` command below:

   ```shell-session
   kill -9 <PID>
   ```
where `<PID>` should be replaced with the actual process ID.

**Service**

To stop the **MetricsHub Service** that is started as a **Linux service**, run the command below:

```shell-session
systemctl stop <metricshub-service>
```

where `<metricshub-service>` should be replaced with the actual service name. For example, `metricshub-service` if the `systemd` service file is `/etc/systemd/system/metricshub-service.service`

### Uninstall

1. Stop the **MetricsHub Service**.
2. Remove the entire `metricshub` directory.

   ```shell-session
   rm -rf /opt/metricshub
   ```

If the **MetricsHub Service** was set up as a **Linux Service**, delete the file `/etc/systemd/system/metricshub-service.service` and run the below command to reload `systemd`:

  ```shell-session
  systemctl daemon-reload
  ```