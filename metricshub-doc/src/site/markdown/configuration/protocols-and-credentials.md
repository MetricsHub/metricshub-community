keywords: protocols, credentials, snmp, wbem, wmi, winrm, ssh, http, ipmi, jdbc, jmx, ping
description: Complete reference for all protocols supported by MetricsHub, with YAML configuration examples.

# Protocols and Credentials

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

This page provides the full reference for configuring protocols in **MetricsHub**. Each protocol section includes the available parameters and a complete YAML example.

For a quick overview of how to set up your first resource, see [Monitoring Configuration](./configure-monitoring.html).

## Choosing a Protocol

| Protocol                    | Best For                                 | Authentication                    |
| --------------------------- | ---------------------------------------- | --------------------------------- |
| [HTTP](#http)               | REST APIs, web services, storage systems | Username/password                 |
| [ICMP Ping](#icmp-ping)     | Basic reachability checks                | None                              |
| [IPMI](#ipmi)               | Out-of-band management (BMC, iLO, iDRAC) | Username/password                 |
| [JDBC](#jdbc)               | Database monitoring                      | Username/password                 |
| [JMX](#jmx)                 | Java application monitoring              | Username/password (optional)      |
| [OS Commands](#os-commands) | Local system monitoring                  | sudo (optional)                   |
| [SSH](#ssh)                 | Linux/Unix remote monitoring             | Username/password or private key  |
| [SNMP v1/v2c](#snmp)        | Network devices, legacy systems          | Community string                  |
| [SNMP v3](#snmp-version-3)  | Network devices (secure)                 | Username/password with encryption |
| [WBEM](#wbem)               | VMware, storage systems                  | Username/password                 |
| [WMI](#wmi)                 | Windows systems (local or domain)        | Username/password                 |
| [WinRM](#winrm)             | Windows remote management                | Username/password                 |

## HTTP

Use the parameters below to configure the HTTP protocol:

| Parameter | Description                                                                                       |
| --------- | ------------------------------------------------------------------------------------------------- |
| https     | Whether to use encryption (`false` for HTTP, `true` for HTTPS).                                   |
| hostname  | The name or IP address of the resource. If not specified, the `host.name` attribute will be used. |
| port      | The HTTPS port number used to perform HTTP requests (Default: 443).                               |
| username  | Name used to establish the connection with the host via the HTTP protocol.                        |
| password  | Password used to establish the connection with the host via the HTTP protocol.                    |
| timeout   | How long until the HTTP request times out (Default: 60s).                                         |

**Example**

```yaml
resourceGroups:
  boston:
    attributes:
      site: boston
    resources:
      myHost1:
        attributes:
          host.name: my-host-01
          host.type: storage
        protocols:
          http:
            https: true
            port: 443
            username: myusername
            password: mypwd
            timeout: 60
```

## ICMP Ping

Use the parameters below to configure the ICMP ping protocol:

| Parameter | Description                                                                                       |
| --------- | ------------------------------------------------------------------------------------------------- |
| hostname  | The name or IP address of the resource. If not specified, the `host.name` attribute will be used. |
| ping      | Protocol used to test the host reachability through ICMP.                                         |
| timeout   | How long until the ping command times out (Default: 5s).                                          |

**Example**

```yaml
resourceGroups:
  boston:
    attributes:
      site: boston
    resources:
      myHost1:
        attributes:
          host.name: my-host-01
          host.type: linux
        protocols:
          ping:
            timeout: 10s
```

## IPMI

Use the parameters below to configure the IPMI protocol:

| Parameter | Description                                                                                       |
| --------- | ------------------------------------------------------------------------------------------------- |
| ipmi      | Protocol used to access the host.                                                                 |
| hostname  | The name or IP address of the resource. If not specified, the `host.name` attribute will be used. |
| username  | Name used to establish the connection with the host via the IPMI protocol.                        |
| password  | Password used to establish the connection with the host via the IPMI protocol.                    |

**Example**

```yaml
resourceGroups:
  boston:
    attributes:
      site: boston
    resources:
      myHost1:
        attributes:
          host.name: my-host-01
          host.type: oob
        protocols:
          ipmi:
            username: myusername
            password: mypwd
```

## JDBC

Use the parameters below to configure JDBC to connect to a database:

| Parameter | Description                                                                                       |
| --------- | ------------------------------------------------------------------------------------------------- |
| jdbc      | JDBC configuration used to connect to a database on the host                                      |
| hostname  | The name or IP address of the resource. If not specified, the `host.name` attribute will be used. |
| timeout   | How long until the SQL query times out (Default: 120s).                                           |
| username  | Name used to authenticate against the database.                                                   |
| password  | Password used to authenticate against the database.                                               |
| url       | The JDBC connection URL to access the database.                                                   |
| type      | The type of database (e.g., Oracle, PostgreSQL, MSSQL, Informix, Derby, H2).                      |
| port      | The port number used to connect to the database.                                                  |
| database  | The name of the database instance to connect to on the server.                                    |

**Example**

```yaml
resourceGroups:
  boston:
    attributes:
      site: boston
    resources:
      db-host:
        attributes:
          host.name: my-host-02
          host.type: win
        protocols:
          jdbc:
            hostname: my-host-02
            username: dbuser
            password: dbpassword
            url: jdbc:mysql://my-host-02:3306
            timeout: 120s
            type: mysql
            port: 3306
```

## JMX

Use the parameters below to configure the JMX protocol:

| Parameter | Description                                                                                       |
| --------- | ------------------------------------------------------------------------------------------------- |
| jmx       | JMX configuration used to access the host.                                                        |
| hostname  | The name or IP address of the resource. If not specified, the `host.name` attribute will be used. |
| timeout   | How long until the JMX query times out (Default: 30s).                                            |
| username  | Name used to authenticate against the JMX service.                                                |
| password  | Password used to authenticate against the JMX service.                                            |
| port      | The port number used to connect to the JMX service (Default: 1099).                               |

**Example**

```yaml
resourceGroups:
  boston:
    attributes:
      site: boston
    resources:
      db-host:
        attributes:
          host.name: cassandra-01
          host.type: linux
        protocols:
          jmx:
            timeout: 30s
            port: 7199
```

## OS Commands

Use the parameters below to configure OS Commands that are executed locally:

| Parameter       | Description                                                                                       |
| --------------- | ------------------------------------------------------------------------------------------------- |
| osCommand       | Protocol used to access the host.                                                                 |
| hostname        | The name or IP address of the resource. If not specified, the `host.name` attribute will be used. |
| timeout         | How long until the local OS Commands time out (Default: 120s).                                    |
| useSudo         | Whether sudo is used or not for the local OS Command: true or false (Default: false).             |
| useSudoCommands | List of commands for which sudo is required.                                                      |
| sudoCommand     | Sudo command to be used (Default: sudo).                                                          |

**Example**

```yaml
resourceGroups:
  boston:
    attributes:
      site: boston
    resources:
      myHost1:
        attributes:
          host.name: my-host-01
          host.type: linux
        protocols:
          osCommand:
            timeout: 120
            useSudo: true
            useSudoCommands: [cmd1, cmd2]
            sudoCommand: sudo
```

## SSH

Use the parameters below to configure the SSH protocol:

| Parameter       | Description                                                                                       |
| --------------- | ------------------------------------------------------------------------------------------------- |
| ssh             | Protocol used to access the host.                                                                 |
| hostname        | The name or IP address of the resource. If not specified, the `host.name` attribute will be used. |
| timeout         | How long until the command times out (Default: 120s).                                             |
| port            | The SSH port number to use for the SSH connection (Default: 22).                                  |
| useSudo         | Whether sudo is used or not for the SSH Command (true or false).                                  |
| useSudoCommands | List of commands for which sudo is required.                                                      |
| sudoCommand     | Sudo command to be used (Default: sudo).                                                          |
| username        | Name to use for performing the SSH query.                                                         |
| password        | Password to use for performing the SSH query.                                                     |
| privateKey      | Private Key File to use to establish the connection to the host through the SSH protocol.         |

**Example**

```yaml
resourceGroups:
  boston:
    attributes:
      site: boston
    resources:
      myHost1:
        attributes:
          host.name: my-host-01
          host.type: linux
        protocols:
          ssh:
            timeout: 120
            port: 22
            useSudo: true
            useSudoCommands: [cmd1, cmd2]
            sudoCommand: sudo
            username: myusername
            password: mypwd
            privateKey: /tmp/ssh-key.txt
```

## SNMP

Use the parameters below to configure the SNMP protocol (versions 1 and 2c):

| Parameter | Description                                                                                       |
| --------- | ------------------------------------------------------------------------------------------------- |
| snmp      | Protocol used to access the host.                                                                 |
| hostname  | The name or IP address of the resource. If not specified, the `host.name` attribute will be used. |
| version   | The version of the SNMP protocol (v1, v2c).                                                       |
| community | The SNMP Community string to use to perform SNMP v1 queries (Default: public).                    |
| port      | The SNMP port number used to perform SNMP queries (Default: 161).                                 |
| timeout   | How long until the SNMP request times out (Default: 120s).                                        |

**Example**

```yaml
resourceGroups:
  boston:
    attributes:
      site: boston
    resources:
      myHost1:
        attributes:
          host.name: my-host-01
          host.type: linux
        protocols:
          snmp:
            version: v1
            community: public
            port: 161
            timeout: 120s

      myHost2:
        attributes:
          host.name: my-host-02
          host.type: linux
        protocols:
          snmp:
            version: v2c
            community: public
            port: 161
            timeout: 120s
```

## SNMP Version 3

Use the parameters below to configure the SNMP version 3 protocol:

| Parameter       | Description                                                                                                                     |
| --------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| snmpv3          | Protocol used to access the host using SNMP version 3.                                                                          |
| hostname        | The name or IP address of the resource. If not specified, the `host.name` attribute will be used.                               |
| timeout         | How long until the SNMP request times out (Default: 120s).                                                                      |
| port            | The SNMP port number used to perform SNMP version 3 queries (Default: 161).                                                     |
| contextName     | The name of the SNMP version 3 context, used to identify the collection of management information.                              |
| authType        | The SNMP version 3 authentication protocol (MD5, SHA, SHA224, SHA256, SHA512, SHA384 or NoAuth) to ensure message authenticity. |
| privacy         | The SNMP version 3 privacy protocol (DES, AES, AES192, AES256 or NONE) used to encrypt messages for confidentiality.            |
| username        | The username used for SNMP version 3 authentication.                                                                            |
| privacyPassword | The password used to encrypt SNMP version 3 messages for confidentiality.                                                       |
| password        | The password used for SNMP version 3 authentication.                                                                            |
| retryIntervals  | The intervals (in milliseconds) between SNMP request retries.                                                                   |

**Example**

```yaml
resourceGroups:
  boston:
    attributes:
      site: boston
    resources:
      myHost3:
        attributes:
          host.name: my-host-03
          host.type: linux
        protocols:
          snmpv3:
            port: 161
            timeout: 120s
            contextName: myContext
            authType: SHA
            privacy: AES
            username: myUser
            privacyPassword: myPrivacyPassword
            password: myAuthPassword
```

## WBEM

Use the parameters below to configure the WBEM protocol:

| Parameter | Description                                                                                       |
| --------- | ------------------------------------------------------------------------------------------------- |
| wbem      | Protocol used to access the host.                                                                 |
| hostname  | The name or IP address of the resource. If not specified, the `host.name` attribute will be used. |
| protocol  | The protocol used to access the host.                                                             |
| port      | The HTTPS port number used to perform WBEM queries (Default: 5989 for HTTPS or 5988 for HTTP).    |
| timeout   | How long until the WBEM request times out (Default: 120s).                                        |
| username  | Name used to establish the connection with the host via the WBEM protocol.                        |
| password  | Password used to establish the connection with the host via the WBEM protocol.                    |
| vcenter   | vCenter hostname providing the authentication ticket, if applicable.                              |

**Example**

```yaml
resourceGroups:
  boston:
    attributes:
      site: boston
    resources:
      myHost1:
        attributes:
          host.name: my-host-01
          host.type: storage
        protocols:
          wbem:
            protocol: https
            port: 5989
            timeout: 120s
            username: myusername
            password: mypwd
```

## WMI

Use the parameters below to configure the WMI protocol:

| Parameter | Description                                                                                       |
| --------- | ------------------------------------------------------------------------------------------------- |
| wmi       | Protocol used to access the host.                                                                 |
| hostname  | The name or IP address of the resource. If not specified, the `host.name` attribute will be used. |
| timeout   | How long until the WMI request times out (Default: 120s).                                         |
| username  | Name used to establish the connection with the host via the WMI protocol.                         |
| password  | Password used to establish the connection with the host via the WMI protocol.                     |

**Example**

```yaml
resourceGroups:
  boston:
    attributes:
      site: boston
    resources:
      myHost1:
        attributes:
          host.name: my-host-01
          host.type: win
        protocols:
          wmi:
            timeout: 120s
            username: myusername
            password: mypwd
```

## WinRM

Use the parameters below to configure the WinRM protocol:

| Parameter       | Description                                                                                          |
| --------------- | ---------------------------------------------------------------------------------------------------- |
| winrm           | Protocol used to access the host.                                                                    |
| hostname        | The name or IP address of the resource. If not specified, the `host.name` attribute will be used.    |
| timeout         | How long until the WinRM request times out (Default: 120s).                                          |
| username        | Name used to establish the connection with the host via the WinRM protocol.                          |
| password        | Password used to establish the connection with the host via the WinRM protocol.                      |
| protocol        | The protocol used to access the host: HTTP or HTTPS (Default: HTTP).                                 |
| port            | The port number used to perform WQL queries and commands (Default: 5985 for HTTP or 5986 for HTTPS). |
| authentications | Ordered list of authentication schemes: NTLM, KERBEROS (Default: NTLM).                              |

**Example**

```yaml
resourceGroups:
  boston:
    attributes:
      site: boston
    resources:
      myHost1:
        attributes:
          host.name: my-host-01
          host.type: win
        protocols:
          winrm:
            protocol: http
            port: 5985
            username: myusername
            password: mypwd
            timeout: 120s
            authentications: [ntlm]
```
