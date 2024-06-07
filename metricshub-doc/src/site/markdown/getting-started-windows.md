keywords: quick start, getting started
description: Short step-by-step instructions to follow for installing and configuring MetricsHub in a Windows environment. 

# Quick Start - Windows

<!-- MACRO{toc|fromDepth=1|toDepth=1|id=toc} -->

This quick start guide provides step-by-step instructions for operating MetricsHub and Prometheus in a Windows environment, ensuring you can efficiently monitor your systems.

After completing this quick start, you will have:
* MetricsHub and Prometheus installed on your machine
* The MetricsHub Agent configured to collect hardware metrics from your local host and push data to Prometheus
* MetricsHub and Prometheus up and running
* Hardware metrics available in Prometheus. 

## Step 1: Install MetricsHub

1. Download the latest package, `metricshub-windows-${project.version}.zip`, from the [MetricsHub Releases](https://github.com/sentrysoftware/metricshub/releases/) page

2. Right-click on the archive, select **Extract All...**, enter `C:\Program Files\`, and click **Extract**. This will place the `MetricsHub` directory in `C:\Program Files\`.

> Note: You will need administrative privileges to unzip into `C:\Program Files`.

## Step 2: Install Prometheus

1. Download [prometheus-{version}.windows-{architecture}.zip](https://prometheus.io/download/)

2. Right-click the archive, select **Extract All...**", enter `C:\Program Files\`, and click **Extract**. This will place the `prometheus-{version}.windows-{architecture}` directory in `C:\Program Files\`

3. Under `C:\Program Files\`, rename the `prometheus-{version}.windows-{architecture}` directory to `Prometheus`.

> Note: Make sure to use the corresponding Prometheus version and CPU architecture for `{version}` and `{architecture}`. For example, `prometheus-2.52.0.windows-amd64` for version `2.52.0` and `amd64` architecture.

## Step 3: Configure the MetricsHub Agent

### Create your configuration file

1. Before creating your configuration file (`metricshub.yaml`), ensure that the required directories exist. If they do not, open a Command Prompt and run the following commands to create them:

   ```shell
   mkdir C:\ProgramData\metricshub
   mkdir C:\ProgramData\metricshub\config
   ```

2. Copy the configuration example `metricshub-example.yaml` available in `C:\Program Files\MetricsHub\config\`, paste it into `C:\ProgramData\metricshub\config\` and rename the file to `metricshub.yaml`.

### Configure localhost monitoring

The `metricshub-example.yaml` file you copied already contains the necessary configuration to monitor your localhost through WMI. The relevant section should look like this:

```yaml
resources:
  localhost:
    attributes:
      host.name: localhost
      host.type: windows
    protocols:
      wmi:
        timeout: 120
```


Open the `C:\ProgramData\metricshub\config\metricshub.yaml` file and search for the above section to verify that the configuration is active. 

If you wish to use a protocol other than `WMI` (such as `HTTP`, `PING`, `SNMP`, `SSH`, `IPMI`, `WBEM`, or `WinRM`), refer to the configuration examples provided in `C:\ProgramData\metricshub\config\metricshub.yaml`.

### Configure Prometheus to receive MetricsHub data

In the same configuration file (`C:\ProgramData\metricshub\config\metricshub.yaml`), add the below configuration under the `otel` section to push metrics to Prometheus:

```yaml
otel:
  otel.exporter.otlp.metrics.endpoint: http://localhost:9090/api/v1/otlp/v1/metrics
  otel.exporter.otlp.metrics.protocol: http/protobuf
```

## Step 4: Start Prometheus and MetricsHub

### Start Prometheus

1. Run the below command **as administrator** to access the directory where Prometheus is installed:

    ```shell
    cd "C:\Program Files\Prometheus"
    ```

2. Run the below command to start Prometheus:

    ```shell
    prometheus.exe --config.file=prometheus.yml --web.console.templates=consoles --web.console.libraries=console_libraries --storage.tsdb.retention.time=2h --web.enable-lifecycle --web.enable-remote-write-receiver --web.route-prefix=/ --enable-feature=exemplar-storage --enable-feature=otlp-write-receiver
    ```

3. Type [localhost:9090](http://localhost:9090) in your Web browser.

### Start the Metricshub Agent

Run the below command **as administrator** to start the MetricsHub Agent: 

```shell
cd "C:\Program Files\MetricsHub"
MetricsHubServiceManager.exe
```

## Step 5: Perform Last Checks

### Verify that metrics are sent to Prometheus

In [Prometheus](http://localhost:9090), search for any metrics starting with `metricshub_` or `hw_` to confirm that data is actually received. 


### Check Logs

Several log files are created under `C:\Users\{Username}\AppData\Local\metricshub\logs` as soon as the MetricsHub Agent is started:

* a global `MetricsHub` log file
* one log file per configured host. 

You can configure the log level in the `C:\ProgramData\metricshub\config\metricshub.yaml` file by setting the `loggerLevel` parameter to:

* `info` for high level information
* `warn` for logging warning messages that indicate potential issues which are not immediately critical
* `all`, `trace`, or `debug` for more comprehensive details
* `error` or `fatal` for identifying critical issues.

The most common errors you may encounter are:

1. **Incorrect Indentation**

    An incorrect indentation in the `metricshub.yaml` file prevents the MetricsHub Agent from starting and  generates the following exception in the `metricshub-agent-global-error-{timestamp}.log` file:

    ```
    [2024-04-30T15:56:16,944][ERROR][o.s.m.a.MetricsHubAgentApplication] Failed to start MetricsHub Agent.
    com.fasterxml.jackson.dataformat.yaml.snakeyaml.error.MarkedYAMLException: mapping values are not allowed here in 'reader', line 29, column 16:
        host.type:windows
    ```

2. **Wrong Host Configuration**

    The following entry will be created in the `metricshub-agent-{hostname}-{timestamp}.log` file if the host configured cannot be reached:

    ```css
    [o.s.m.e.c.h.NetworkHelper] Hostname {hostname} - Could not resolve the hostname to a valid IP address. The host is considered remote.
    ```

    If the host is correctly configured, ensure it is reachable by pinging it and testing your network.