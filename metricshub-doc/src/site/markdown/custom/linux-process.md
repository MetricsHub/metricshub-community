keywords: linux, process
description: How to configure MetricsHub to monitor a Linux Process

# Linux Process Monitoring

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

You can configure **MetricsHub** to monitor a Linux process. In the example below, we configured **MetricsHub** to monitor the `systemd` process running on the `prod-web` resource using SSH.

## Procedure

To achieve this use case, we:

* Declare the resource to be monitored (`prod-web`)​ and its attributes (`host.name`, `host.type`)​

```yaml
    resources:
      prod-web:
        attributes:
          host.name: prod-web
          host.type: linux
```

* Configure the `SSH` protocol with `credentials` and `timeout​`

```yaml
        protocols:
          ssh:
            username: <username>
            password: <password>
            timeout: 30
```

* Add a new instance of the [`LinuxProcess`](../connectors/linuxprocess.html) connector for the monitoring of `systemd`. Name this new instance `systemdProcess`, for example:

```yaml
        additionalConnectors:
          systemdProcess: 
            uses: LinuxProcess
```

* Set the variable `matchName` for the service to be monitored (`systemd`):

```yaml
            variables:
              matchName: systemd
```

Here is the complete YAML configuration:

```yaml
    resources:
      prod-web:
        attributes:
          host.name: prod-web
          host.type: linux
        protocols:
          ssh:
            username: <username>
            password: <username>
            timeout: 30
        additionalConnectors:
          systemdProcess: 
            uses: LinuxProcess
            variables:
              matchName: systemd
```

## Supporting Resources

* [Configure resources](../configuration/configure-monitoring.md#step-3-configure-resources)
* [Resource attributes](../configuration/configure-monitoring.md#resource-attributes)
* [SSH](../configuration/configure-monitoring.md#ssh)
* [Customize resource monitoring](../configuration/configure-monitoring.md#customize-resource-monitoring)
* [Customize data collection](../configuration/configure-monitoring.md#customize-data-collection)
