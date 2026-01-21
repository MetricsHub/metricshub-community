keywords: windows, service, WMI
description: How to configure MetricsHub to monitor a Windows service.

# Windows Service Monitoring

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

You can configure **MetricsHub** to monitor a Windows service. In the example below, we configured **MetricsHub** to monitor the `httpd` service running on the `prod-win-web` resource using WMI.

## Procedure

To achieve this use case, we:

- Declare the resource to be monitored (`prod-win-web`)​ and its attributes (`host.name`, `host.type`)​

```yaml
resources:
  prod-win-web:
    attributes:
      host.name: prod-win-web
      host.type: windows
```

- Configure the `WMI` protocol with `credentials` and `timeout​`

```yaml
protocols:
  wmi:
    username: <username>
    password: <password>
    timeout: 30
```

- Add an additional connector (`WindowsServiceHttpd`) using the `WindowsService` module​

```yaml
additionalConnectors:
  WindowsServiceHttpd:
    uses: WindowsService
```

- Set the variable `serviceNames` to specify the service to be monitored (`httpd`).

```yaml
variables:
  serviceNames: httpd
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
        timeout: 30
    additionalConnectors:
      WindowsServiceHttpd:
        uses: WindowsService
        variables:
          serviceNames: httpd
```

## Supporting Resources

- [Configure resources](../configuration/configure-monitoring.md#step-3-configure-resources)
- [Resource attributes](../configuration/configure-monitoring.md#resource-attributes)
- [WMI](../configuration/configure-monitoring.md#wmi)
- [Customize data collection](../configuration/configure-monitoring.md#customize-data-collection)
