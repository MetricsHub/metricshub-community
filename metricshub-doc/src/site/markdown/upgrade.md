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

Use the `otel-config.example.yaml` shipped with this release as the version-aligned baseline.  
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

