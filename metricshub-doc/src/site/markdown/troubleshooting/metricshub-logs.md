keywords: logs
description: How to enable the logging mode of the MetricsHub Agent (core engine).

# MetricsHub Logs

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

The **MetricsHub Agent** automatically sets its internal logging system to `error` to capture and record any error that may arise during operation. Important errors are therefore readily available in the `metricshub-agent-*-$timestamp.log` files.

If you need more comprehensive details to troubleshoot your issues, you can enable the logging mode to obtain these logs:

* `metricshub-agent-global-error-$timestamp.log`: this file logs all fatal errors such as an application crash upon start-up
* `metricshub-agent-global-$timestamp.log`: this file provides the agent global information (agent status, user, version, scheduler, yaml parser, etc.)
* `metricshub-agent-$resourceId-$timestamp.log`: this file logs information about the monitored resource.

## Enabling the logging mode

In the **config/<your_configuration_file>**, add the `loggerLevel` parameter just before the `resourceGroups` section:

```yaml
loggerLevel: debug

resourceGroups:
  <resourceGroupKey>:
    resources:
    # [...]
```

Set the `loggerLevel` parameter to:

* `all`, `trace`, or  `debug` to get more comprehensive details
* `error` or `fatal` to focus on identifying critical issues.

The output files are saved by default in the **logs** directory located under the **MetricsHub** directory:

* On Windows, the output files are stored in the **%LOCALAPPDATA%\MetricsHub** folder of the account running the application:
  * When the Local System account starts the MetricsHub Agent service, the output files are stored under **C:\Windows\System32\config\systemprofile\AppData\Local\MetricsHub\logs**.
  * When a specific user starts the MetricsHub Agent service, the output files are stored under **C:\Users\\<username\>\AppData\Local\MetricsHub\logs**.

* On Linux, the output files are stored in the installation directory: **/opt/metricshub/logs**.

To specify a different output directory, edit your MetricsHub configuration file and add the `outputDirectory` parameter before the `resourceGroups` section:

```yaml
loggerLevel: debug
outputDirectory: C:\Users\<username>\AppData\Local\Temp\logs2021

resourceGroups:
  <resourceGroupKey>:
    resources:
  # [...]
```

## Disabling the debug mode

Set `loggerlevel` to `off` to disable the debug mode.