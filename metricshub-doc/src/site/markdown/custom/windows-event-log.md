keywords: windows, eventlog, WMI
description: How to configure MetricsHub to monitor Windows Event Logs via WMI.

# Windows Event Log Monitoring

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

You can configure **MetricsHub** to monitor Windows Event Logs. 

In the example below, we configured **MetricsHub** to:

* collect `Security` Windows Event Logs
* keep only Windows Event Logs indicating failures
* translate the number of failures into a numerical metric.

## Procedure

To achieve this use case, follow these steps:

* Declare the resource to be monitored (`prod-win-web`) and its attributes (`host.name`, `host.type`)

```yaml
resources:
  prod-win-web:
    attributes:
      host.name: prod-win-web
      host.type: windows
```

* Configure the `WMI` protocol with credentials and timeout

```yaml
    protocols:
      wmi:
        username: <username>
        password: <password>
        timeout: 240
```

> Important: For large Windows Event Logs, make sure you configure a **high enough timeout** (e.g. `240` seconds).

* Configure the monitor job targeting the desired Windows Event Logs

```yaml
    monitors:
      logs:
        simple:
```

* Collect Windows Events from the **Security** Log

```yaml
          sources:
            # Columns
            # 1.RecordNumber, 2.TimeGenerated, 3.TimeWritten, 4.EventCode, 5.EventType, 6.EventIdentifier,
            # 7.SourceName, 8.InsertionStrings, 9.Message, 10.LogFile
            windowsEventLogSource:
              type: eventLog
              logName: Security
              sources: Microsoft-Windows-Security-Auditing
              maxEventsPerPoll: 20
```

* Filter and count Windows Event Logs indicating failures

```yaml
              computes:
              - type: awk
                script: 'BEGIN {c=0} /failed./ {c++} END {print c}'
```

* Create attributes

```yaml
          mapping:
            # Mapping is executed on the result produced by the source (after computes are applied).
            source: ${esc.d}{source::windowsEventLogSource}
            attributes:
              id: Microsoft-Windows-Security-Auditing
              log.name: Security
              log.pattern: ".*failed.*"
```

* Extract and expose the `windows.event.logs` metric

```yaml
            metrics:
              # Emit a single datapoint: number of rows that matched after the awk script.
              windows.event.logs: $1
```

Here is the complete YAML configuration:

```yaml
resources:
  prod-win-web:
    attributes:
      host.name: prod-win-web
      host.type: windows
    protocols:
      wmi:
        username: <username>
        password: <password>
        timeout: 240
    monitors:
      logs:
        simple:
          sources:
            # Columns
            # 1.RecordNumber, 2.TimeGenerated, 3.TimeWritten, 4.EventCode, 5.EventType, 6.EventIdentifier,
            # 7.SourceName, 8.InsertionStrings, 9.Message, 10.LogFile
            windowsEventLogSource:
              type: eventLog
              logName: Security
              sources: Microsoft-Windows-Security-Auditing
              maxEventsPerPoll: 20
              computes:
              - type: awk
                script: 'BEGIN {c=0} /failed./ {c++} END {print c}'
          mapping:
            # Mapping is executed on the result produced by the source (after computes are applied).
            source: ${esc.d}{source::windowsEventLogSource}
            attributes:
              id: Microsoft-Windows-Security-Auditing
              log.name: Security
              log.pattern: ".*failed.*"
            metrics:
              # Emit a single datapoint: number of rows that matched after the awk script.
              windows.event.logs: $1
```

## Supporting Resources

* [Configure resources](../configuration/configure-monitoring.md#step-3-configure-resources)
* [Resource attributes](../configuration/configure-monitoring.md#resource-attributes)
* [WMI](../configuration/configure-monitoring.md#wmi)
* [WinRM](../configuration/configure-monitoring.md#winrm)
* [Customize data collection](../configuration/configure-monitoring.md#customize-data-collection)


