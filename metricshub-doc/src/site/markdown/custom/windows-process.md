keywords: windows, process
description: How to configure MetricsHub to monitor a Windows process.

# Windows Process Monitoring

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

You can configure **MetricsHub** to monitor a Windows process. In the example below, we configured **MetricsHub** to monitor the `sqlservr.exe` process running on the `prod-win-web` resource using WMI.

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
    password: <username>
    timeout: 30
```

- Add an additional connector (`WindowsProcess`) using the `WindowsProcess` module​

```yaml
additionalConnectors:
  WindowsProcess:
    uses: WindowsProcess
```

- Set the variable `matchCommand` for the service to be monitored (`sqlservr.exe`):

```yaml
variables:
  matchCommand: "sqlservr\\.exe"
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
        password: <username>
        timeout: 30
    additionalConnectors:
      WindowsProcess:
        uses: WindowsProcess
        variables:
          matchCommand: "sqlservr\\.exe"
```

## Supporting Resources

- [Configure resources](../configuration/configure-monitoring.md#step-3-configure-resources)
- [Resource attributes](../configuration/configure-monitoring.md#resource-attributes)
- [WMI](../configuration/configure-monitoring.md#wmi)
- [Customize resource monitoring](../configuration/configure-monitoring.md#customize-resource-monitoring)
