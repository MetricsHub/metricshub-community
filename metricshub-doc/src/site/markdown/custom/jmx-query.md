keywords: custom, jmx
description: How to configure MetricsHub to query JMX MBeans and extract JVM metrics

# JMX Query

<!-- MACRO{toc|fromDepth=1|toDepth=3|id=toc} -->

You can configure **MetricsHub** to periodically query any Java application exposing JMX (Java Management Extensions), retrieve specific MBean attributes, and push OpenTelemetry metrics with the extracted values.

In the example below, we configure **MetricsHub** to:

- monitor the `java-app` resource using JMX
- retrieve the `LoadedClassCount` attribute from the `java.lang:type=ClassLoading` MBean
- expose the value as an OpenTelemetry metric named [`jvm.class.count`](https://opentelemetry.io/docs/specs/semconv/runtime/jvm-metrics/#metric-jvmclasscount)

## Procedure

To achieve this use case, we:

- Declare the resource to be monitored (`java-app`) and its attributes (`host.name`, `host.type`):

```yaml
resources:
  java-app:
    attributes:
      host.name: java-app
      host.type: linux
```

- Configure the `jmx` protocol:

```yaml
protocols:
  jmx:
    port: 1099
    username: myUser
    password: myPassword
    timeout: 60s
```

- Define a monitor job (`jvm`) to collect the JVM class loading metric:

```yaml
monitors:
  jvm:
    simple:
```

- Set up a JMX source (`JvmClassLoading`) to retrieve the `LoadedClassCount` attribute from the `java.lang:type=ClassLoading` MBean:

```yaml
sources:
  JvmClassLoading:
    type: jmx
    objectName: java.lang:type=ClassLoading
    attributes:
      - LoadedClassCount
```

- Extract and expose the metric (`jvm.class.count`) from the JMX response:

```yaml
mapping:
  source: ${esc.d}{source::JvmClassLoading}
  attributes:
    id: java-app-classloading
  metrics:
    jvm.class.count: ${esc.d}1
```

### Complete Configuration

```yaml
resources:
  java-app:
    attributes:
      host.name: java-app
      host.type: linux
    protocols:
      jmx:
        port: 1099
        username: myUser
        password: myPassword
        timeout: 60s
    monitors:
      jvm:
        simple:
          sources:
            JvmClassLoading:
              type: jmx
              objectName: java.lang:type=ClassLoading
              attributes:
                - LoadedClassCount
          mapping:
            source: ${esc.d}{source::JvmClassLoading}
            attributes:
              id: java-app-classloading
            metrics:
              jvm.class.count: ${esc.d}1
```

## Supporting Resources

- [Configure resources](../configuration/configure-monitoring.md#step-3-configure-resources)
- [Resource attributes](../configuration/configure-monitoring.md#resource-attributes)
- [JMX](../configuration/configure-monitoring.md#jmx)
- [Customize resource monitoring](../configuration/configure-monitoring.md#customize-resource-monitoring)
