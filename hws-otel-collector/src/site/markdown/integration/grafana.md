keywords: grafana, dashboard, sustainable, sustainability, green
description: How to import and configure Hardware Sentry's Sustainable IT Dashboards for Grafana.

# Grafana Dashboards

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

[Grafana](https://grafana.com/) can easily display the metrics collected by **${project.name}** and stored in a Prometheus Server. Sentry Software provides pre-built **Sustainable IT** dashboards that leverage these metrics to report on the health of the hardware of the monitored systems, and on the carbon emissions of these systems:

![**${project.name}** Sustainable IT Dashboard](../images/grafana-sustainable-it.png)

## Prerequisites

Before you can start configuring and using **${project.name}** dashboards, you must have:

1. configured [Hardware Sentry Exporter for Prometheus](../configuration/configure-exporter.html).
2. configured the [Prometheus server](../integration/prometheus.html)
3. run both **Hardware Sentry Exporter for Prometheus** and the **Prometheus server**.

## Loading Dashboards in Grafana

First, download the latest version of **hardware-dashboards-for-grafana.zip** or **hardware-dashboards-for-grafana.tar.gz** from [Sentry Software’s Web site](https://www.sentrysoftware.com/downloads/products-for-prometheus.html). The package contains:

![Dashboards Package](../images/hardware-dashboards-for-grafana-folders.png)

* the dashboards (.json files)
* the provisioning files (.yml files)

### On Windows

1. Uncompress **hardware-dashboards-for-grafana.zip** in a temporary folder.
2. Copy the `provisioning` folder to the `grafana\conf` folder on the Grafana server (default: "C:\Program Files\GrafanaLabs\grafana\conf").
3. Copy the `sustainable_IT` folder in the directory of your choice on the Grafana server (ex: "C:\Program Files\GrafanaLabs\grafana\public\dashboards").

    ![Download Dashboards on Windows](../images/import-dashboards-windows.png)

### On Linux and UNIX

1. Uncompress **hardware-dashboards-for-grafana.tar.gz** in a temporary folder.
2. Copy the `provisioning` folder to the `grafana` folder on the Grafana server (default: "/etc/grafana").
3. Copy the `sustainable_IT` folder in the directory of your choice folder on the Grafana server (ex: "/var/lib/grafana/dashboards").

## Configuring the Dashboard Provider

1. Go to `%GRAFANA_HOME%\grafana\conf\provisioning\dashboards`.
2. Open the `hardware-sentry.yml` file.

    ![Configuring Dashboard Provider](../images/import_grafana_dashboard_provider-config.png)

3. Search for the `path: ''` parameter.
4. Specify the path to the folder where you uncompressed the *sustainable_IT* folder and save your changes.

Example:

```yaml
apiVersion: 1

providers:
- name: 'Sentry Software'
    orgId: 1
    folder: 'Sustainable IT by Sentry Software'
    folderUid: ''
    type: file
    updateIntervalSeconds: 60
    allowUiUpdates: true
    options:
    path: 'C:/Program Files/GrafanaLabs/grafana/public/dashboards'
    foldersFromFilesStructure: true
```

<div class="alert alert-warning"> The path should point to the folder containing the <i>sustainable_IT</i> folder. This folder should only contain dashboards for Grafana.</div>

## Configuring the Data Source

The dashboards for Grafana query the Prometheus server to display the status of the hardware components. A Prometheus data source needs to be configured on the Grafana server.

1. In `\grafana\conf\provisioning\datasource`, open the *hardware-sentry-prometheus.yml* file.
   ![Configuring Data Source Provider](../images/import_grafana_dashboards_config.png)
2. Enter the required settings to connect to your Prometheus server and save your changes. This will create a new data source called **hardware_sentry_prometheus** in Grafana.
3. Restart the Grafana service.

 The dashboards are now loaded in Grafana.

 Example:

```yaml
# config file version
apiVersion: 1

datasources:
  # <string, required> name of the datasource. Required
- name: hardware_sentry_prometheus
  # <string, required> datasource type. Required
  type: prometheus
  # <string, required> access mode. direct or proxy. Required
  access: proxy
  # <int> org id. will default to orgId 1 if not specified
  orgId: 1
  # <string> url
  url: http://myhost-01:9090
  # <string> database password, if used
  password:
  # <string> database user, if used
  user:
  # <string> database name, if used
  database:
  # <bool> enable/disable basic auth
  basicAuth: false
  # <string> basic auth username, if used
  basicAuthUser:
  # <string> basic auth password, if used
  basicAuthPassword:
  # <bool> enable/disable with credentials headers
  withCredentials:
  # <bool> mark as default datasource. Max one per org
  isDefault: true
  version: 1
  # <bool> allow users to edit datasources from the UI.
  editable: true
```
