keywords: prometheus, integration
description: How to push the metrics collected by MetricsHub to Prometheus

# Prometheus integration

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

> Note: This integration procedure is intended for the MetricsHub Enterprise Edition. If you are using the Community Edition, please refer to [Quick Start](../getting-started/quick-start-community-prometheus.md).

**MetricsHub** sends the collected metrics to a Prometheus server using the *Remote Write Protocol*.

![MetricsHub pushes the collected metrics to Prometheus and Grafana](../images/metricshub-prometheus-diagram.png)


## Prerequisites

Before you can start viewing the metrics collected by **MetricsHub** in Prometheus, you must have:

1. [Installed Prometheus](../getting-started/quick-start-community-prometheus.md#step-2-install-prometheus)
2. [Installed **MetricsHub**](../installation/index.md) on one or more systems that has network access to the resources to be monitored
3. [Configured the monitoring of your resources](../configuration/configure-monitoring.md).

## Configuration

1. First, configure your Prometheus server to allow the the *Remote Write* feature:

   * [Enable the Remote Write Receiver](https://prometheus.io/docs/prometheus/latest/feature_flags/#remote-write-receiver) with the `--web.enable-remote-write-receiver` option
   * [Configure the Remote Write Receiver](https://prometheus.io/docs/prometheus/latest/configuration/configuration/#remote_write)

2. Then, edit the `exporters` section of the `otel/otel-config.yaml` configuration file:

    ```yaml
    exporters:
      prometheusremotewrite/your-prom-server: # The name of your Prometheus server
        endpoint: http://your-prom-server:9090/api/v1/write # The URL of your Prometheus server
        resource_to_telemetry_conversion:
          enabled: true
    ```
3. Declare the exporter in the pipeline section of **otel/otel-config.yaml**:

    ```yaml
    service:
      extensions: [health_check]
      pipelines:
        metrics:
          receivers: [otlp, prometheus/internal]
          processors: [memory_limiter, batch, resourcedetection, metricstransform]
          exporters: [prometheusremotewrite/your-prom-server] # Declare your prometheus prometheusremotewrite exporter
    ```
4. Finally, restart Prometheus then MetricsHub to ensure that the configuration changes take effect.