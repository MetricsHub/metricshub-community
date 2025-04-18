keywords: data, observability platforms, troubleshooting
description: How to resolve issues where MetricsHub fails to send data to observability platforms like Grafana, Prometheus, Datadog, and more

# No Data in the Observability Platform

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

Missing data in your observability platform often indicates an issue with telemetry data transmission, likely caused by a misconfiguration at the exporter level.

The troubleshooting steps vary depending on your edition of **MetricsHub**.

## MetricsHub Community

  1. [Enable logging](./metricshub-logs.md)
  2. Check the **resource logs** (`metricshub-agent-$resourceId-$timestamp.log`) and **connector logs** for potential issues, such as:

       * Misconfigured endpoints (e.g., incorrect hostnames)
       * Authentication failures (e.g., incorrect credentials).

## MetricsHub Enterprise

  1. Enable logging for the [MetricsHub Agent](./metricshub-logs.md) and the [OTel Collector](./otel-logs.md)
  2. Check the **resource logs** (`metricshub-agent-$resourceId-$timestamp.log`) for any errors or anomalies
  3. Review the **OpenTelemetry Collector logs** (`otelcol-$timestamp.log`) to ensure the following:

       * the **MetricsHub Agent** successfully launched the OpenTelemetry Collector
       * **Exporters and processors properly started**
       * **No connection issues or authentication failures** with the configured observability platform(s) (Datadog, BMC Helix, Prometheus, Grafana, etc.).

Refer to [What to look for in otelcol-$timestamp.log](./otel-logs.md#what-to-look-for-in-otelcol--3ctimestamp-3e-log) for more details.
