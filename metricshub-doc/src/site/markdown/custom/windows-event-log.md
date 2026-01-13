keywords: windows, eventlog, WMI, WinRM
description: How to configure MetricsHub to monitor Windows Event Logs (via WMI or WinRM).

# Windows Event Log Monitoring

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

You can configure **MetricsHub** to monitor Windows Event Logs. In the example below, we configured **MetricsHub** to run a connector that defines a single `eventLog` source (Security) on the `prod-win-web` resource using **WMI**.

> Note: Event log collection is incremental. MetricsHub keeps a per-host, per-EventLog-source cursor to avoid re-reading the same events.

## Procedure

To achieve this use case, we:

* Declare the resource to be monitored (`prod-win-web`) and its attributes (`host.name`, `host.type`)

```yaml
    resources:
      prod-win-web:
        attributes:
          host.name: prod-win-web
          host.type: windows
```

* Configure the `WMI` protocol with credentials and timeout:

```yaml
        protocols:
          wmi:
            username: <username>
            password: <password>
            timeout: 240
```

> Important: Make sure you configure a **high enough timeout** on the host (e.g. `240` seconds), otherwise the request may time out when large input is processed.

* Define the `WindowsEventLog` source

This example demonstrates:

- retrieving events from the **Security** log
- filtering and counting events containing `failed` word.
- mapping the results into a metric

Each source returns the following columns:

1/ `RecordNumber`, 2/ `TimeGenerated`, 3/ `TimeWritten`, 4/ `EventCode`, 5/ `EventType`,
6/ `EventIdentifier`, 7/ `SourceName`, 8/ `InsertionStrings`, 9/ `Message`, 10/ `LogFile`

```yaml
monitors:
  logs:
    simple:
      sources:
        windowsEventLogSource:
          # 1.RecordNumber, 2.TimeGenerated, 3.TimeWritten, 4.EventCode, 5.EventType, 6.EventIdentifier,
          # 7.SourceName, 8.InsertionStrings, 9.Message, 10.LogFile
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
          log.file.name: Security
          log.match.pattern: ".*failed.*"
        metrics:
          # Emit a single datapoint: number of rows that matched after the awk script.
          log.count: $1
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
        eventLogSource:
          # 1.RecordNumber, 2.TimeGenerated, 3.TimeWritten, 4.EventCode, 5.EventType, 6.EventIdentifier,
          # 7.SourceName, 8.InsertionStrings, 9.Message, 10.LogFile
          type: eventLog
          logName: Security
          sources: Microsoft-Windows-Security-Auditing
          maxEventsPerPoll: 20
          # Optional: filter by event IDs (e.g., eventIds: [4624, 4625])
          # Optional: filter by levels (e.g., levels: ["error", "warning", "audit failure"])
          computes:
            - type: awk
              script: 'BEGIN {c=0} /failed./ {c++} END {print c}'
      mapping:
        # Mapping is executed per row returned by the source.
        source: ${esc.d}{source::eventLogSource}
        attributes:
          log.file.name: Security
          log.match.pattern: ".*failed.*"
        metrics:
          # Emit a single datapoint: number of rows that matched after the awk script.
          log.count: $1
```

## Supporting Resources

* [Configure resources](../configuration/configure-monitoring.md#step-3-configure-resources)
* [Resource attributes](../configuration/configure-monitoring.md#resource-attributes)
* [WMI](../configuration/configure-monitoring.md#wmi)
* [WinRM](../configuration/configure-monitoring.md#winrm)
* [Customize data collection](../configuration/configure-monitoring.md#customize-data-collection)


