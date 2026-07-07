# MetricsHub JDBC Extension

This module contains the MetricsHub JDBC extension.

## External JDBC drivers

MetricsHub ships built-in JDBC drivers for MariaDB, PostgreSQL, MySQL, and H2.

Additional JDBC drivers can be provided externally.

## Driver declaration

Connector-level driver defaults are declared under `connector.jdbc.driver`:

```yaml
connector:
  detection: ...
  jdbc:
    driver:
      className: oracle.jdbc.OracleDriver
      jarPath: $APP_DIR/extensions/jdbc/ojdbc11.jar # optional
```

Resource-level overrides are declared under `jdbc.driver`:

```yaml
jdbc:
  username: scott
  password: tiger
  url: jdbc:oracle:thin:@dbhost:1521/ORCLCDB
  driver:
    className: oracle.jdbc.OracleDriver
    jarPath: C:/oracle/instantclient/ojdbc11.jar
```

Resolution priority is:

1. `jdbc.driver` from the resource configuration.
2. `connector.jdbc.driver` from the connector identity.

## Supported fields

- `className`: required fully-qualified `java.sql.Driver` implementation class.
- `jarPath`: optional path expression pointing to a JDBC driver JAR.

When `jarPath` is omitted, MetricsHub scans the default JDBC drivers directory.

## Default driver directory

When no explicit `jarPath` is provided, MetricsHub resolves the default JDBC drivers directory in this order:

1. Java system property `metricshub.jdbc.driversDir`
2. Environment variable `METRICSHUB_JDBC_DRIVERS_DIR`
3. App-relative directory `$APP_DIR/extensions/jdbc/` — `/opt/metricshub/lib/extensions/jdbc/` on Linux, `C:\Program Files\MetricsHub\extensions\jdbc\` on Windows.

If that directory does not exist, only the built-in drivers are available.

## `jarPath` expressions

`jarPath` supports plain file paths and placeholder-based expressions.

Supported placeholders:

- `$APP_DIR`
- `$USER_HOME`
- `$WORKING_DIR`

Absolute paths and all placeholders are accepted in both connector-level and resource-level configurations. `..` segments are rejected.

`jarPath` may also contain glob patterns (`*`, `?`, `[...]`). The pattern must resolve to exactly one JAR file; zero or multiple matches cause driver resolution to fail.

Examples:

```yaml
connector:
  jdbc:
    driver:
      className: com.ibm.as400.access.AS400JDBCDriver
      jarPath: $APP_DIR/extensions/jdbc/jt400.jar
```

```yaml
connector:
  jdbc:
    driver:
      className: oracle.jdbc.OracleDriver
      jarPath: $USER_HOME/.metricshub/drivers/ojdbc11.jar
```

```yaml
jdbc:
  driver:
    className: oracle.jdbc.OracleDriver
    jarPath: C:/oracle/instantclient/ojdbc*.jar
```