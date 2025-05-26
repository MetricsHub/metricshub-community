keywords: linux, database, process
description: How to configure MetricsHub to monitor a database and process on a Linux system.

# Database and Process Monitoring (Linux)

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

You can configure **MetricsHub** to monitor a database and its corresponding process running on a Linux system. In the example below, we configured **MetricsHub** to monitor the `mysqld` process running on the `raz-docker` resource using SSH.

## Procedure

To achieve this use case, we:

* Declare the resource to be monitored (`raz-docker`)​ and its attributes (`host.name`, `host.type`)​

```yaml
    resources:
      raz-docker:
        attributes:
          host.name: raz-docker
          host.type: linux
```

* Configure the `SSH` protocol with `credentials` and `timeout​`:

```yaml
        protocols:
          ssh:
            username: <username>
            password: <username>
            timeout: 30
```

* Configure the `jdbc` protocol:

```yaml
        jdbc:
            port: 3306
            database: sys
            type: MySQL
            url: jdbc:mysql://raz-docker:3306/sys
            username: root
            password: <username>   
```

* Add an additional connector (`LinuxProcess`) using the `LinuxProcess` module​

```yaml
        additionalConnectors:
          LinuxProcess: 
            uses: LinuxProcess
```

* Set the variable for the process to be monitored (`mysqld`):

```yaml
            variables:
              matchName: mysqld
```

Here is the complete YAML configuration:

```yaml
    resources:
      raz-docker:
        attributes:
          host.name: raz-docker
          host.type: linux
        protocols:
          ssh:
            username: <username>
            password: <username>
            timeout: 30
        jdbc:
            port: 3306
            database: sys
            type: MySQL
            url: jdbc:mysql://raz-docker:3306/sys
            username: root
            password: <username>   
        additionalConnectors:
          LinuxProcess: 
            uses: LinuxProcess
            variables:
              matchName: mysqld
```

## Supporting Resources

* [Configure resources](../configuration/configure-monitoring.md#step-3-configure-resources)
* [Resource attributes](../configuration/configure-monitoring.html#resource-attributes)
* [SSH](../configuration/configure-monitoring.md#ssh)
* [JDBC](../configuration/configure-monitoring.md#jdbc)
* [Customize resource monitoring](../configuration/configure-monitoring.html#customize-resource-monitoring)
* [Customize data collection](../configuration/configure-monitoring.html#customize-data-collection)
