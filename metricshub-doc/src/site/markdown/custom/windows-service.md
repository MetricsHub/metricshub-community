keywords: windows, service
description: How to 

# Windows Service Monitoring

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

## Introduction

## Procedure

To achieve this use case, we:

1. Declare the resource to be monitored (`prod-win-web`)​
2. Define resource attributes (`host.name`, `host.type`)​
3. Configure the `WMI` protocol with `credentials` and `timeout​`
4. Add an additional connector (`httpd`) using the `WindowsService` module​
5. Set the variable `serviceNames` to specify the service to monitor (`httpd`).

Here is the complete YAML configuration:

## Supporting Resources

* [Configure resources](../configuration/configure-monitoring.md#step-3-configure-resources)
* [Resource attributes](../configuration/configure-monitoring#resource-attributes)
* [WMI](../configuration/configure-monitoring.md#wmi)
* [Customize data collection][../configuration/configure-monitoring.html#customize-data-collection]