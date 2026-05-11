# MetricsHub Recording and Emulation Guide

This guide explains how to:

- record protocol exchanges from a real target
- replay recorded data offline with emulation flags
- run protocol-specific CLI examples

## 1) Quick start

1. Run your normal `metricshub` CLI command with `--record`.
2. Keep the generated protocol folders.
3. Re-run with `--emulate-* <directory>` to replay without live calls.

## 2) Recording mode

Enable recording with:

- `-rec`
- `--record`

Example:

```bash
metricshub SER01 -t oob --https -u USER -c +RedFish --record
```

This will record the HTTP requests and responses to the RedFish API under the `http` folder (Linux: `/opt/metricshub/logs/http` / Windows: `C:\Program Files\MetricsHub\logs\http`)

When recording is enabled, MetricsHub writes protocol-specific folders under its default output directory (Linux: `/opt/metricshub/logs` / Windows: `C:\Program Files\MetricsHub\logs`), for example `http`, `wbem`, `command`, `jdbc`, `ipmi`, `jmx`, `wmi`, each containing an `image.yaml` index plus response files.

## 3) Emulation mode

Use one or more emulation flags to replay recorded data:

- `--emulate-http <dir>`
- `--emulate-snmp <dir>`
- `--emulate-ssh <dir>`
- `--emulate-wbem <dir>`
- `--emulate-jdbc <dir>`
- `--emulate-ipmi <dir>`
- `--emulate-jmx <dir>`
- `--emulate-wmi <dir>`

Example:

```bash
metricshub SER01 -t oob --emulate-http /opt/metricshub/logs/http -c +RedFish 
```

## 4) SNMP recording and emulation

- SNMP emulation expects `.walk` files in the provided directory.
- SNMP is replayed from walk files (not from an `image.yaml` recorder index).

### SNMP recording mode

Use the `snmpcli` CLI tool to record SNMP walk files.

```bash
snmpcli dev-01 --walk 1.3.6.1 --community public --version v1 --port 161 --timeout 60 > /opt/metricshub/logs/snmp/1.3.6.1.walk
```

### SNMP emulation mode

Use the `--emulate-snmp` flag to replay the recorded SNMP walk files.

```bash
metricshub --host demo.local --emulate-snmp /opt/metricshub/logs/snmp -c +MIB2
```

## 5) Emulation mode through metricshub.yaml

The following example shows how to use the emulation mode through the `metricshub.yaml` file:

```yaml
resources:
  localhost:
    attributes:
      host.name: localhost
      host.type: windows
    protocols:
      emulation:
        wmi: # protocol name (accepted values: http, snmp, ssh, wbem, jdbc, ipmi, jmx, wmi)
          directory: src/it/resources/WinStorageSpaces/emulation/wmi # directory containing the emulation files
    connectors: [ +WinStorageSpaces ] # connectors to use for the emulation
```

## 6) Tips and troubleshooting

- Use absolute paths for emulation directories to avoid ambiguity.
- Make sure each protocol flag points to the matching protocol folder.
- If replay returns no data, verify the expected request exists in that protocol's recorded files.
