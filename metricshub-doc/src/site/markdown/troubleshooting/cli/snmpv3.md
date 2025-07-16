keywords: snmpv3, cli
description: How to execute SNMPv3 queries with MetricsHub SNMPv3 CLI.

# SNMPv3 CLI Documentation

SNMPv3 (Simple Network Management Protocol Version 3) is an enhanced version of SNMP, providing secure communication with features like encryption, authentication, and access control. It is widely used for monitoring and managing devices in a secure environment. Refer to [Simple Network Management Protocol Version 3](https://en.wikipedia.org/wiki/Simple_Network_Management_Protocol#Version_3) for more details.

The **MetricsHub SNMPv3 CLI** allows users to interact with SNMPv3-enabled devices through `GET`, `GETNEXT`, `WALK`, and `TABLE` queries. Users can configure options such as authentication, encryption, and context name.

## Syntax

### SNMPv3 Get Request

```bash
snmpv3cli <HOSTNAME> --get <OID> --privacy <DES|AES|AES192|AES256> --privacy-password <PASSWORD> --auth <SHA|SHA256|SHA512|SHA384|SHA224|MD5> --username <USERNAME> --password <PASSWORD> --context-name <CONTEXT> --timeout <TIMEOUT> --retry <INTERVAL1>,<INTERVAL2>,...
```

### SNMPv3 Get Next Request

```bash
snmpv3cli <HOSTNAME> --getNext <OID> --privacy <DES|AES|AES192|AES256> --privacy-password <PASSWORD> --auth <SHA|SHA256|SHA512|SHA384|SHA224|MD5> --username <USERNAME> --password <PASSWORD> --context-name <CONTEXT> --timeout <TIMEOUT> --retry <INTERVAL1>,<INTERVAL2>,...
```

### SNMPv3 Walk Request

```bash
snmpv3cli <HOSTNAME> --walk <OID> --privacy <DES|AES|AES192|AES256> --privacy-password <PASSWORD> --auth <SHA|SHA256|SHA512|SHA384|SHA224|MD5> --username <USERNAME> --password <PASSWORD> --context-name <CONTEXT> --timeout <TIMEOUT> --retry <INTERVAL1>,<INTERVAL2>,...
```

### SNMPv3 Table Request

```bash
snmpv3cli <HOSTNAME> --table <OID> --columns <COLUMN,COLUMN,...> --privacy <DES|AES|AES192|AES256> --privacy-password <PASSWORD> --auth <SHA|SHA256|SHA512|SHA384|SHA224|MD5> --username <USERNAME> --password <PASSWORD> --context-name <CONTEXT> --timeout <TIMEOUT> --retry <INTERVAL1>,<INTERVAL2>,...
```

## Options

| Option               | Description                                                                                               | Default Value |
| -------------------- |-----------------------------------------------------------------------------------------------------------| ------------- |
| `HOSTNAME`           | Hostname or IP address of the SNMPv3-enabled device. **This option is required.**                         | None          |
| `--privacy`          | Encryption type. Possible values: `DES`, `AES`, `AES192`, `AES256`, or `none`.                            | None          |
| `--privacy-password` | Password for SNMPv3 encryption.                                                                           | None          |
| `--auth`             | Authentication type. Possible values: `SHA`, `SHA256`, `SHA512`, `SHA224`, `SHA384`, `MD5`, or `NO_AUTH`. | None          |
| `--username`         | Username for SNMPv3 authentication.                                                                       | None          |
| `--password`         | Password for SNMPv3 authentication. If not provided, you will be prompted interactively.                  | None          |
| `--context-name`     | Context name for SNMPv3.                                                                                  | None          |
| `--timeout`          | Timeout in seconds for SNMPv3 operations.                                                                 | 5             |
| `--port`             | Port of the SNMPv3 agent.                                                                                 | 161           |
| `--retry`            | Comma-separated retry intervals in milliseconds (e.g., `500,1000`).                                       | None          |
| `--get`              | OID for SNMPv3 Get request.                                                                               | None          |
| `--getNext`          | OID for SNMPv3 Get Next request.                                                                          | None          |
| `--walk`             | OID for SNMPv3 Walk request.                                                                              | None          |
| `--table`            | OID for SNMPv3 Table request.                                                                             | None          |
| `--columns`          | Comma-separated columns for SNMPv3 Table request.                                                         | None          |
| `-v`                 | Enables verbose mode. Use `-v` for basic logs, `-vv` for detailed logs.                                   | None          |
| `-h, --help`         | Displays detailed help information about available options.                                               | None          |

## Examples

### Example 1: SNMPv3 Get Request

```bash
snmpv3cli dev-01 --get 1.3.6.1.4.1.674.10892.5.5.1.20.130.4.1.1.1 --privacy AES --privacy-password privacyPassword --auth MD5 --username admin --password secret --context-name context --timeout 120 --retry 500,1000
```

### Example 2: SNMPv3 Get Next Request

```bash
snmpv3cli dev-01 --getNext 1.3.6.1.4.1.674.10892.5.5.1.20.130.4 --privacy AES --privacy-password privacyPassword --auth SHA --username admin --password secret --context-name context --timeout 120 --retry 500,1000
```

### Example 3: SNMPv3 Walk Request

```bash
snmpv3cli dev-01 --walk 1.3.6.1 --privacy DES --privacy-password privacyPassword --auth SHA --username admin --password secret --context-name context --timeout 120 --retry 500,1000
```

### Example 4: SNMPv3 Table Request

```bash
snmpv3cli dev-01 --table 1.3.6.1.4.1.674.10892.5.4.300.10.1 --columns 1,3,8,9,11 --privacy AES --privacy-password privacyPassword --auth MD5 --username admin --password secret --context-name context --timeout 120 --retry 500,1000
```
