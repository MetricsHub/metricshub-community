keywords: web, http, request
description: How to configure MetricsHub to monitor the health of a Web service.

# Web Request

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

You can configure **MetricsHub** to send HTTP request against a Web service and analyze the responses.

In the example below, we configured **MetricsHub** to:

- send an HTTP GET request to the health endpoint of the `grafana-service`
- parse the JSON response and extract key information such as the service version and database status
- translate the health state into a numerical metric (`grafana.db.state`).

## Procedure

To achieve this use case, we:

- Declare the resource to be monitored​ (`grafana-service`) and its attributes (`service.name`, `host.name`)

```yaml
resources:
  grafana-service:
    attributes:
      service.name: Grafana
      host.name: hws-demo.sentrysoftware.com
```

- Configure the `HTTP` protocol

```yaml
protocols:
  http:
    https: true
    port: 443
```

- Configure the monitor job targeting the desired service​

```yaml
monitors:
  grafana:
    simple: # simple job. Creates monitors and collects associated metrics.
```

- Send an HTTP request to the Grafana service​

```yaml
sources:
  grafanaHealth:
    type: http
    path: /api/health
    method: get
    header: "Accept: application/json"
    # {
    #   "commit": "838218ba20",
    #   "database": "ok",
    #   "version": "10.1.0"
    # }
```

- Process the JSON response using the `json2Csv` and `translate` transformation steps

```yaml
computes:
  - type: json2Csv
    entryKey: /
    properties: commit;database;version
    separator: ;
  - type: translate
    column: 3
    translationTable:
      ok: 1
      default: 0
```

- Map the extracted data to resource attributes and metrics

```yaml
mapping:
  source: ${esc.d}{source::grafanaHealth}
  attributes:
    id: ${esc.d}2
    service.instance.id: ${esc.d}2
    service.version: ${esc.d}4
```

- Extract and expose the `grafana.db.state` metric

```yaml
metrics:
  grafana.db.state: ${esc.d}3
```

Here is the complete YAML configuration:

```yaml
resources:
  grafana-service:
    attributes:
      service.name: Grafana
      host.name: hws-demo.sentrysoftware.com
    protocols:
      http:
        https: true
        port: 443
    monitors:
      grafana:
        simple: # simple job. Creates monitors and collects associated metrics.
          sources:
            grafanaHealth:
              type: http
              path: /api/health
              method: get
              header: "Accept: application/json"
              # {
              #   "commit": "838218ba20",
              #   "database": "ok",
              #   "version": "10.1.0"
              # }
              computes:
                - type: json2Csv
                  entryKey: /
                  properties: commit;database;version
                  separator: ;
                - type: translate
                  column: 3
                  translationTable:
                    ok: 1
                    default: 0
          mapping:
            source: ${esc.d}{source::grafanaHealth}
            attributes:
              id: ${esc.d}2
              service.instance.id: ${esc.d}2
              service.version: ${esc.d}4
            metrics:
              grafana.db.state: ${esc.d}3
```

## Supporting Resources

- [Configure resources](../configuration/configure-monitoring.md#step-3-configure-resources)
- [Resource attributes](../configuration/configure-monitoring.html#resource-attributes)
- [HTTP](../configuration/configure-monitoring.html#http)
- [Customize resource monitoring](../configuration/configure-monitoring.html#customize-resource-monitoring)
- [Customize data collection](../configuration/configure-monitoring.html#customize-data-collection)
