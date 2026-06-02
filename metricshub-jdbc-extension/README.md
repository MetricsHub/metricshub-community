# MetricsHub JDBC Extension

This module contains the MetricsHub JDBC extension.

## External JDBC drivers

MetricsHub ships built-in JDBC drivers for MariaDB, PostgreSQL, MySQL, and H2.

Additional JDBC drivers can be provided externally.

## Driver declaration

Connector-level driver defaults are declared under `connector.jdbc`:

```yaml
connector:
  detection: ...
  jdbc:
    driverClass: oracle.jdbc.OracleDriver
    driverPath: $INSTALL_DIR/lib/extensions/jdbc/ojdbc11.jar # optional
```

Resource-level overrides are declared under `jdbc.driver`:

```yaml
jdbc:
  username: scott
  password: tiger
  url: jdbc:oracle:thin:@dbhost:1521/ORCLCDB
  driver:
    driverClass: oracle.jdbc.OracleDriver
    driverPath: C:/oracle/instantclient/ojdbc11.jar
```

Resolution priority is:

1. `jdbc.driver` from the resource configuration.
2. `connector.jdbc` from the connector identity.

## Supported fields

- `driverClass`: required fully-qualified `java.sql.Driver` implementation class.
- `driverPath`: optional path expression pointing to a JDBC driver JAR.

When `driverPath` is omitted, MetricsHub scans the default JDBC drivers directory.

## Default driver directory

When no explicit `driverPath` is provided, MetricsHub resolves the default JDBC drivers directory in this order:

1. Java system property `metricshub.jdbc.driversDir`
2. Environment variable `METRICSHUB_JDBC_DRIVERS_DIR`
3. Install-relative directory `lib/extensions/jdbc/`

If that directory does not exist, only the built-in drivers are available.

## `driverPath` expressions

`driverPath` supports plain file paths and placeholder-based expressions.

Supported placeholders:

- `$INSTALL_DIR`
- `$USER_HOME`
- `$WORKING_DIR` for resource-level configuration only

Connector-level `driverPath` values are restricted to paths starting with `$INSTALL_DIR` or `$USER_HOME`.
Resource-level `driverPath` values may also use absolute paths and `$WORKING_DIR`.

`driverPath` may also contain glob patterns (`*`, `?`, `[...]`). The pattern must resolve to exactly one JAR file; zero or multiple matches cause driver resolution to fail.

Examples:

```yaml
connector:
  jdbc:
    driverClass: com.ibm.as400.access.AS400JDBCDriver
    driverPath: $INSTALL_DIR/lib/extensions/jdbc/jt400.jar
```

```yaml
connector:
  jdbc:
    driverClass: oracle.jdbc.OracleDriver
    driverPath: $USER_HOME/.metricshub/drivers/ojdbc11.jar
```

```yaml
jdbc:
  driver:
    driverClass: oracle.jdbc.OracleDriver
    driverPath: C:/oracle/instantclient/ojdbc*.jar
```