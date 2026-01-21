keywords: custom, snmp
description: How to configure MetricsHub to poll the SNMP agent

# SNMP Polling

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

You can configure **MetricsHub** to periodically poll any system or device through SNMP v1, v2 and v3, retrieve the value of a given OID (object identifier) or an entire SNMP table, and push OpenTelemetry metrics with the extracted values.

In the example below, we configured **MetricsHub** to:

- monitor the `power-cooling-prod` resource using SNMP
- track power-related metrics
- retrieve values of a given OID
- extract and expose the `cooling.power` metric.

## Procedure

To achieve this use case, we:

- Declare the resource to be monitored (`power-cooling-prod`)​ and its attributes (`host.name`, `host.type`)​

```yaml
resources:
  power-cooling-prod:
    attributes:
      host.name: power-cooling-prod
      host.type: other
```

- Configure the `SNMP` protocol

```yaml
protocols:
  snmp:
    version: v2c
    community: public
    timeout: 120s
    port: 161
```

- Define a monitor job (`power_cooling`) to track power-related metrics​

```yaml
monitors:
  power_cooling:
    simple:
```

- Set up an SNMP source (`PowerCooling`) to retrieve the power consumed by the cooling system

```yaml
sources:
  PowerCooling:
    type: snmpGet
    oid: 1.3.6.1.4.1.4555.10.20.30.1.80031.5.1
```

- Extract and expose the metric (`cooling.power`) from the SNMP response.

```yaml
mapping:
  source: ${esc.d}{source::PowerCooling}
  attributes:
    id: power-cooling-prod
  metrics:
    cooling.power: ${esc.d}1
```

Here is the complete YAML configuration:

```yaml
resources:
  power-cooling-prod:
    attributes:
      host.name: power-cooling-prod
      host.type: other
    protocols:
      snmp:
        version: v2c
        community: public
        timeout: 120s
        port: 161
    monitors:
      power_cooling:
        simple:
          sources:
            PowerCooling:
              type: snmpGet
              oid: 1.3.6.1.4.1.4555.10.20.30.1.80031.5.1
          mapping:
            source: ${esc.d}{source::PowerCooling}
            attributes:
              id: power-cooling-prod
            metrics:
              cooling.power: ${esc.d}1
```

## Supporting Resources

- [Configure resources](../configuration/configure-monitoring.md#step-3-configure-resources)
- [Resource attributes](../configuration/configure-monitoring.html#resource-attributes)
- [SNMP](../configuration/configure-monitoring.md#snmp)
- [Customize resource monitoring](../configuration/configure-monitoring.html#customize-resource-monitoring)
