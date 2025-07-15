keywords: custom, database, sql, jdbc
description: How to configure MetricsHub to poll SQL-compatible databases using JDBC.

# Database Query

<!-- MACRO{toc|fromDepth=1|toDepth=3|id=toc} -->

You can configure **MetricsHub** to periodically poll any SQL-compatible database using JDBC, execute custom queries, retrieve tabular results, and push OpenTelemetry metrics with the extracted values.

> If the datatabase to be monitored is not currently supported by [MetricsHub](https://metricshub.com/docs/latest/connectors/tags/database.html) (e.g., **ClickHouse**, **Sybase**, etc.), you need to download and install the appropriate JDBC driver.

In the example below, we configured **MetricsHub** to:

* monitor the [`clickhouse-server`](https://clickhouse.com/) resource using JDBC
* connect to a ClickHouse database
* execute a custom SQL query
* extract and expose database server metrics.

## Procedure

### Install the ClickHouse JDBC driver

1. **Download** the ClickHouse JDBC driver:
   [`clickhouse-jdbc-0.8.6-shaded-all.jar`](https://repo1.maven.org/maven2/com/clickhouse/clickhouse-jdbc/0.8.6/clickhouse-jdbc-0.8.6-shaded-all.jar)
2. **Copy** the downloaded `.jar` file in the `extensions/` directory of your **MetricsHub** installation.
3. **Update** the service configuration file to include the driver in the classpath:

    * **Community Edition**:

      * Windows: `MetricsHub/app/MetricsHubServiceManager.cfg`
      * Linux: `metricshub/lib/app/service.cfg`
    * **Enterprise Edition**:

      * Windows: `MetricsHub/app/MetricsHubEnterpriseService.cfg`
      * Linux: `metricshub/lib/app/enterprise-service.cfg`

    Example:

    ```ini
    [Application]
    ...
    app.classpath=$APPDIR\..\extensions\clickhouse-jdbc-0.8.6-shaded-all.jar
    ```

### Configure MetricsHub

To achieve this use case, we:
  
* Declare the resource to be monitored (`clickhouse-server`) and its attributes (`host.name`, `host.type`)

```yaml
resources:
  clickhouse-server:
    attributes:
      host.name: clickhouse-server
      host.type: linux
```

* Configure the `JDBC` protocol

```yaml
    protocols:
      jdbc:
        url: jdbc:ch://clickhouse-server:18123/system
        username: default
        password: changeme
```

* Define a monitor job (`clickhouse`) to extract server metrics

```yaml
    monitors:
      clickhouse:
        simple:
```

* Set up the SQL source (`clickhouseMetrics`) with a ClickHouse query returning multiple metrics

```yaml
          sources:
            clickhouseMetrics:
              type: sql
              query: |
                SELECT
                    currentDatabase() AS db_namespace,
                    hostName() AS db_server_name,
                    MAX(IF(metric = 'Query', value, NULL)) AS db_server_queries,
                    MAX(IF(metric = 'HTTPConnection', value, NULL)) AS db_server_current_connections,
                    MAX(IF(metric = 'OpenFileForRead', value, NULL)) AS db_server_storage_files,
                    MAX(IF(metric = 'MemoryTracking', value, NULL)) AS db_server_cache_usage
                FROM system.metrics
                WHERE metric IN (
                    'Query',
                    'HTTPConnection',
                    'OpenFileForRead',
                    'MemoryTracking'
                );
```

* Map the query result to OpenTelemetry attributes and metrics

```yaml
          mapping:
            source: ${esc.d}{source::clickhouseMetrics}
            attributes:
              db.system: clickhouse
              id: ${esc.d}1
              db.server.namespace: ${esc.d}1
              db.server.name: ${esc.d}2
            metrics:
              db.server.queries: ${esc.d}3
              db.server.current_connections: ${esc.d}4
              db.server.storage.files: ${esc.d}5
              db.server.cache.usage: ${esc.d}6
```

Here is the complete YAML configuration:

```yaml
resources:
  clickhouse-server:
    attributes:
      host.name: clickhouse-server
      host.type: linux
    protocols:
      jdbc:
        url: jdbc:ch://clickhouse-server:18123/system
        username: default
        password: changeme
    monitors:
      clickhouse:
        simple:
          sources:
            clickhouseMetrics:
              type: sql
              query: |
                SELECT
                    currentDatabase() AS db_namespace,
                    hostName() AS db_server_name,
                    MAX(IF(metric = 'Query', value, NULL)) AS db_server_queries,
                    MAX(IF(metric = 'HTTPConnection', value, NULL)) AS db_server_current_connections,
                    MAX(IF(metric = 'OpenFileForRead', value, NULL)) AS db_server_storage_files,
                    MAX(IF(metric = 'MemoryTracking', value, NULL)) AS db_server_cache_usage
                FROM system.metrics
                WHERE metric IN (
                    'Query',
                    'HTTPConnection',
                    'OpenFileForRead',
                    'MemoryTracking'
                );
          mapping:
            source: ${esc.d}{source::clickhouseMetrics}
            attributes:
              db.system: clickhouse
              id: ${esc.d}1
              db.server.namespace: ${esc.d}1
              db.server.name: ${esc.d}2
            metrics:
              db.server.queries: ${esc.d}3
              db.server.current_connections: ${esc.d}4
              db.server.storage.files: ${esc.d}5
              db.server.cache.usage: ${esc.d}6
```

## Supporting Resources

* [Configure resources](../configuration/configure-monitoring.md#step-3-configure-resources)
* [Resource attributes](../configuration/configure-monitoring.md#resource-attributes)
* [JDBC](../configuration/configure-monitoring.md#jdbc)
* [Customize resource monitoring](../configuration/configure-monitoring.md#customize-resource-monitoring)
