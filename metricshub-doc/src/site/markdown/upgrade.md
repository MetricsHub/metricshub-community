keywords: upgrade
description: Describes version-specific changes that may affect functionality, performance, or compatibility.

# Upgrade Notes

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

## MetricsHub Enterprise

### Upgrading to v3.0.01

#### Updating otel-config.yaml

To guarantee the correct operation of **MetricsHub Enterprise v3.0.01**, you must replace the following `service:` section of your `otel-config.yaml` file:

```yaml
service:
  telemetry:
    logs:
      level: info # Change to debug for more details
    metrics:
      address: localhost:8888
      level: basic
```

with this one:

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
```

By default, the `otel-config.yaml` file is stored in:

* `/opt/metricshub/lib/otel/otel-config.yaml` **(Linux)**
* or `C:\ProgramData\MetricsHub\otel\otel-config.yaml` **(Windows)**.

If you have any doubt, you can refer to the example files provided for the different operating systems:

* `/opt/metricshub/lib/otel/otel-config-example.yaml` **(Linux)**
* `C:\Program Files\MetricsHub\otel\otel-config-example.yaml` **(Windows)**

or download the latest **[otel-config-example.yaml](https://metricshub.com/docs/latest/resources/config/otel/otel-config-example.yaml)** example.

### Upgrading to v2.0.00

#### Support for multiple configuration files

**MetricsHub** parses all the `.yaml` or `.yml` files with valid syntax found in the `config/` directory, regardless of their names or intended purpose (e.g., `resources-example.yaml`, `old-license.yaml`).

**To prevent configuration conflicts, duplicates, or unintended overrides**:

* Move backup or example files to a subfolder such as `config/examples/` or `config/backups/`.
* Disable unused files by renaming them with a non-YAML extension (e.g., `.bak`, `.txt`, or `.disabled`).
* Regularly check the files stored in the `config/` directory to ensure only intended configurations are loaded.
