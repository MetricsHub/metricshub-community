keywords: troubleshooting, status, check, health
description: There are several ways to easily assess the status of ${solutionName}

# Health Check

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

## Check the collector is up and running

Verify that both processes are running:

- `otelcol-contrib`
- `metricshub/bin/enterprise-service`

On Windows, you will need to verify the status of the **MetricsHub Enterprise** service.

## Check the collector status

Connect to [`http://localhost:13133`](http://localhost:13133), which typically responds with:

```json
{ "status": "Server available", "upSince": "2021-10-25T00:59:24.340626+02:00", "uptime": "12h12m21.5832293s" }
```

Alternatively, you can use _cURL_:

```shell-session
$ curl http://localhost:13133
{"status":"Server available","upSince":"2021-10-25T00:59:24.340626+02:00","uptime":"12h13m33.8777673s"}
```

## Check the pipelines status

Add `zpages` in the `service:extensions` section of the **otel/otel-config.yaml** file:

```yaml
service:
  extensions: [health_check, zpages] # <-- Added zpages
  # [...]
```

Restart the _Collector_.

Connect to:

- [`http://localhost:55679/debug/servicez`](http://localhost:55679/debug/servicez) for general information about the Collector
- [`http://localhost:55679/debug/pipelinez`](http://localhost:55679/debug/pipelinez) for details about the active pipeline
- [`http://localhost:55679/debug/tracez`](http://localhost:55679/debug/tracez) for activity details of each receiver and exporter in the pipeline

## Check the collector is running properly

The _OpenTelemetry Collector_ runs an internal Prometheus Exporter on port 8888, exposing metrics related to its operations, notably the number of metrics being processed in its pipeline, and how many errors have been encountered pushing these metrics to the outside.

These metrics can be scraped with a _Prometheus Server_, or simply visualized by connecting to `http://localhost:8888/metrics`.

```text
# HELP otelcol_exporter_queue_size Current size of the retry queue (in batches)
# TYPE otelcol_exporter_queue_size gauge
otelcol_exporter_queue_size{exporter="datadog/api",service_instance_id="xxxxxxxxx-xxxx-xxxx-xxxxxxxxxxxx"} 0
# HELP otelcol_exporter_send_failed_metric_points Number of metric points in failed attempts to send to destination.
# TYPE otelcol_exporter_send_failed_metric_points counter
otelcol_exporter_send_failed_metric_points{exporter="datadog/api",service_instance_id="xxxxxxxxx-xxxx-xxxx-xxxxxxxxxxxx"} 0
# HELP otelcol_exporter_sent_metric_points Number of metric points successfully sent to destination.
# TYPE otelcol_exporter_sent_metric_points counter
otelcol_exporter_sent_metric_points{exporter="datadog/api",service_instance_id="xxxxxxxxx-xxxx-xxxx-xxxxxxxxxxxx"} 2592
# HELP otelcol_process_cpu_seconds Total CPU user and system time in seconds
# TYPE otelcol_process_cpu_seconds gauge
otelcol_process_cpu_seconds{service_instance_id="xxxxxxxxx-xxxx-xxxx-xxxxxxxxxxxx"} 0.640625
# HELP otelcol_process_memory_rss Total physical memory (resident set size)
# TYPE otelcol_process_memory_rss gauge
otelcol_process_memory_rss{service_instance_id="xxxxxxxxx-xxxx-xxxx-xxxxxxxxxxxx"} 4.8041984e+07
# HELP otelcol_process_runtime_heap_alloc_bytes Bytes of allocated heap objects (see 'go doc runtime.MemStats.HeapAlloc')
# TYPE otelcol_process_runtime_heap_alloc_bytes gauge
otelcol_process_runtime_heap_alloc_bytes{service_instance_id="xxxxxxxxx-xxxx-xxxx-xxxxxxxxxxxx"} 1.0002296e+07
# HELP otelcol_process_runtime_total_alloc_bytes Cumulative bytes allocated for heap objects (see 'go doc runtime.MemStats.TotalAlloc')
# TYPE otelcol_process_runtime_total_alloc_bytes gauge
otelcol_process_runtime_total_alloc_bytes{service_instance_id="xxxxxxxxx-xxxx-xxxx-xxxxxxxxxxxx"} 3.694764e+07
# HELP otelcol_process_runtime_total_sys_memory_bytes Total bytes of memory obtained from the OS (see 'go doc runtime.MemStats.Sys')
# TYPE otelcol_process_runtime_total_sys_memory_bytes gauge
otelcol_process_runtime_total_sys_memory_bytes{service_instance_id="xxxxxxxxx-xxxx-xxxx-xxxxxxxxxxxx"} 2.703848e+07
...
```

The above processor time utilization and memory consumption metrics pertain to the `otelcol-contrib` process only, and do not represent the activity of the internal **MetricsHub Agent**.

You can choose to integrate these internal metrics in the pipeline of the _OpenTelemetry Collector_ to push them to the platform of your choice. To do so, [edit the otel/otel-config.yaml configuration file](../configuration/send-telemetry.md) to add `prometheus/internal` in the list of receivers:

```yaml
# [...]

# ACTUAL COLLECTOR PIPELINE DESCRIPTION
service:
  telemetry:
    logs:
      level: info # Change to debug for more details
  extensions: [health_check]
  pipelines:
    metrics:
      receivers: [otlp, prometheus/internal]
      processors: [memory_limiter, batch, metricstransform]
      exporters: [...] # List here the platform of your choice
```

> Additional troubleshooting information is available in the [OpenTelemetry Collector's Troubleshooting Guide](https://opentelemetry.io/docs/collector/troubleshooting/).
