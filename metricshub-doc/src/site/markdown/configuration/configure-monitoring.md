keywords: agent, configuration, protocols, jmx, snmp, wbem, wmi, ping, ipmi, ssh, http, os command, winrm, sites
description: How to configure the MetricsHub Agent to collect metrics from a variety of resources with various protocols.

# Monitoring Configuration

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

**MetricsHub** extracts metrics from the resources defined in the configuration file(s). These **resources** can be hosts, applications, or any components running in your IT infrastructure.
Each **resource** is typically associated with a physical location, such as a data center or server room, or a logical location, like a business unit.
In **MetricsHub**, these locations are referred to as **sites**.
In highly distributed infrastructures, multiple resources can be organized into **resource groups** to simplify management and monitoring.

To reflect this organization, you are asked to define your **resource group** first, followed by your **site** and its corresponding **resources** in one or multiple `.yaml` or `.yml` configuration file(s) stored in:

> * `C:\ProgramData\MetricsHub\config` on Windows systems
> * `./metricshub/lib/config` on Linux systems

> **Important**: We recommend using an editor supporting the
[Schemastore](https://www.schemastore.org/metricshub.json) to edit **MetricsHub**'s configuration YAML
 files (Example: [Visual Studio Code](https://code.visualstudio.com/download) and
 [vscode.dev](https://vscode.dev),
 with [RedHat's YAML extension](https://marketplace.visualstudio.com/items?itemName=redhat.vscode-yaml)).

## Step 1: Structure your configuration

Before diving into your monitoring setup, take a moment to choose the right configuration structure. A thoughtful approach will make ongoing maintenance and updates much easier.

Starting from version 2.0.00, you can choose between:

* One single `metricshub.yaml` configuration file(ideal for small-scale environment)
* Multiple configuration files. Recommended for larger environments with hundreds or thousands of systems, this structure allows you to:
  * separate concerns (e.g., global settings, license, monitored site, monitored resource)
  * dynamically manage configuration through templating or automation scripts.

> Note: If you’re currently using a single configuration file and plan to split it into multiple files, refer to the [Upgrades Notes](../upgrade.md#support-for-multiple-configuration-files) for guidance.

### Recommendations for multiple configuration files

For clear and maintainable configurations, we recommend the following structure:

```
config/
├── global-settings.yaml         # Collect period, logging, otel settings, etc.
├── license.yaml                 # License configuration for the Enterprise edition
├── <site>-resources.yaml        # Defines all resources monitored at the <site> location
├── <resource-id>-resource.yaml  # Your resource configuration
├── resources.vm                 # Your programmatic resources configuration (.vm extension)
├── ...
```

Each file must contain a valid fragment of the complete configuration. The **MetricsHub Agent** parses all the `.yaml` or `.yml` files with valid syntax found in the `config/` directory`.

> **To prevent configuration conflicts, duplicates, or unintended overrides**:

* Move backup or example files to a subfolder such as `config/examples/` or `config/backups/`.
* Disable unused files by renaming them with a non-YAML extension (e.g., `.bak`, `.txt`, or `.disabled`).

#### Examples

**`global-settings.yaml`**

```yaml
collectPeriod: 1m
loggerLevel: info
enableSelfMonitoring: true
```

**`license.yaml`**

```yaml
license:
  product: MetricsHub Enterprise
  organization: YOUR_ORGANIZATION
  expiresOn: EXPIRATION_DATE
  resources: NUMBER_OF_RESOURCES
  key: YOUR_LICENSE_KEY
```

**`paris-resources.yaml`**

```yaml
resourceGroups:
  paris:
    attributes:
      site: paris-dc
    resources:
      server-1:
        attributes:
          host.name: paris-server-1
          host.type: linux
        protocols:
          ssh:
            username: root
            password: changeme
```

**`paris-server-2-resource.yaml`**

```yaml
resources:
  server-2:
    attributes:
      site: paris-dc
      host.name: paris-server-2
      host.type: linux
    protocols:
      ssh:
        username: root
        password: changeme
```

## Step 2: Configure resource groups

> Note: For centralized infrastructures, `resourceGroups` are not required.
 Simply configure resources as explained in [Step 3](./configure-monitoring.md#step-3-configure-resources).

Create a resource group for each site to be monitored under the `resourceGroups:` section:

```yaml
resourceGroups:
  <resource-group-name>: 
    attributes:
      site: <site-name> # Specify where resources are hosted
```

Replace:

* `<resource-group-name>` with the actual name of your resource group
* `<site-name>` with the name of a logical or physical location. This value must be unique.

**Example:**

```yaml
resourceGroups:
  boston:
    attributes:
      site: boston
```

At this stage, you can configure sustainability metrics reporting. For more details, refer to the [Sustainability](configure-sustainability-metrics.html) page.

## Step 3: Configure resources

**Resources** can either be configured:

* under the `resources` section located at the top of the configuration file *(recommended for centralized infrastructures)*

    ```yaml
    attribute:
      site: <central-site>

    resources:
      <resource-id>:
        attributes:
          host.name: <hostname>
          host.type: <type>
        <protocol-configuration>
    ```

* or under the resource group you previously specified *(recommended for highly distributed infrastructures)*

  ```yaml
  resourceGroups: 
    <resource-group-name>: 
      attributes:
        site: <site-name> 
      resources:
        <resource-id>:
          attributes:
            host.name: <hostname>
            host.type: <type>
          <protocol-configuration>
  ```

The syntax to adopt for configuring your resources will differ whether your resources have unique
or similar characteristics (such as device type, protocols, and credentials).

### Syntax for unique resources

```yaml
resources:
  <resource-id>:
    attributes:
      host.name: <hostname> 
      host.type: <type>  
    <protocol-configuration> 
```

### Syntax for resources sharing similar characteristics

```yaml
resources:
  <resource-id>:
    attributes:
      host.name: [ <hostname1>, <hostname2>, etc. ]
      host.type: <type>
      host.extra.attribute: [ <extra-attribute-for-hostname1>, <extra-attribute-for-hostname2>, etc. ]
    <protocol-configuration>
```

Whatever the syntax adopted, replace:

* `<hostname>` with the actual hostname or IP address of the resource
* `<type>` with the type of resource to be monitored. Possible values are:
  * [`win`](https://metricshub.com/docs/latest/connectors/tags/windows.html) for Microsoft Windows systems
  * [`linux`](https://metricshub.com/docs/latest/connectors/tags/linux.html) for Linux systems
  * [`network`](https://metricshub.com/docs/latest/connectors/tags/network.html) for network devices
  * `oob` for Out-of-band management cards
  * [`storage`](https://metricshub.com/docs/latest/connectors/tags/storage.html) for storage systems
  * [`aix`](https://metricshub.com/docs/latest/connectors/tags/aix.html) for IBM AIX systems
  * [`hpux`](https://metricshub.com/docs/latest/connectors/tags/hp-ux.html) for HP UX systems
  * [`solaris`](https://metricshub.com/docs/latest/connectors/tags/solaris.html) for Oracle Solaris systems
  * [`tru64`](https://metricshub.com/docs/latest/connectors/tags/hpe.html) for HP Tru64 systems
  * [`vms`](https://metricshub.com/docs/latest/connectors/tags/hpe.html) for HP Open VMS systems.
  Check out the [Connector Directory](https://metricshub.com/docs/latest/metricshub-connectors-directory.html) to find out which type corresponds to your system.
* `<protocol-configuration>` with the protocol(s) **MetricsHub** will use to communicate with the resources:
 [`http`](./configure-monitoring.md#http), [`ipmi`](./configure-monitoring.md#ipmi), [`jdbc`](./configure-monitoring.md#jdbc), [`jmx`](./configure-monitoring.md#jmx), [`oscommand`](./configure-monitoring.md#os-commands), [`ping`](./configure-monitoring.md#icmp-ping), [`ssh`](./configure-monitoring.md#ssh), [`snmp`](./configure-monitoring.md#snmp), [`wbem`](./configure-monitoring.md#wbem),[`wmi`](./configure-monitoring.md#wmi),  or [`winrm`](./configure-monitoring.md#winrm).
 Refer to [Protocols and Credentials](./configure-monitoring.html#protocols-and-credentials) for more details.

> Note: You can use the `${esc.d}{env::ENV_VARIABLE_NAME}` syntax in the configuration file to call your environment variables.

**Example**

```yaml
resourceGroups:
  boston:
    attributes:
      site: boston
    resources:
      myBostonHost1:
        attributes:
          host.name: my-boston-host-01
          host.type: storage
        <protocol-configuration>
      myBostonHost2:
        attributes:
          host.name: my-boston-host-02
          host.type: storage
        <protocol-configuration>
  chicago:
    attributes:
      site: chicago
    resources:
      myChicagoHost1:
        attributes:
          host.name: my-chicago-host-01
          host.type: storage
        <protocol-configuration>
      myChicagoHost2:
        attributes:
          host.name: my-chicago-host-02
          host.type: storage
        <protocol-configuration>
```

### Protocols and credentials

#### HTTP

Use the parameters below to configure the HTTP protocol:

| Parameter | Description                                                                                       |
|-----------|---------------------------------------------------------------------------------------------------|
| http      | Protocol used to access the host.                                                                 |
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

#### ICMP Ping

Use the parameters below to configure the ICMP ping protocol:

| Parameter | Description                                                                                       |
|-----------|---------------------------------------------------------------------------------------------------|
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

#### IPMI

Use the parameters below to configure the IPMI protocol:

| Parameter | Description                                                                                       |
|-----------|---------------------------------------------------------------------------------------------------|
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

#### JDBC

Use the parameters below to configure JDBC to connect to a database:

| Parameter | Description                                                                                       |
|-----------|---------------------------------------------------------------------------------------------------|
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

#### JMX

Use the parameters below to configure the JMX protocol:

| Parameter | Description                                                                                       |
|-----------|---------------------------------------------------------------------------------------------------|
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

#### OS commands

Use the parameters below to configure OS Commands that are executed locally:

| Parameter       | Description                                                                                       |
|-----------------|---------------------------------------------------------------------------------------------------|
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
            useSudoCommands: [ cmd1, cmd2 ]
            sudoCommand: sudo
```

#### SSH

Use the parameters below to configure the SSH protocol:

| Parameter       | Description                                                                                       |
|-----------------|---------------------------------------------------------------------------------------------------|
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
            useSudoCommands: [ cmd1, cmd2 ]
            sudoCommand: sudo
            username: myusername
            password: mypwd
            privateKey: /tmp/ssh-key.txt

```

#### SNMP

Use the parameters below to configure the SNMP protocol:

| Parameter | Description                                                                                       |
|-----------|---------------------------------------------------------------------------------------------------|
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

#### SNMP version 3

Use the parameters below to configure the SNMP version 3 protocol:

| Parameter       | Description                                                                                                                     |
|-----------------|---------------------------------------------------------------------------------------------------------------------------------|
| snmpv3          | Protocol used to access the host using SNMP version 3.                                                                          |
| hostname        | The name or IP address of the resource. If not specified, the `host.name` attribute will be used.                               |
| timeout         | How long until the SNMP request times out (Default: 120s).                                                                      |
| port            | The SNMP port number used to perform SNMP version 3 queries (Default: 161).                                                     |
| contextName     | The name of the SNMP version 3 context, used to identify the collection of management information.                              |
| authType        | The SNMP version 3 authentication protocol (MD5, SHA, SHA224, SHA256, SHA512, SHA384 or NoAuth) to ensure message authenticity. |
| privacy         | The SNMP version 3 privacy protocol (DES, AES or NONE) used to encrypt messages for confidentiality.                            |
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

#### WBEM

Use the parameters below to configure the WBEM protocol:

| Parameter | Description                                                                                       |
|-----------|---------------------------------------------------------------------------------------------------|
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

#### WMI

Use the parameters below to configure the WMI protocol:

| Parameter | Description                                                                                       |
|-----------|---------------------------------------------------------------------------------------------------|
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

#### WinRM

Use the parameters below to configure the WinRM protocol:

| Parameter       | Description                                                                                          |
|-----------------|------------------------------------------------------------------------------------------------------|
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

### Programmable Configuration

**MetricsHub** introduces the **programmable configuration support** via [Apache Velocity](https://velocity.apache.org) templates. This enables dynamic generation of resource configurations using external sources such as files or HTTP APIs.

You can now use tools like `${esc.d}http.execute(...)` or `${esc.d}file.readAllLines(...)` in the `.vm` templates to fetch and transform external data into valid resource configuration blocks.

These templates leverage [Velocity Tools](https://velocity.apache.org/tools/3.1/tools-summary.html) to simplify logic and data handling. Some commonly used tools include:

- `${esc.d}http`: for making HTTP requests
- `${esc.d}file`: for reading local files
- `${esc.d}json`: for parsing JSON data
- `${esc.d}collection`: for splitting strings or manipulating collections
- `${esc.d}date`, `${esc.d}number`, `${esc.d}esc`, and many others.

#### `${esc.d}http.execute` tool arguments

The `${esc.d}http` tool allows you to execute HTTP requests directly from templates using the `execute` function (`${esc.d}http.execute({ ...args })`). The following arguments are supported:

| Argument   | Description                                                            |
| ---------- | ---------------------------------------------------------------------- |
| `url`      | (Required) The HTTP(S) endpoint to call.                               |
| `method`   | HTTP method (`GET`, `POST`, etc.). Defaults to `GET` if not specified. |
| `username` | Username used for authentication.                                |
| `password` | Password used for authentication.                                |
| `headers`  | HTTP headers, written as newline-separated `Key: Value` pairs.         |
| `body`     | Payload to send with the request (e.g., for `POST`).                   |
| `timeout`  | Request timeout in seconds. Defaults to `60`.                          |

Two convenience functions are also available:
- `${esc.d}http.get(...)`: Executes a `GET` request without requiring a `method` argument.
- `${esc.d}http.post(...)`: Executes a `POST` request without requiring a `method` argument.

#### Example 1: Load resources from an HTTP API

Suppose your API endpoint at `https://cmdb/servers` returns:

```
[
  {"hostname":"host1","OSType":"win","adminUsername":"admin1"},
  {"hostname":"host2","OSType":"win","adminUsername":"admin2"}
]
```

You can dynamically create resource blocks using:

```
resources:
${esc.h}set(${esc.d}hostList = ${esc.d}json.parse(${esc.d}http.get({ "url": "https://cmdb/servers" }).body).root())

${esc.h}foreach(${esc.d}host in ${esc.d}hostList)
  ${esc.h}if(${esc.d}host.OSType == "win")
  ${esc.d}host.hostname:
    attributes:
      host.name: ${esc.d}host.hostname
      host.type: windows
    protocols:
      ping:
      wmi:
        timeout: 120
        username: ${esc.d}host.adminUsername
        password: ${esc.d}http.get({ "url": "https://passwords/servers/${esc.d}{host.hostname}/password" }).body
  ${esc.h}end
${esc.h}end
```

#### Example 2: Load resources from a local file

Assume a CSV file contains:

```
host1,win,wmi,user1,pass1
host2,linux,ssh,user2,pass2
```

Use this Velocity template to generate the resource block:

```
${esc.h}set(${esc.d}lines = ${esc.d}file.readAllLines("/opt/data/resources.csv"))
resources:
${esc.h}foreach(${esc.d}line in ${esc.d}lines)
  ${esc.h}set(${esc.d}fields = ${esc.d}collection.split(${esc.d}line, ","))
  ${esc.h}set(${esc.d}hostname = ${esc.d}fields.get(0))
  ${esc.h}set(${esc.d}hostType = ${esc.d}fields.get(1))
  ${esc.h}set(${esc.d}protocol = ${esc.d}fields.get(2))
  ${esc.h}set(${esc.d}username = ${esc.d}fields.get(3))
  ${esc.h}set(${esc.d}password = ${esc.d}fields.get(4))
  ${esc.d}hostname:
    attributes:
      host.name: ${esc.d}hostname
      host.type: ${esc.d}hostType
    protocols:
      ${esc.d}protocol:
        username: ${esc.d}username
        password: ${esc.d}password
${esc.h}end
```

## Step 4: Configure additional settings

### Customize resource hostname

By default, the `host.name` attribute specified for a resource determines both:

* the hostname used to execute requests against the resource for collecting metrics
* the hostname associated with each OpenTelemetry metric collected for the resource.

If your resource requires different hostnames for these purposes, you can customize the configuration as follows.

#### Example for unique resources

Here’s an example of customizing the hostname for a unique resource:

```yaml
resources:
  myHost1:
    attributes:
      host.name: custom-hostname # Hostname applied to the collected metrics 
      host.type: linux
    protocols:
      snmp:
        hostname: my-host-01 # Hostname used for the SNMP requests
        version: v1
        community: public
        port: 161
        timeout: 1m
```

#### Example for resources sharing similar characteristics

For resources with shared characteristics, you can define multiple hostnames in the configuration:

```yaml
resources:
  shared-characteristic-hosts:
    attributes:
      host.name: [ custom-hostname1, custom-hostname2 ] # Hostnames applied to the collected metrics 
      host.type: linux
    protocols:
      snmp:
        hostname: [ my-host-01, my-host-02 ] # Hostnames used for the SNMP requests
        version: v1
        community: public
        port: 161
        timeout: 1m
```

> **Important**: Ensure the values of `host.name` are listed in the exact same order as those in `hostname`. Each value listed in `host.name` must correspond to the value at the same position in `hostname`. Misaligned orders will result in mismatched data and inconsistencies in the collected metrics for each resource.

### Customize resource monitoring

If the connectors included in **MetricsHub** do not collect the metrics you need, you can configure one or several monitors to obtain this data from your resource and specify its corresponding attributes and metrics in **MetricsHub**.

A monitor defines how **MetricsHub** collects and processes data for the resource. For each monitor, you must provide the following information:

* its name
* the type of job it performs (e.g., `simple` for straightforward monitoring tasks)
* the data sources from which metrics are collected
* how the collected metrics are mapped to **MetricsHub**'s monitoring model.

#### Configuration

Follow the structure below to declare your monitor:

```yaml
<resource-group>:
  <resource-key>:
    attributes:
      # <attributes...>
    protocols:
      # <credentials...>
    monitors:
      <monitor-name>:
        <job>: # Job type, e.g., "simple"
          sources:
            <source-name>:
              # <source-content>
          mapping:
            source: <mapping-source-reference>
            attributes:
              # <attributes-mapping...>
            metrics:
              # <metrics-mapping...>
```

Refer to:
- [Monitors](https://metricshub.org/community-connectors/develop/monitors.html) for more information on how to configure custom resource monitoring.
- [Monitoring the health of a Web service](https://metricshub.com/usecases/monitoring-the-health-of-a-web-service/) for a practical example that demonstrates how to use this feature effectively.

### OTLP Exporter settings

**MetricsHub** sends collected metrics to an OTLP Receiver using **gRPC** or **HTTP/Protobuf**.

* In the **Enterprise Edition**, telemetry is **automatically sent** to the embedded *OpenTelemetry Collector*. You can also configure it to send metrics directly to observability platforms that support **native OTLP ingestion**. A working example is provided in the `metricshub-example.yaml` file.
* In the **Community Edition**, you need to manually configure the OTLP Exporter settings in the [MetricsHub configuration file](#step-1-structure-your-configuration) under the `otel` section.

The table below describes the available OTLP Exporter properties:

| Property                                                    | Description                                                                                                                                                                                                                    |
| ----------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **`otel.exporter.otlp.metrics.endpoint`**                   | The OTLP metrics endpoint URL. <p><p></p>Must be an `http` or `https` URL, depending on whether TLS is used.</p><p></p><p>**Default:** `http://localhost:4317` (gRPC) / `http://localhost:4318/v1/metrics` (HTTP/Protobuf)</p> |
| **`otel.exporter.otlp.metrics.protocol`**                   | Transport protocol for OTLP metric requests.<p></p><p>Possible Values: `grpc`, `http/protobuf`.</p><p></p><p>**Default:** `grpc`</p>                                                                                           |
| **`otel.exporter.otlp.metrics.certificate`**                | Path to a PEM-formatted file containing trusted certificates for verifying the OTLP server’s TLS credentials.<p></p><p>**Default:** Uses the host platform’s trusted root certificates</p>                                     |
| **`otel.exporter.otlp.metrics.headers`**                    | Custom headers to send with OTLP metric requests, typically for authentication.<p></p><p>**Default:** Not set</p>                                                                                                              |
| **`otel.exporter.otlp.metrics.timeout`**                    | Timeout for OTLP metric requests (in seconds).<p></p><p>**Default:** `10` </p>                                                                                                                                                 |
| **`otel.exporter.otlp.metrics.pool.size`**                  | Exporter pool size.<p></p><p> This setting directly determines how many metric export operations can run in parallel.</p><p></p><p>**Default:** `20`</p>                                                                       |
| **`otel.exporter.otlp.metrics.append_resource_attributes`** | When enabled, all resource attributes will be added as attributes on each exported metric.<p></p><p>**Default:** `false`</p>                                                                                                   |

#### Example

To send **MetricsHub** metrics via **gRPC** to an `OTLP Receiver` at `https://localhost:4317`, including an *Authorization* header, configure the following in the [MetricsHub configuration file](#step-1-structure-your-configuration):

```yaml
otel:
  otel.exporter.otlp.metrics.endpoint: https://localhost:4317
  otel.exporter.otlp.metrics.protocol: grpc
  otel.exporter.otlp.metrics.headers: Authorization=<value>
```

This configuration ensures that metrics are securely transmitted to the specified endpoint.

### Basic Authentication settings

#### Enterprise Edition authentication

In the Enterprise Edition, the **MetricsHub**'s internal `OTLP Exporter` authenticates itself with the *OpenTelemetry Collector*'s [OTLP gRPC Receiver](send-telemetry.md#otlp-grpc) by including the HTTP `Authorization` request header with the credentials.

These settings are already configured in the **MetricsHub Enterprise Edition** configuration file. Changing them is **not recommended** unless you are familiar with managing communication between the **MetricsHub** `OTLP Exporter` and the *OpenTelemetry Collector*'s `OTLP Receiver`.

To override the default value of the *Basic Authentication Header*, configure the `otel.exporter.otlp.metrics.headers` parameter under the `otel` section:

```yaml
# Internal OpenTelemetry configuration
otel:
  otel.exporter.otlp.metrics.endpoint: https://localhost:4317
  otel.exporter.otlp.metrics.protocol: grpc
  otel.exporter.otlp.metrics.headers: Authorization=Basic <base64-username-password>

resourceGroups: # ...
```

where `<base64-username-password>` credentials are built by first joining your username and password with a colon (`myUsername:myPassword`) and then encoding the value in `base64`.

> **Warning**: If you update the *Basic Authentication Header*, you must generate a new `.htpasswd` file for the [OpenTelemetry Collector Basic Authenticator](send-telemetry.md#basic-authenticator).

#### Community Edition authentication

If your `OTLP Receiver` requires authentication headers, configure the `otel.exporter.otlp.metrics.headers` parameter under the `otel` section:

```yaml
otel:
  otel.exporter.otlp.metrics.headers: <custom-header1>

resourceGroups: # ...
```

### Monitoring settings

#### Collect period

By default, **MetricsHub** collects metrics from the monitored resources every minute. To change the default collect period:

* For all your resources, add the `collectPeriod` parameter just before the `resourceGroups` section:

    ```yaml
    collectPeriod: 2m

    resourceGroups: # ...
    ```

* For a specific resource, add the `collectPeriod` parameter at the resource level. In the example below, we set the `collectPeriod` to `1m30s` for `myHost1`:

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
            collectPeriod: 1m30s # Customized
    ```

> **Warning**: Collecting metrics too frequently can cause CPU-intensive workloads.

#### Connectors

When running **MetricsHub**, the connectors are automatically selected based on the device type provided and the enabled protocols. However, you have the flexibility to specify which connectors should be utilized or omitted.

The `connectors` parameter allows you to force, select, or exclude specific connectors. Connector names or category tags should be separated by commas, as illustrated in the example below:

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
        connectors: [ "#system" ]
```

* To force a connector, precede the connector identifier with a plus sign (`+`), as in `+MIB2`.
* To exclude a connector from automatic detection, precede the connector identifier with an exclamation mark (`!`), like `!MIB2`.
* To stage a connector for processing by automatic detection, configure the connector identifier, for instance, `MIB2`.
* To stage a category of connectors for processing by automatic detection, precede the category tag with a hash (`#`), such as `#hardware` or `#system`.
* To exclude a category of connectors from automatic detection, precede the category tag to be excluded with an exclamation mark and a hash sign (`!#`), such as `!#system`.

> **Notes**:
>
>* Any misspelled connector will be ignored.
>* Misspelling a category tag will prevent automatic detection from functioning due to an empty connectors staging.

##### Examples

* Example 1:

  ```yaml
  connectors: [ "#hardware" ]
  ```

 The core engine will automatically detect connectors categorized under `hardware`.

* Example 2:

  ```yaml
  connectors: [ "!#hardware", "#system" ]
  ```

  The core engine will perform automatic detection on connectors categorized under `system`, excluding those categorized under `hardware`.

* Example 3:

  ```yaml
  connectors: [ DiskPart, MIB2, "#system" ]
  ```

  The core engine will automatically detect connectors named `DiskPart`, `MIB2`, and all connectors under the `system` category.

* Example 4:

  ```yaml
  connectors: [ +DiskPart, MIB2, "#system" ]
  ```

  The core engine will force the execution of the `DiskPart` connector and then proceed with the automatic detection of `MIB2` and all connectors under the `system` category.

* Example 5:

  ```yaml
  connectors: [ DiskPart, "!#system" ]
  ```

  The core engine will perform automatic detection exclusively on the `DiskPart` connector.

* Example 6:

  ```yaml
  connectors: [ +Linux, MIB2 ]
  ```

  The core engine will force the execution of the `Linux` connector and subsequently perform automatic detection on the `MIB2` connector.

* Example 7:

  ```yaml
  connectors: [ "!Linux" ]
  ```

  The core engine will perform automatic detection on all connectors except the `Linux` connector.

* Example 8:

  ```yaml
  connectors: [ "#hardware", "!MIB2" ]
  ```

  The core engine will perform automatic detection on connectors categorized under `hardware`, excluding the `MIB2` connector.

To know which connectors are available, refer to [Connectors Directory](../metricshub-connectors-directory.html).

Otherwise, you can list the available connectors using the below command:

```shell-session
metricshub -l
```

For more information about the `metricshub` command, refer to [MetricsHub CLI (metricshub)](../guides/cli.md).

#### Patch Connectors

By default, **MetricsHub** loads connectors from the `connectors` subdirectory within its installation directory. However, you can extend this functionality by adding a custom directory for additional connectors. This can be done by specifying a patch directory in the [MetricsHub configuration file](#step-1-structure-your-configuration).

To configure an additional connector directory, set the `patchDirectory` property to the path of your custom connectors directory, as shown in the example below:

```yaml

patchDirectory: /opt/patch/connectors # Replace with the path to your patch connectors directory.

loggerLevel: ...
```

#### Customize data collection

**MetricsHub** allows you to customize data collection on your Windows or Linux servers, specifying exactly which processes or services to monitor. This customization is achieved by configuring the following connector variables:

| Connector Variable | Available for                                                                                                                                                    | Usage                                                                      |
|--------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------|
| `matchCommand`     | [Linux - Processes (ps)](../connectors/linuxprocess.html#linux---processes-28ps-29) <br/> [Windows - Processes (WMI)](../connectors/windowsprocess.html)         | Used to specify the command lines to monitor on a Linux or Windows server. |
| `matchName`        | [Linux - Processes (ps)](../connectors/linuxprocess.html#linux---processes-28ps-29) <br/>[Windows - Processes (WMI)](../connectors/windowsprocess.html)          | Used to specify the processes to monitor on a Linux or Windows server.     |
| `matchUser`        | [Linux - Processes (ps)](../connectors/linuxprocess.html#linux---processes-28ps-29)                                                                              | Used to specify the users to include.                                      |
| `serviceNames`     | [Linux - Service (systemctl)](../connectors/linuxservice.html) <br/> [Windows - Services (WMI)](../connectors/windowsservice.html#!#windows---services-28wmi-29) | Used to specify the services to monitor on a Linux or Windows server.      |

Refer to the [Connectors directory](../metricshub-connectors-directory.html#) and more especially to the `Variables` section of the connector to know the supported variables and their accepted values.

##### Procedure

In the MetricsHub configuration file, locate the resource for which you wish to customize data collection and specify the `variables` attribute available under the `additionalConnectors` section:

```yaml
resources:
  <host-id>:
    attributes:
      host.name: <hostname>
      host.type: <type>
    additionalConnectors:
      <connector-custom-id>: # Unique ID. Use 'uses' if different from the original connector ID
        uses: <connector-original-id> # Optional - Original ID if not in key
        force: true # Optional (default: true); false for auto-detection only
        variables:
          <variable-name>: <value>
```

| Property                | Description                                                                                                                    |
|-------------------------|--------------------------------------------------------------------------------------------------------------------------------|
| `<connector-custom-id>` | Custom ID for this additional connector.                                                                                       |
| `uses`                  | *(Optional)* Provide an ID for this additional connector. If not specified, the key ID will be used.                           |
| `force`                 | *(Optional)* Set to `false` if you want the connector to only be activated when detected (Default: `true` - always activated). |
| `variables`             | Specify the connector variable to be used and its value (Format: `<variable-name>: <value>`).                                  |

> Note: If a connector is added under the `additionalConnectors` section with missing or unspecified variables, those variables will automatically be populated with default values defined by the connector itself.

For practical examples demonstrating effective use of this feature, refer to the following pages:
* [Monitoring a process command line](https://metricshub.com/usecases/monitoring-a-process-on-windows/)
* [Monitoring a service running on Linux](https://metricshub.com/usecases/monitoring-a-service-running-on-linux/).

#### Filter monitors

A monitor is any entity tracked by **MetricsHub** within the main resource, such as processes, services, storage volumes, or physical devices like disks.

To manage the volume of telemetry data sent to your observability platform and therefore reduce costs and optimize performance, you can specify which monitors to include or exclude.

You can apply monitor inclusion or exclusion in data collection for the following scopes:

* All resources
* All the resources within a specific resource group. A resource group is a container that holds resources to be monitored and generally refers to a site or a specific location.
* A specific resource

This is done by  adding the `monitorFilters` parameter in the relevant section of the [MetricsHub configuration file](#step-1-structure-your-configuration) as described below:

| Filter monitors                                    | Add monitorFilters                                      |
|----------------------------------------------------|---------------------------------------------------------|
| For all resources                                  | In the global section (top of the file)                 |
| For all the resources of a specific resource group | Under the corresponding `<resource-group-name>` section |
| For a specific resource                            | Under the corresponding `<resource-id>` section         |

The `monitorFilters` parameter accepts the following values:

* `+<monitor_name>` for inclusion
* `"!<monitor_name>"` for exclusion.

To obtain the monitor name:

1. Refer to the [`MetricsHub Connector Library`](../metricshub-connectors-directory.html)
2. Click the connector of your choice (e.g.: [WindowsOS Metrics](../connectors/windows.html))
3. Scroll-down to the **Metrics** section and note down the relevant monitor **Type**.

> **Warning**: Excluding monitors may lead to missed outage detection or inconsistencies in collected data, such as inaccurate power consumption estimates or other metrics calculated by the engine. Use exclusions carefully to avoid overlooking important information.
The monitoring of critical devices such as batteries, power supplies, CPUs, fans, and memories should not be disabled.

##### Example 1: Including monitors for all resources

   ```yaml
   monitorFilters: [ +enclosure, +fan, +power_supply ] # Include specific monitors globally
   resourceGroups: ...
   ```

##### Example 2: Excluding monitors for all resources

   ```yaml
   monitorFilters: [ "!volume" ] # Exclude specific monitors globally
   ```

##### Example 3: Including monitors for all resources within a specific resource group

   ```yaml
   resourceGroups:
     <resource-group-name>:
       monitorFilters: [ +enclosure, +fan, +power_supply ] # Include specific monitors for this group
       resources: ...
   ```

##### Example 4: Excluding monitors for all resources within a specific resource group

   ```yaml
   resourceGroups:
     <resource-group-name>:
       monitorFilters: [ "!volume" ] # Exclude specific monitors for this group
       resources: ...
   ```

##### Example 5: Including monitors for a specific resource

   ```yaml
   resourceGroups:
     <resource-group-name>:
       resources:
         <resource-id>:
           monitorFilters: [ +enclosure, +fan, +power_supply ] # Include specific monitors for this resource
   ```

##### Example 6: Excluding monitors for a specific resource

   ```yaml
   resourceGroups:
     <resource-group-name>:
       resources:
         <resource-id>:
           monitorFilters: [ "!volume" ] # Exclude specific monitors for this resource
   ```

#### Discovery cycle

**MetricsHub** periodically performs discoveries to detect new components in your monitored environment. By default, **MetricsHub** runs a discovery after 30 collects. To change this default discovery cycle:

* For all your resources, add the `discoveryCycle` just before the `resourceGroups` section:

    ```yaml
    discoveryCycle: 15

    resourceGroups: # ...
    ```

* For a specific host, add the `discoveryCycle` parameter at the resource level and indicate the number of collects after which a discovery will be performed. In the example below, we set the `discoveryCycle` to be performed after `5` collects for `myHost1`:

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
            discoveryCycle: 5 # Customized
    ```

> **Warning**: Running discoveries too frequently can cause CPU-intensive workloads.

#### Resource Attributes

Add labels in the `attributes` section to override the data collected by the **MetricsHub Agent** or add additional attributes to the [Host Resource](https://opentelemetry.io/docs/specs/semconv/resource/host/). These attributes are added to each metric of that *Resource* when exported to time series platforms like Prometheus.

In the example below, we added a new `app` attribute and indicated that this is the `Jenkins` app:

```yaml
resourceGroups:
  boston:
    attributes:
      site: boston
    resources:
      myHost1:
        attributes:
          host.name: my-host-01
          host.type: windows
          app: Jenkins
        protocols:
          http:
            https: true
            port: 443
            username: myusername
            password: mypwd
            timeout: 60
```

#### Hostname resolution

By default, **MetricsHub** uses the configured `host.name` value as-is to populate the [Host Resource](https://opentelemetry.io/docs/specs/semconv/resource/host/) attributes. This ensures that the `host.name` remains consistent with what is configured.

To resolve the `host.name` to its Fully Qualified Domain Name (FQDN), set the `resolveHostnameToFqdn` configuration property to `true` as shown below:

```yaml
resolveHostnameToFqdn: true

resourceGroups:
```

This ensures that each configured resource will resolve its `host.name` to FQDN.

To enable FQDN resolution for a specific resource group, set the `resolveHostnameToFqdn` property to `true` under the desired resource group configuration as shown below:

```yaml
resourceGroups:
  boston:
    resolveHostnameToFqdn: true
    attributes:
      site: boston
    resources:
      # ...
```

This ensures that all resources within the `boston` resource group will resolve their `host.name` to FQDN.

To enable FQDN resolution for an individual resource within a resource group, set the `resolveHostnameToFqdn` under the resource configuration as shown below:

```yaml
resourceGroups:
  boston:
    attributes:
      site: boston
    resources:
      my-host-01:
        resolveHostnameToFqdn: true
        attributes:
          host.name: my-host-01
          host.type: linux
```

In this case, only `my-host-01` will resolve its `host.name` to FQDN, while other resources in the `boston` group will retain their original `host.name` values.

> **Warning**: If there is an issue during the resolution, it may result in a different `host.name` value, potentially impacting metric identity.

#### Job pool size

By default, **MetricsHub** runs up to 20 discovery and collect jobs in parallel. To increase or decrease the number of jobs **MetricsHub** can run simultaneously, add the `jobPoolSize` parameter just before the `resourceGroups` section:

```yaml
jobPoolSize: 40 # Customized

resourceGroups: # ...
```

> **Warning**: Running too many jobs in parallel can lead to an OutOfMemory error.

#### Sequential mode

By default, **MetricsHub** sends the queries to the resource in parallel. Although the parallel mode is faster than the sequential one, too many requests at the same time can lead to the failure of the targeted system.

To force all the network calls to be executed in sequential order:

* For all your resources, add the `sequential` parameter before the `resourceGroups` section (**NOT RECOMMENDED**) and set it to `true`:

    ```yaml
    sequential: true

    resourceGroups: # ...
    ```

* For a specific resource, add the `sequential` parameter at the resource level and set it to `true`. In the example below, we enabled the `sequential` mode for `myHost1`

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
            sequential: true # Customized
    ```

> **Warning**: Sending requests in sequential mode slows down the monitoring significantly. Instead of using the sequential mode, you can increase the maximum number of allowed concurrent requests in the monitored system, if the manufacturer allows it.

#### StateSet metrics compression

By default, **MetricsHub** compresses StateSet metrics to reduce unnecessary reporting of zero values and to avoid high cardinality in time series databases. This compression can be configured at various levels: globally, per resource group, or for a specific resource.

##### Compression configuration `stateSetCompression`

This configuration controls how StateSet metrics are reported, specifically whether zero values should be suppressed or not.

* **Supported values:**
  * `none`: No compression is applied. All StateSet metrics, including zero values, are reported on every collection cycle.
  * `suppressZeros` (default): **MetricsHub** compresses StateSet metrics by reporting the zero value only the first time a state transitions to zero. Subsequent reports will include only the non-zero state values.

To configure the StateSet compression level, you can apply the `stateSetCompression` setting in the following scopes:

1. **Global configuration** (applies to all resources):

   Add `stateSetCompression` to the root of the [MetricsHub configuration file](#step-1-structure-your-configuration):

   ```yaml
   stateSetCompression: suppressZeros # set to "none" to disable the StateSet compression
   resourceGroups: ...
   ```

2. **Per resource group** (applies to all resources within a specific group):

   Add `stateSetCompression` within a specific `resourceGroup` in the [MetricsHub configuration file](#step-1-structure-your-configuration):

   ```yaml
   resourceGroups:
     <resource-group-name>:
       stateSetCompression: suppressZeros # set to "none" to disable the StateSet compression
       resources: ...
   ```

3. **Per resource** (applies to a specific resource):

   Add `stateSetCompression` for an individual resource in the [MetricsHub configuration file](#step-1-structure-your-configuration):

   ```yaml
   resourceGroups:
     <resource-group-name>:
       resources:
         <resource-id>:
           stateSetCompression: suppressZeros # set to "none" to disable the StateSet compression
   ```

##### How it works

By default, with `suppressZeros` enabled, **MetricsHub** optimizes metric reporting by suppressing repeated zero values after the initial transition. Only non-zero state metrics will continue to be reported.

**Example: Monitoring the health status of a resource**

Let’s say **MetricsHub** monitors the health status of a specific resource, which can be in one of three states: `ok`, `degraded`, or `failed`.

When compression is **disabled** (`stateSetCompression: none`), **MetricsHub** will report all states, including zeros, during each collection cycle. For example:

```yaml
hw.status{state="ok"} 0
hw.status{state="degraded"} 1
hw.status{state="failed"} 0
```

Here, the resource is in the `degraded` state, but the metrics for the `ok` and `failed` states are also reported with values of `0`. This leads to unnecessary data being sent.

When compression is **enabled** (`stateSetCompression: suppressZeros`), **MetricsHub** will only report the non-zero state, significantly reducing the amount of data collected. For the same scenario, the report would look like this:

```yaml
hw.status{state="degraded"} 1
```

In this case, only the `degraded` state is reported, and the zero values for `ok` and `failed` are suppressed after the initial state transition.

#### Self-Monitoring

The self-monitoring feature helps you track **MetricsHub**'s performance by providing metrics like job duration. These metrics offer detailed insights into task execution times, helping identify bottlenecks or inefficiencies and optimizing performance.

To enable this feature, set the `enableSelfMonitoring` parameter to `true` in the relevant section of the [MetricsHub configuration file](#step-1-structure-your-configuration) as described below:

| Self-Monitoring                                    | Set enableSelfMonitoring to true                        |
|----------------------------------------------------|---------------------------------------------------------|
| For all resources                                  | In the global section (top of the file)                 |
| For all the resources of a specific resource group | Under the corresponding `<resource-group-name>` section |
| For a specific resource                            | Under the corresponding `<resource-id>` section         |

##### Example 1: Enabling self-monitoring for all resources

   ```yaml
   enableSelfMonitoring: true # Set to "false" to disable
   resourceGroups: ...
   ```

##### Example 2: Enabling self-monitoring for all resources of a specific resource group

   ```yaml
   resourceGroups:
     <resource-group-name>:
       enableSelfMonitoring: true # Set to "false" to disable
       resources: ...
   ```

##### Example 3: Enabling self-monitoring for a specific resource

   ```yaml
   resourceGroups:
     <resource-group-name>:
       resources:
         <resource-id>:
           enableSelfMonitoring: true # Set to "false" to disable
   ```

#### Timeout, duration and period format

Timeouts, durations and periods are specified with the below format:

| Unit | Description                   | Examples   |
|------|-------------------------------|------------|
| s    | seconds                       | 120s       |
| m    | minutes                       | 90m, 1m15s |
| h    | hours                         | 1h, 1h30m  |
| d    | days (based on a 24-hour day) | 1d         |

