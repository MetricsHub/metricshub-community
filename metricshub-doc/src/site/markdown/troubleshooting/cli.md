keywords: cli
description: The MetricsHub CLI is the core MetricsHub's engine wrapped in a command line interface. System administrators can easily invoke this tool in a shell to discover and report problems related to the specified resources, including hardware, systems, applications, services, etc.

# MetricsHub CLI (`metricshub`)

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

## Overview

The `metricshub` CLI is the core MetricsHub's engine wrapped in a command line interface. System administrators can easily invoke this tool in a shell to discover and report problems related to the specified resources, including hardware, systems, applications, services, etc.

Discovered components include:

* Enclosure (manufacturer, model, serial number)
* Processors
* Memory modules
* GPUs
* Disks (HDD, SDD, RAID)
* Network and Fiber Channel Adapters
* Sensors (temperature, voltage, fans, power, LEDs)

Supported systems include:

* Servers (Linux, AIX, HP-UX, Solaris, Windows, in-band and out-of-band)
* Blade chassis
* Network switches
* SAN switches
* Storage systems (disk arrays, filers, tape libraries)

The detailed list of systems supported by the enterprise edition (manufacturer and product family and the required instrumentation stack) is listed in [Sentry's Enterprise Connectors Library](../enterprise-platform-requirements.html).

The detailed list of systems supported by the basic edition (manufacturer and product family and the required instrumentation stack) is listed in [Sentry Basic Connectors Library](../basic-platform-requirements.html).

The quantity and quality of the information that **${solutionName}** will gather depends on the instrumentation stack available on the targeted resource (host).

![Output example for an Hitachi system](../images/metricshub-hitachi.png)

Only a few options are required to run the `metricshub` command:

* Hostname or IP address of the device to be monitored
* Device type
* Protocols to be used:
    * HTTP
    * IPMI-over-LAN
    * SSH
    * SNMP
    * WBEM
    * WMI (on Windows only)
* Credentials

![Usage of MetricsHub](../images/metricshub-usage.png)

The `metricshub` command can be used to troubleshoot the monitoring performed by the **MetricsHub Agent** (the core engine) or by other *Sentry Software* products such as the KM for PATROL.

## The Basics

The `metricshub` command invokes the *MetricsHub Engine* against one resource and performs 3 tasks:

1. Detection of the instrumentation stack on the targeted resource (host)
2. Discovery of the resource's components
3. Collection of metrics and status for each resource's component

The `metricshub` command requires a few parameters to run:

* the hostname to connect to
* the type of the resource (Windows, Linux, Management, Storage, Network, AIX, HP-UX, Solaris)
* the protocols to use to gather information from the resource (HTTP, IPMI, SNMP, SSH, WBEM or WMI)
* the credentials

Example:

```batch
$ metricshub server01 -t win --snmp 1 --community secret
```

The above command will connect to `server01` (a `Win`dows system), and will detect which instrumentation stack responds to `SNMP` version `1` with the `secret` community.

Assuming **server01** is an *Hitachi* server, the `metricshub` output will look like:

![Monitoring the server01 Windows system with SNMP](../images/metricshub-hitachi.png)

To learn more about the various options available, simply run the below command:

```batch
$ metricshub -h
```

### Verbose Modes

To get additional details about the operations performed by the *MetricsHub Engine*, run:

* `-v` to display internal warning messages (**WARN**)
* `-vv` to get details about each operation performed (**INFO**)
* `-vvv` to get initialization and connections details (**DEBUG**)
* `-vvvv` to get full visibility about what is happening (**TRACE**)

## Examples

### Storage System, REST API

```batch
$ metricshub STOR01 -t storage --https -u USER
```

This command will connect to the `STOR01` `storage` system in `HTTPS` (port 443 by default) and check whether a known REST API responds with the `USER` credentials. The password will be asked for interactively. Use the `-p P4SSW0RD` option to specify the password directly in the command line (**not secure!**).

### Linux, SNMP v1

```batch
$ metricshub HOST02 -t linux --snmp 1 --community COMM02
```

This command will connect to the `HOST02` `Linux` system using `SNMP` version `1` and the `COMM02` community. If a known SNMP agent is running on this host, `metricshub` will leverage it to discover the physical components of the system and collect various metrics.

> Note: If no SNMP community is specified, **public** is assumed by default.

### Storage System, SNMP v3

```batch
$ metricshub CISC03 -t sto --snmp 3-sha -u USER --snmp-privacy AES --snmp-privacy-password MYSECRET
```

This command will connect to the `CISC03` SAN switch (`sto`rage) using `SNMP` version `3` using `SHA`-based authentication as `USER`. Communication will be encrypted using `AES` with the `MYSECRET` privacy password.

> Note: SAN switches fall into the **Storage** category of systems, like disk arrays and tape libraries, and not in the ~~Network~~ category, like network switches and IP routers.

### Windows, WMI and SNMP v2c

```batch
C:\Program Files\MetricsHub> metricshub WIN04 -t win --snmp 2 --wmi
```

This command will connect to the `WIN04` `Win`dows system using `SNMP` version `2` with the **public** (default) community, and `WMI` as the current.

> Note: The WMI protocol can only be used from a Windows system to monitor another Windows system.

### Windows, WMI and SNMP v3 (Alternate Credentials)

```batch
C:\Program Files\MetricsHub> metricshub WIN05 -t win --snmp 3-sha --snmp-username USERA --wmi --wmi-username WINUSER
```

This command will connect to the `WIN05` `Win`dows system using `SNMP` version `3` as `USERA`, and `WMI` as `WINUSER`. Both passwords will be asked for interactively.

> Note: Instead of using the common `-u` or `--username` options, we had to use the `--snmp-username` and `--wmi-username` options to specify different credentials for SNMP and WMI.

### Ouf-of-band Management Card, IPMI-over-LAN

```batch
$ metricshub MGMT06 -t oob --ipmi -u USER
```

This command will connect to the `MGMT06` out-of-band `management` card (typically a BMC chip) using the `IPMI`-over-LAN protocol as `USER`.

### VMware ESX, WBEM

```batch
$ metricshub ESX07 -t esx --wbem -u admin
```

This command will connect to the `ESX07` VMware `ESX` host using the `WBEM` protocol (HTTPS/5989 by default) with the `admin` account.

### VMware vCenter, WBEM

```batch
$ metricshub ESX08 -t esx --wbem -u admin --wbem-vcenter=vcenter01
```

This command uses the multi-tier authentication `vcenter01` server, which provides a connection ticket to access the `ESX08` VMware `ESX` host via the `WBEM` protocol (HTTPS/5989 by default) with the `admin` account.

### Solaris, SSH

```batch
$ metricshub SOLAR08 -t sol --ssh -u USER --sudo-command-list /usr/sbin/dladm,/usr/sbin/ndd
```

This command will connect to the `SOLAR08` `Solaris` system using the `SSH` protocol to execute commands as `USER`. `sudo` will be used to execute the `/usr/sbin/dladm` and `/usr/sbin/ndd` commands, as they require root privileges.

> Note: The system must have been configured to allow `USER` to execute these commands with `sudo`.

### WinRM

```batch
$ metricshub WIN09 -t mgmt --winrm --winrm-username USER --winrm-password MYSECRET
```

This command will connect to the `WIN09` system using the `WinRM` protocol to execute commands as `USER`.

## Automatic Detection vs Manual Selection

**${solutionName}** is bundled with Sentry's **Connector Library**, a library which consists of a list of *connectors* (hundreds of hardware and storage connectors offered by the enterprise edition) that describe how to discover resource components (such as hardware, service and application components) and detect failures in a given system, with a specific instrumentation stack.

Examples of connectors:

* Dell OpenManage Server Administrator (SNMP)
* Network Cards on Windows (WMI)
* IBM AIX physical disks, using system commands
* VMware ESX (WBEM)
* etc.

When running the `metricshub` command, the connectors are automatically selected based on the specified system type and the protocol enabled. This is the detection phase.

You can however specify manually which connectors must be used to monitor the specified host, or exclude some connectors from the list that will be tested during the detection phase.

### Configure connectors

The connectors are automatically selected based on the device type provided and the enabled protocols. However, you have the flexibility to specify which connectors should be utilized or omitted.

The `--connectors` CLI option allows you to enforce, select, or exclude specific connectors. Connector identifiers or category tags should be separated by commas, as illustrated in the example below:

```batch
$ metricshub SERVER01 -t oob --snmp v2c --community public --connectors +MIB2,#hardware,-Windows
```

- To force a connector, precede the connector identifier with a plus sign (`+`), as in `+MIB2`.
- To exclude a connector from automatic detection, precede the connector identifier with a minus sign (`-`), like `-Windows`.
- To stage a connector for processing by automatic detection, configure the connector identifier, for instance, `MIB2`.
- To stage a category of connectors for processing by automatic detection, precede the category tag with a hash (`#`), such as `#hardware` or `#system`.
- To exclude a category of connectors from automatic detection, precede the category tag to be excluded with a minus and a hash sign (`-#`), such as `-#system`.

#### Examples

- Example 1:
  ```batch
  $ metricshub SERVER01 -t win --snmp v2c --community public --connectors #hardware
  ```
  The core engine will automatically detect connectors categorized under `hardware`.

- Example 2:
  ```batch
  $ metricshub SERVER01 -t win --wmi --connectors -#hardware,#system
  ```
  The core engine will perform automatic detection on connectors categorized under `system`, excluding those categorized under `hardware`.

- Example 3:
  ```batch
  $ metricshub SERVER01 -t win --snmp v2c --community public --wmi --connectors MIB2NT,MIB2,#system
  ```
  The core engine will automatically detect connectors named `MIB2NT`, `MIB2`, and all connectors under the `system` category.

- Example 4:
  ```batch
  $ metricshub SERVER01 -t win --snmp v2c --community public --wmi --connectors +DiskPart,MIB2,#system
  ```
  The core engine will enforce the execution of the `DiskPart` connector and then proceed with the automatic detection of `MIB2` and all connectors under the `system` category.

- Example 5:
  ```batch
  $ metricshub SERVER01 -t win --wmi --connectors DiskPart,-#system
  ```
  The core engine will perform automatic detection exclusively on the `DiskPart` connector.

- Example 6:
  ```batch
  $ metricshub SERVER01 -t win --snmp v2c --community public --connectors +Windows,MIB2
  ```
  The core engine will enforce the execution of the `Windows` connector and subsequently perform automatic detection on the `MIB2` connector.

- Example 7:
  ```batch
  metricshub SERVER01 -t win --snmp v2c --community public --connectors -Linux
  ```
  The core engine will perform automatic detection on all connectors except the `Linux` connector.

- Example 8:
  ```batch
  metricshub SERVER01 -t win --snmp v2c --community public --connectors #hardware,-MIB2
  ```
  The core engine will perform automatic detection on connectors categorized under `hardware`, excluding the `MIB2` connector.


To get the list of connectors bundled in **${solutionName}** and their corresponding internal name (**id**), you can run the below command:

```batch
$ metricshub --list
```

This will provide a list as below:

![Output of the metricshub --list command, listing all connectors, their ID, categories, applicable system types and display name](../images/metricshub-list.png)

This list displays the internal name (**id**) of each connector, its applicable system types and its display name.

## Sequential Mode

By default, the *MetricsHub Engine* sends the queries simultaneously to the host resource. Although the parallel transmission is faster than the sequential one, too many requests at the same time can lead to the failure of the targeted host resource.

Use the `--sequential` option to force all the requests to be executed in a sequential order, thus the monitored host is not overloaded.

```batch
$ metricshub SERVER01 -t linux --snmp 1 --community COMM02 --sequential
```

## Iterations

You can run the collect operation many times (e.g: to compute energy or rate metrics, you need to run it at least twice).

Use `--iterations` option to define the number of times the collect operation will be executed and `--sleep-iteration` to define the
duration in seconds of the pause between two collect operations.

```batch
$ metricshub SERVER01 -t oob --snmp v2c --community public --iterations 2 --sleep-iteration 5
```
