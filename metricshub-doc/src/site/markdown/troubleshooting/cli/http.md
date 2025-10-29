keywords: http, cli
description: How to test connectivity to HTTP-based resources with MetricsHub HTTP CLI.

# HTTP CLI Documentation

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

The **MetricsHub HTTP CLI** is a powerful command-line utility for interacting with monitored resources. It supports common HTTP methods (`GET`, `POST`, `PUT`, and `DELETE`) to validate configurations, troubleshoot connectivity issues, and perform HTTP-based operations directly from the command line. The CLI supports authentication, custom headers, and request body configurations.

Before using the CLI, ensure your platform supports HTTP monitoring by checking the [Connector Directory](https://metricshub.com/docs/latest/metricshub-connectors-directory.html).

## Syntax

```bash
httpcli <GET|POST|PUT|DELETE> <URL> --username <USERNAME> --password <PASSWORD> [--body <BODY> | --body-file <FILE PATH>] [--header <HEADER> | --header-file <FILE PATH>] --timeout <TIMEOUT>
```
or
```
httpcli --method <GET|POST|PUT|DELETE> --url <URL> --username <USERNAME> --password <PASSWORD> [--body <BODY> | --body-file <FILE PATH>] [--header <HEADER> | --header-file <FILE PATH>] --timeout <TIMEOUT>
```

## Options

| Option          | Description                                                                              | Default Value |
| --------------- | ---------------------------------------------------------------------------------------- | ------------- |
| `--url`         | The URL for the HTTP request.                                                            | None          |
| `--method`      | the HTTP method to use for the request. Possible values: `GET`, `POST`, `PUT`, `DELETE`. | `GET`         |
| `--header`      | Custom headers for the request. Can be specified multiple times.                         | None          |
| `--header-file` | Path to a file containing headers for the request.                                       | None          |
| `--body`        | Request body as a string.                                                                | None          |
| `--body-file`   | Path to a file containing the request body.                                              | None          |
| `--username`    | Username for HTTP authentication.                                                        | None          |
| `--password`    | Password for HTTP authentication. If not provided, you will be prompted interactively.   | None          |
| `--timeout`     | Timeout in seconds for the HTTP operation.                                               | 120           |
| `-v`            | Enables verbose mode. Use `-v` for basic logs, `-vv` for detailed logs.                  | None          |
| `-h, --help`    | Displays detailed help information about available options.                              | None          |

## Examples

### Example 1: HTTP GET Request with Headers and Body

```bash
httpcli get https://dev-01:443/users --username username --password password --header="Content-Type:application/xml" --header="Accept:application/json" --body="<aaaLogin inName='username' inPassword='password' />" --timeout 120
```

### Example 2: HTTP POST Request with Header and Body Files

```bash
httpcli post https://dev-01:443/users --username admin --password pass --header-file="/opt/metricshub/header.txt" --body-file="/opt/metricshub/body.txt" --timeout 120
```

### Example 3: HTTP Get Request with Interactive Password Input

```bash
httpcli --method get --url https://example.com --username user
```

The CLI prompts for a password if not provided.
