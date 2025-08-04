keywords: release notes
description: Learn more about the new features, changes and improvements, and bug fixes brought to MetricsHub Enterprise.

# Release Notes

<!-- MACRO{toc|fromDepth=1|toDepth=1|id=toc} -->

## MetricsHub Enterprise Edition v3.0.00

### MetricsHub Enterprise Edition v3.0.00

#### What's New

| ID                                                                        | Description                                                          |
| ------------------------------------------------------------------------- | -------------------------------------------------------------------- |
| [**\#83**](https://github.com/MetricsHub/metricshub-enterprise/issues/83) | **`apikey`**: New CLI executable is now available to manage API keys |

#### Changes and Improvements

| ID                                                                        | Description                                                                              |
| ------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| [**\#75**](https://github.com/MetricsHub/metricshub-enterprise/issues/75) | Added `JDBC` and `JMX` configuration examples to `metricshub-example.yaml`               |
| [**\#78**](https://github.com/MetricsHub/metricshub-enterprise/issues/78) | Dynamic configuration reloading has been enhanced allowing settings to update on the fly |

### MetricsHub Enterprise Connectors v108

#### What's New

| ID                                                                        | Description                                                                                                                         |
| ------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| [**\#56**](https://github.com/MetricsHub/enterprise-connectors/issues/56) | Added support for [**Citrix NetScaler**](./connectors/citrixnetscalerrest.html) via REST API                                        |
| [**\#61**](https://github.com/MetricsHub/enterprise-connectors/issues/61) | Added support for [**Cisco ASA Firewall**](./connectors/ciscosecurefirewallasa.html) via SNMP                                       |
| [**\#62**](https://github.com/MetricsHub/enterprise-connectors/issues/62) | Added support for [**Arista Switch**](./connectors/aristabgpswitch.html) via SNMP to report `Border Gateway Protocol` (BGP) metrics |

#### Changes and Improvements

| ID                                                                        | Description                                                                  |
| ------------------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| [**\#55**](https://github.com/MetricsHub/enterprise-connectors/issues/55) | **NetApp Filer (REST)**: Added QoS latency metrics                           |
| [**\#59**](https://github.com/MetricsHub/enterprise-connectors/issues/59) | **EMC SMI-S Agent (ECOM)**: Fan names now use `+` instead of `-+-`           |
| [**\#71**](https://github.com/MetricsHub/enterprise-connectors/issues/71) | **Dell XtremIO - REST**: Added performance and capacity metrics via REST API |

#### Fixed Issues

| ID                                                                        | Description                                                                                                                                                                                                                                                                                                                                       |
| ------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [**\#73**](https://github.com/MetricsHub/enterprise-connectors/issues/73) | The storage array connectors report `direction` and `storage.direction` attributes instead of `storage.io.direction` attributes: <ul><li>**Dell EMC PowerMax Storage (REST)**</li><li>**NetApp Filer (REST)**</li><li>**Pure Storage FA Series (REST)**</li><li>**Pure Storage FA Series v2 (REST)**</li><li>**Dot Hill System (REST)**</li></ul> |
| [**\#76**](https://github.com/MetricsHub/enterprise-connectors/issues/76) | **Dell EMC PowerMax Storage (REST)**: Incorrect unit for the `storage.size` metric                                                                                                                                                                                                                                                                |
| [**\#80**](https://github.com/MetricsHub/enterprise-connectors/issues/80) | **NetApp Filer (REST)**: <ul><li>Storage pools are missing</li><li>Volume capacities are incorrectly calculated</li><li>Disk discovery is inaccurate</li></ul>                                                                                                                                                                                    |
| [**\#83**](https://github.com/MetricsHub/enterprise-connectors/issues/83) | **Dell EMC PowerMax Storage (REST)**: <ul><li>Incorrect subscribed and configured capacities for storage pools</li><li>Configured capacity reported incorrectly</li><li>Missing volume consumed and available capacity metrics</li><li>`Thin pools` are not detected</li></ul>                                                                    |
| [**\#84**](https://github.com/MetricsHub/enterprise-connectors/issues/84) | **Pure Storage FA Series (REST)** and **Pure Storage FA Series v2 (REST)**: <ul><li>Incorrect mapping of storage pool metrics</li><li>Missing `storage.volume.type` attribute</li></ul>                                                                                                                                                           |
| [**\#86**](https://github.com/MetricsHub/enterprise-connectors/issues/86) | **Dot Hill System (REST)**: Missing `storage.size` metric on the storage system and physical disks                                                                                                                                                                                                                                                |

### MetricsHub Community Edition v1.0.06

#### What's New

| ID                                                                         | Description                                                                                                                                                                  |
| -------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [**\#438**](https://github.com/MetricsHub/metricshub-community/issues/438) | Added support for [programmable configurations](./configuration/configure-monitoring.md#programmable-configuration) to generate the list of resources to monitor dynamically |
| [**\#746**](https://github.com/MetricsHub/metricshub-community/issues/746) | **`apikey`**: New CLI executable is now available to manage API keys                                                                                                         |
| [**\#749**](https://github.com/MetricsHub/metricshub-community/issues/749) | Added API key-based authentication                                                                                                                                           |
| [**\#750**](https://github.com/MetricsHub/metricshub-community/issues/750) | Added [MCP tool](./integrations/ai-agent-mcp.md) to list configured hosts                                                                                                    |
| [**\#756**](https://github.com/MetricsHub/metricshub-community/issues/756) | Added [MCP tool](./integrations/ai-agent-mcp.md) to detect hosts and collect metrics                                                                                         |

#### Changes and Improvements

| ID                                                                         | Description                                                                                 |
| -------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------- |
| [**\#671**](https://github.com/MetricsHub/metricshub-community/issues/671) | Dynamic configuration reloading has been enhanced allowing settings to update on the fly    |
| [**\#739**](https://github.com/MetricsHub/metricshub-community/issues/739) | Redesigned documentation to look more like the [MetricsHub](https://metricshub.com) website |
| [**\#741**](https://github.com/MetricsHub/metricshub-community/issues/741) | **SNMP v3**: Added support for `AES192` and `AES256` encryptions                            |

#### Fixed Issues

| ID                                                                         | Description                                                                                                                                                    |
| -------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [**\#734**](https://github.com/MetricsHub/metricshub-community/issues/734) | The logs incorrectly report that the `connector_id` attribute is missing on `host` monitors, although the `connector_id` attribute is not applicable to hosts. |
| [**\#754**](https://github.com/MetricsHub/metricshub-community/issues/754) | `UserPrincipalNotFoundException` is thrown when **MetricsHub** starts on Windows and cannot find the `Users` group.                                            |

### MetricsHub Community Connectors v1.0.13

#### Changes and Improvements

| ID                                                                         | Description                                            |
| -------------------------------------------------------------------------- | ------------------------------------------------------ |
| [**\#253**](https://github.com/MetricsHub/metricshub-community/issues/240) | Added `storage.latency` metric to semantic conventions |

#### Fixed Issues

| ID                                                                         | Description                                                          |
| -------------------------------------------------------------------------- | -------------------------------------------------------------------- |
| [**\#250**](https://github.com/MetricsHub/metricshub-community/issues/250) | Updated unit for `hw.network.dropped` from `{packet}` to `{packets}` |

## MetricsHub Enterprise Edition v2.1.00

### MetricsHub Enterprise Edition v2.1.00

#### What's New

| ID                                                                        | Description                                                                 |
| ------------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| [**\#60**](https://github.com/MetricsHub/metricshub-enterprise/issues/60) | Included the **IBM Informix** JDBC driver                                   |
| [**\#68**](https://github.com/MetricsHub/metricshub-enterprise/issues/68) | Added support for Java Management Extension (JMX)                           |
| [**\#69**](https://github.com/MetricsHub/metricshub-enterprise/issues/69) | Added support for [REST API and remote MCP](./integrations/ai-agent-mcp.md) |

#### Changes and Improvements

| ID                                                                        | Description                                         |
| ------------------------------------------------------------------------- | --------------------------------------------------- |
| [**\#57**](https://github.com/MetricsHub/metricshub-enterprise/issues/57) | Upgraded OpenTelemetry Collector Contrib to 0.127.0 |

### MetricsHub Enterprise Connectors v107

#### What's New

| ID                                                                        | Description                                                                              |
| ------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| [**\#27**](https://github.com/MetricsHub/enterprise-connectors/issues/27) | Added support for [**IBM Informix**](./connectors/informix.html) databases via JDBC      |
| [**\#32**](https://github.com/MetricsHub/enterprise-connectors/issues/32) | Added support for [**Palo Alto**](./connectors/paloaltofirewall.html) firewalls via SNMP |

#### Changes and Improvements

| ID                                                                        | Description                                                                     |
| ------------------------------------------------------------------------- | ------------------------------------------------------------------------------- |
| [**\#57**](https://github.com/MetricsHub/enterprise-connectors/issues/57) | **Nvidia DGX Server (REST)**: Improved HTTP requests initiated by the connector |

### MetricsHub Community Edition v1.0.05

#### What's New

| ID                                                                         | Description                                                                                                         |
| -------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| [**\#718**](https://github.com/MetricsHub/metricshub-community/issues/718) | **AI Agents**: A new set of [MCP Tools](./integrations/ai-agent-mcp.md) is now available to perform protocol checks |

### MetricsHub Community Edition v1.0.04

#### What's New

| ID                                                                         | Description                                                                 |
| -------------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| [**\#692**](https://github.com/MetricsHub/metricshub-community/issues/692) | Added support for [REST API and remote MCP](./integrations/ai-agent-mcp.md) |
| [**\#694**](https://github.com/MetricsHub/metricshub-community/issues/694) | Added support for Java Management Extensions (JMX)                          |

#### Changes and Improvements

| ID                                                                         | Description                                                                                                                                                                                                                |
| -------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [**\#357**](https://github.com/MetricsHub/metricshub-community/issues/357) | **MetricsHub CLI**: Added the ability to specify connector variables                                                                                                                                                       |
| [**\#658**](https://github.com/MetricsHub/metricshub-community/issues/658) | The `database` property is no longer required under the `jdbc` configuration                                                                                                                                               |
| [**\#698**](https://github.com/MetricsHub/metricshub-community/issues/698) | **Prometheus Alert Rules**: Three categories of [alert rules](./prometheus/alertmanager.md#prometheus-alertmanager) are provided to inform you of MetricsHub issues, hardware failures, and system performance degradation |
| [**\#726**](https://github.com/MetricsHub/metricshub-community/issues/726) | **MetricsHub CLI**: The `--list` option now shows connectors that define variables                                                                                                                                         |

#### Fixed Issues

| ID                                                                         | Description                                               |
| -------------------------------------------------------------------------- | --------------------------------------------------------- |
| [**\#693**](https://github.com/MetricsHub/metricshub-community/issues/693) | Monitor names are generated for non-hardware monitors     |
| [**\#703**](https://github.com/MetricsHub/metricshub-community/issues/703) | Metrics processing failure due to `NumberFormatException` |
| [**\#708**](https://github.com/MetricsHub/metricshub-community/issues/708) | Incorrect SchemaStore link in the documentation           |

#### Documentation Updates

| ID                                                                         | Description                                                                                    |
| -------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| [**\#684**](https://github.com/MetricsHub/metricshub-community/issues/684) | Documented [custom monitoring](./custom/index.md) examples                                     |
| [**\#702**](https://github.com/MetricsHub/metricshub-community/issues/702) | Explained how to implement [custom monitoring through SQL queries](./custom/database-query.md) |
| [**\#704**](https://github.com/MetricsHub/metricshub-community/issues/704) | Documented [Prometheus Alert Rules](./prometheus/alertmanager.md)                              |
| [**\#705**](https://github.com/MetricsHub/metricshub-community/issues/705) | Documented [BMC Helix integration](./integrations/bmc-helix.md)                                |

### MetricsHub Community Connectors v1.0.12

#### What's New

| ID                                                                         | Description                                                                             |
| -------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| [**\#162**](https://github.com/MetricsHub/community-connectors/issues/162) | Added support for [**Apache Cassandra**](./connectors/cassandra.html) databases via JMX |

#### Changes and Improvements

| ID                                                                         | Description                                                                                              |
| -------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| [**\#233**](https://github.com/MetricsHub/community-connectors/issues/233) | **MIB-2 Standard SNMP Agent - Network Interfaces**: Added discarded inbound and outbound packets metrics |

## MetricsHub Enterprise Edition v2.0.00

### MetricsHub Enterprise Edition v2.0.00

#### What's New

| ID                                                                        | Description                                                     |
| ------------------------------------------------------------------------- | --------------------------------------------------------------- |
| [**\#13**](https://github.com/MetricsHub/metricshub-enterprise/issues/13) | Enabled self-observability through the OpenTelemetry Java Agent |
| [**\#33**](https://github.com/MetricsHub/metricshub-enterprise/issues/33) | Added support for multiple configuration files                  |

#### Changes and Improvements

| ID                                                                        | Description                                                            |
| ------------------------------------------------------------------------- | ---------------------------------------------------------------------- |
| [**\#6**](https://github.com/MetricsHub/metricshub-enterprise/issues/6)   | Updated MetricsHub Enterprise EULA                                     |
| [**\#11**](https://github.com/MetricsHub/metricshub-enterprise/issues/11) | Added the ability to define custom connector variables                 |
| [**\#23**](https://github.com/MetricsHub/metricshub-enterprise/issues/23) | Added digital signature to MetricsHub Enterprise Windows MSI installer |
| [**\#37**](https://github.com/MetricsHub/metricshub-enterprise/issues/37) | Cleaned up Datadog pipeline example in `otel-config-example.yaml`      |

### MetricsHub Enterprise Connectors v106

#### What's New

| ID                                                                        | Description                            |
| ------------------------------------------------------------------------- | -------------------------------------- |
| [**\#18**](https://github.com/MetricsHub/enterprise-connectors/issues/18) | Added support for Nvidia's DGX servers |

#### Changes and Improvements

| ID                                                                        | Description                                                                                                               |
| ------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| [**\#19**](https://github.com/MetricsHub/enterprise-connectors/issues/19) | **HPE Synergy**: Added the ability to configure login domain through the `authLoginDomain` variable                       |
| [**\#21**](https://github.com/MetricsHub/enterprise-connectors/issues/21) | **Dell iDRAC9 (REST)**: Added status and firmware version for the iDRAC management interface                              |
| [**\#26**](https://github.com/MetricsHub/enterprise-connectors/issues/26) | **Brocade SAN Switch**: `hw.network.name` now reported                                                                    |
| [**\#35**](https://github.com/MetricsHub/enterprise-connectors/issues/35) | **Microsoft SQL Server** and **Oracle**: Renamed metric `db.server.active_connections` to `db.server.current_connections` |
| [**\#39**](https://github.com/MetricsHub/enterprise-connectors/issues/39) | **Dell OpenManage Server Administrator**: Memory device failure modes now exposed in logs                                 |
| [**\#40**](https://github.com/MetricsHub/enterprise-connectors/issues/40) | **Microsoft SQL Server** and **Oracle**: Standardized storage metrics under the `db.server.storage` namespace             |
| [**\#44**](https://github.com/MetricsHub/enterprise-connectors/issues/44) | **Juniper Switch**: Temperatures now monitored                                                                            |

#### Fixed Issues

| ID                                                                        | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| ------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [**\#11**](https://github.com/MetricsHub/enterprise-connectors/issues/11) | **Pure Storage FA Series (REST)** and **Pure Storage FA Series v2 (REST)**: Redundant `disk_controller` monitor already reported by the `blade` monitor                                                                                                                                                                                                                                                                                                          |
| [**\#34**](https://github.com/MetricsHub/enterprise-connectors/issues/34) | Incorrect monitor names reported by various connectors: <ul><li>**Brocade SAN Switch**</li> <li>**Dell EMC PowerStore (REST)**</li> <li>**Dell OpenManage Server Administrator**</li> <li>**EMC SMI-S Agent (ECOM)**</li> <li>**Fibre Alliance SNMP Agent (Switches)**</li> <li>**Hitachi HNAS (SNMP)**</li> <li>**IBM AIX - Common**</li> <li>**IBM AIX - SCSI disks**</li> <li>**NetApp Filer (SNMP)**</li> <li>**MegaCLI Managed RAID Controllers**</li></ul> |
| [**\#47**](https://github.com/MetricsHub/enterprise-connectors/issues/47) | **Cisco Entity Sensor (SNMP)**: Excessive power consumption values observed on certain Cisco devices                                                                                                                                                                                                                                                                                                                                                             |
| [**\#52**](https://github.com/MetricsHub/enterprise-connectors/issues/52) | **Cisco UCS B-Series (SNMP)**: CPU frequency incorrectly reported in the `name` attribute                                                                                                                                                                                                                                                                                                                                                                        |

### MetricsHub Community Edition v1.0.03

#### What's New

| ID                                                                         | Description                                    |
| -------------------------------------------------------------------------- | ---------------------------------------------- |
| [**\#650**](https://github.com/MetricsHub/metricshub-community/issues/650) | Added support for multiple configuration files |

#### Changes and Improvements

| ID                                                                         | Description                                                                 |
| -------------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| [**\#628**](https://github.com/MetricsHub/metricshub-community/issues/628) | Enhanced connectors parser to better handle Enterprise Connector variables  |
| [**\#651**](https://github.com/MetricsHub/metricshub-community/issues/651) | The MetricsHub core engine now dynamically generates Hardware monitor names |

#### Fixed Issues

| ID                                                                         | Description                                                                                             |
| -------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| [**\#503**](https://github.com/MetricsHub/metricshub-community/issues/503) | `hw.host.power` metric incorrectly reported as both estimated and measured for the same host            |
| [**\#662**](https://github.com/MetricsHub/metricshub-community/issues/662) | Incorrect conversion of WMI unsigned 32-bit integers caused invalid performance counter values          |
| [**\#674**](https://github.com/MetricsHub/metricshub-community/issues/674) | NullPointerException in Connector AWK scripts caused by race condition in the MetricsHub JAWK extension |

#### Documentation Updates

| ID                                                                         | Description                             |
| -------------------------------------------------------------------------- | --------------------------------------- |
| [**\#486**](https://github.com/MetricsHub/metricshub-community/issues/486) | Documented the Datadog Integration      |
| [**\#655**](https://github.com/MetricsHub/metricshub-community/issues/655) | Documented the Self-Observability setup |

### MetricsHub Community Connectors v1.0.11

#### What's New

| ID                                                                         | Description                  |
| -------------------------------------------------------------------------- | ---------------------------- |
| [**\#200**](https://github.com/MetricsHub/community-connectors/issues/200) | Added support for PostgreSQL |

#### Changes and Improvements

| ID                                                                         | Description                                                                                                                                  |
| -------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| [**\#199**](https://github.com/MetricsHub/community-connectors/issues/199) | **lm_sensors**: Connector now acts as fallback when temperatures are unavailable from other connectors                                       |
| [**\#212**](https://github.com/MetricsHub/community-connectors/issues/212) | **MySQL**: Renamed metric `db.server.active_connections` to `db.server.current_connections`                                                  |
| [**\#218**](https://github.com/MetricsHub/community-connectors/issues/218) | **Linux System**: Added memory swap metrics                                                                                                  |
| [**\#221**](https://github.com/MetricsHub/community-connectors/issues/221) | **MySQL**: Standardized storage metrics under the `db.server.storage` namespace                                                              |
| [**\#224**](https://github.com/MetricsHub/community-connectors/issues/224) | **Windows System** and **Linux System**: Added `system.network.bandwidth.limit` metric and optionally `system.network.bandwidth.utilization` |

#### Fixed Issues

| ID                                                                         | Description                                                                                                                                                     |
| -------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [**\#202**](https://github.com/MetricsHub/community-connectors/issues/202) | **SmartMon Tools**: Failed to discover physical disks                                                                                                           |
| [**\#204**](https://github.com/MetricsHub/community-connectors/issues/204) | **Windows System**: Incorrect values reported for `system.disk.io_time` and `system.disk.operation_time` metrics                                                |
| [**\#205**](https://github.com/MetricsHub/community-connectors/issues/205) | **Linux System**: Incorrect unit reported for `system.disk.io_time` and `system.disk.operation.time` metrics                                                    |
| [**\#209**](https://github.com/MetricsHub/community-connectors/issues/209) | Incorrect monitor names reported by various connectors: <ul><li>**IPMI**</li> <li>**SmartMon Tools**</li> <li>**WMI - Disks**</li><li>**lm_sensors**</li> </ul> |
| [**\#211**](https://github.com/MetricsHub/community-connectors/issues/211) | **Linux System**: `system.memory.utilization{system.memory.state="used"}` is incorrectly calculated                                                             |
| [**\#215**](https://github.com/MetricsHub/community-connectors/issues/215) | **Linux System**: Metric incorrectly named `system.disk.operation.time` instead of `system.disk.operation_time`                                                 |
| [**\#220**](https://github.com/MetricsHub/community-connectors/issues/220) | **Windows System**: Inaccurate paging metrics and missing handling for systems without a pagefile                                                               |
| [**\#229**](https://github.com/MetricsHub/community-connectors/issues/229) | **Windows System**: The `system.paging.type` attribute returned wrong labels (`soft`, `hard` instead of expected `major`, `minor`)                              |
| [**\#231**](https://github.com/MetricsHub/community-connectors/issues/231) | **Linux System**: Incorrect attribute name `system.paging.type` used instead of `system.paging.direction` for `system.paging.operations` metrics                |
| [**\#210**](https://github.com/MetricsHub/community-connectors/issues/210) | **Windows - DiskPart**: The connector is tested remotely whereas it should work only locally                                                                    |

## MetricsHub Enterprise Edition v1.2.00

### MetricsHub Enterprise Edition v1.2.00

#### What's New

| ID       | Description                                                                                         |
| -------- | --------------------------------------------------------------------------------------------------- |
| M8BEE-45 | Simplified deployment with new Docker image for MetricsHub Enterprise                               |
| M8BEE-49 | Added `jawk` CLI, a command-line interface for executing AWK scripts                                |
| M8BEE-50 | Integrated MetricsHub Community metrics exporter for optimized resource usage and memory efficiency |
| M8BEE-53 | Published configuration examples needed for Docker setup                                            |
| M8BEE-55 | Integrated Datadog pipeline into `otel-config-example.yaml`                                         |

#### Changes and Improvements

| ID       | Description                                                                                       |
| -------- | ------------------------------------------------------------------------------------------------- |
| M8BEE-46 | Prometheus Alertmanager: Alerts are now triggered for degraded voltage levels (both low and high) |
| M8BEE-48 | Added SNMPv3 authentication examples to `metricshub-example.yaml`                                 |

#### Fixed Issues

| ID       | Description                                                                                             |
| -------- | ------------------------------------------------------------------------------------------------------- |
| M8BEE-47 | Security Vulnerabilities: CVE-2022-46364, CVE-2024-28752, CVE-2022-46363, CVE-2025-23184, CVE-2024-7254 |

### MetricsHub Enterprise Connectors v105

#### What's New

| ID     | Description                                      |
| ------ | ------------------------------------------------ |
| EC-3   | Added support for Dell EMC PowerMax              |
| EC-4   | Added support for Hitachi Disk Arrays            |
| EC-70  | Added support for HPE MSA 2060 via HTTP API      |
| EC-107 | Added support for Oracle databases               |
| EC-109 | Added support for Microsoft SQL Server databases |
| EC-118 | Added support for DotHill storage systems        |

#### Changes and Improvements

| ID     | Description                                                                                     |
| ------ | ----------------------------------------------------------------------------------------------- |
| EC-32  | **HPE OneView (Frames and Blades)**: Blade ID now corresponds to the Blade Server Hostname/FQDN |
| EC-33  | **HPE OneView (Frames and Blades)**: Added Ambient Temperature on Enclosure Frames              |
| EC-36  | **HPE OneView (Frames and Blades)**: Added Blades Server Network Card monitoring                |
| EC-92  | **HPE OneView (Frames and Blades)**: Added InterConnect port monitoring                         |
| EC-126 | **NetApp Filer (REST)**: Improved controller visibility and network metrics collection          |
| EC-130 | **Cisco Ethernet Switch**: `hw.network.name` and `hw.network.alias` now reported                |
| EC-134 | **Juniper Switch**: `hw.network.name` and `hw.network.alias` now reported                       |
| EC-135 | **Cisco Entity Sensor (SNMP)**: `hw.network.name` and `hw.network.alias` now reported           |

#### Fixed Issues

| ID     | Description                                                                                                      |
| ------ | ---------------------------------------------------------------------------------------------------------------- |
| EC-34  | **HPE OneView (Frames and Blades)**: CPU instances are not reported under Blades                                 |
| EC-117 | **Dell MX Chassis and Blades (REST)**: Sled Inlet Temperature is in ALARM after blade reboot                     |
| EC-119 | **Lenovo ThinkSystem (IMM, XCC)**: Threshold is not computed for `hw.voltage.limit` metric                       |
| EC-120 | **Citrix Netscaler (SNMP)**: Connector incorrectly activates on a Linux server                                   |
| EC-123 | **Cisco Ethernet Switch**: Connector may fail to activate for some Cisco switches                                |
| EC-129 | **IBM Director Agent 6 - Windows**: Enclosures are duplicated                                                    |
| EC-132 | **HPE iLO 5 (ProLiant Gen10 and Gen10 Plus)**: `hw.network.bandwidth.limit` is reported in bits instead of bytes |

### MetricsHub Community Edition v1.0.02

#### What's New

| ID                                                                         | Description                                                          |
| -------------------------------------------------------------------------- | -------------------------------------------------------------------- |
| [**\#583**](https://github.com/metricshub/metricshub-community/issues/583) | Added `jawk` CLI, a command-line interface for executing AWK scripts |

#### Changes and Improvements

| ID                                                                         | Description                                                                                            |
| -------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------ |
| [**\#339**](https://github.com/metricshub/metricshub-community/issues/339) | **MetricsHub Agent**: Improved OpenTelemetry resource usage                                            |
| [**\#575**](https://github.com/metricshub/metricshub-community/issues/575) | **Authentication Protocols**: Added support for SNMPv3 HMAC-SHA-2 (SHA224, SHA256, SHA384, and SHA512) |
| [**\#576**](https://github.com/metricshub/metricshub-community/issues/576) | **Authentication Protocols**: Added support for IPMI-over-LAN (SHA-256 and MD5)                        |
| [**\#582**](https://github.com/metricshub/metricshub-community/issues/582) | **HTTP CLI**: Improved usability with simplified syntax                                                |
| [**\#620**](https://github.com/metricshub/metricshub-community/issues/620) | **Health Check**: Added support for Oracle and Cassandra databases                                     |
| [**\#626**](https://github.com/metricshub/metricshub-community/issues/626) | **Metrics Collection**: The `id` attribute is now optional                                             |
| [**\#634**](https://github.com/metricshub/metricshub-community/issues/634) | **MetricsHub CLI**: Added the `--job-timeout` option                                                   |

#### Fixed Issues

| ID                                                                         | Description                                                                                                 |
| -------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| [**\#572**](https://github.com/metricshub/metricshub-community/issues/572) | **HTTP CLI**: A `NullPointerException` occurs when no password is provided                                  |
| [**\#574**](https://github.com/metricshub/metricshub-community/issues/574) | Extensions are not updated during upgrade                                                                   |
| [**\#577**](https://github.com/metricshub/metricshub-community/issues/577) | **IPMI CLI**: An `ArrayIndexOutOfBoundsException` occurs when running the `ipmitool` command                |
| [**\#580**](https://github.com/metricshub/metricshub-community/issues/580) | **SNMPv3 CLI**: The `--snmpv3-retry-intervals` option is not standardized                                   |
| [**\#584**](https://github.com/metricshub/metricshub-community/issues/584) | **Security Vulnerabilities**: CVE-2022-46364, CVE-2024-28752, CVE-2022-46363, CVE-2025-23184, CVE-2024-7254 |
| [**\#590**](https://github.com/metricshub/metricshub-community/issues/590) | **Metrics Collection**: `hw_voltage_limit_volts{limit_type="low.critical"}` is incorrectly collected        |
| [**\#597**](https://github.com/metricshub/metricshub-community/issues/597) | **[BREAKING_CHANGE]** Renamed `metricshub.host.up.response_time` to `metricshub.host.response_time`         |
| [**\#605**](https://github.com/metricshub/metricshub-community/issues/605) | An incorrect JAR name prevents MetricsHub from starting                                                     |
| [**\#608**](https://github.com/metricshub/metricshub-community/issues/608) | **SNMP CLI**: The `--snmp-retry-intervals` option is not standardized                                       |
| [**\#614**](https://github.com/metricshub/metricshub-community/issues/614) | **Metrics Collection**: `metricshub.host.response_time` is reported even when `metricshub.host.up` is down  |
| [**\#619**](https://github.com/metricshub/metricshub-community/issues/619) | **SNMP Logs**: Text tables are not properly displayed                                                       |
| [**\#623**](https://github.com/metricshub/metricshub-community/issues/623) | **JDBC Protocol:** URL is not generated when database type is `mssql`                                       |

#### Documentation Updates

| ID                                                                         | Description                                                      |
| -------------------------------------------------------------------------- | ---------------------------------------------------------------- |
| [**\#631**](https://github.com/metricshub/metricshub-community/issues/631) | Documented the Docker deployment setup for MetricsHub Enterprise |
| [**\#632**](https://github.com/metricshub/metricshub-community/issues/632) | Listed the supported operating systems                           |

### MetricsHub Community Connectors v1.0.09

#### What's New

| ID                                                                         | Description                                                                        |
| -------------------------------------------------------------------------- | ---------------------------------------------------------------------------------- |
| [**\#188**](https://github.com/metricshub/community-connectors/issues/188) | Defined semantic conventions for Oracle and SQL Server database metrics            |
| [**\#190**](https://github.com/metricshub/community-connectors/issues/190) | **MySQL:** `db.server.name` attribute is now reported                              |
| [**\#193**](https://github.com/metricshub/community-connectors/issues/193) | **Generic Ethernet Switch:** `hw.network.name` and `hw.network.alias` now reported |

#### Changes and Improvements

| ID                                                                         | Description                                                                          |
| -------------------------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| [**\#177**](https://github.com/metricshub/community-connectors/issues/177) | **Linux System and Windows System**: `network.interface.name` attribute now reported |

#### Fixed Issues

| ID                                                                         | Description                                                                              |
| -------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| [**\#173**](https://github.com/metricshub/community-connectors/issues/173) | **Generic UPS:** Some voltage sensors are not discovered                                 |
| [**\#176**](https://github.com/metricshub/community-connectors/issues/176) | **Linux and Windows System**: `system.device` attribute missing from FS and disk metrics |
| [**\#178**](https://github.com/metricshub/community-connectors/issues/178) | **Linux System**: Incorrect attribute names and invalid mapping syntax on disk metrics   |
| [**\#187**](https://github.com/metricshub/community-connectors/issues/187) | **Linux System**: Docker overlay FS causes inaccurate capacity reporting                 |

#### Documentation Updates

| ID                                                                         | Description                                                |
| -------------------------------------------------------------------------- | ---------------------------------------------------------- |
| [**\#184**](https://github.com/metricshub/community-connectors/issues/184) | Documented `awk` source in the Connector Developer’s Guide |

## MetricsHub Enterprise Edition v1.1.00

### MetricsHub Enterprise Edition v1.1.00

#### What's New

| ID       | Description                                                                                                        |
| -------- | ------------------------------------------------------------------------------------------------------------------ |
| M8BEE-37 | Added protocol CLIs to validate configurations, troubleshoot connectivity, and ensure successful metric extraction |

#### Changes and Improvements

| ID       | Description                                                |
| -------- | ---------------------------------------------------------- |
| M8BEE-38 | Updated OpenTelemetry Collector Contrib to version 0.116.0 |

### MetricsHub Enterprise Connectors v103

#### What's New

| ID    | Description                                                                                      |
| ----- | ------------------------------------------------------------------------------------------------ |
| EC-72 | Added performance and capacity metrics for Pure Storage FlashArray storage systems via REST API  |
| EC-75 | Added performance and capacity metrics for NetApp FAS and AFF storage systems via ONTAP REST API |
| EC-86 | Added support for Citrix NetScaler via SNMP                                                      |

#### Changes and Improvements

| ID     | Description                                                                                                                       |
| ------ | --------------------------------------------------------------------------------------------------------------------------------- |
| EC-10  | Enhanced detection criteria in the `EMC VPLEX Version 5`, `EMC VPLEX Version 6`, and `Huawei OceanStor (REST)` connectors         |
| EC-57  | **Pure Storage FA Series (REST Token Authentication)**: NVRAM modules are now reported as memory monitors                         |
| EC-88  | Added support for HPE ProLiant Gen 11 servers via iLO 6                                                                           |
| EC-90  | **HP iLO Gen 10 (REST)**: Split into two connectors: `HPE iLO 5 (ProLiant Gen10 and Gen10 Plus)` and `HPE iLO 6 (ProLiant Gen11)` |
| EC-91  | **HP iLO Gen 9 (REST)**: Renamed to `HPE iLO4 (ProLiant Gen 8, Gen9)`                                                             |
| EC-100 | **EMC uemcli (VNXe)**: Power and temperature metrics are now collected                                                            |

#### Fixed Issues

| ID    | Description                                                                                                                    |
| ----- | ------------------------------------------------------------------------------------------------------------------------------ |
| EC-84 | **Pure Storage FA Series**: The `hw.parent.type` attribute is reported as `DiskController` instead of `disk_controller`        |
| EC-95 | **Dell EMC PowerStore (REST)**: Metrics are missing for physical disks, network cards, memory modules, fans and power supplies |
| EC-97 | **Pure Storage FA Series (SSH)**: `hw.temperature` metrics are not collected                                                   |
| EC-98 | **Dell iDRAC9 (REST)**: Incorrect JSON response handling leads to HTTP 404 error on network devices                            |

### MetricsHub Enterprise Connectors v104

#### Changes and Improvements

| ID     | Description                                                                                             |
| ------ | ------------------------------------------------------------------------------------------------------- |
| EC-112 | Reduced high CPU usage caused by internal DB queries in `NetAppRESTv2` and `PureStorageREST` connectors |

### MetricsHub Community Edition v1.0.00

#### What's New

| ID                                                                         | Description                                                                                                        |
| -------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| [**\#424**](https://github.com/metricshub/metricshub-community/issues/424) | Added protocol CLIs to validate configurations, troubleshoot connectivity, and ensure successful metric extraction |

#### Changes and Improvements

| ID                                                                         | Description                                                                                                                            |
| -------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| [**\#519**](https://github.com/metricshub/metricshub-community/issues/519) | Updated connector semantics by replacing `leftConcat` with `prepend`, `rightConcat` with `append`, and `osCommand` with `commandLine`. |
| [**\#521**](https://github.com/metricshub/metricshub-community/issues/521) | Updated OpenTelemetry Java dependencies to version `1.45.0`                                                                            |
| [**\#525**](https://github.com/metricshub/metricshub-community/issues/525) | Added the ability to enable or disable self-monitoring                                                                                 |

#### Fixed Issues

| ID                                                                         | Description                                                                                 |
| -------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------- |
| [**\#523**](https://github.com/metricshub/metricshub-community/issues/523) | The `hw.network.up` metric is not reported for connectors with `WARN` or `ALARM` link state |

#### Documentation Updates

| ID                                                                         | Description                                                                                       |
| -------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| [**\#546**](https://github.com/metricshub/metricshub-community/issues/546) | Integrated platform icons and enhanced connectors directory page                                  |
| [**\#541**](https://github.com/metricshub/metricshub-community/issues/541) | Moved use cases from the documentation to [MetricsHub Use Cases](https://metricshub.com/usecases) |
| [**\#533**](https://github.com/metricshub/metricshub-community/issues/533) | Documented the self-monitoring feature                                                            |
| [**\#529**](https://github.com/metricshub/metricshub-community/issues/529) | Created a Troubleshooting section in the user documentation                                       |

### MetricsHub Community Edition v1.0.01

#### Changes and Improvements

| ID                                                                         | Description                                                                        |
| -------------------------------------------------------------------------- | ---------------------------------------------------------------------------------- |
| [**\#563**](https://github.com/metricshub/metricshub-community/issues/563) | MetricsHub Engine Internal DB: Improved memory usage and data types identification |

### MetricsHub Community Connectors v1.0.08

#### What's New

| ID                                                                         | Description                                    |
| -------------------------------------------------------------------------- | ---------------------------------------------- |
| [**\#137**](https://github.com/metricshub/community-connectors/issues/137) | Added support for `MySQL` databases via `JDBC` |

#### Changes and Improvements

| ID                                                                         | Description                                                                       |
| -------------------------------------------------------------------------- | --------------------------------------------------------------------------------- |
| [**\#158**](https://github.com/metricshub/community-connectors/issues/158) | Updated platforms for community connectors                                        |
| [**\#160**](https://github.com/metricshub/community-connectors/issues/160) | Created Storage metric semantic conventions                                       |
| [**\#163**](https://github.com/metricshub/community-connectors/issues/163) | `MIB2Switch` and `GenericSwitchEnclosure` connectors now support Arista platforms |

#### Fixed Issues

| ID                                                                         | Description                                               |
| -------------------------------------------------------------------------- | --------------------------------------------------------- |
| [**\#111**](https://github.com/metricshub/community-connectors/issues/111) | LinuxIPNetwork: Fails to monitor some Ethernet interfaces |

## MetricsHub Enterprise Edition v1.0.02

### MetricsHub Enterprise Edition v1.0.02

#### Changes and Improvements

| ID       | Description                                                                              |
| -------- | ---------------------------------------------------------------------------------------- |
| M8BEE-35 | Replaced deprecated `loggingexporter` with `debugexporter` in `otel-config-example.yaml` |

### MetricsHub Enterprise Connectors v102

#### Changes and Improvements

| ID    | Description                                                                       |
| ----- | --------------------------------------------------------------------------------- |
| EC-87 | The `StatusInformation` of the `led` monitor now reports the `LedIndicator` value |

#### Fixed Issues

| ID    | Description                                                                                                            |
| ----- | ---------------------------------------------------------------------------------------------------------------------- |
| EC-74 | **HP Insight Management Agent - Drive Array**: The `disk_controller` status is not reported                            |
| EC-77 | **Redfish**: Enclosures are duplicated for Dell iDRAC and HP                                                           |
| EC-78 | **Dell OpenManage Server Administrator**: The `hw.enclosure.energy` metric is not converted to Joules                  |
| EC-79 | **Dell XtremIO REST API**: The `hw.parent.type` attribute is reported as `DiskController` instead of `disk_controller` |
| EC-93 | Connectors reporting voltage metrics do not set the `high.critical` threshold                                          |

### MetricsHub Community Edition v0.9.08

#### Changes and Improvements

| ID                                                                         | Description                                                                                        |
| -------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------- |
| [**\#379**](https://github.com/metricshub/metricshub-community/issues/379) | Added support for escaped macros                                                                   |
| [**\#422**](https://github.com/metricshub/metricshub-community/issues/422) | Developed a **JDBC** Extension to enable support for SQL-based connectors                          |
| [**\#432**](https://github.com/metricshub/metricshub-community/issues/432) | Standardized the log messages for all the criteria tests                                           |
| [**\#435**](https://github.com/metricshub/metricshub-community/issues/435) | **[BREAKING_CHANGE]** Added support for multiple variable values for the same connector            |
| [**\#468**](https://github.com/metricshub/metricshub-community/issues/468) | Added support for shared-characteristics for centralized resource configuration                    |
| [**\#470**](https://github.com/metricshub/metricshub-community/issues/470) | Added support for `host.id`, `host.name`, and other attributes as arrays in resource configuration |
| [**\#472**](https://github.com/metricshub/metricshub-community/issues/472) | Prevented sensitive configuration details from being displayed in error logs                       |
| [**\#474**](https://github.com/metricshub/metricshub-community/issues/474) | Handled blank values when creating INSERT queries for `internalDbQuery` Sources                    |
| [**\#498**](https://github.com/metricshub/metricshub-community/issues/498) | Improved monitoring jobs when invoking **Jawk** sources in connectors                              |

#### Fixed Issues

| ID                                                                         | Description                                                             |
| -------------------------------------------------------------------------- | ----------------------------------------------------------------------- |
| [**\#478**](https://github.com/metricshub/metricshub-community/issues/478) | A NullPointerException occurs when processing `HTTP` detection criteria |
| [**\#480**](https://github.com/metricshub/metricshub-community/issues/480) | IPMITool criteria and source fail due to invalid `ipmitool` command     |
| [**\#500**](https://github.com/metricshub/metricshub-community/issues/500) | Only one monitor is processed due to incorrect indexing                 |
| [**\#502**](https://github.com/metricshub/metricshub-community/issues/502) | Incorrect link status check leads to an incorrect power consumption     |

#### Documentation Updates

| ID                                                                         | Description                                                                                                                                                                                                                                                                |
| -------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [**\#462**](https://github.com/metricshub/metricshub-community/issues/462) | Reviewed **Configure Monitoring** documentation                                                                                                                                                                                                                            |
| [**\#462**](https://github.com/metricshub/metricshub-community/issues/462) | Moved the CLI documentation to the Appendix section                                                                                                                                                                                                                        |
| [**\#463**](https://github.com/metricshub/metricshub-community/issues/463) | Combined the Linux and Windows Prometheus quick starts into a unique Prometheus quick start                                                                                                                                                                                |
| [**\#484**](https://github.com/metricshub/metricshub-community/issues/484) | Documented the Prometheus/Grafana integration                                                                                                                                                                                                                              |
| [**\#289**](https://github.com/metricshub/metricshub-community/issues/289) | Documented the use cases: **Monitoring network interfaces using SNMP**, **Monitoring a process on Windows**, **Monitoring a remote system running on Linux**, **Monitoring a service running on Linux**, **Monitoring the Health of a Service**, and **Pinging resources** |
| [**\#505**](https://github.com/metricshub/metricshub-community/issues/505) | Updated references to the deprecated `loggingexporter`                                                                                                                                                                                                                     |

### MetricsHub Community Connectors v1.0.07

#### Changes and Improvements

| ID                                                                         | Description                                                                                                          |
| -------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| [**\#112**](https://github.com/metricshub/community-connectors/issues/112) | **Windows Process**: The process user name is now retrieved and selectable through configuration variables           |
| [**\#143**](https://github.com/metricshub/community-connectors/issues/143) | **Linux System**: The connector no longer reports services, as these are now handled by the `LinuxService` connector |
| [**\#148**](https://github.com/metricshub/community-connectors/issues/148) | **Linux System**: Enhanced `filesystem` utilization calculation                                                      |

#### Fixed Issues

| ID                                                                         | Description                                                                                                                                       |
| -------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| [**\#140**](https://github.com/metricshub/community-connectors/issues/140) | `Platform` mispelling in `Linux` & `LinuxService` connectors                                                                                      |
| [**\#145**](https://github.com/metricshub/community-connectors/issues/145) | **IpmiTool**: The `hw.status` metric is not collected because `enclosure.awk` reports `OK`, `WARN`, `ALARM` instead of `ok`, `degraded`, `failed` |
| [**\#152**](https://github.com/metricshub/community-connectors/issues/152) | Connectors reporting voltage metrics do not set the `high.critical` threshold                                                                     |

#### Documentation Updates

| ID                                                                         | Description                                             |
| -------------------------------------------------------------------------- | ------------------------------------------------------- |
| [**\#128**](https://github.com/metricshub/community-connectors/issues/128) | Documented default connector `variables`                |
| [**\#129**](https://github.com/metricshub/community-connectors/issues/129) | Replaced all references to `sql` with `internalDbQuery` |

## MetricsHub Enterprise Edition v1.0.01

### MetricsHub Enterprise Edition v1.0.01

#### Changes and Improvements

| ID           | Description                                                                                                              |
| ------------ | ------------------------------------------------------------------------------------------------------------------------ |
| **M8BEE-29** | Removed Otel collector resourcedetection processor to prevent localhost system resource attributes from being overridden |
| **M8BEE-32** | Moved the localhost resource configuration to the `data center 1` resource group in `metricshub-example.yaml`            |

### MetricsHub Enterprise Connectors v101

#### Changes and Improvements

| ID       | Description                                                    |
| -------- | -------------------------------------------------------------- |
| **EC-9** | The hw.network.bandwidth.limit metric is now reported in bytes |

#### Fixed Issues

| ID        | Description                                                            |
| --------- | ---------------------------------------------------------------------- |
| **EC-73** | Dell iDRAC9 (REST): Some network link physical addresses are incorrect |

### MetricsHub Community Edition v0.9.07

#### Changes and Improvements

| ID                                                                         | Description                                                                                                              |
| -------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| [**\#433**](https://github.com/metricshub/metricshub-community/issues/433) | **[BREAKING_CHANGE]** Disabled Automatic Hostname to FQDN resolution                                                     |
| [**\#427**](https://github.com/metricshub/metricshub-community/issues/427) | BMC Helix Integration: Added the `StatusInformation` internal text parameter to the connector monitor                    |
| [**\#421**](https://github.com/metricshub/metricshub-community/issues/421) | Reduced Alert noise for `hw.status{state="present"}`                                                                     |
| [**\#414**](https://github.com/metricshub/metricshub-community/issues/414) | Added a link to MetricsHub Community Connectors 1.0.06                                                                   |
| [**\#412**](https://github.com/metricshub/metricshub-community/issues/412) | The `hw.status{state="present"}` metric is no longer reported for cpu monitors discovered by Linux and Window connectors |
| [**\#383**](https://github.com/metricshub/metricshub-community/issues/383) | Implemented a new engine method `megaBit2Byte` to align with OpenTelemetry unit standards                                |
| [**\#374**](https://github.com/metricshub/metricshub-community/issues/374) | Default connector variables can now be specified in YAML connector files                                                 |
| [**\#302**](https://github.com/metricshub/metricshub-community/issues/302) | Defined `afterAll` and `beforeAll` jobs in YAML connectors                                                               |
| [**\#423**](https://github.com/metricshub/metricshub-community/issues/423) | Added the ability to filter monitors                                                                                     |

#### Fixed Issues

| ID                                                                         | Description                                                                      |
| -------------------------------------------------------------------------- | -------------------------------------------------------------------------------- |
| [**\#436**](https://github.com/metricshub/metricshub-community/issues/436) | The log message for SNMP v3 credential validation is incorrect                   |
| [**\#439**](https://github.com/metricshub/metricshub-community/issues/439) | Connector default variables are not serializable                                 |
| [**\#417**](https://github.com/metricshub/metricshub-community/issues/417) | JavaDoc references are incorrect                                                 |
| [**\#410**](https://github.com/metricshub/metricshub-community/issues/410) | Protocol definition is applied to only one host in a multiple-host configuration |
| [**\#368**](https://github.com/metricshub/metricshub-community/issues/368) | The `hw.power{hw.type="vm"}` metric is erroneously set to 0                      |
| [**\#456**](https://github.com/metricshub/metricshub-community/issues/456) | An exception occurs when monitoring ESXi through vCenter authentication          |

### MetricsHub Community Connectors v1.0.06

#### Changes and Improvements

| ID                                                                         | Description                                                                                                         |
| -------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| [**\#125**](https://github.com/metricshub/community-connectors/issues/125) | Disabled automatic detection for WindowsProcess, WindowsService, and LinuxService                                   |
| [**\#122**](https://github.com/metricshub/community-connectors/issues/122) | Added default values for connector variables in `WindowsService`, `LinuxService`, `WindowsProcess` & `LinuxProcess` |
| [**\#114**](https://github.com/metricshub/community-connectors/issues/114) | The `hw.network.bandwidth.limit` metric is now displayed in bytes                                                   |

#### Fixed Issues

| ID                                                                         | Description                                                              |
| -------------------------------------------------------------------------- | ------------------------------------------------------------------------ |
| [**\#120**](https://github.com/metricshub/community-connectors/issues/120) | The `hw.vm.power_ratio` unit is incorrect. It should be 1 instead of Cel |
