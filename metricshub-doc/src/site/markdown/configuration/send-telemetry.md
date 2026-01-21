keywords: prometheus, exporter
description: How to configure MetricsHub to send telemetry to an observability back-end.

# Sending Telemetry to Observability Platforms

<!-- MACRO{toc|fromDepth=1|toDepth=1|id=toc} -->

Like any application instrumented with OpenTelemetry, **MetricsHub** utilizes the OTLP protocol to transmit data. Although **MetricsHub Community** can directly send metrics to observability platforms that support OpenTelemetry natively, it is usually recommended in production environments to use an OpenTelemetry Collector to:

- Aggregate metrics across different sources.
- Serve as a proxy, particularly in firewall-secured areas.
- Manage error handling, including retries.

Bundled with OpenTelemetry Collector Contrib, **MetricsHub Enterprise** facilitates connections to over 30 different observability platforms.

## Configure the OTel Collector (Enterprise Edition)

As a regular _OpenTelemetry Collector_, **MetricsHub Enterprise** consists of:

- receivers
- processors
- exporters
- and several extensions.

**MetricsHub Enterprise v${enterpriseVersion}** leverages **version ${otelVersion}** of OpenTelemetry.

To configure the OpenTelemetry Collector of **MetricsHub Enterprise**, edit the `otel/otel-config.yaml` file.

> Note: To assist with the setup process, a configuration example of the OpenTelemetry Collector, `otel/otel-config-example.yaml`, is provided for guidance.

