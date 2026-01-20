keywords: bmc helix, integration
description: How to push the metrics collected by MetricsHub to BMC Helix through the BMC Helix Exporter.

# BMC Helix Integration

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

**MetricsHub** pushes the collected metrics to [**BMC Helix Operations Management**](https://www.bmc.com/it-solutions/bmc-helix-operations-management.html) through the **BMC Helix Exporter**, which leverages the [BMC Helix REST API](https://docs.bmc.com/docs/helixoperationsmanagement/244/en/metric-operation-management-endpoints-in-the-rest-api-1392780044.html).

## Prerequisites

Before integrating **MetricsHub** with BMC Helix:

1. [Install MetricsHub](../installation/index.md) on one or more systems that can access the monitored resources over the network
2. [Configure the monitoring of your resources](../configuration/configure-monitoring.md)
3. Create a dedicated [authentication key for MetricsHub integration](https://docs.bmc.com/docs/helixportal244/using-api-keys-for-external-integrations-1391501992.html).

## Configuring the integration

### Configuring the BMC Helix Exporter

1. Edit the `otel/otel-config.yaml` configuration file
2. In the `exporters` section, configure the BMC Helix Exporter as follows:
    
    ```yaml
    exporters:
      bmchelix/helix1:
        endpoint: https://company.onbmc.com
        api_key: <api-key>
        timeout: 20s
    ```

    where:
   - `endpoint` is the URL of your BMC Helix Portal (e.g., `*.onbmc.com` for SaaS tenants, or your on-premises Helix instance URL)
   - `api_key` is the API key used to authenticate the exporter. To obtain it, connect to BMC Helix, navigate to **Administration > Repository** and click **Copy API Key**
   - `timeout` is the number of seconds before a request times out (default = `10s`).
 
3. `(Optional)` Enable automatic retries in case of export failures by adding:

    ```yaml
        retry_on_failure:
        enabled: true
        initial_interval: 5s
        max_interval: 1m
        max_elapsed_time: 8m
    ```

    Where
     - `enabled` activates the retry mechanism when set to `true`
     - `initial_interval` is the time to wait after the first failure before retrying. Ignored if `enabled` is `false`. Default: `5s`.
     - `max_interval` is the maximum wait time between retry attempts. Ignored if `enabled` is `false`. Default: `30s`.
     - `max_elapsed_time` is the maximum total time to attempt sending a batch. If set to 0, the retries are never stopped. Ignored if `enabled` is `false`. Set to `0` for unlimited retries. Default: `300s`.


    > For more details, refer to the [Exporter Helper](https://github.com/open-telemetry/opentelemetry-collector/tree/main/exporter/exporterhelper#configuration)

4. Configure the [transform processor](#transformer-example-for-hardware-metrics) to ensure metrics are properly formatted for BMC Helix.

5. Declare the **BMC Helix Exporter** and the **transform processor** in a separate pipeline dedicated to BMC Helix, to avoid affecting other exporters with metrics formatted for BMC Helix:

    ```yaml
    service:
      extensions: [health_check, basicauth, basicauth/partner]
      pipelines:
        # Dedicated pipeline for BMC Helix
        metrics/bmchelix:
          receivers: [otlp, prometheus/internal]
          processors: [memory_limiter, batch, transform/hardware_for_helix]
          exporters: [bmchelix/helix1]
    ```

6. Restart the **MetricsHub** service to apply the changes.

### Transforming metrics

To ensure metrics are properly ingested by BMC Helix, the following attributes must be set either at the *Resource* level, or  *Metric* level:  

- `entityName`: Unique identifier for the entity. Used as display name if `instanceName` is missing.
- `entityTypeId`: Type identifier for the entity.
- `instanceName`: Display name of the entity.

> **Note:**  Metrics missing `entityName` or `entityTypeId` will not be exported.

To populate these attributes, we recommend using the [transform processor](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/processor/transformprocessor) with [OTTL](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/pkg/ottl), and include it in the configuration of the telemetry pipeline.

A typical pipeline structure looks like: `OTEL metrics --> (batch/memory limit) --> transform processor --> bmchelix exporter`.

#### Transformer Example for Hardware Metrics

The following example shows how to assign `entityName`, `instanceName`, and `entityTypeId` dynamically for hardware metrics:

```yaml
processors:

  # ...

  transform/hardware_for_helix:
    # Apply transformations to all metrics
    metric_statements:

      - context: datapoint
        statements:
          # Create a new attribute 'entityName' with the value of 'id'
          - set(attributes["entityName"], resource.attributes["id"]) where resource.attributes["id"] != nil
          - set(attributes["instanceName"], resource.attributes["name"]) where resource.attributes["name"] != nil

      - context: datapoint
        conditions:
          - IsMatch(metric.name, ".*\\.agent\\..*") or IsMatch(metric.name, "metricshub\\.license\\..*")
        statements:
          - set(attributes["entityName"], resource.attributes["host.name"]) where resource.attributes["host.name"] != nil
          - set(attributes["instanceName"], resource.attributes["service.name"]) where resource.attributes["service.name"] != nil
          - set(attributes["entityTypeId"], "agent")

      - context: datapoint
        conditions:
          - IsMatch(metric.name, ".*\\.host\\..*")
        statements:
          - set(attributes["entityName"], resource.attributes["host.name"]) where resource.attributes["host.name"] != nil
          - set(attributes["instanceName"], resource.attributes["host.name"]) where resource.attributes["host.name"] != nil

      - context: datapoint
        statements:
          # Mapping entityTypeId based on metric names and attributes
          - set(attributes["entityTypeId"], "connector") where IsMatch(metric.name, ".*\\.connector\\..*")
          - set(attributes["entityTypeId"], "host") where IsMatch(metric.name, ".*\\.host\\..*") or attributes["hw.type"] == "host"
          - set(attributes["entityTypeId"], "battery") where IsMatch(metric.name, "hw\\.battery\\..*") or attributes["hw.type"] == "battery"
          - set(attributes["entityTypeId"], "blade") where IsMatch(metric.name, "hw\\.blade\\..*") or attributes["hw.type"] == "blade"
          - set(attributes["entityTypeId"], "cpu") where IsMatch(metric.name, "hw\\.cpu\\..*") or attributes["hw.type"] == "cpu"
          - set(attributes["entityTypeId"], "disk_controller") where IsMatch(metric.name, "hw\\.disk_controller\\..*") or attributes["hw.type"] == "disk_controller"
          - set(attributes["entityTypeId"], "enclosure") where IsMatch(metric.name, "hw\\.enclosure\\..*") or attributes["hw.type"] == "enclosure"
          - set(attributes["entityTypeId"], "fan") where IsMatch(metric.name, "hw\\.fan\\..*") or attributes["hw.type"] == "fan"
          - set(attributes["entityTypeId"], "gpu") where IsMatch(metric.name, "hw\\.gpu\\..*") or attributes["hw.type"] == "gpu"
          - set(attributes["entityTypeId"], "led") where IsMatch(metric.name, "hw\\.led\\..*") or attributes["hw.type"] == "led"
          - set(attributes["entityTypeId"], "logical_disk") where IsMatch(metric.name, "hw\\.logical_disk\\..*") or attributes["hw.type"] == "logical_disk"
          - set(attributes["entityTypeId"], "lun") where IsMatch(metric.name, "hw\\.lun\\..*") or attributes["hw.type"] == "lun"
          - set(attributes["entityTypeId"], "memory") where IsMatch(metric.name, "hw\\.memory\\..*") or attributes["hw.type"] == "memory"
          - set(attributes["entityTypeId"], "network") where IsMatch(metric.name, "hw\\.network\\..*") or attributes["hw.type"] == "network"
          - set(attributes["entityTypeId"], "other_device") where IsMatch(metric.name, "hw\\.other_device\\..*") or attributes["hw.type"] == "other_device"
          - set(attributes["entityTypeId"], "physical_disk") where IsMatch(metric.name, "hw\\.physical_disk\\..*") or attributes["hw.type"] == "physical_disk"
          - set(attributes["entityTypeId"], "power_supply") where IsMatch(metric.name, "hw\\.power_supply\\..*") or attributes["hw.type"] == "power_supply"
          - set(attributes["entityTypeId"], "robotics") where IsMatch(metric.name, "hw\\.robotics\\..*") or attributes["hw.type"] == "robotics"
          - set(attributes["entityTypeId"], "tape_drive") where IsMatch(metric.name, "hw\\.tape_drive\\..*") or attributes["hw.type"] == "tape_drive"
          - set(attributes["entityTypeId"], "temperature") where IsMatch(metric.name, "hw\\.temperature.*") or attributes["hw.type"] == "temperature"
          - set(attributes["entityTypeId"], "vm") where IsMatch(metric.name, "hw\\.vm\\..*") or attributes["hw.type"] == "vm"
          - set(attributes["entityTypeId"], "voltage") where IsMatch(metric.name, "hw\\.voltage.*") or attributes["hw.type"] == "voltage"

      - context: datapoint
        statements:
          # Rename based on the attribute presence "state", "direction", "hw.error.type", "limit_type", "task"
          - set(metric.name, Concat([metric.name, attributes["state"]], ".")) where attributes["state"] != nil
          - set(metric.name, Concat([metric.name, attributes["direction"]], ".")) where attributes["direction"] != nil
          - set(metric.name, Concat([metric.name, attributes["hw.error.type"]], ".")) where attributes["hw.error.type"] != nil
          - set(metric.name, Concat([metric.name, attributes["limit_type"]], ".")) where attributes["limit_type"] != nil
          - set(metric.name, Concat([metric.name, attributes["task"]], ".")) where attributes["task"] != nil
          - set(metric.name, Concat([metric.name, attributes["protocol"]], ".")) where attributes["protocol"] != nil
```

This configuration ensures that required attributes are automatically mapped based on metric names and resource metadata.

### Transformer Example for System Metrics

The following example shows how to assign `entityName`, `instanceName`, and `entityTypeId` dynamically for system metrics:

```yaml
processors:

  transform/system_for_helix:
    metric_statements:
      # ------------------------------------------------------------------------------
      # 1) Create entityName and instanceName from known attributes
      # ------------------------------------------------------------------------------
      - context: datapoint
        statements:
          # system.cpu => entityName = system.cpu.logical_number, instanceName = name
          - set(attributes["entityName"], resource.attributes["system.cpu.logical_number"]) where IsMatch(metric.name, "system\\.cpu\\..*") and resource.attributes["system.cpu.logical_number"] != nil
          - set(attributes["instanceName"], resource.attributes["name"]) where IsMatch(metric.name, "system\\.cpu\\..*") and resource.attributes["name"] != nil

          # system.memory => entityName = id, instanceName = id
          - set(attributes["entityName"], resource.attributes["id"]) where IsMatch(metric.name, "system\\.memory\\..*") and resource.attributes["id"] != nil
          - set(attributes["instanceName"], resource.attributes["id"]) where IsMatch(metric.name, "system\\.memory\\..*") and resource.attributes["id"] != nil

          # system.paging => entityName = id, instanceName = id
          - set(attributes["entityName"], resource.attributes["system.device"]) where IsMatch(metric.name, "system\\.paging\\..*") and resource.attributes["system.device"] != nil  
          - set(attributes["instanceName"], resource.attributes["system.device"]) where IsMatch(metric.name, "system\\.paging\\..*") and resource.attributes["system.device"] != nil
          # Last resort to set entityName and instanceName to id if system.device is not available
          - set(attributes["entityName"], resource.attributes["id"]) where IsMatch(metric.name, "system\\.paging\\..*") and resource.attributes["id"] != nil and resource.attributes["system.device"] == nil 
          - set(attributes["instanceName"], resource.attributes["id"]) where IsMatch(metric.name, "system\\.paging\\..*") and resource.attributes["id"] != nil and resource.attributes["system.device"] == nil

          # system.disk => entityName and instanceName could be id or system.device
          - set(attributes["entityName"], resource.attributes["system.device"]) where IsMatch(metric.name, "system\\.disk\\..*") and resource.attributes["system.device"] != nil
          - set(attributes["instanceName"], resource.attributes["system.device"]) where IsMatch(metric.name, "system\\.disk\\..*")  and resource.attributes["system.device"] != nil
          # Last resort to set entityName and instanceName to id if system.device is not available
          - set(attributes["entityName"], resource.attributes["id"]) where IsMatch(metric.name, "system\\.disk\\..*") and resource.attributes["id"] != nil and resource.attributes["system.device"] == nil
          - set(attributes["instanceName"], resource.attributes["id"]) where IsMatch(metric.name, "system\\.disk\\..*")  and resource.attributes["id"] != nil and resource.attributes["system.device"] == nil

          # system.filesystem => entityName and instanceName could be id or system.device
          - set(attributes["entityName"], resource.attributes["system.device"]) where IsMatch(metric.name, "system\\.filesystem\\..*") and resource.attributes["system.device"] != nil
          - set(attributes["instanceName"], resource.attributes["system.device"]) where IsMatch(metric.name, "system\\.filesystem\\..*")  and resource.attributes["system.device"] != nil
          # Last resort to set entityName and instanceName to id if system.device is not available
          - set(attributes["entityName"], resource.attributes["id"]) where IsMatch(metric.name, "system\\.filesystem\\..*") and resource.attributes["id"] != nil and resource.attributes["system.device"] == nil
          - set(attributes["instanceName"], resource.attributes["id"]) where IsMatch(metric.name, "system\\.filesystem\\..*")  and resource.attributes["id"] != nil and resource.attributes["system.device"] == nil

          # system.network => entityName and instanceName could be id or network.interface.name
          - set(attributes["entityName"], resource.attributes["network.interface.name"]) where IsMatch(metric.name, "system\\.network\\..*") and resource.attributes["network.interface.name"] != nil
          - set(attributes["instanceName"], resource.attributes["network.interface.name"]) where IsMatch(metric.name, "system\\.network\\..*")  and resource.attributes["network.interface.name"] != nil
          # Last resort to set entityName and instanceName to id if network.interface.name is not available
          - set(attributes["instanceName"], resource.attributes["id"]) where IsMatch(metric.name, "system\\.network\\..*")  and resource.attributes["id"] != nil and resource.attributes["network.interface.name"] == nil
          - set(attributes["entityName"], resource.attributes["id"]) where IsMatch(metric.name, "system\\.network\\..*") and resource.attributes["id"] != nil and resource.attributes["network.interface.name"] == nil

          # system.process => entityName = process.id, instanceName = process.name
          - set(attributes["entityName"], resource.attributes["process.id"]) where IsMatch(metric.name, "process\\..*") and resource.attributes["process.id"] != nil
          - set(attributes["instanceName"], resource.attributes["process.name"]) where IsMatch(metric.name, "process\\..*") and resource.attributes["process.name"] != nil

          # system.service => entityName = id, instanceName = system.service.name
          - set(attributes["entityName"], resource.attributes["system.service.name"]) where IsMatch(metric.name, "system\\.service\\..*") and resource.attributes["system.service.name"] != nil
          - set(attributes["instanceName"], resource.attributes["system.service.name"]) where IsMatch(metric.name, "system\\.service\\..*")  and resource.attributes["system.service.name"] != nil
          # Last resort to set entityName and instanceName to id if system.service.name is not available
          - set(attributes["instanceName"], resource.attributes["id"]) where IsMatch(metric.name, "system\\.service\\..*")  and resource.attributes["id"] != nil and resource.attributes["system.service.name"] == nil
          - set(attributes["entityName"], resource.attributes["id"]) where IsMatch(metric.name, "system\\.service\\..*") and resource.attributes["id"] != nil and resource.attributes["system.service.name"] == nil

      # ------------------------------------------------------------------------------
      # 2) [OPTIONAL] Handle any special or "agent-like" metrics, if desired
      # (Remove if you do not have an agent pattern in your system metrics)
      # ------------------------------------------------------------------------------
      - context: datapoint
        conditions:
          - IsMatch(metric.name, ".*\\.agent\\..*") or IsMatch(metric.name, "metricshub\\.license\\..*")
        statements:
          - set(attributes["entityName"], resource.attributes["host.name"]) where resource.attributes["host.name"] != nil
          - set(attributes["instanceName"], resource.attributes["service.name"]) where resource.attributes["service.name"] != nil
          - set(attributes["entityTypeId"], "agent")


      # ------------------------------------------------------------------------------
      # 3) Assign the entityTypeId for each known system metric pattern
      # ------------------------------------------------------------------------------
      - context: datapoint
        statements:
          - set(attributes["entityTypeId"], "system_cpu") where IsMatch(metric.name, "system\\.cpu\\..*")
          - set(attributes["entityTypeId"], "system_memory") where IsMatch(metric.name, "system\\.memory\\..*")
          - set(attributes["entityTypeId"], "system_paging") where IsMatch(metric.name, "system\\.paging\\..*")
          - set(attributes["entityTypeId"], "system_disk")  where IsMatch(metric.name, "system\\.disk\\..*")
          - set(attributes["entityTypeId"], "system_filesystem") where IsMatch(metric.name, "system\\.filesystem\\..*")
          - set(attributes["entityTypeId"], "system_network") where IsMatch(metric.name, "system\\.network\\..*")
          - set(attributes["entityTypeId"], "system_process") where IsMatch(metric.name, "process\\..*")
          - set(attributes["entityTypeId"], "system_service") where IsMatch(metric.name, "system\\.service\\..*")


      # ------------------------------------------------------------------------------
      # 4) Rename the metric.name by appending known "state/direction" keys 
      # ------------------------------------------------------------------------------
      - context: datapoint
        statements:
          - set(metric.name, Concat([metric.name, attributes["cpu.mode"]], ".")) where attributes["cpu.mode"] != nil
          - set(metric.name, Concat([metric.name, attributes["system.cpu.state"]], ".")) where attributes["system.cpu.state"] != nil
          - set(metric.name, Concat([metric.name, attributes["system.memory.state"]], ".")) where attributes["system.memory.state"] != nil
          - set(metric.name, Concat([metric.name, attributes["system.paging.state"]], ".")) where attributes["system.paging.state"] != nil
          - set(metric.name, Concat([metric.name, attributes["system.paging.direction"]], ".")) where attributes["system.paging.direction"] != nil
          - set(metric.name, Concat([metric.name, attributes["system.paging.type"]], ".")) where attributes["system.paging.type"] != nil
          - set(metric.name, Concat([metric.name, attributes["disk.io.direction"]], ".")) where attributes["disk.io.direction"] != nil
          - set(metric.name, Concat([metric.name, attributes["network.io.direction"]], ".")) where attributes["network.io.direction"] != nil
          - set(metric.name, Concat([metric.name, attributes["system.filesystem.state"]], ".")) where attributes["system.filesystem.state"] != nil
          - set(metric.name, Concat([metric.name, attributes["system.process.status"]], ".")) where attributes["system.process.status"] != nil
          # If the metric is managed by transform/hardware_for_helix, do not change its name
          # - set(metric.name, Concat([metric.name, attributes["state"]], ".")) where attributes["state"] != nil
```
