keywords: windows, eventlog, WMI, WinRM
description: How to configure MetricsHub to monitor Windows Event Logs (via WMI or WinRM).

# Windows Event Log Monitoring

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

You can configure **MetricsHub** to monitor Windows Event Logs. In the example below, we configured **MetricsHub** to run a connector that defines a single `eventLog` source (Security) on the `prod-win-web` resource using **WMI**.

> Note: Event log collection is incremental. MetricsHub keeps a per-host cursor per EventLog source to avoid re-reading the same events.

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

* Configure **either** the `WMI` protocol protocol with credentials and timeout:

```yaml
        protocols:
          wmi:
            username: <username>
            password: <password>
            timeout: 240
```

> Important: Make sure you configure a **high enough timeout** on the host (e.g. `240` seconds), otherwise the request may time out when large input is processed.

* Define the `WindowsEventLog` source

This example demonstrates 

- retrieving events from the **Security** log
- applying computes to keep wanted events (having `failed` word)
- mapping the results into metrics

Each source returns the following columns:

1. `RecordNumber`, 2. `TimeGenerated`, 3. `TimeWritten`, 4. `EventCode`, 5. `EventType`,
2. `EventIdentifier`, 7. `SourceName`, 8. `InsertionStrings` (in Base64), 9. `Message`, 10. `LogFile`

```yaml
monitors:
  logs:
    simple:
      sources:
        eventLogSource:
          # 1.RecordNumber, 2.TimeGenerated, 3.TimeWritten, 4.EventCode, 5.EventType, 6.EventIdentifier,
          # 7.SourceName, 8.InsertionStrings (Base64), 9.Message (Base64), 10.LogFile
          type: eventLog
          logName: Security
          sources: Microsoft-Windows-Security-Auditing
          maxEventsPerPoll: 20
          computes:
            # Decode InsertionStrings before filtering/matching.
            - type: decode
              column: 8
              encoding: Base64
            # Example: keep only events where decoded insertion strings contain a keyword.
            - type: keepOnlyMatchingLines
              column: 8
              regExp: ".*failed.*"
        eventCountSource:
          type: copy
          from: ${esc.d}{source::eventLogSource}
          computes:
            # Aggregate: replace the whole table by a single row containing the number of remaining rows (NR).
            - type: awk
              script: END { print NR }

      mapping:
        # Mapping is executed per row returned by the source.
        source: ${esc.d}{source::eventCountSource}
        attributes:
          log.file.name: Security
          log.match.pattern: ".*failed.*"
        metrics:
          # Emit a single datapoint: number of rows that matched after keepOnlyMatchingLines.
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
                  # 7.SourceName, 8.InsertionStrings (Base64), 9.Message (Base64), 10.LogFile
                  type: eventLog
                  logName: Security
                  sources: Microsoft-Windows-Security-Auditing
                  maxEventsPerPoll: 20
                  computes:
                    # Decode InsertionStrings before filtering/matching.
                    - type: decode
                      column: 8
                      encoding: Base64
                    # Example: keep only events where decoded insertion strings contain a keyword.
                    - type: keepOnlyMatchingLines
                      column: 8
                      regExp: ".*failed.*"
                eventCountSource:
                  type: copy
                  from: ${esc.d}{source::eventLogSource}
                  computes:
                    # Aggregate: replace the whole table by a single row containing the number of remaining rows (NR).
                    - type: awk
                      script: END { print NR }

              mapping:
                # Mapping is executed per row returned by the source.
                source: ${esc.d}{source::eventCountSource}
                attributes:
                  log.file.name: Security
                  log.match.pattern: ".*failed.*"
                metrics:
                  # Emit a single datapoint: number of rows that matched after keepOnlyMatchingLines.
                  log.count: $1
```

## Supporting Resources

* [Configure resources](../configuration/configure-monitoring.md#step-3-configure-resources)
* [Resource attributes](../configuration/configure-monitoring.md#resource-attributes)
* [WMI](../configuration/configure-monitoring.md#wmi)
* [WinRM](../configuration/configure-monitoring.md#winrm)
* [Customize data collection](../configuration/configure-monitoring.md#customize-data-collection)


