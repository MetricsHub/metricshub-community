keywords: grafana, dashboard, green, power consumption, carbon, CO₂
description: How to import the Hardware (MetricsHub) dashboards for Grafana.

# Grafana Dashboards

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

**MetricsHub** collects the hardware health and performance metrics as well as the sustainability KPIs of any server, storage system, or network switch available in your on-prem infrastructure and exposes the telemetry data in the **Hardware Main (MetricsHub)**, **Hardware Site (MetricsHub)**, and **Hardware Host (MetricsHub)** Grafana dashboards.

![MetricsHub Main - Overview of all monitored sites](../images/grafana-metricshub-main.png)

![MetricsHub Site - Metrics associated to one site and its monitored hosts](../images/grafana-metricshub-site.png)

![MetricsHub Host - Metrics associated to one host and its internal components](../images/grafana-metricshub-host.png)

In this section, you will learn how to import the **MetricsHub** dashboards in Grafana. 

## Prerequisites

Before importing the **MetricsHub** dashboards in Grafana, ensure that you have:

1. [configured the monitoring of your resources](../configuration/configure-monitoring.md)
2. [configured the sustainability metrics](../guides/configure-sustainability-metrics.md)
3. [configured the Prometheus Server](prometheus.md)
4. run both **MetricsHub** and the **Prometheus server**.

## Importing the MetricsHub dashboards

1. Log on to Grafana
2. [Import](https://grafana.com/docs/grafana/latest/dashboards/build-dashboards/import-dashboards/) the dashboard(s)
3. Enter the dashboard ID:
   
   - `22053` for the **[Hardware Main (MetricsHub)](https://grafana.com/grafana/dashboards/22053-hardware-main-metricshub/)** dashboard
   - `22052` for the **[Hardware Site (MetricsHub)](https://grafana.com/grafana/dashboards/22052-hardware-site-metricshub/)** dashboard
   - `22051` for the **[Hardware Host (MetricsHub)](https://grafana.com/grafana/dashboards/22051-hardware-host-metricshub/)** dashboard

4. Click **Load**
5. Select the **Prometheus** datasource

6. Click **Import**
7. Repeat the procedure to import all the **Hardware (MetricsHub) dashboards**.

The **MetricsHub** dashboards are now loaded in Grafana:

| Dashboard                      | Description                                                                                 |
| ------------------------------ | ------------------------------------------------------------------------------------------- |
| **Hardware Main (MetricsHub)** | Overview of all monitored _sites_                                                           |
| **Hardware Site (MetricsHub)** | Metrics associated to one _site_ (a data center or a server room) and its monitored _hosts_ |
| **Hardware Host (MetricsHub)** | Metrics associated to one _host_ and its internal components                                |