keywords: linux, service, SSH
description: How to configure MetricsHub to monitor a Linux Service.

# Linux Service Monitoring

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

You can configure **MetricsHub** to monitor a Linux service. In the example below, we configured **MetricsHub** to monitor the `httpd` service running on the `prod-web` resource using SSH.

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

* Add a new instance of the [`LinuxService`](https://metricshub.com/docs/latest/connectors/linuxservice.html) connector for the monitoring of the `httpd` service, and name it `LinuxServiceHttpd` for example:

```yaml
        additionalConnectors:
          LinuxServiceHttpd:
            uses: LinuxService
```

* Set the variable `serviceNames` to specify the service to monitor (`httpd`)​

```yaml
            variables:
                serviceNames: httpd
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
            password: <password>
            timeout: 30
        additionalConnectors:
          LinuxServiceHttpd:
            uses: LinuxService
            variables:
                serviceNames: httpd
```

## Supporting Resources

* [Configure resources](../configuration/configure-monitoring.md#step-3-configure-resources)
* [Resource attributes](../configuration/configure-monitoring.md#resource-attributes)
* [SSH](../configuration/configure-monitoring.md#ssh)
* [Customize resource monitoring](../configuration/configure-monitoring.md#customize-resource-monitoring)
* [Customize data collection](../configuration/configure-monitoring.md#customize-data-collection)
