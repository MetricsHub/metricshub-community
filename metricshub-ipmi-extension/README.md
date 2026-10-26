# MetricsHub IPMI Extension

This module contains the MetricsHub IPMI extension. It reads the chassis status, the FRUs and the sensors of a BMC over IPMI 2.0 (RMCP+) with [ipmi-java](https://metricshub.org/ipmi-java), and `IpmiResultConverter` turns these results into the semicolon-separated text that the IPMI connectors parse.

## Chassis status

The IPMI criterion of the connectors receives the power state:

```text
System power state is up
```

or `System power state is down`.

## Text output format

The IPMI source receives four groups of lines, in this order:

```text
Chassis;1;0;0;0;0;0;0;0;0;PowerRestored;Off;
FRU;LENOVO;RD350;S4M00000 - 00000000000001
FRU;LITEON;PS-2451-6L-LF;0000
Power Unit;3;Power Unit 3;;;;PSU Redundancy=Fully Redundant
Power Supply;1;Power Supply 1;;;;PSU1 Present=Presence detected
Power Supply;2;Power Supply 2;;;;PSU2 Present=Presence detected
Temperature;0008;Ambient Temp;Air Inlet 1;17.0;37;39
Temperature;0009;CPU1 DTS;Processor 1;-44.0;;
PowerConsumption;000d;System Power;Power Unit 2;92.0
Fan;0014;Fan 1;Fan Device 1;6600.0;1600;
Voltage;0022;System 3.3V;System Board 1;3380.0;3040;3560
```

### Chassis line

```text
Chassis;$powerOn;$powerFault;$powerOverload;$interlock;$powerControlFault;$coolingFault;$driveFault;$intrusion;$frontPanelLockout;$powerRestorePolicy;$identifyState;$lastPowerEvents
```

One line, first, from the Get Chassis Status reply (IPMI 2.0 section 28.2):

| Field                                                                                                                                             | Content                                                                                                                                      |
| ------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| `$powerOn`                                                                                                                                        | `1` when the system is powered on, `0` otherwise                                                                                             |
| `$powerFault`, `$powerOverload`, `$interlock`, `$powerControlFault`, `$coolingFault`, `$driveFault`, `$intrusion`, `$frontPanelLockout` | `1` when the BMC reports the condition, `0` otherwise                                                                                        |
| `$powerRestorePolicy`                                                                                                                             | What the system does when AC power returns: `PoweredOff`, `PowerRestored` (its previous state), `PoweredUp` or `Unknown`                    |
| `$identifyState`                                                                                                                                  | The chassis identify LED: `Off`, `TemporaryOn` or `IndefiniteOn`; empty when the BMC does not report it                                    |
| `$lastPowerEvents`                                                                                                                                | The causes of the last power-down or power-up, separated with `\|`: `AC_FAILED`, `POWER_OVERLOAD`, `INTERLOCK`, `POWER_FAULT`, `IPMI_COMMAND`; empty when none |

### FRU lines

```text
FRU;$vendor;$model;$serialNumber
```

One line per FRU. The fields come from the **Product** area when it has a manufacturer name, and from the **Board** area otherwise:

| Field           | Product area                                                                                | Board area                                                                       |
| --------------- | ------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------- |
| `$vendor`       | Manufacturer name                                                                           | Board manufacturer                                                               |
| `$model`        | Product name                                                                                | Board product name                                                               |
| `$serialNumber` | Product serial number, followed by ` - ` and the product part/model number when both exist | Board serial number, followed by ` - ` and the board part number when both exist |

A FRU with neither a vendor nor a model is not listed. The lines are sorted by how complete and how central they are:

1. the system chassis and system board, with a model and a serial number (from the Product area);
2. the front and back panel boards, then the other FRUs, with a model and a serial number (from the Product area);
3. every other FRU (incomplete Product area, or Board area only).

### Device state lines

```text
$deviceType;$deviceId;$deviceUniqueId;$vendor;$model;$serialNumber;$states
```

One line per **entity** (device) that has at least one sensor with an asserted state:

| Field                                | Content                                                                                                                                         |
| ------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| `$deviceType`                        | The entity type, in the wording of `ipmiutil`: `System Board`, `Processor`, `Power Supply`, `Fan Device`, `Memory Device`, `Disk or Disk Bay`, ... |
| `$deviceId`                          | The entity instance number, as the signed byte of the record                                                                                    |
| `$deviceUniqueId`                    | `$deviceType $deviceId`, for example `Power Supply 1`                                                                                           |
| `$vendor`, `$model`, `$serialNumber` | From the FRU of the same entity type and instance, if any; empty otherwise                                                                      |
| `$states`                            | The states of every sensor of the entity, `sensorName=state` separated with `\|`                                                                |

Sensors that report `Device Absent` are left out.

### Reading lines

One line per Full Sensor record with a reading, for the units below. Sensors in other units are not reported, and neither are sensors with no reading (raw value `0xFF`).

```text
Temperature;$sensorId;$sensorName;$deviceUniqueId;$value;$threshold1;$threshold2
Voltage;$sensorId;$sensorName;$deviceUniqueId;$value;$threshold1;$threshold2
Fan;$sensorId;$sensorName;$deviceUniqueId;$value;$threshold1;$threshold2
Current;$sensorId;$sensorName;$deviceUniqueId;$value
PowerConsumption;$sensorId;$sensorName;$deviceUniqueId;$value
Energy;$sensorId;$sensorName;$deviceUniqueId;$value
```

| Line             | Unit of `$value`            | `$threshold1`                                                       | `$threshold2`                                                       |
| ---------------- | --------------------------- | ------------------------------------------------------------------- | ------------------------------------------------------------------- |
| Temperature      | °C (°F and K are converted) | Upper non-critical                                                  | Upper critical, else upper non-recoverable                          |
| Voltage          | **mV**                      | Lower non-critical, else lower critical, else lower non-recoverable | Upper non-critical, else upper critical, else upper non-recoverable |
| Fan              | RPM                         | Lower critical, else lower non-recoverable                          | Lower non-critical                                                  |
| Current          | A                           |                                                                     |                                                                     |
| PowerConsumption | W                           |                                                                     |                                                                     |
| Energy           | J                           |                                                                     |                                                                     |

- `$sensorId` is the SDR record ID, as 4 lowercase hexadecimal digits.
- `$deviceUniqueId` is the entity of the sensor, as in the device state lines.
- Thresholds are rounded to integers, in the same unit as `$value`, and empty when the BMC does not define them.

## Problems that do not fail a request

ipmi-java does not log. The SDR records it skips (OEM record types, for example), the sensors it cannot read and the FRUs it cannot fully decode are logged by the extension at the `debug` level.