> **Important**: We recommend using an editor supporting the [Schemastore](https://www.schemastore.org/metricshub.json) to edit **MetricsHub**'s configuration YAML files (Example: [Visual Studio Code](https://code.visualstudio.com/download) and [vscode.dev](https://vscode.dev), with [RedHat's YAML extension](https://marketplace.visualstudio.com/items?itemName=redhat.vscode-yaml)).

### Receivers

#### OTLP gRPC

> **Warning**: Only update this section if you customized the [MetricsHub Agent settings](configure-monitoring.html#a-28optional-29-additional-settings).
> The **MetricsHub Agent** pushes the collected data to the [`OTLP Receiver`](https://github.com/open-telemetry/opentelemetry-collector/tree/main/receiver/otlpreceiver) via [gRPC](https://grpc.io/) on port **TCP/4317**.

The `OTLP Receiver` is configured by default with the self-signed certificate `security/otel.crt` and the private key `security/otel.key` to enable the TLS protocol. If you wish to set your own certificate file, configure the **MetricsHub Agent** with the correct [Trusted Certificates File](send-telemetry.html#trusted-certificates-file). Because the `OTLP Exporter` of the **MetricsHub Agent** performs hostname verification, you will also have to add the `localhost` entry (`DNS:localhost,IP:127.0.0.1`) to the `Subject Alternative Name (SAN)` extension of the new generated certificate.

Clients requests are authenticated with the [Basic Authenticator extension](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/extension/basicauthextension).

```yaml
otlp:
  protocols:
    grpc:
      endpoint: localhost:4317
      tls:
        cert_file: ../security/otel.crt
        key_file: ../security/otel.key
      auth:
        authenticator: basicauth
```

#### OpenTelemetry Collector Internal Exporter for Prometheus

The _OpenTelemetry Collector_'s internal _Exporter for Prometheus_ is an optional source of data. It provides information about the collector activity. It's referred to as `prometheus/internal` in the pipeline and leverages the [standard `prometheus` receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/receiver/prometheusreceiver).

```yaml
prometheus/internal:
  config:
    scrape_configs:
      - job_name: otel-collector-internal
        scrape_interval: 60s
        static_configs:
          - targets: [localhost:8888]
```

Under the `service:telemetry:metrics` section, you can set the metrics `level` or the `address` of the OpenTelemetry Collector Internal Exporter (by default: **localhost:8888**).

```yaml
service:
  telemetry:
    metrics:
      address: localhost:8888
      level: basic
```

### Processors

By default, the collected metrics go through 5 processors:

- [`memory_limiter`](https://github.com/open-telemetry/opentelemetry-collector/tree/main/processor/memorylimiterprocessor) to limit the memory consumed by the _OpenTelemetry Collector_ process (configurable)
- [`filter`](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/processor/filterprocessor) to include or exclude metrics
- [`batch`](https://github.com/open-telemetry/opentelemetry-collector/tree/main/processor/batchprocessor) to process data in batches of 10 seconds (configurable).
- [`metricstransform`](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/processor/metricstransformprocessor) to enrich the collected metrics, typically with labels required by the observability platforms. The `metricstransform` processor has [many options to add, rename, delete labels and metrics](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/processor/metricstransformprocessor).

### Exporters

The `exporters` section defines the destination of the collected metrics. **MetricsHub Enterprise** version **${enterpriseVersion}** includes support for all the [OpenTelemetry Collector Contrib exporters](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/exporter), such as:

- [Prometheus Remote Write Exporter](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/exporter/prometheusremotewriteexporter)
- [Datadog Exporter](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/exporter/datadogexporter)
- [Debug Exporter](https://github.com/open-telemetry/opentelemetry-collector/tree/main/exporter/debugexporter)
- [New Relic (OTLP exporter)](https://github.com/open-telemetry/opentelemetry-collector/tree/main/exporter/otlpexporter)
- [Prometheus Exporter](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/exporter/prometheusexporter)
- [Splunk SignalFx](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/exporter/signalfxexporter)
- and [many more...](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/exporter)

You can configure several exporters in the same instance of the _OpenTelemetry Collector_ to send the collected metrics to multiple platforms.

### Extensions

#### HealthCheck

The [healthcheck](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/extension/healthcheckextension) extension checks the status of **MetricsHub Enterprise** . It is activated by default and runs on port 13133 ([`http://localhost:13133`](http://localhost:13133)).

Refer to [Check the collector is up and running](../troubleshooting/health-check.html#check-the-collector-is-up-and-running) for more details.

#### zpages

The **zpages** extension provides debug information about all the different components. It notably provides:

- general information about **MetricsHub**
- details about the active pipeline
- activity details of each receiver and exporter configured in the pipeline.

Refer to [Check the pipelines status](../troubleshooting/health-check.html#check-the-pipelines-status) for more details.

#### Basic Authenticator

The [`Basic Authenticator`](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/extension/basicauthextension) extension authenticates the `OTLP Exporter` requests by comparing the _Authorization_ header sent by the `OTLP Exporter` and the credentials provided in the `security/.htpasswd` file.
Refer to the [Apache htpasswd](https://httpd.apache.org/docs/2.4/programs/htpasswd.html) documentation to know how to manage user files for basic authentication.

```yaml
basicauth:
  htpasswd:
    file: ../security/.htpasswd
```

The `.htpasswd` file is stored in the `security` directory.

> **Warning**: If a different password is specified in the `.htpasswd` file, update the [Basic Authentication Header](configure-monitoring.html#enterprise-edition-authentication) of the **MetricsHub Agent**.

### The Pipeline

Configured extensions, receivers, processors and exporters are taken into account **if and only if** they are declared in the pipeline:

```yaml
service:
  telemetry:
    logs:
      level: info # Change to debug for more details
    metrics:
      address: localhost:8888
      level: basic
  extensions: [health_check, basicauth]
  pipelines:
    metrics:
      receivers: [otlp, prometheus/internal]
      processors: [memory_limiter, batch, metricstransform]
      exporters: [prometheusremotewrite/your-server] # List here the platform of your choice


    # Uncomment the section below to enable logging of hardware alerts.
    # logs:
    #   receivers: [otlp]
    #   processors: [memory_limiter, batch]
    #   exporters: [debug] # List here the platform of your choice
```

## Configure the OTLP Exporter (Community Edition)

By default, the **MetricsHub Agent** pushes the collected metrics to the [`OTLP Receiver`](https://github.com/open-telemetry/opentelemetry-collector/tree/main/receiver/otlpreceiver) through gRPC on port **TCP/4317**. To push data to the OTLP receiver of your choice:

- locate the `otel` section in your configuration file
- configure the `otel.exporter.otlp.metrics.endpoint` parameter as follows:

```yaml
otel:
  otel.exporter.otlp.metrics.endpoint: https://<my-host>:4317

resourceGroups: #...
```

where `<my-host>` should be replaced with the hostname or IP address of the server where the OTLP receiver is installed.

> Additional properties are available, refer to [OTLP Exporter settings](configure-monitoring.md#otlp-exporter-settings) for details.

Use the below syntax if you wish to push metrics to the Prometheus OTLP Receiver:

```yaml
otel:
  otel.exporter.otlp.metrics.endpoint: http://<prom-server-host>:9090/api/v1/otlp/v1/metrics
  otel.exporter.otlp.metrics.protocol: http/protobuf
```

where `<prom-server-host>` should be replaced with the hostname or IP address of the server where _Prometheus_ is running.

> **Note:**
> For specific configuration details, refer to the [OpenTelemetry Auto-Configure documentation](https://opentelemetry.io/docs/languages/java/configuration/). This resource provides information about the properties to be configured depending on your deployment requirements.

#### Trusted certificates file

If an `OTLP Receiver` certificate is required, configure the `otel.exporter.otlp.metrics.certificate` parameter under the `otel` section:

```yaml
otel:
  otel.exporter.otlp.metrics.certificate: /opt/metricshub/security/new-server-cert.crt

resourceGroups: # ...
```

The file should contain one or more X.509 certificates in PEM format.
