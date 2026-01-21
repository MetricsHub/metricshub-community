keywords: logs
description: How to enable the debug mode of the OpenTelemetry Collector.

# OTel Logs

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

The **OTel Collector** periodically retrieves metrics, traces, and logs, then forwards them to the observability platform. If you encounter missing data, enabling its debug mode can provide a more detailed output in the `otelcol-$timestamp.log` file. This additional verbosity offers deeper insights into MetricsHub operations, including initialization, pipeline processes, and termination workflows

## Enable debug

First set the log `level` to `debug` in the **otel/otel-config.yaml** file:

```yaml
service:
  telemetry:
    logs:
      level: debug
  extensions: [health_check]
  pipelines:
  # [...]
```

Then, restart **MetricsHub** for these new settings to be considered.

Finally, check the **logs/otelcol-\<timestamp\>.log** file, where `<timestamp>` is the time at which the log was started.

> Note: The **logs/otelcol-\<timestamp\>.log** file is reset each time the _Collector_ is started. Previous logs are identified with the `<timestamp>` value (ex: `otelcol-2022-09-19-02-05-18.log`). **MetricsHub** rotates the **otelcol-\<timestamp\>.log** file when it reaches a maximum size of **100MB** and retains old log files for **2 days**.

### What to look for in otelcol-\<timestamp\>.log

First check that the **MetricsHub Agent** successfully launched the _OpenTelemetry Collector_.

Then check that the exporters and processors properly started.

Finally look for any connection issues or authentication failures to the configured observability platform(s) (Datadog, BMC Helix, Prometheus, Grafana, etc.).

### Get more details about the exported data

You can enable the `debug` exporter in the **otel/otel-config.yaml** file to check which metrics, labels, and values are sent by the _Collector_ to the observability platforms and verify that the configured processors did not alter the collected data.

First, list the `debug` exporter under the `exporters` section and set `verbosity` to `detailed`:

```yaml
exporters:
  # [...]
  debug:
    verbosity: detailed
```

Then, declare the `debug` exporter in the pipeline:

```yaml
service:
  pipelines:
    metrics:
      receivers: # receivers
      processors: # processors
      exporters: [prometheusremotewrite/your-server, debug] # <-- added debug
```

Restart the _Collector_ for the new settings to be considered.

The metric name, its labels and value are listed in the **logs/otelcol-\<timestamp\>.log** file.

> **Important**: Disable the `debug` exporter when unused as its operation may affect the overall performance of the _Collector_ and fill your file system.

### Reduce the amount of information logged

To reduce the amount of information logged, you can configure the `filter` processor to only log metrics of specific hosts. In the example below, we configured the `filter/keep1HostOnly` processor to only log information about systems whose hostname contains `my-server.big-corp.com`:

```yaml
processors:
  filter/keep1HostOnly:
    metrics:
      include:
        match_type: expr
        expressions:
          - Label("host.name") == "my-server.big-corp.com"
```

We then declared the `filter/keep1HostOnly` processor in the pipeline and restarted the _Collector_:

```yaml
service:
  pipelines:
    metrics:
      receivers: [otlp, prometheus/internal]
      processors: [memory_limiter, batch, filter/keep1HostOnly] # <-- added filter
      exporters: # exporters
```

> **Important**: Remove the `filter` processor from your pipeline once the troubleshooting is completed.
