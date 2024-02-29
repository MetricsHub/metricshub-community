keywords: guide, connector, connector, object, reference
description: This page shows how to reference connector objects such as sources, entries, columns and files, etc. inside the YAML document.

# Connector Object References

This page shows how to reference connector objects such as sources, entries, columns and files, etc. inside the YAML document.

* [Source Reference](#source_reference)
    * [Format](#source-format)
    * [Example](#source-example)
* [Entry Reference](#entry_reference)
    * [Format](#entry-format)
    * [Example](#entry-example)
* [Column Reference](#column_reference)
    * [Format](#column-format)
    * [Example](#column-example)
* [File Reference](#file_reference)
    * [Format](#file-format)
    * [Example](#file-example)
* [Mono-Instance Reference](#mono-instance_reference)
    * [Format](#mono-instance-format)
    * [Example](#mono-instance-example)
* [Translation Table Reference](#translation_table_reference)
    * [Format](#translation-table-format)
    * [Example](#translation-table-example)
* [Awk Script Reference](#awk_script_reference)
    * [Format](#awk-script-format)
    * [Example](#awk-script-example)

## Source Reference

### <a id="source-format" />Format

A Source can be referenced either with its full path or its relative path. A relative path can be used only if the referenced source is in the same monitor and same job as the current source.

### <a id="source-example" />Example
```yaml
      mapping:
        # InstanceTable = Source(1)
        source: ${source::monitors.disk_controller.discovery.sources.source_discovery}
```

```yaml
        sourceDiscovery:
          type: tableJoin
          leftTable: ${source::monitors.enclosure.collect.sources.source_chassis} # full path for a source in another monitor
          rightTable: ${source::source_enclosure} # relative path
          leftKeyColumn: 1
          rightKeyColumn: 1
```

## Entry Reference

### <a id="entry-format" />Format
```yaml
$<columnNumber>
```

### <a id="entry-example" />Example
```yaml
        source(3):
          type: http
          method: GET
          executeForEachEntryOf:
            source: ${source::monitors.enclosure.discovery.sources.source(2)}
            concatMethod: list
          url: /api/rest/StorageCenter/ScChassis/$2/PowerSupplyList
```

## Column Reference

### <a id="column-format" />Format
```yaml
$<columnNumber>
```

### <a id="column-example" />Example
```yaml
      mapping:
        # PowerSupply
        # tableID;ID;DisplayName;objectType;enclosure/controllerID;deviceType
        source: ${source::monitors.power_supply.discovery.sources.source(4)}
        attributes:
          id: $2
          __display_id: $3
          hw.parent.type: $6
          hw.parent.id: $5
          name: $3
```

## File Reference

### <a id="file-format" />Format
```yaml
${file::<relativeFilePath>}
```

### <a id="file-example" />Example
```yaml
   criteria:
    - type: osCommand
      commandLine: /bin/sh ${file::storman-drives.sh}
      expectedResult: Hard drive
      errorMessage: No Adaptec Controller with Physical Disks attached or not enough rights to execute arcconf.
```

## Mono-Instance Reference

### <a id="mono-instance-format" />Format
```yaml
${attribute::<attribute-key>}
```

### <a id="mono-instance-example" />Example
```yaml
 collect:
      # Collect type is multi-instance
      type: monoInstance
      sources:
        source(1):
          type: osCommand
          commandLine: /bin/sh ${file::script.sh} ${attribute::id}
          keep: ^MSHW;
          separators: ;
          selectColumns: "2,3,4,5,6,7,8,9"
```

## Translation Table Reference

### <a id="translation-table-format" />Format
```yaml
${translation::<translationTable>}
```

### <a id="translation-table-example" />Example
```yaml
    collect:
      # Collect type = multi-instance
      type: multiInstance
      sources:
        source(1):
          # Source(1) = connUnitSensorTable SNMP Table
          # ID;Status;Value;
          type: snmpTable
          oid: 1.3.6.1.4.1.1588.2.1.1.1.1.22.1
          selectColumns: "ID,3,4"
          computes:
            # Translate the first column status into a PATROLStatus
            # ID;PatrolStatus;Value;
          - type: translate
            column: 2
            translationTable: ${translation::SensorStatusTranslationTable}
```

## Awk Script Reference

### <a id="awk-script-format" />Format
```yaml
${awk::<script>}
```

### <a id="awk-script-example" />Example
```yaml
    monitors:
      battery:
        discovery:
          mapping:
            source: ${source::monitors.battery.discovery.sources.source(1)}
            attributes:
              name: ${awk::sprintf("%s (%s)", "Cisco", $1)}
```
