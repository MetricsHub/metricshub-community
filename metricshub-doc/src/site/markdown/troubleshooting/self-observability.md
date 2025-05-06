keywords: agent, configuration, self-observability, trace, span, pipeline  
description: How to configure the self-observability of MetricsHub.

# Self-Observability

<!-- MACRO{toc|fromDepth=1|toDepth=3|id=toc} -->

> **Important**: Self-observability is intended to help advanced users debug or troubleshoot **MetricsHub**. We recommend using it cautiously, as an incorrect configuration can lead to monitoring issues.

**MetricsHub** can monitor itself, granting you access to additional metrics such as the number of requests for a specific protocol or the current state of the JVM.
Using the **OpenTelemetry Java Agent**, **MetricsHub** can export traces and metrics to observe its own performance.

These traces describe the internal "path" that **MetricsHub** takes to execute internal tasks, providing a clear picture of what is happening during operations.

**Self-Observability** generates a variety of **traces** and tracks every protocol request such as:

- HTTP
- IPMI
- OS Command
- SNMP
- SSH
- WBEM
- WinRM
- WMI

The **metrics** range from the number of requests sent for each protocol to the latency measured for each request.

All JVM metrics share these attributes:
`host.arch`, `host.name`, `os.description`, `os.type`, `pool`, `process.command_line`, `process.executable.path`, `process.pid`, `process.runtime.description`, `process.runtime.name`, `process.runtime.version`, `service.name`, `service`, `telemetry.auto.version`, `telemetry.sdk.language`, `telemetry.sdk.name`, `telemetry.sdkversion`, `type`, `host`.

| JVM Metrics                 | Description                                                   | Type    | Unit |
| --------------------------- | ------------------------------------------------------------- | ------- | ---- |
| jvm.cpu.recent_utilization  | Recent CPU utilization for the JVM process                    | Gauge   |      |
| jvm.system.cpu.utilization  | Recent CPU utilization for the whole system                   | Gauge   |      |
| jvm.classes.count           | Number of classes currently loaded by the JVM process         | Gauge   |      |
| jvm.classes.unloaded        | Number of classes unloaded by the JVM process since its start | Counter |      |
| jvm.classes.loaded          | Number loaded classes by the JVM process since its start      | Counter |      |
| jvm.threads.count           | JVM process's number of executing threads                     | Gauge   |      |
| jvm.memory.committed        | JVM process's memory committed                                | Gauge   | By   |
| jvm.memory.init             | JVM process's initial memory requested                        | Gauge   | By   |
| jvm.memory.limit            | JVM process's memory size limit                               | Gauge   | By   |
| jvm.memory.used             | JVM process's memory usage                                    | Gauge   | By   |

## Self-Observe

The **Self-Observability** feature is **disabled by default**. Follow the steps below to activate it.

### Enable OpenTelemetry traces

In order to export the traces to the observability back-end, comment out the traces pipeline inside the `otel/otel-config.yaml` file and configure the exporter.

```yaml
traces:
  receivers: [otlp]
  processors: [memory_limiter, batch]
  exporters: [debug] # Replace with your target platform E.g. datadog/api
```

### Configure Java Options

Now configure the Java options to link the **OpenTelemetry Java Agent** with the **MetricsHub** service and its exporter.

> **Warning**: If you're using multiple **MetricsHub** instances, give each `service.name` a unique value (in the `.cfg` files) to avoid aggregation issues on the observability back-end.

#### On Windows

Edit `MetricsHubEnterpriseService.cfg` located in `C:\Program Files\MetricsHub\app`:

```java
java-options=-javaagent:otel\opentelemetry-javaagent.jar
java-options=-Dotel.resource.attributes=service.namespace=metricshub,service.name=MetricsHub
java-options=-Dotel.traces.exporter=otlp
java-options=-Dotel.metrics.exporter=otlp
java-options=-Dotel.exporter.otlp.protocol=grpc
java-options=-Dotel.exporter.otlp.endpoint=https://localhost:4317
java-options=-Dotel.exporter.otlp.certificate=security\otel.crt
java-options=-Dotel.exporter.otlp.headers=Authorization=Basic bWV0cmljc2h1Yjp2R2d3Li83XmdbfklRSkUubThiZWU=
```

#### On Linux

Edit `enterprise-service.cfg` located in `/opt/metricshub/lib/app`:

```java
java-options=-javaagent:/opt/metricshub/otel/opentelemetry-javaagent.jar
java-options=-Dotel.resource.attributes=service.namespace=metricshub,service.name=MetricsHub
java-options=-Dotel.traces.exporter=otlp
java-options=-Dotel.metrics.exporter=otlp
java-options=-Dotel.exporter.otlp.protocol=grpc
java-options=-Dotel.exporter.otlp.endpoint=https://localhost:4317
java-options=-Dotel.exporter.otlp.certificate=/opt/metricshub/security/otel.crt
java-options=-Dotel.exporter.otlp.headers=Authorization=Basic bWV0cmljc2h1Yjp2R2d3Li83XmdbfklRSkUubThiZWU=
```

## Trace example

A span is created for each request. Here’s an example:

```log

ScopeSpans #2
ScopeSpans SchemaURL: 
InstrumentationScope io.opentelemetry.opentelemetry-instrumentation-annotations-1.16 2.15.0-alpha
Span #0
    Trace ID       : 0b29d8113037e88b1c94fec651324ed8
    Parent ID      : 5beaf926969caa1b
    ID             : 7439338a80205fc4
    Name           : SNMP Get Next
    Kind           : Internal
    Start time     : 2025-04-25 11:35:25.307855 +0000 UTC
    End time       : 2025-04-25 11:35:25.4769308 +0000 UTC
    Status code    : Unset
    Status message : 
Attributes:
    code.namespace : Str(org.metricshub.extension.snmp.AbstractSnmpRequestExecutor)
    code.function  : Str(executeSNMPGetNext)
    thread.id      : Int(43)
    host.hostname  : Str(10.0.27.5)
    snmp.config    : Str(SNMP v2c (public))
    thread.name    : Str(pool-13-thread-1)
    snmp.oid       : Str(1.3.6.1.2.1.33)

```

Each span includes:

1. **ScopeSpans**: The group it belongs to
2. **Span**: ID, name, timing, and status details
3. **Attributes**: Span-specific metadata (e.g., HTTP details, thread info)
