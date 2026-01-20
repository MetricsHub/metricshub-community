keywords: release notes
description: Learn more about the new features, changes and improvements, and bug fixes brought to MetricsHub Enterprise.

# Release Notes

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

## MetricsHub Enterprise Edition v3.9.00

### MetricsHub Enterprise Edition v3.9.00

#### What's New

| ID                                                                         | Description                                                                                                          |
|----------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| [**#157**](https://github.com/MetricsHub/metricshub-enterprise/issues/157) | Added [`winremotecli`](./troubleshooting/cli/winremote.md) to execute remote Windows commands via WMI or WinRM       |
| [**#161**](https://github.com/MetricsHub/metricshub-enterprise/issues/161) | Added the [MetricsHub Web UI](./operating-web-ui.md), featuring configuration, metrics explorer, and an AI assistant |

#### Changes and Improvements

| ID                                                                         | Description                                                                                                                                            |
|----------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| [**#165**](https://github.com/MetricsHub/metricshub-enterprise/issues/165) | Upgraded OpenTelemetry Collector Contrib to version [0.144.0](https://github.com/open-telemetry/opentelemetry-collector-contrib/releases/tag/v0.144.0) |

### MetricsHub Enterprise Connectors v112

#### What's New

| ID                                                                       | Description                                                                           |
|--------------------------------------------------------------------------|---------------------------------------------------------------------------------------|
| [**#29**](https://github.com/MetricsHub/enterprise-connectors/issues/29) | Added support for **[HP-UX](./connectors/hpuxsystem.html)** systems via command lines |

#### Changes and Improvements

| ID                                                                         | Description                                                                                                                                             |
|----------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| [**#38**](https://github.com/MetricsHub/enterprise-connectors/issues/38)   | **[Nimble](./connectors/nimble.html)**: Controller power supplies are now monitored on HPE Alletra 6000 systems                                         |
| [**#169**](https://github.com/MetricsHub/enterprise-connectors/issues/169) | **[HPE iLO4 (ProLiant Gen8, Gen9)](./connectors/hpegen9ilorest.html)**: Credentials are now validated during detection                                  |
| [**#169**](https://github.com/MetricsHub/enterprise-connectors/issues/169) | **[HPE iLO 5 (ProLiant Gen10)](./connectors/hpegen10ilorest.html)**: Credentials are now validated during detection                                     |
| [**#169**](https://github.com/MetricsHub/enterprise-connectors/issues/169) | **[HPE iLO 6 (ProLiant Gen11)](./connectors/hpeilo6rest.html)**: Credentials are now validated during detection                                         |
| [**#173**](https://github.com/MetricsHub/enterprise-connectors/issues/173) | **[Pure Storage FA Series (REST Token Authentication)](./connectors/purestorageresttoken.html)**: Added Storage System performance and capacity metrics |

### MetricsHub Community Edition v3.9.00

#### What's New

| ID                                                                          | Description                                                                                                                      |
|-----------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------|
| [**#983**](https://github.com/MetricsHub/metricshub-community/issues/983)   | Added support for executing remote Windows commands via SSH                                                                      |
| [**#1016**](https://github.com/MetricsHub/metricshub-community/issues/1016) | Added [`winremotecli`](./troubleshooting/cli/winremote.md) to execute remote Windows commands via WMI or WinRM                   |
| [**#1017**](https://github.com/MetricsHub/metricshub-community/issues/1017) | Added the `ExecuteWinRemoteCommand` [MCP Tool](./integrations/ai-agent-mcp.md) to run remote Windows commands via WMI or WinRM   |
| [**#1028**](https://github.com/MetricsHub/metricshub-community/issues/1028) | Added support for [Windows Event Log monitoring](./custom/windows-event-log.md) via WMI                                          |
| [**#1070**](https://github.com/MetricsHub/metricshub-community/issues/1070) | Added the [MetricsHub Web UI](./operating-web-ui.md), featuring configuration, metrics explorer, and an AI assistant             |

#### Changes and Improvements

| ID                                                                          | Description                                                                                                    |
|-----------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------|
| [**#1009**](https://github.com/MetricsHub/metricshub-community/issues/1009) | The [installer](./installation/index.md) now registers MetricsHub as a background service on Windows and Linux |

### MetricsHub Community Connectors v1.0.18

#### What's New

| ID                                                                        | Description                                                                      |
|---------------------------------------------------------------------------|----------------------------------------------------------------------------------|
| [**#99**](https://github.com/MetricsHub/community-connectors/issues/99)   | Added support for **[AMD Radeon GPU](./connectors/amdradeon.html)** via ROCm SMI |
| [**#322**](https://github.com/MetricsHub/community-connectors/issues/322) | Added support for **[MariaDB](./connectors/mariadb.html)** databases via JDBC    |
| [**#329**](https://github.com/MetricsHub/community-connectors/issues/329) | Added support for servers exposing **[RedFish API](./connectors/redfish.html)**  |

#### Changes and Improvements

| ID                                                                        | Description                                                                                                        |
|---------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| [**#323**](https://github.com/MetricsHub/community-connectors/issues/323) | **[Windows File Monitoring](./connectors/windowsfile.html)**: Detection now supports WMI, WinRM, and SSH protocols |

#### Fixed Issues

| ID                                                                        | Description                                                                           |
|---------------------------------------------------------------------------|---------------------------------------------------------------------------------------|
| [**#328**](https://github.com/MetricsHub/community-connectors/issues/328) | **[Linux System](./connectors/linux.html)**: Raspberry Pi SD cards are not discovered |

## MetricsHub Enterprise Edition v3.0.03

### MetricsHub Enterprise Edition v3.0.03

#### Changes and Improvements

| ID                                                                         | Description                                                                                                                                            |
|----------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| [**#147**](https://github.com/MetricsHub/metricshub-enterprise/issues/147) | Upgraded OpenTelemetry Collector Contrib to version [0.140.1](https://github.com/open-telemetry/opentelemetry-collector-contrib/releases/tag/v0.140.1) |

### MetricsHub Enterprise Connectors v111

#### What's New

| ID                                                                         | Description                                                                           |
|----------------------------------------------------------------------------|---------------------------------------------------------------------------------------|
| [**#147**](https://github.com/MetricsHub/enterprise-connectors/issues/147) | Added support for **[H3C UniServer Series](./connectors/h3cuniserver.html)** via SNMP |

#### Changes and Improvements

| ID                                                                         | Description                                                                                                                                                                 |
|----------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [**#134**](https://github.com/MetricsHub/enterprise-connectors/issues/134) | **[Citrix NetScaler (REST)](./connectors/citrixnetscalerrest.html)**: Added new load balancer and service metrics to detect backend failures and performance issues         |
| [**#143**](https://github.com/MetricsHub/enterprise-connectors/issues/143) | **[SuperMicro (REST)](./connectors/supermicro.html)**: All HTTP requests now include the `Accept: application/json` header for consistent data retrieval                    |
| [**#157**](https://github.com/MetricsHub/enterprise-connectors/issues/157) | **[Dell iDRAC9 (REST)](./connectors/dellidracrest.html)** now reports remaining SSD lifetime through the `hw.physical_disk.endurance_utilization{state="remaining"}` metric |
| [**#158**](https://github.com/MetricsHub/enterprise-connectors/issues/158) | **[Dell iDRAC9 (REST)](./connectors/dellidracrest.html)** now reports network errors on HBA and NIC interfaces                                                              |

#### Fixed Issues

| ID                                                                         | Description                                                                                                                                                                                                               |
|----------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [**#139**](https://github.com/MetricsHub/enterprise-connectors/issues/139) | **[HPE OneView (Frames and Blades)](./connectors/hpesynergy.html)**: <ul><li>Incorrect Smart Storage Batteries alerts on Gen11</li><li>Interconnect interfaces are not excluded</li><li>Truncated attribute ids</li></ul> |
| [**#142**](https://github.com/MetricsHub/enterprise-connectors/issues/142) | **[Cisco MDS9000 Series (SSH)](./connectors/ciscossh.html)**: Incorrect status reported for Power Supplies                                                                                                                |
| [**#146**](https://github.com/MetricsHub/enterprise-connectors/issues/146) | **[HPE iLO4 (Proliant Gen 8, Gen9)](./connectors/hpegen9ilorest.html)**: Incorrect Temperature alerts generated                                                                                                           |
| [**#152**](https://github.com/MetricsHub/enterprise-connectors/issues/152) | **[HP-UX (WBEM)](./connectors/hpuxwbem.html)**: CPUs are not discovered                                                                                                                                                   |
| [**#160**](https://github.com/MetricsHub/enterprise-connectors/issues/160) | **[RedFish (REST)](./connectors/redfish.html)**: The fan name is not meaningful                                                                                                                                           |

### MetricsHub Community Edition v1.0.14

#### Changes and Improvements

| ID                                                                        | Description                                                         |
|---------------------------------------------------------------------------|---------------------------------------------------------------------|
| [**#904**](https://github.com/MetricsHub/metricshub-community/issues/904) | Log files now sanitize sensitive HTTP information                   |
| [**#934**](https://github.com/MetricsHub/metricshub-community/issues/934) | Updated package names to match RPM, DEB, and MSI naming conventions |

#### Fixed Issues

| ID                                                                        | Description                                                                       |
|---------------------------------------------------------------------------|-----------------------------------------------------------------------------------|
| [**#972**](https://github.com/MetricsHub/metricshub-community/issues/972) | The collect stops when the connector no longer matches the monitored host         |
| [**#976**](https://github.com/MetricsHub/metricshub-community/issues/976) | Connectors may fail to collect metrics when processing negative or signed values. |

### MetricsHub Community Connectors v1.0.17

#### What's New

| ID                                                                        | Description                                                                |
|---------------------------------------------------------------------------|----------------------------------------------------------------------------|
| [**#311**](https://github.com/MetricsHub/community-connectors/issues/311) | Added **[Linux file monitoring](./connectors/linuxfile.html)** via SSH     |
| [**#312**](https://github.com/MetricsHub/community-connectors/issues/312) | Added **[Windows file monitoring](./connectors/windowsfile.html)** via WMI |

## MetricsHub Enterprise Edition v3.0.02

### MetricsHub Enterprise Edition v3.0.02

#### What's New

| ID                                                                       | Description                                                         |
|--------------------------------------------------------------------------|---------------------------------------------------------------------|
| [**#89**](https://github.com/MetricsHub/metricshub-enterprise/issues/89) | Installable packages are now available for Red Hat and Debian ARM64 |

#### Changes and Improvements

| ID                                                                         | Description                                                                                                                                            |
|----------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| [**#116**](https://github.com/MetricsHub/metricshub-enterprise/issues/116) | Upgraded OpenTelemetry Collector Contrib to version [0.138.0](https://github.com/open-telemetry/opentelemetry-collector-contrib/releases/tag/v0.138.0) |

### MetricsHub Enterprise Connectors v110

#### What's New

| ID                                                                       | Description                                                                                                                   |
|--------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------|
| [**#92**](https://github.com/MetricsHub/enterprise-connectors/issues/92) | Added support for **[Cisco UCS C885A](./connectors/ciscoucsc885a.html)** rack servers via Redfish API                         |
| [**#96**](https://github.com/MetricsHub/enterprise-connectors/issues/96) | Added support for **[Dell PowerFlex 3](./connectors/dellpowerflexrestv3.html)** storage systems via REST API                  |
| [**#96**](https://github.com/MetricsHub/enterprise-connectors/issues/96) | Added support for **[Dell PowerFlex 4](./connectors/dellpowerflexrestv4.html)** storage systems via REST API                  |
| [**#99**](https://github.com/MetricsHub/enterprise-connectors/issues/99) | Added performance and capacity metrics for **[Dell EMC PowerStore](./connectors/dellemcpowerstorerest.html)** storage systems |

#### Changes and Improvements

| ID                                                                         | Description                                                                                                                                     |
|----------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| [**#111**](https://github.com/MetricsHub/enterprise-connectors/issues/111) | **[Dell XtremIO - REST](./connectors/dellemcxtremiorest.html)**: The Storage System Subscribed and Configured Capacity metrics are now reported |
| [**#136**](https://github.com/MetricsHub/enterprise-connectors/issues/136) | **[EMC Isilon Cluster (REST)](./connectors/emcisilonrest.html)**: Added vendor and model attributes to the Storage System metrics               |

#### Fixed Issues

| ID                                                                         | Description                                                                                                                                                                                                |
|----------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [**#114**](https://github.com/MetricsHub/enterprise-connectors/issues/114) | **[HPE iLO 6 (ProLiant Gen11)](./connectors/hpeilo6rest.html)**: An "Invalid credentials" error message was incorrectly displayed for HTTP detection criteria that did not require authentication          |
| [**#115**](https://github.com/MetricsHub/enterprise-connectors/issues/115) | **[HPE iLO 5 (ProLiant Gen10)](./connectors/hpegen10ilorest.html)**: An "Invalid credentials" error message was incorrectly displayed for HTTP detection criteria that did not require authentication      |
| [**#116**](https://github.com/MetricsHub/enterprise-connectors/issues/116) | **[HPE iLO4 (ProLiant Gen8 or Gen9)](./connectors/hpegen9ilorest.html)**: An "Invalid credentials" error message was incorrectly displayed for HTTP detection criteria that did not require authentication |
| [**#119**](https://github.com/MetricsHub/enterprise-connectors/issues/119) | **[NetApp Filer (REST)](./connectors/netapprestv2.html)**: Storage System capacity metrics are inaccurately reported                                                                                       |

### MetricsHub Community Edition v1.0.10

#### Changes and Improvements

| ID                                                                        | Description                                                                                                                                  |
|---------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| [**#930**](https://github.com/MetricsHub/metricshub-community/issues/930) | Exporting metrics to the OpenTelemetry Collector now consumes less memory                                                                    |
| [**#941**](https://github.com/MetricsHub/metricshub-community/issues/941) | MetricsHub Community is bundled with Community Connectors [v1.0.16](https://github.com/MetricsHub/community-connectors/releases/tag/v1.0.16) |

### MetricsHub Community Edition v1.0.09

#### What's New

| ID                                                                        | Description                                                                                                                                                |
|---------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [**#766**](https://github.com/MetricsHub/metricshub-community/issues/766) | AI Agents: A new set of [MCP Tools](./integrations/ai-agent-mcp.md) is now available to run custom requests (`SNMP GET`, `HTTP GET`, `WMI`, `IPMI`, etc.). |
| [**#851**](https://github.com/MetricsHub/metricshub-community/issues/851) | MetricsHub Community can now be installed directly via [get.metricshub.com](./installation/debian-linux.md#option-1-automatic-28recommended-29)            |

#### Changes and Improvements

| ID                                                                        | Description                                                                                                                |
|---------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------|
| [**#771**](https://github.com/MetricsHub/metricshub-community/issues/771) | Metrics metadata can now be included directly within [monitoring jobs](./custom/index.md).                                 |
| [**#834**](https://github.com/MetricsHub/metricshub-community/issues/834) | [Programmable Configurations](./configuration/configure-monitoring.md#programmable-configuration) now support SQL queries. |
| [**#837**](https://github.com/MetricsHub/metricshub-community/issues/837) | [MCP Tools](./integrations/ai-agent-mcp.md) are now able to report information for multiple hosts through a single query.  |

#### Fixed Issues

| ID                                                                        | Description                                                   |
|---------------------------------------------------------------------------|---------------------------------------------------------------|
| [**#843**](https://github.com/MetricsHub/metricshub-community/issues/843) | Connectors may fail to load due to unmanaged external scripts |
| [**#869**](https://github.com/MetricsHub/metricshub-community/issues/869) | Security Vulnerability CVE-2025-41249                         |

#### Documentation Updates

| ID                                                                        | Description                                                                                                                                                         |
|---------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [**#849**](https://github.com/MetricsHub/metricshub-community/issues/849) | Clarified Velocity Template Syntax in the [Programmable Configuration example](./configuration/configure-monitoring.md#example-loading-resources-from-an-http-api). |

### MetricsHub Community Connectors v1.0.16

#### Fixed Issues

| ID                                                                        | Description                                                                                    |
|---------------------------------------------------------------------------|------------------------------------------------------------------------------------------------|
| [**#297**](https://github.com/MetricsHub/community-connectors/issues/297) | **[Ethernet Switch with Sensors (SNMP)](./connectors/mib2switch.html)**: Fans are not detected |

### MetricsHub Community Connectors v1.0.15

#### Fixed Issues

| ID                                                                        | Description                                                                                                                                       |
|---------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| [**#283**](https://github.com/MetricsHub/community-connectors/issues/283) | **[Linux System](./connectors/linux.html)**: Network interface names may be parsed incorrectly or not detected when using `ls -l /sys/class/net`. |
| [**#291**](https://github.com/MetricsHub/community-connectors/issues/291) | **[Linux System](./connectors/linux.html)**: A negative value is reported for `system.network.bandwidth.limit`                                    |

## MetricsHub Enterprise Edition v3.0.01

### MetricsHub Enterprise Edition v3.0.01

#### Changes and Improvements

| ID                                                                          | Description                                                                                                                                                                                                                                                    |
|-----------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [**\#98**](https://github.com/MetricsHub/metricshub-enterprise/issues/98)   | Added a CLI tool (`user`) to securely create, list, and delete REST API users for JWT-based authentication.                                                                                                                                                    |
| [**\#100**](https://github.com/MetricsHub/metricshub-enterprise/issues/100) | Upgraded OpenTelemetry Collector Contrib to version [0.136.0](https://github.com/open-telemetry/opentelemetry-collector-contrib/releases/tag/v0.136.0). Refer to [Upgrading to v3.0.01](./upgrade.md#upgrading-to-v3001) for installation upgrade instructions |

### MetricsHub Enterprise Connectors v109

#### What's New

| ID                                                                        | Description                                                                                      |
|---------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------|
| [**\#30**](https://github.com/MetricsHub/enterprise-connectors/issues/30) | Added support for [**AIX**](./connectors/aix.html) systems via command lines                     |
| [**\#70**](https://github.com/MetricsHub/enterprise-connectors/issues/70) | Added support for [**EMC Isilon**](./connectors/emcisilonrest.html) storage systems via REST API |

#### Changes and Improvements

| ID                                                                        | Description                                                                                                                                                                                                                                                                                                                                                                                     |
|---------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [**\#72**](https://github.com/MetricsHub/enterprise-connectors/issues/72) | The `storage.size` metric is now collected for storage systems and physical disks by the following storage array connectors: <ul><li>**[Dell EMC PowerMax Storage (REST)](./connectors/dellemcpowermaxrest.html)**</li><li>**[Pure Storage FA Series (REST)](./connectors/purestoragerest.html)**</li><li>**[Pure Storage FA Series v2 (REST)](./connectors/purestoragerestv2.html)**</li></ul> |

#### Fixed Issues

| ID                                                                          | Description                                                                                                                                                                                                                                                                                             |
|-----------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [**\#90**](https://github.com/MetricsHub/enterprise-connectors/issues/90)   | **[Hitachi (REST)](./connectors/hitachirest.html)**: <ul><li>The consumed capacity metric uses the wrong unit for storage pools</li><li>The configured capacity metric is missing for storage pools</li><li>The consumed capacity for traditional volumes does not match their total capacity</li></ul> |
| [**\#94**](https://github.com/MetricsHub/enterprise-connectors/issues/94)   | **[NetApp Filer (REST)](./connectors/netapprest.html)**: The physical disk `speed` is wrongly exposed as a metric instead of an attribute.                                                                                                                                                              |
| [**\#100**](https://github.com/MetricsHub/enterprise-connectors/issues/100) | **[Cisco UCS Manager (REST)](./connectors/ciscoucsrest.html)**: Early logout during discovery and collect invalidates the session cookie and breaks metric collection                                                                                                                                   |
| [**\#102**](https://github.com/MetricsHub/enterprise-connectors/issues/102) | **[RedFish (REST)](./connectors/redfish.html)**: Connector fails to request REST API URLs                                                                                                                                                                                                               |
| [**\#109**](https://github.com/MetricsHub/enterprise-connectors/issues/109) | **[Brocade SAN Switch](./connectors/brocadeswitch.html)**: The `bandwidth` attribute incorrectly displays the WWN instead of the actual network bandwidth                                                                                                                                               |
| [**\#104**](https://github.com/MetricsHub/enterprise-connectors/issues/104) | **[HPE OneView (Frames and Blades)](./connectors/hpesynergy.html)**: The CPU collect fails due to a missing REST API URL                                                                                                                                                                                |
| [**\#106**](https://github.com/MetricsHub/enterprise-connectors/issues/106) | **[Palo Alto Firewall (SNMP)](./connectors/paloaltofirewall.html)**: The `firewall.sessions` metric reports the protocol attribute as `ucp` instead of `udp`                                                                                                                                            |

### MetricsHub Community Edition v1.0.08

#### What's New

| ID                                                                         | Description                                                                                                                                                                                                      |
|----------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [**\#765**](https://github.com/MetricsHub/metricshub-community/issues/765) | Added the [GetHostDetails MCP Tool](./integrations/ai-agent-mcp.md) to retrieve detailed information about a host                                                                                                |
| [**\#772**](https://github.com/MetricsHub/metricshub-community/issues/772) | [Connector Design] Added the ability to refer to [resource attributes](https://metricshub.org/community-connectors/develop/references.html#resource-attribute-reference) when configuring custom monitoring jobs |
| [**\#773**](https://github.com/MetricsHub/metricshub-community/issues/773) | [Connector Design] Added the ability to refer to [protocol properties](https://metricshub.org/community-connectors/develop/references.html#protocol-reference) when configuring custom monitoring jobs           |
| [**\#796**](https://github.com/MetricsHub/metricshub-community/issues/796) | Added support for JWT authentication in the REST API to enable stateless sessions and improve security                                                                                                           |

#### Changes and Improvements

| ID                                                                         | Description                                                                                                                                                                    |
|----------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [**\#768**](https://github.com/MetricsHub/metricshub-community/issues/768) | MetricsHub no longer passes the deprecated `pkg.translator.prometheus.NormalizeName` feature gate when starting the OpenTelemetry Collector                                    |
| [**\#775**](https://github.com/MetricsHub/metricshub-community/issues/775) | On Windows, the logs are now written to the working directory (`.\logs`) if it is writable, instead of the system logs path                                                    |
| [**\#788**](https://github.com/MetricsHub/metricshub-community/issues/788) | The `metricshub -V` command now outputs both log and config directories                                                                                                        |
| [**\#804**](https://github.com/MetricsHub/metricshub-community/issues/804) | System alert rules now include the `site` attribute                                                                                                                            |
| [**\#810**](https://github.com/MetricsHub/metricshub-community/issues/810) | New API keys and users can now be recognized by MetricsHub on the fly, without requiring an agent restart                                                                      |
| [**\#825**](https://github.com/MetricsHub/metricshub-community/issues/825) | Internal dependency [Jawk](https://github.com/MetricsHub/jawk) has been upgraded to [v4.1.00](https://github.com/MetricsHub/jawk/releases/tag/v4.1.00) for better API handling |

#### Fixed Issues

| ID                                                                         | Description                                                                |
|----------------------------------------------------------------------------|----------------------------------------------------------------------------|
| [**\#777**](https://github.com/MetricsHub/metricshub-community/issues/777) | Table unions are not performed correctly by the core engine on raw results |

### MetricsHub Community Connectors v1.0.14

#### What's New

| ID                                                                         | Description                                                                                           |
|----------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| [**\#277**](https://github.com/MetricsHub/community-connectors/issues/277) | The system semantic conventions now include `system.disk.limit` and `system.filesystem.limit` metrics |

#### Documentation Updates

| ID                                                                         | Description                                                                                                                                                         |
|----------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [**\#273**](https://github.com/MetricsHub/community-connectors/issues/273) | Documented [${esc.d}{resource.attribute::ATTRIBUTE}](https://metricshub.org/community-connectors/develop/references.html#!#resource-attribute-reference) references |
| [**\#278**](https://github.com/MetricsHub/community-connectors/issues/278) | Documented [${esc.d}{protocol::PROTOCOL.PROPERTY}](https://metricshub.org/community-connectors/develop/references.html#!#protocol-reference) references             |

## MetricsHub Enterprise Edition v3.0.00

### MetricsHub Enterprise Edition v3.0.00

#### What's New

| ID                                                                        | Description                                        |
|---------------------------------------------------------------------------|----------------------------------------------------|
| [**\#83**](https://github.com/MetricsHub/metricshub-enterprise/issues/83) | Added CLI executable to manage API keys (`apikey`) |

#### Changes and Improvements

| ID                                                                        | Description                                                                |
|---------------------------------------------------------------------------|----------------------------------------------------------------------------|
| [**\#75**](https://github.com/MetricsHub/metricshub-enterprise/issues/75) | Added `JDBC` and `JMX` configuration examples to `metricshub-example.yaml` |
| [**\#78**](https://github.com/MetricsHub/metricshub-enterprise/issues/78) | Dynamic configuration reloading has been enhanced                          |

### MetricsHub Enterprise Connectors v108

#### What's New

| ID                                                                        | Description                                                                                                                         |
|---------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| [**\#56**](https://github.com/MetricsHub/enterprise-connectors/issues/56) | Added support for [**Citrix NetScaler**](./connectors/citrixnetscalerrest.html) via REST API                                        |
| [**\#61**](https://github.com/MetricsHub/enterprise-connectors/issues/61) | Added support for [**Cisco ASA Firewall**](./connectors/ciscosecurefirewallasa.html) via SNMP                                       |
| [**\#62**](https://github.com/MetricsHub/enterprise-connectors/issues/62) | Added support for [**Arista Switch**](./connectors/aristabgpswitch.html) via SNMP to report `Border Gateway Protocol` (BGP) metrics |

#### Changes and Improvements

| ID                                                                        | Description                                                                                                          |
|---------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| [**\#55**](https://github.com/MetricsHub/enterprise-connectors/issues/55) | **[NetApp Filer (REST)](./connectors/netapprest.html)**: Added QoS latency metrics                                   |
| [**\#59**](https://github.com/MetricsHub/enterprise-connectors/issues/59) | **[EMC SMI-S Agent (ECOM)](./connectors/emcdiskarray.html)**: Fan names now use `+` instead of `-+-`                 |
| [**\#71**](https://github.com/MetricsHub/enterprise-connectors/issues/71) | **[Dell XtremIO (REST)](./connectors/dellemcxtremiorest.html)**: Added performance and capacity metrics via REST API |

#### Fixed Issues

| ID                                                                        | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
|---------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [**\#73**](https://github.com/MetricsHub/enterprise-connectors/issues/73) | The storage array connectors use the `direction` and `storage.direction` attributes whereas they should use the `storage.io.direction` attribute: <ul><li>**[Dell EMC PowerMax Storage (REST)](./connectors/dellemcpowermaxrest.html)**</li><li>**[NetApp Filer (REST)](./connectors/netapprest.html)**</li><li>**[Pure Storage FA Series (REST)](./connectors/purestoragerest.html)**</li><li>**[Pure Storage FA Series v2 (REST)](./connectors/purestoragerestv2.html)**</li><li>**[Dot Hill System (REST)](./connectors/dothillrest.html)**</li></ul> |
| [**\#76**](https://github.com/MetricsHub/enterprise-connectors/issues/76) | **[Dell EMC PowerMax Storage (REST)](./connectors/dellemcpowermaxrest.html)**: Incorrect unit for the `storage.size` metric                                                                                                                                                                                                                                                                                                                                                                                                                              |
| [**\#80**](https://github.com/MetricsHub/enterprise-connectors/issues/80) | **[NetApp Filer (REST)](./connectors/netapprest.html)**: <ul><li>Storage pools are missing</li><li>Volume capacities are incorrectly calculated</li><li>Disk discovery is inaccurate</li></ul>                                                                                                                                                                                                                                                                                                                                                           |
| [**\#82**](https://github.com/MetricsHub/enterprise-connectors/issues/82) | **[Dell EMC PowerMax Storage (REST)](./connectors/dellemcpowermaxrest.html)**: <ul><li>Storage pool subscribed and configured capacity metrics are incorrect</li><li>Volume consumed and available capacity metrics are missing</li><li>Thin pools are not detected</li></ul>                                                                                                                                                                                                                                                                            |
| [**\#84**](https://github.com/MetricsHub/enterprise-connectors/issues/84) | **[Pure Storage FA Series (REST)](./connectors/purestoragerest.html)** and **[Pure Storage FA Series v2 (REST)](./connectors/purestoragerestv2.html)**: <ul><li>Incorrect mapping of storage pool metrics</li><li>Missing `storage.volume.type` attribute</li></ul>                                                                                                                                                                                                                                                                                      |
| [**\#86**](https://github.com/MetricsHub/enterprise-connectors/issues/86) | **[Dot Hill System (REST)](./connectors/dothillrest.html)**: Missing `storage.size` metric on the storage system and physical disks                                                                                                                                                                                                                                                                                                                                                                                                                      |

### MetricsHub Community Edition v1.0.07

#### Fixed Issues

| ID                                                                         | Description                                                                                    |
|----------------------------------------------------------------------------|------------------------------------------------------------------------------------------------|
| [**\#762**](https://github.com/MetricsHub/metricshub-community/issues/762) | Devices configured with SNMPv3 AES cannot be monitored in MetricsHub Community Edition v1.0.06 |

### MetricsHub Community Edition v1.0.06

#### What's New

| ID                                                                         | Description                                                                                                                                                                  |
|----------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [**\#438**](https://github.com/MetricsHub/metricshub-community/issues/438) | Added support for [programmable configurations](./configuration/configure-monitoring.md#programmable-configuration) to generate the list of resources to monitor dynamically |
| [**\#744**](https://github.com/MetricsHub/metricshub-community/issues/744) | Added [MCP tool](./integrations/ai-agent-mcp.md) to list configured hosts                                                                                                    |
| [**\#746**](https://github.com/MetricsHub/metricshub-community/issues/746) | Added CLI executable to manage API keys (`apikey`)                                                                                                                           |
| [**\#749**](https://github.com/MetricsHub/metricshub-community/issues/749) | Added API key-based authentication                                                                                                                                           |
| [**\#756**](https://github.com/MetricsHub/metricshub-community/issues/756) | Added [MCP tool](./integrations/ai-agent-mcp.md) to detect hosts and collect metrics                                                                                         |

#### Changes and Improvements

| ID                                                                         | Description                                                                                 |
|----------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| [**\#671**](https://github.com/MetricsHub/metricshub-community/issues/671) | Dynamic configuration reloading has been enhanced                                           |
| [**\#739**](https://github.com/MetricsHub/metricshub-community/issues/739) | Redesigned documentation to look more like the [MetricsHub](https://metricshub.com) website |
| [**\#741**](https://github.com/MetricsHub/metricshub-community/issues/741) | Added support for **SNMP v3** `AES192` and `AES256` encryptions                             |

#### Fixed Issues

| ID                                                                         | Description                                                                                                                                                    |
|----------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [**\#734**](https://github.com/MetricsHub/metricshub-community/issues/734) | The logs incorrectly report that the `connector_id` attribute is missing on `host` monitors, although the `connector_id` attribute is not applicable to hosts. |
| [**\#754**](https://github.com/MetricsHub/metricshub-community/issues/754) | `UserPrincipalNotFoundException` is thrown when **MetricsHub** starts on Windows and cannot find the `Users` group.                                            |

### MetricsHub Community Connectors v1.0.13

#### Changes and Improvements

| ID                                                                         | Description                                                |
|----------------------------------------------------------------------------|------------------------------------------------------------|
| [**\#253**](https://github.com/MetricsHub/community-connectors/issues/253) | Added `storage.latency` metric to the semantic conventions |

#### Fixed Issues

| ID                                                                         | Description                                                                    |
|----------------------------------------------------------------------------|--------------------------------------------------------------------------------|
| [**\#250**](https://github.com/MetricsHub/community-connectors/issues/250) | The `hw.network.dropped` metric reports `{packet}` instead of `{packets}` unit |

## MetricsHub Enterprise Edition v2.1.00

### MetricsHub Enterprise Edition v2.1.00

#### What's New

| ID                                                                        | Description                                                                 |
|---------------------------------------------------------------------------|-----------------------------------------------------------------------------|
| [**\#60**](https://github.com/MetricsHub/metricshub-enterprise/issues/60) | Included the [**IBM Informix**](./connectors/informix.html) JDBC driver     |
| [**\#68**](https://github.com/MetricsHub/metricshub-enterprise/issues/68) | Added support for Java Management Extension (JMX)                           |
| [**\#69**](https://github.com/MetricsHub/metricshub-enterprise/issues/69) | Added support for [REST API and remote MCP](./integrations/ai-agent-mcp.md) |

#### Changes and Improvements

| ID                                                                        | Description                                         |
|---------------------------------------------------------------------------|-----------------------------------------------------|
| [**\#57**](https://github.com/MetricsHub/metricshub-enterprise/issues/57) | Upgraded OpenTelemetry Collector Contrib to 0.127.0 |

### MetricsHub Enterprise Connectors v107

#### What's New

| ID                                                                        | Description                                                                              |
|---------------------------------------------------------------------------|------------------------------------------------------------------------------------------|
| [**\#27**](https://github.com/MetricsHub/enterprise-connectors/issues/27) | Added support for [**IBM Informix**](./connectors/informix.html) databases via JDBC      |
| [**\#32**](https://github.com/MetricsHub/enterprise-connectors/issues/32) | Added support for [**Palo Alto**](./connectors/paloaltofirewall.html) firewalls via SNMP |

#### Changes and Improvements

| ID                                                                        | Description                                                                                                        |
|---------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| [**\#57**](https://github.com/MetricsHub/enterprise-connectors/issues/57) | **[Nvidia DGX Server (REST)](./connectors/nvidiadgxrest.html)**: Improved HTTP requests initiated by the connector |

### MetricsHub Community Edition v1.0.05

#### What's New

| ID                                                                         | Description                                                                                                         |
|----------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------|
| [**\#718**](https://github.com/MetricsHub/metricshub-community/issues/718) | **AI Agents**: A new set of [MCP Tools](./integrations/ai-agent-mcp.md) is now available to perform protocol checks |

### MetricsHub Community Edition v1.0.04

#### What's New

| ID                                                                         | Description                                                                 |
|----------------------------------------------------------------------------|-----------------------------------------------------------------------------|
| [**\#692**](https://github.com/MetricsHub/metricshub-community/issues/692) | Added support for [REST API and remote MCP](./integrations/ai-agent-mcp.md) |
| [**\#694**](https://github.com/MetricsHub/metricshub-community/issues/694) | Added support for Java Management Extensions (JMX)                          |

#### Changes and Improvements

| ID                                                                         | Description                                                                                                                                                                                                                |
|----------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [**\#357**](https://github.com/MetricsHub/metricshub-community/issues/357) | **MetricsHub CLI**: Added the ability to specify connector variables                                                                                                                                                       |
| [**\#658**](https://github.com/MetricsHub/metricshub-community/issues/658) | The `database` property is no longer required under the `jdbc` configuration                                                                                                                                               |
| [**\#698**](https://github.com/MetricsHub/metricshub-community/issues/698) | **Prometheus Alert Rules**: Three categories of [alert rules](./prometheus/alertmanager.md#prometheus-alertmanager) are provided to inform you of MetricsHub issues, hardware failures, and system performance degradation |
| [**\#726**](https://github.com/MetricsHub/metricshub-community/issues/726) | **MetricsHub CLI**: The `--list` option now shows connectors that define variables                                                                                                                                         |

#### Fixed Issues

| ID                                                                         | Description                                               |
|----------------------------------------------------------------------------|-----------------------------------------------------------|
| [**\#693**](https://github.com/MetricsHub/metricshub-community/issues/693) | Monitor names are generated for non-hardware monitors     |
| [**\#703**](https://github.com/MetricsHub/metricshub-community/issues/703) | Metrics processing failure due to `NumberFormatException` |
| [**\#708**](https://github.com/MetricsHub/metricshub-community/issues/708) | Incorrect SchemaStore link in the documentation           |

#### Documentation Updates

| ID                                                                         | Description                                                                                    |
|----------------------------------------------------------------------------|------------------------------------------------------------------------------------------------|
| [**\#684**](https://github.com/MetricsHub/metricshub-community/issues/684) | Documented [custom monitoring](./custom/index.md) examples                                     |
| [**\#702**](https://github.com/MetricsHub/metricshub-community/issues/702) | Explained how to implement [custom monitoring through SQL queries](./custom/database-query.md) |
| [**\#704**](https://github.com/MetricsHub/metricshub-community/issues/704) | Documented [Prometheus Alert Rules](./prometheus/alertmanager.md)                              |
| [**\#705**](https://github.com/MetricsHub/metricshub-community/issues/705) | Documented [BMC Helix integration](./integrations/bmc-helix.md)                                |

### MetricsHub Community Connectors v1.0.12

#### What's New

| ID                                                                         | Description                                                                             |
|----------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|
| [**\#162**](https://github.com/MetricsHub/community-connectors/issues/162) | Added support for [**Apache Cassandra**](./connectors/cassandra.html) databases via JMX |

#### Changes and Improvements

| ID                                                                         | Description                                                                                                                        |
|----------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------|
| [**\#233**](https://github.com/MetricsHub/community-connectors/issues/233) | **[MIB-2 Standard SNMP Agent - Network Interfaces](./connectors/mib2.html)**: Added discarded inbound and outbound packets metrics |

## MetricsHub Enterprise Edition v2.0.00

### MetricsHub Enterprise Edition v2.0.00

#### What's New

| ID                                                                        | Description                                                     |
|---------------------------------------------------------------------------|-----------------------------------------------------------------|
| [**\#13**](https://github.com/MetricsHub/metricshub-enterprise/issues/13) | Enabled self-observability through the OpenTelemetry Java Agent |
| [**\#33**](https://github.com/MetricsHub/metricshub-enterprise/issues/33) | Added support for multiple configuration files                  |

#### Changes and Improvements

| ID                                                                        | Description                                                            |
|---------------------------------------------------------------------------|------------------------------------------------------------------------|
| [**\#6**](https://github.com/MetricsHub/metricshub-enterprise/issues/6)   | Updated MetricsHub Enterprise EULA                                     |
| [**\#11**](https://github.com/MetricsHub/metricshub-enterprise/issues/11) | Added the ability to define custom connector variables                 |
| [**\#23**](https://github.com/MetricsHub/metricshub-enterprise/issues/23) | Added digital signature to MetricsHub Enterprise Windows MSI installer |
| [**\#37**](https://github.com/MetricsHub/metricshub-enterprise/issues/37) | Cleaned up Datadog pipeline example in `otel-config-example.yaml`      |

### MetricsHub Enterprise Connectors v106

#### What's New

| ID                                                                        | Description                                                                   |
|---------------------------------------------------------------------------|-------------------------------------------------------------------------------|
| [**\#18**](https://github.com/MetricsHub/enterprise-connectors/issues/18) | Added support for **[Nvidia's DGX servers](./connectors/nvidiadgxrest.html)** |

#### Changes and Improvements

| ID                                                                        | Description                                                                                                                                                                      |
|---------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [**\#19**](https://github.com/MetricsHub/enterprise-connectors/issues/19) | **[HPE Synergy](./connectors/hpesynergy.html)**: Added the ability to configure login domain through the `authLoginDomain` variable                                              |
| [**\#21**](https://github.com/MetricsHub/enterprise-connectors/issues/21) | **[Dell iDRAC9 (REST)](./connectors/dellidracrest.html)**: Added status and firmware version for the iDRAC management interface                                                  |
| [**\#26**](https://github.com/MetricsHub/enterprise-connectors/issues/26) | **[Brocade SAN Switch](./connectors/brocadeswitch.html)**: `hw.network.name` now reported                                                                                        |
| [**\#35**](https://github.com/MetricsHub/enterprise-connectors/issues/35) | **[Microsoft SQL Server](./connectors/mssql.html)** and **[Oracle](./connectors/oracle.html)**: Renamed metric `db.server.active_connections` to `db.server.current_connections` |
| [**\#39**](https://github.com/MetricsHub/enterprise-connectors/issues/39) | **[Dell OpenManage Server Administrator](./connectors/dellopenmanage.html)**: Memory device failure modes now exposed in logs                                                    |
| [**\#40**](https://github.com/MetricsHub/enterprise-connectors/issues/40) | **[Microsoft SQL Server](./connectors/mssql.html)** and **[Oracle](./connectors/oracle.html)**: Standardized storage metrics under the `db.server.storage` namespace             |
| [**\#44**](https://github.com/MetricsHub/enterprise-connectors/issues/44) | **[Juniper Switch](./connectors/juniper.html)**: Temperatures now monitored                                                                                                      |

#### Fixed Issues

| ID                                                                        | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
|---------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [**\#11**](https://github.com/MetricsHub/enterprise-connectors/issues/11) | **[Pure Storage FA Series (REST)](./connectors/purestoragerest.html)** and **[Pure Storage FA Series v2 (REST)](./connectors/purestoragerestv2.html)**: Redundant `disk_controller` monitor already reported by the `blade` monitor                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| [**\#34**](https://github.com/MetricsHub/enterprise-connectors/issues/34) | Incorrect monitor names reported by various connectors: <ul><li>**[Brocade SAN Switch](./connectors/brocadeswitch.html)**</li> <li>**[Dell EMC PowerStore (REST)](./connectors/dellemcpowerstorerest.html)**</li> <li>**[Dell OpenManage Server Administrator](./connectors/dellopenmanage.html)**</li> <li>**[EMC SMI-S Agent (ECOM)](./connectors/emcdiskarray.html)**</li> <li>**[Fibre Alliance SNMP Agent (Switches)](./connectors/fibreallianceswitch.html)**</li> <li>**[Hitachi HNAS (SNMP)](./connectors/hitachihnas.html)**</li> <li>**[IBM AIX - Common](./connectors/ibmaix.html)**</li> <li>**[IBM AIX - SCSI disks](./connectors/ibmaixdisk.html)**</li> <li>**[NetApp Filer (SNMP)](./connectors/netapp.html)**</li> <li>**[MegaCLI Managed RAID Controllers](./connectors/sunmegacli.html)**</li></ul> |
| [**\#47**](https://github.com/MetricsHub/enterprise-connectors/issues/47) | **[Cisco Entity Sensor (SNMP)](./connectors/ciscoentitysensor.html)**: Excessive power consumption values observed on certain Cisco devices                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| [**\#52**](https://github.com/MetricsHub/enterprise-connectors/issues/52) | **[Cisco UCS B-Series (SNMP)](./connectors/ciscoucsbladesnmp.html)**: CPU frequency incorrectly reported in the `name` attribute                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |

### MetricsHub Community Edition v1.0.03

#### What's New

| ID                                                                         | Description                                    |
|----------------------------------------------------------------------------|------------------------------------------------|
| [**\#650**](https://github.com/MetricsHub/metricshub-community/issues/650) | Added support for multiple configuration files |

#### Changes and Improvements

| ID                                                                         | Description                                                                 |
|----------------------------------------------------------------------------|-----------------------------------------------------------------------------|
| [**\#628**](https://github.com/MetricsHub/metricshub-community/issues/628) | Enhanced connectors parser to better handle Enterprise Connector variables  |
| [**\#651**](https://github.com/MetricsHub/metricshub-community/issues/651) | The MetricsHub core engine now dynamically generates Hardware monitor names |

#### Fixed Issues

| ID                                                                         | Description                                                                                             |
|----------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| [**\#503**](https://github.com/MetricsHub/metricshub-community/issues/503) | `hw.host.power` metric incorrectly reported as both estimated and measured for the same host            |
| [**\#662**](https://github.com/MetricsHub/metricshub-community/issues/662) | Incorrect conversion of WMI unsigned 32-bit integers caused invalid performance counter values          |
| [**\#674**](https://github.com/MetricsHub/metricshub-community/issues/674) | NullPointerException in Connector AWK scripts caused by race condition in the MetricsHub JAWK extension |

#### Documentation Updates

| ID                                                                         | Description                             |
|----------------------------------------------------------------------------|-----------------------------------------|
| [**\#486**](https://github.com/MetricsHub/metricshub-community/issues/486) | Documented the Datadog Integration      |
| [**\#655**](https://github.com/MetricsHub/metricshub-community/issues/655) | Documented the Self-Observability setup |

### MetricsHub Community Connectors v1.0.11

#### What's New

| ID                                                                         | Description                                                      |
|----------------------------------------------------------------------------|------------------------------------------------------------------|
| [**\#200**](https://github.com/MetricsHub/community-connectors/issues/200) | Added support for **[PostgreSQL](./connectors/postgresql.html)** |

#### Changes and Improvements

| ID                                                                         | Description                                                                                                                                                                                          |
|----------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [**\#199**](https://github.com/MetricsHub/community-connectors/issues/199) | **[lm_sensors](./connectors/lmsensors.html)**: Connector now acts as fallback when temperatures are unavailable from other connectors                                                                |
| [**\#212**](https://github.com/MetricsHub/community-connectors/issues/212) | **[MySQL](./connectors/mysql.html)**: Renamed metric `db.server.active_connections` to `db.server.current_connections`                                                                               |
| [**\#218**](https://github.com/MetricsHub/community-connectors/issues/218) | **[Linux System](./connectors/linux.html)**: Added memory swap metrics                                                                                                                               |
| [**\#221**](https://github.com/MetricsHub/community-connectors/issues/221) | **[MySQL](./connectors/mysql.html)**: Standardized storage metrics under the `db.server.storage` namespace                                                                                           |
| [**\#224**](https://github.com/MetricsHub/community-connectors/issues/224) | **[Windows System](./connectors/windows.html)** and **[Linux System](./connectors/linux.html)**: Added `system.network.bandwidth.limit` metric and optionally `system.network.bandwidth.utilization` |

#### Fixed Issues

| ID                                                                         | Description                                                                                                                                                                                                                                                                                        |
|----------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [**\#202**](https://github.com/MetricsHub/community-connectors/issues/202) | **[SmartMon Tools](./connectors/smartmonlinux.html)**: Failed to discover physical disks                                                                                                                                                                                                           |
| [**\#204**](https://github.com/MetricsHub/community-connectors/issues/204) | **[Windows System](./connectors/windows.html)**: Incorrect values reported for `system.disk.io_time` and `system.disk.operation_time` metrics                                                                                                                                                      |
| [**\#205**](https://github.com/MetricsHub/community-connectors/issues/205) | **[Linux System](./connectors/linux.html)**: Incorrect unit reported for `system.disk.io_time` and `system.disk.operation.time` metrics                                                                                                                                                            |
| [**\#209**](https://github.com/MetricsHub/community-connectors/issues/209) | Incorrect monitor names reported by various connectors: <ul><li>**[IPMI](./connectors/ipmitool.html)**</li> <li>**[SmartMon Tools](./connectors/smartmonlinux.html)**</li> <li>**[WMI - Disks](./connectors/wbemgendisknt.html)**</li><li>**[lm_sensors](./connectors/lmsensors.html)**</li> </ul> |
| [**\#211**](https://github.com/MetricsHub/community-connectors/issues/211) | **[Linux System](./connectors/linux.html)**: `system.memory.utilization{system.memory.state="used"}` is incorrectly calculated                                                                                                                                                                     |
| [**\#215**](https://github.com/MetricsHub/community-connectors/issues/215) | **[Linux System](./connectors/linux.html)**: Metric incorrectly named `system.disk.operation.time` instead of `system.disk.operation_time`                                                                                                                                                         |
| [**\#220**](https://github.com/MetricsHub/community-connectors/issues/220) | **[Windows System](./connectors/windows.html)**: Inaccurate paging metrics and missing handling for systems without a pagefile                                                                                                                                                                     |
| [**\#229**](https://github.com/MetricsHub/community-connectors/issues/229) | **[Windows System](./connectors/windows.html)**: The `system.paging.type` attribute returned wrong labels (`soft`, `hard` instead of expected `major`, `minor`)                                                                                                                                    |
| [**\#231**](https://github.com/MetricsHub/community-connectors/issues/231) | **[Linux System](./connectors/linux.html)**: Incorrect attribute name `system.paging.type` used instead of `system.paging.direction` for `system.paging.operations` metrics                                                                                                                        |
| [**\#210**](https://github.com/MetricsHub/community-connectors/issues/210) | **[Windows - DiskPart](./connectors/diskpart.html)**: The connector is tested remotely whereas it should work only locally                                                                                                                                                                         |

## MetricsHub Enterprise Edition v1.2.00

### MetricsHub Enterprise Edition v1.2.00

#### What's New

| ID       | Description                                                                                         |
|----------|-----------------------------------------------------------------------------------------------------|
| M8BEE-45 | Simplified deployment with new Docker image for MetricsHub Enterprise                               |
| M8BEE-49 | Added `jawk` CLI, a command-line interface for executing AWK scripts                                |
| M8BEE-50 | Integrated MetricsHub Community metrics exporter for optimized resource usage and memory efficiency |
| M8BEE-53 | Published configuration examples needed for Docker setup                                            |
| M8BEE-55 | Integrated Datadog pipeline into `otel-config-example.yaml`                                         |

#### Changes and Improvements

| ID       | Description                                                                                       |
|----------|---------------------------------------------------------------------------------------------------|
| M8BEE-46 | Prometheus Alertmanager: Alerts are now triggered for degraded voltage levels (both low and high) |
| M8BEE-48 | Added SNMPv3 authentication examples to `metricshub-example.yaml`                                 |

#### Fixed Issues

| ID       | Description                                                                                             |
|----------|---------------------------------------------------------------------------------------------------------|
| M8BEE-47 | Security Vulnerabilities: CVE-2022-46364, CVE-2024-28752, CVE-2022-46363, CVE-2025-23184, CVE-2024-7254 |

### MetricsHub Enterprise Connectors v105

#### What's New

| ID     | Description                                                                      |
|--------|----------------------------------------------------------------------------------|
| EC-3   | Added support for **[Dell EMC PowerMax](./connectors/dellemcpowermaxrest.html)** |
| EC-4   | Added support for **[Hitachi Disk Arrays](./connectors/hitachidiskarray.html)**  |
| EC-70  | Added support for **[HPE MSA](./connectors/hpemsarest.html)** 2060 via HTTP API  |
| EC-107 | Added support for **[Oracle databases](./connectors/oracle.html)**               |
| EC-109 | Added support for **[Microsoft SQL Server databases](./connectors/mssql.html)**  |
| EC-118 | Added support for **[DotHill storage systems](./connectors/dothillrest.html)**   |

#### Changes and Improvements

| ID     | Description                                                                                                                     |
|--------|---------------------------------------------------------------------------------------------------------------------------------|
| EC-32  | **[HPE OneView (Frames and Blades)](./connectors/hpesynergy.html)**: Blade ID now corresponds to the Blade Server Hostname/FQDN |
| EC-33  | **[HPE OneView (Frames and Blades)](./connectors/hpesynergy.html)**: Added Ambient Temperature on Enclosure Frames              |
| EC-36  | **[HPE OneView (Frames and Blades)](./connectors/hpesynergy.html)**: Added Blades Server Network Card monitoring                |
| EC-92  | **[HPE OneView (Frames and Blades)](./connectors/hpesynergy.html)**: Added InterConnect port monitoring                         |
| EC-126 | **[NetApp Filer (REST)](./connectors/netapprest.html)**: Improved controller visibility and network metrics collection          |
| EC-130 | **[Cisco Ethernet Switch](./connectors/ciscoethernetswitch.html)**: `hw.network.name` and `hw.network.alias` now reported       |
| EC-134 | **[Juniper Switch](./connectors/juniper.html)**: `hw.network.name` and `hw.network.alias` now reported                          |
| EC-135 | **[Cisco Entity Sensor (SNMP)](./connectors/ciscoentitysensor.html)**: `hw.network.name` and `hw.network.alias` now reported    |

#### Fixed Issues

| ID     | Description                                                                                                                                           |
|--------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| EC-34  | **[HPE OneView (Frames and Blades)](./connectors/hpesynergy.html)**: CPU instances are not reported under Blades                                      |
| EC-117 | **[Dell MX Chassis and Blades (REST)](./connectors/dellmxrest.html)**: Sled Inlet Temperature is in ALARM after blade reboot                          |
| EC-119 | **[Lenovo ThinkSystem (IMM, XCC)](./connectors/lenovothinksystem.html)**: Threshold is not computed for `hw.voltage.limit` metric                     |
| EC-120 | **[Citrix Netscaler (SNMP)](./connectors/citrixnetscaler.html)**: Connector incorrectly activates on a Linux server                                   |
| EC-123 | **[Cisco Ethernet Switch](./connectors/ciscoethernetswitch.html)**: Connector may fail to activate for some Cisco switches                            |
| EC-129 | **[IBM Director Agent 6 - Windows](./connectors/director61nt.html)**: Enclosures are duplicated                                                       |
| EC-132 | **[HPE iLO 5 (ProLiant Gen10 and Gen10 Plus)](./connectors/hpegen10ilorest.html)**: `hw.network.bandwidth.limit` is reported in bits instead of bytes |

### MetricsHub Community Edition v1.0.02

#### What's New

| ID                                                                         | Description                                                          |
|----------------------------------------------------------------------------|----------------------------------------------------------------------|
| [**\#583**](https://github.com/metricshub/metricshub-community/issues/583) | Added `jawk` CLI, a command-line interface for executing AWK scripts |

#### Changes and Improvements

| ID                                                                         | Description                                                                                            |
|----------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------|
| [**\#339**](https://github.com/metricshub/metricshub-community/issues/339) | **MetricsHub Agent**: Improved OpenTelemetry resource usage                                            |
| [**\#575**](https://github.com/metricshub/metricshub-community/issues/575) | **Authentication Protocols**: Added support for SNMPv3 HMAC-SHA-2 (SHA224, SHA256, SHA384, and SHA512) |
| [**\#576**](https://github.com/metricshub/metricshub-community/issues/576) | **Authentication Protocols**: Added support for IPMI-over-LAN (SHA-256 and MD5)                        |
| [**\#582**](https://github.com/metricshub/metricshub-community/issues/582) | **HTTP CLI**: Improved usability with simplified syntax                                                |
| [**\#620**](https://github.com/metricshub/metricshub-community/issues/620) | **Health Check**: Added support for Oracle and Cassandra databases                                     |
| [**\#626**](https://github.com/metricshub/metricshub-community/issues/626) | **Metrics Collection**: The `id` attribute is now optional                                             |
| [**\#634**](https://github.com/metricshub/metricshub-community/issues/634) | **MetricsHub CLI**: Added the `--job-timeout` option                                                   |

#### Fixed Issues

| ID                                                                         | Description                                                                                                 |
|----------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------|
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
|----------------------------------------------------------------------------|------------------------------------------------------------------|
| [**\#631**](https://github.com/metricshub/metricshub-community/issues/631) | Documented the Docker deployment setup for MetricsHub Enterprise |
| [**\#632**](https://github.com/metricshub/metricshub-community/issues/632) | Listed the supported operating systems                           |

### MetricsHub Community Connectors v1.0.09

#### What's New

| ID                                                                         | Description                                                                                                                            |
|----------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------|
| [**\#188**](https://github.com/metricshub/community-connectors/issues/188) | Defined semantic conventions for **[Oracle](./connectors/oracle.html)** and **[SQL Server](./connectors/mssql.html)** database metrics |
| [**\#190**](https://github.com/metricshub/community-connectors/issues/190) | **[MySQL](./connectors/mysql.html):** `db.server.name` attribute is now reported                                                       |
| [**\#193**](https://github.com/metricshub/community-connectors/issues/193) | **[Generic Ethernet Switch](./connectors/genericswitchenclosure.html):** `hw.network.name` and `hw.network.alias` now reported         |

#### Changes and Improvements

| ID                                                                         | Description                                                                                                                                  |
|----------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| [**\#177**](https://github.com/metricshub/community-connectors/issues/177) | **[Linux System](./connectors/linux.html) and [Windows System](./connectors/windows.html)**: `network.interface.name` attribute now reported |

#### Fixed Issues

| ID                                                                         | Description                                                                                                                                      |
|----------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------|
| [**\#173**](https://github.com/metricshub/community-connectors/issues/173) | **[Generic UPS](./connectors/genericups.html):** Some voltage sensors are not discovered                                                         |
| [**\#176**](https://github.com/metricshub/community-connectors/issues/176) | **[Linux](./connectors/linux.html) and [Windows](./connectors/windows.html) System**: `system.device` attribute missing from FS and disk metrics |
| [**\#178**](https://github.com/metricshub/community-connectors/issues/178) | **[Linux System](./connectors/linux.html)**: Incorrect attribute names and invalid mapping syntax on disk metrics                                |
| [**\#187**](https://github.com/metricshub/community-connectors/issues/187) | **[Linux System](./connectors/linux.html)**: Docker overlay FS causes inaccurate capacity reporting                                              |

#### Documentation Updates

| ID                                                                         | Description                                                |
|----------------------------------------------------------------------------|------------------------------------------------------------|
| [**\#184**](https://github.com/metricshub/community-connectors/issues/184) | Documented `awk` source in the Connector Developer’s Guide |

## MetricsHub Enterprise Edition v1.1.00

### MetricsHub Enterprise Edition v1.1.00

#### What's New

| ID       | Description                                                                                                        |
|----------|--------------------------------------------------------------------------------------------------------------------|
| M8BEE-37 | Added protocol CLIs to validate configurations, troubleshoot connectivity, and ensure successful metric extraction |

#### Changes and Improvements

| ID       | Description                                                |
|----------|------------------------------------------------------------|
| M8BEE-38 | Updated OpenTelemetry Collector Contrib to version 0.116.0 |

### MetricsHub Enterprise Connectors v103

#### What's New

| ID    | Description                                                                                                                              |
|-------|------------------------------------------------------------------------------------------------------------------------------------------|
| EC-72 | Added performance and capacity metrics for **[Pure Storage](./connectors/purestoragerest.html)** FlashArray storage systems via REST API |
| EC-75 | Added performance and capacity metrics for **[NetApp](./connectors/netapprest.html)** FAS and AFF storage systems via ONTAP REST API     |
| EC-86 | Added support for **[Citrix NetScaler](./connectors/citrixnetscaler.html)** via SNMP                                                     |

#### Changes and Improvements

| ID     | Description                                                                                                                                                                                                                              |
|--------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| EC-10  | Enhanced detection criteria in the [EMC VPLEX Version 5](./connectors/vplex5.html), [EMC VPLEX Version 6](./connectors/vplex.html), and [Huawei OceanStor (REST)](./connectors/huaweioceanstorrest.html) connectors                      |
| EC-57  | **[Pure Storage FA Series (REST Token Authentication)](./connectors/purestorageresttoken.html)**: NVRAM modules are now reported as memory monitors                                                                                      |
| EC-88  | Added support for HPE ProLiant Gen 11 servers via [iLO 6](./connectors/hpeilo6rest.html)                                                                                                                                                 |
| EC-90  | **[HP iLO Gen 10 (REST)](./connectors/hpegen10ilorest.html)**: Split into two connectors: [HPE iLO 5 (ProLiant Gen10 and Gen10 Plus)](./connectors/hpegen10ilorest.html) and [HPE iLO 6 (ProLiant Gen11)](./connectors/hpeilo6rest.html) |
| EC-91  | **[HP iLO Gen 9 (REST)](./connectors/hpegen9ilorest.html)**: Renamed to `HPE iLO4 (ProLiant Gen 8, Gen9)`                                                                                                                                |
| EC-100 | **[EMC uemcli (VNXe)](./connectors/emcuemcli.html)**: Power and temperature metrics are now collected                                                                                                                                    |

#### Fixed Issues

| ID    | Description                                                                                                                                                                                   |
|-------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| EC-84 | **[Pure Storage FA Series (REST Token Authentication)](./connectors/purestorageresttoken.html)**: The `hw.parent.type` attribute is reported as `DiskController` instead of `disk_controller` |
| EC-95 | **[Dell EMC PowerStore (REST)](./connectors/dellemcpowerstorerest.html)**: Metrics are missing for physical disks, network cards, memory modules, fans and power supplies                     |
| EC-97 | **[Pure Storage FA Series (SSH)](./connectors/purestorage.html)**: `hw.temperature` metrics are not collected                                                                                 |
| EC-98 | **[Dell iDRAC9 (REST)](./connectors/dellidracrest.html)**: Incorrect JSON response handling leads to HTTP 404 error on network devices                                                        |

### MetricsHub Enterprise Connectors v104

#### Changes and Improvements

| ID     | Description                                                                                                                                                                |
|--------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| EC-112 | Reduced high CPU usage caused by internal DB queries in [NetAppRESTv2](./connectors/netapprestv2.html) and [PureStorageREST](./connectors/purestoragerest.html) connectors |

### MetricsHub Community Edition v1.0.00

#### What's New

| ID                                                                         | Description                                                                                                        |
|----------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| [**\#424**](https://github.com/metricshub/metricshub-community/issues/424) | Added protocol CLIs to validate configurations, troubleshoot connectivity, and ensure successful metric extraction |

#### Changes and Improvements

| ID                                                                         | Description                                                                                                                            |
|----------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------|
| [**\#519**](https://github.com/metricshub/metricshub-community/issues/519) | Updated connector semantics by replacing `leftConcat` with `prepend`, `rightConcat` with `append`, and `osCommand` with `commandLine`. |
| [**\#521**](https://github.com/metricshub/metricshub-community/issues/521) | Updated OpenTelemetry Java dependencies to version `1.45.0`                                                                            |
| [**\#525**](https://github.com/metricshub/metricshub-community/issues/525) | Added the ability to enable or disable self-monitoring                                                                                 |

#### Fixed Issues

| ID                                                                         | Description                                                                                 |
|----------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| [**\#523**](https://github.com/metricshub/metricshub-community/issues/523) | The `hw.network.up` metric is not reported for connectors with `WARN` or `ALARM` link state |

#### Documentation Updates

| ID                                                                         | Description                                                                                       |
|----------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|
| [**\#546**](https://github.com/metricshub/metricshub-community/issues/546) | Integrated platform icons and enhanced connectors directory page                                  |
| [**\#541**](https://github.com/metricshub/metricshub-community/issues/541) | Moved use cases from the documentation to [MetricsHub Use Cases](https://metricshub.com/usecases) |
| [**\#533**](https://github.com/metricshub/metricshub-community/issues/533) | Documented the self-monitoring feature                                                            |
| [**\#529**](https://github.com/metricshub/metricshub-community/issues/529) | Created a Troubleshooting section in the user documentation                                       |

### MetricsHub Community Edition v1.0.01

#### Changes and Improvements

| ID                                                                         | Description                                                                        |
|----------------------------------------------------------------------------|------------------------------------------------------------------------------------|
| [**\#563**](https://github.com/metricshub/metricshub-community/issues/563) | MetricsHub Engine Internal DB: Improved memory usage and data types identification |

### MetricsHub Community Connectors v1.0.08

#### What's New

| ID                                                                         | Description                                                                 |
|----------------------------------------------------------------------------|-----------------------------------------------------------------------------|
| [**\#137**](https://github.com/metricshub/community-connectors/issues/137) | Added support for **[MySQL](./connectors/mysql.html)** databases via `JDBC` |

#### Changes and Improvements

| ID                                                                         | Description                                                                                                                                                                             |
|----------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [**\#158**](https://github.com/metricshub/community-connectors/issues/158) | Updated platforms for community connectors                                                                                                                                              |
| [**\#160**](https://github.com/metricshub/community-connectors/issues/160) | Created Storage metric semantic conventions                                                                                                                                             |
| [**\#163**](https://github.com/metricshub/community-connectors/issues/163) | **[Ethernet Switch with Sensors (SNMP)](./connectors/mib2switch.html)** and **[Generic Ethernet Switch](./connectors/genericswitchenclosure.html)**: Added support for Arista platforms |

#### Fixed Issues

| ID                                                                         | Description                                                                                   |
|----------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------|
| [**\#111**](https://github.com/metricshub/community-connectors/issues/111) | [LinuxIPNetwork](./connectors/linuxipnetwork.html): Fails to monitor some Ethernet interfaces |

## MetricsHub Enterprise Edition v1.0.02

### MetricsHub Enterprise Edition v1.0.02

#### Changes and Improvements

| ID       | Description                                                                              |
|----------|------------------------------------------------------------------------------------------|
| M8BEE-35 | Replaced deprecated `loggingexporter` with `debugexporter` in `otel-config-example.yaml` |

### MetricsHub Enterprise Connectors v102

#### Changes and Improvements

| ID    | Description                                                                       |
|-------|-----------------------------------------------------------------------------------|
| EC-87 | The `StatusInformation` of the `led` monitor now reports the `LedIndicator` value |

#### Fixed Issues

| ID    | Description                                                                                                                                                    |
|-------|----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| EC-74 | **[HP Insight Management Agent - Drive Array](./connectors/cpqdrivearraynt.html)**: The `disk_controller` status is not reported                               |
| EC-77 | **[Redfish](./connectors/redfish.html)**: Enclosures are duplicated for Dell iDRAC and HP                                                                      |
| EC-78 | **[Dell OpenManage Server Administrator](./connectors/dellopenmanage.html)**: The `hw.enclosure.energy` metric is not converted to Joules                      |
| EC-79 | **[Dell XtremIO REST API](./connectors/dellemcxtremiorest.html)**: The `hw.parent.type` attribute is reported as `DiskController` instead of `disk_controller` |
| EC-93 | Connectors reporting voltage metrics do not set the `high.critical` threshold                                                                                  |

### MetricsHub Community Edition v0.9.08

#### Changes and Improvements

| ID                                                                         | Description                                                                                        |
|----------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
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

| ID                                                                         | Description                                                                                       |
|----------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|
| [**\#478**](https://github.com/metricshub/metricshub-community/issues/478) | A NullPointerException occurs when processing `HTTP` detection criteria                           |
| [**\#480**](https://github.com/metricshub/metricshub-community/issues/480) | [IPMITool](./connectors/ipmitool.html) criteria and source fail due to invalid `ipmitool` command |
| [**\#500**](https://github.com/metricshub/metricshub-community/issues/500) | Only one monitor is processed due to incorrect indexing                                           |
| [**\#502**](https://github.com/metricshub/metricshub-community/issues/502) | Incorrect link status check leads to an incorrect power consumption                               |

#### Documentation Updates

| ID                                                                         | Description                                                                                                                                                                                                                                                                |
|----------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [**\#462**](https://github.com/metricshub/metricshub-community/issues/462) | Reviewed **Configure Monitoring** documentation                                                                                                                                                                                                                            |
| [**\#462**](https://github.com/metricshub/metricshub-community/issues/462) | Moved the CLI documentation to the Appendix section                                                                                                                                                                                                                        |
| [**\#463**](https://github.com/metricshub/metricshub-community/issues/463) | Combined the Linux and Windows Prometheus quick starts into a unique Prometheus quick start                                                                                                                                                                                |
| [**\#484**](https://github.com/metricshub/metricshub-community/issues/484) | Documented the Prometheus/Grafana integration                                                                                                                                                                                                                              |
| [**\#289**](https://github.com/metricshub/metricshub-community/issues/289) | Documented the use cases: **Monitoring network interfaces using SNMP**, **Monitoring a process on Windows**, **Monitoring a remote system running on Linux**, **Monitoring a service running on Linux**, **Monitoring the Health of a Service**, and **Pinging resources** |
| [**\#505**](https://github.com/metricshub/metricshub-community/issues/505) | Updated references to the deprecated `loggingexporter`                                                                                                                                                                                                                     |

### MetricsHub Community Connectors v1.0.07

#### Changes and Improvements

| ID                                                                         | Description                                                                                                                                                                     |
|----------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [**\#112**](https://github.com/metricshub/community-connectors/issues/112) | **[Windows Process](./connectors/windowsprocess.html)**: The process user name is now retrieved and selectable through configuration variables                                  |
| [**\#143**](https://github.com/metricshub/community-connectors/issues/143) | **[Linux System](./connectors/linux.html)**: The connector no longer reports services, as these are now handled by the [LinuxService](./connectors/linuxservice.html) connector |
| [**\#148**](https://github.com/metricshub/community-connectors/issues/148) | **[Linux System](./connectors/linux.html)**: Enhanced `filesystem` utilization calculation                                                                                      |

#### Fixed Issues

| ID                                                                         | Description                                                                                                                                                                     |
|----------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [**\#140**](https://github.com/metricshub/community-connectors/issues/140) | `Platform` mispelling in [Linux System](./connectors/linux.html) & [LinuxService](./connectors/linuxservice.html) connectors                                                    |
| [**\#145**](https://github.com/metricshub/community-connectors/issues/145) | **[IpmiTool](./connectors/ipmitool.html)**: The `hw.status` metric is not collected because `enclosure.awk` reports `OK`, `WARN`, `ALARM` instead of `ok`, `degraded`, `failed` |
| [**\#152**](https://github.com/metricshub/community-connectors/issues/152) | Connectors reporting voltage metrics do not set the `high.critical` threshold                                                                                                   |

#### Documentation Updates

| ID                                                                         | Description                                             |
|----------------------------------------------------------------------------|---------------------------------------------------------|
| [**\#128**](https://github.com/metricshub/community-connectors/issues/128) | Documented default connector `variables`                |
| [**\#129**](https://github.com/metricshub/community-connectors/issues/129) | Replaced all references to `sql` with `internalDbQuery` |

## MetricsHub Enterprise Edition v1.0.01

### MetricsHub Enterprise Edition v1.0.01

#### Changes and Improvements

| ID           | Description                                                                                                              |
|--------------|--------------------------------------------------------------------------------------------------------------------------|
| **M8BEE-29** | Removed Otel collector resourcedetection processor to prevent localhost system resource attributes from being overridden |
| **M8BEE-32** | Moved the localhost resource configuration to the `data center 1` resource group in `metricshub-example.yaml`            |

### MetricsHub Enterprise Connectors v101

#### Changes and Improvements

| ID       | Description                                                    |
|----------|----------------------------------------------------------------|
| **EC-9** | The hw.network.bandwidth.limit metric is now reported in bytes |

#### Fixed Issues

| ID        | Description                                                                                               |
|-----------|-----------------------------------------------------------------------------------------------------------|
| **EC-73** | [Dell iDRAC9 (REST)](./connectors/dellidracrest.html): Some network link physical addresses are incorrect |

### MetricsHub Community Edition v0.9.07

#### Changes and Improvements

| ID                                                                         | Description                                                                                                                                                                       |
|----------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [**\#433**](https://github.com/metricshub/metricshub-community/issues/433) | **[BREAKING_CHANGE]** Disabled Automatic Hostname to FQDN resolution                                                                                                              |
| [**\#427**](https://github.com/metricshub/metricshub-community/issues/427) | BMC Helix Integration: Added the `StatusInformation` internal text parameter to the connector monitor                                                                             |
| [**\#421**](https://github.com/metricshub/metricshub-community/issues/421) | Reduced Alert noise for `hw.status{state="present"}`                                                                                                                              |
| [**\#414**](https://github.com/metricshub/metricshub-community/issues/414) | Added a link to MetricsHub Community Connectors 1.0.06                                                                                                                            |
| [**\#412**](https://github.com/metricshub/metricshub-community/issues/412) | The `hw.status{state="present"}` metric is no longer reported for cpu monitors discovered by [Linux](./connectors/linux.html) and [Windows](./connectors/windows.html) connectors |
| [**\#383**](https://github.com/metricshub/metricshub-community/issues/383) | Implemented a new engine method `megaBit2Byte` to align with OpenTelemetry unit standards                                                                                         |
| [**\#374**](https://github.com/metricshub/metricshub-community/issues/374) | Default connector variables can now be specified in YAML connector files                                                                                                          |
| [**\#302**](https://github.com/metricshub/metricshub-community/issues/302) | Defined `afterAll` and `beforeAll` jobs in YAML connectors                                                                                                                        |
| [**\#423**](https://github.com/metricshub/metricshub-community/issues/423) | Added the ability to filter monitors                                                                                                                                              |

#### Fixed Issues

| ID                                                                         | Description                                                                                             |
|----------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| [**\#436**](https://github.com/metricshub/metricshub-community/issues/436) | The log message for SNMP v3 credential validation is incorrect                                          |
| [**\#439**](https://github.com/metricshub/metricshub-community/issues/439) | Connector default variables are not serializable                                                        |
| [**\#417**](https://github.com/metricshub/metricshub-community/issues/417) | JavaDoc references are incorrect                                                                        |
| [**\#410**](https://github.com/metricshub/metricshub-community/issues/410) | Protocol definition is applied to only one host in a multiple-host configuration                        |
| [**\#368**](https://github.com/metricshub/metricshub-community/issues/368) | The `hw.power{hw.type="vm"}` metric is erroneously set to 0                                             |
| [**\#456**](https://github.com/metricshub/metricshub-community/issues/456) | An exception occurs when monitoring [ESXi](./connectors/vmwareesxi.html) through vCenter authentication |

### MetricsHub Community Connectors v1.0.06

#### Changes and Improvements

| ID                                                                         | Description                                                                                                                                                                                                                                              |
|----------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [**\#125**](https://github.com/metricshub/community-connectors/issues/125) | Disabled automatic detection for [Windows Process](./connectors/windowsprocess.html), [WindowsService](./connectors/windowsservice.html), and [LinuxService](./connectors/linuxservice.html)                                                             |
| [**\#122**](https://github.com/metricshub/community-connectors/issues/122) | Added default values for connector variables in [WindowsService](./connectors/windowsservice.html), [LinuxService](./connectors/linuxservice.html), [Windows Process](./connectors/windowsprocess.html) & [LinuxProcess](./connectors/linuxprocess.html) |
| [**\#114**](https://github.com/metricshub/community-connectors/issues/114) | The `hw.network.bandwidth.limit` metric is now displayed in bytes                                                                                                                                                                                        |

#### Fixed Issues

| ID                                                                         | Description                                                              |
|----------------------------------------------------------------------------|--------------------------------------------------------------------------|
| [**\#120**](https://github.com/metricshub/community-connectors/issues/120) | The `hw.vm.power_ratio` unit is incorrect. It should be 1 instead of Cel |
