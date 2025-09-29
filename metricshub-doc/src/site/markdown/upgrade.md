keywords: upgrade
description: Describes version-specific changes that may affect functionality, performance, or compatibility.

# Upgrade Notes

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

## MetricsHub Enterprise

### Upgrading to v3.0.01

#### Support for multiple configuration files

**MetricsHub** parses all the `.yaml` or `.yml` files with valid syntax found in the `config/` directory, regardless of their names or intended purpose (e.g., `resources-example.yaml`, `old-license.yaml`).

**To prevent configuration conflicts, duplicates, or unintended overrides**:

* Move backup or example files to a subfolder such as `config/examples/` or `config/backups/`.
* Disable unused files by renaming them with a non-YAML extension (e.g., `.bak`, `.txt`, or `.disabled`).
* Regularly check the files stored in the `config/` directory to ensure only intended configurations are loaded.

#### OpenTelemetry Collector configuration

MetricsHub Enterprise leverages **version `${otelVersion}`** of the OpenTelemetry Collector Contrib, which enhances and extends the configuration options of the `otel/otel-config.yaml` file.

To avoid confusion, always refer to the example reference file `otel-config.example.yaml`:

* **Linux (default):**
  `/opt/metricshub/lib/otel/otel-config.example.yaml`

* **Windows (default):**
  `C:\Program Files\MetricsHub\otel\otel-config.example.yaml`

* **Alternatively, download the latest example from:**
  [https://metricshub.com/docs/latest/resources/config/otel/otel-config-example.yaml](https://metricshub.com/docs/latest/resources/config/otel/otel-config-example.yaml)

Your active configuration file `otel-config.yaml` is located at:

* **Linux (default):**
  `/opt/metricshub/lib/otel/otel-config.yaml`

* **Windows (default):**
  `C:\ProgramData\MetricsHub\otel\otel-config.yaml`

Update `otel-config.yaml` as follows:

from:

```yaml
service:
  telemetry:
    logs:
      level: info # Change to debug for more details
    metrics:
      address: localhost:8888
      level: basic
```

to:

```yaml
service:
  telemetry:
    logs:
      level: info # Change to debug for more details
    metrics:
      level: basic
      readers:
        - pull:
            exporter:
              prometheus:
                host: '0.0.0.0'
                port: 8888
```

