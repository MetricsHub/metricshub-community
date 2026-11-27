# MetricsHub GPU and NVLink Alert Rules for Prometheus

## GPUs

### Memory Errors

#### Purpose
Identify GPUs reporting new memory errors. The generic hardware error rule cannot match the GPU's error limit to its counters because only the counters identify the error type.

#### What it detects
The rules track increases in corrected and uncorrected ECC memory errors over the last hour. If a counter drops, Prometheus treats it as a reset. The rules skip that window because the drop could distort the error count.

#### When does it fire
- The **warning** fires when the corrected memory error count increases.
- The **critical** alert fires when the uncorrected memory error count increases. A firing `MetricsHub-Hardware-Device-Status-Critical` alert for the same GPU's memory subsystem suppresses it.

#### This may indicate
- Memory cells that are wearing out
- A faulty memory row that may need replacement
- A job failure or incorrect results if a job used memory with an uncorrected error

#### Warning `for: 0m`
```
sum by (host_name, site, id, name, hw_error_type) (
  increase(hw_errors_total{hw_type="gpu", hw_error_type="ecc-memory-corrected"}[1h])
) > 0
and
sum by (host_name, site, id, name, hw_error_type) (
  resets(hw_errors_total{hw_type="gpu", hw_error_type="ecc-memory-corrected"}[1h])
) == 0
```

#### Critical `for: 0m`
```
sum by (host_name, site, id, name, hw_error_type) (
  increase(hw_errors_total{hw_type="gpu", hw_error_type="ecc-memory-uncorrected"}[1h])
) > 0
and
sum by (host_name, site, id, name, hw_error_type) (
  resets(hw_errors_total{hw_type="gpu", hw_error_type="ecc-memory-uncorrected"}[1h])
) == 0
unless on (host_name, site, id) ALERTS{alertstate="firing", alertname="MetricsHub-Hardware-Device-Status-Critical", hw_gpu_subsystem="memory"}
```

### XID Errors

#### Purpose
Identify GPUs for which the NVIDIA driver has reported an XID error.

#### What it detects
The exporter counts XID errors from startup. Check the server's kernel log for the individual XID codes. When Multi-Instance GPU (MIG) is enabled, the rule can alert separately for each instance that reports a counter.

#### When does it fire
- The XID error count increases during the last hour.

#### This may indicate
- An application fault on the GPU
- A driver problem
- A hardware fault identified by an XID code in the kernel log

#### Warning `for: 0m`
```
sum by (host_name, site, id, name, hw_error_type) (
  increase(hw_errors_total{hw_type="gpu", hw_error_type="xid"}[1h])
) > 0
and
sum by (host_name, site, id, name, hw_error_type) (
  resets(hw_errors_total{hw_type="gpu", hw_error_type="xid"}[1h])
) == 0
```

### Thermal Throttling

#### Purpose
Identify GPUs that slow down because of heat. These alerts also work on GPUs that do not report a temperature limit.

#### What it detects
Two counters track how long the driver and the GPU hardware have reduced clock speeds because of heat. Each rule adds up the time for its own counter over the last 15 minutes. Several short periods can add up to more than a minute.

#### When does it fire
- The **warning** fires when driver throttling totals more than 1 minute. A firing hardware status warning or critical alert for the same GPU's thermal subsystem suppresses it.
- The **critical** alert fires when hardware throttling totals more than 1 minute.

#### This may indicate
- A fan failure or blocked airflow in the server
- A problem with data center cooling
- A heavy workload on a server that is already warm

#### Warning `for: 0m`
```
sum by (host_name, site, id, name) (
  increase(hw_gpu_throttle_time_seconds_total{hw_gpu_throttle_reason="software-thermal"}[15m])
) > 60
unless on (host_name, site, id) ALERTS{alertstate="firing", alertname=~"MetricsHub-Hardware-Device-Status-(Warning|Critical)", hw_gpu_subsystem="thermal"}
```

#### Critical `for: 0m`
```
sum by (host_name, site, id, name) (
  increase(hw_gpu_throttle_time_seconds_total{hw_gpu_throttle_reason="hardware-thermal"}[15m])
) > 60
```

### Recovery Action

#### Purpose
Identify GPUs that require operator action to recover.

#### What it detects
The alert shows the recovery action reported by the driver. Possible values are `gpu-reset`, `node-reboot`, `drain-p2p`, `drain-and-reset`, `recover-imex-domain`, `bus-reset`, `system-reboot` or `unknown`. The connector uses `unknown` for a value it does not recognize.

#### When does it fire
- The same recovery state other than `none` stays active for 5 minutes.
- A firing hardware status warning or critical alert for the same GPU's driver subsystem suppresses the recovery alert.

#### This may indicate
- A GPU that needs a reset to recover from a fault
- A server that needs a reboot to recover a GPU
- Peer-to-peer traffic that needs to drain

#### Warning `for: 5m`
```
hw_gpu_recovery_action{state!="none"} == 1
unless on (host_name, site, id) ALERTS{alertstate="firing", alertname=~"MetricsHub-Hardware-Device-Status-(Warning|Critical)", hw_gpu_subsystem="driver"}
```

### Fabric Configuration

#### Purpose
Name the configuration fault that keeps a GPU from working correctly in its NVLink fabric. The hardware status alerts say that the fabric is degraded or failed. This alert says why.

#### What it detects
A fabric configuration state other than `ok`. Possible faults are `incorrect-system-guid`, `incorrect-chassis-serial-number`, `no-partition`, `insufficient-nvlinks`, `incompatible-gpu-firmware`, `invalid-location` and `gpu-state-invalid`. It needs `DCGM_FI_DEV_FABRIC_HEALTH_MASK` in the DCGM exporter counters file.

#### When does it fire
- The same fault stays active for 5 minutes.
- It can fire together with a hardware status alert for the fabric of the same GPU.

#### This may indicate
- A GPU firmware that the fabric does not support
- A GPU with no partition, or with too few links
- A system identity or location that does not match the fabric inventory

#### Warning `for: 5m`
```
hw_gpu_fabric_configuration_status{state!="ok"} == 1
```

### Memory Row Remapping

#### Purpose
Track repairs that replace a faulty GPU memory row with a spare.

#### What it detects
A pending flag means the repair will take effect at the next GPU reset. Unaffected jobs can continue running. The failure flag records a previous attempt that failed and stays set.

#### When does it fire
- The **warning** fires when a row remap has been pending for 5 minutes.
- The **critical** alert fires when the failure flag has been set for 5 minutes. A firing `MetricsHub-Hardware-Device-Status-Critical` alert for the same GPU's memory subsystem suppresses it.

#### This may indicate
- A GPU that needs a reset to finish a repair
- A memory bank that ran out of spare rows
- A card that needs diagnostics

#### Warning `for: 5m`
```
hw_gpu_memory_condition_active_ratio{hw_gpu_memory_condition_type="row-remap-pending"} == 1
```

#### Critical `for: 5m`
```
hw_gpu_memory_condition_active_ratio{hw_gpu_memory_condition_type="row-remap-failure-occurred"} == 1
unless on (host_name, site, id) ALERTS{alertstate="firing", alertname="MetricsHub-Hardware-Device-Status-Critical", hw_gpu_subsystem="memory"}
```

### SRAM Error Threshold

#### Purpose
Identify GPUs that need NVIDIA Field Diagnostics after uncorrectable SRAM errors.

#### What it detects
The GPU sets this flag when the number of unique uncorrectable errors in an SRAM bank exceeds NVIDIA's threshold for that bank. Enable `DCGM_FI_DEV_SRAM_EXCEEDED` in the DCGM exporter counters file.

#### When does it fire
- The flag has been set for 5 minutes.

#### This may indicate
- A GPU that needs NVIDIA Field Diagnostics
- A card that may qualify for replacement, depending on the diagnostic result

#### Critical `for: 5m`
```
hw_gpu_memory_condition_active_ratio{hw_gpu_memory_condition_type="sram-threshold-exceeded"} == 1
```

### Spare Memory Rows

#### Purpose
Identify GPU memory banks that are low on spare rows or have already run out.

#### What it detects
The alert value counts memory banks with at most one spare row left. Each bank uses its spare rows to replace faulty rows.

#### When does it fire
- The number of banks with one or no spare rows stays above zero for 5 minutes.

#### This may indicate
- A GPU that has remapped many rows in the same memory bank
- A memory bank that may be unable to replace the next faulty row

#### Warning `for: 5m`
```
sum by (host_name, site, id, name) (
  hw_gpu_memory_row_remap_bank_count{hw_gpu_memory_row_remap_availability=~"low|none"}
) > 0
```

### GPU NVLink Errors

#### Purpose
Detect NVLink errors reported by the GPU. A single fault may appear in both GPU and port counters. Some servers report only the GPU counters.

#### What it detects
The rules check physical GPUs for increases in each NVLink error counter over the last hour. They exclude MIG instances and skip windows in which the counter decreases.

#### When does it fire
- The **warning** fires when a `nvlink-crc` or `nvlink-crc-flit` counter increases.
- The **critical** alert fires when a `nvlink-replay` or `nvlink-recovery` counter increases.
- Both alerts can fire for the same GPU if different error types increase.

#### This may indicate
- A degrading link between the GPU and an NVSwitch or another GPU
- A GPU that needs a hardware inspection

#### Warning `for: 0m`
```
sum by (host_name, site, id, name, hw_error_type) (
  increase(hw_errors_total{hw_type="gpu", hw_gpu_nvidia_mig_instance_id="", hw_error_type=~"nvlink-crc|nvlink-crc-flit"}[1h])
) > 0
and
sum by (host_name, site, id, name, hw_error_type) (
  resets(hw_errors_total{hw_type="gpu", hw_gpu_nvidia_mig_instance_id="", hw_error_type=~"nvlink-crc|nvlink-crc-flit"}[1h])
) == 0
```

#### Critical `for: 0m`
```
sum by (host_name, site, id, name, hw_error_type) (
  increase(hw_errors_total{hw_type="gpu", hw_gpu_nvidia_mig_instance_id="", hw_error_type=~"nvlink-replay|nvlink-recovery"}[1h])
) > 0
and
sum by (host_name, site, id, name, hw_error_type) (
  resets(hw_errors_total{hw_type="gpu", hw_gpu_nvidia_mig_instance_id="", hw_error_type=~"nvlink-replay|nvlink-recovery"}[1h])
) == 0
```

## NVLink Ports

### Port Flapping

#### Purpose
Identify NVLink ports that repeatedly go down and come back up. A brief interruption can register here even if it clears before the hardware status alert fires.

#### What it detects
The rule counts changes between up and down over the last hour. A port's name can change when its state changes, so the rule uses the most recent sample for each host, site and port ID.

#### When does it fire
- The **info** alert fires after 2 or 3 state changes in the last hour.
- The **warning** fires after 4 or more state changes in the last hour.
- Both alerts clear when Prometheus no longer has a current port sample. They also clear when a hardware status alert (warning or critical) or a predicted failure warning fires for the same port.

#### This may indicate
- A brief interruption during a GPU reset, job activity or scheduled maintenance
- A GPU or an NVSwitch with a failing link
- Repeated link retraining by Fabric Manager

#### Info `for: 0m`
```
changes((
  max by (host_name, site, id) (
    hw_network_up{device_type="NVLink"}
    and topk by (host_name, site, id) (1, timestamp(hw_network_up{device_type="NVLink"}))
  )
)[1h:15s]) >= 2 < 4
and on (host_name, site, id) hw_network_up{device_type="NVLink"}
unless on (host_name, site, id) ALERTS{alertstate="firing", hw_type="network", alertname=~"MetricsHub-Hardware-Device-(Status-(Critical|Warning)|Predicted-Failure-Warning)"}
```

#### Warning `for: 0m`
```
changes((
  max by (host_name, site, id) (
    hw_network_up{device_type="NVLink"}
    and topk by (host_name, site, id) (1, timestamp(hw_network_up{device_type="NVLink"}))
  )
)[1h:15s]) >= 4
and on (host_name, site, id) hw_network_up{device_type="NVLink"}
unless on (host_name, site, id) ALERTS{alertstate="firing", hw_type="network", alertname=~"MetricsHub-Hardware-Device-(Status-(Critical|Warning)|Predicted-Failure-Warning)"}
```

### Port Errors

#### Purpose
Identify NVLink ports whose error counts are increasing.

#### What it detects
The rules compare samples within each time series and report increases by port and error type over the last hour. A decrease within a series suppresses the alert for that port and error type. A port rename starts a separate series, so an increase or decrease between the old and new names can go undetected.

#### When does it fire
- The **warning** fires when a `crc`, `ecc` or `flit` counter increases.
- The **critical** alert fires when a `replay` or `recovery` counter increases.
- Both alerts can fire for the same port if different error types increase.

#### This may indicate
- A degrading link between a GPU and an NVSwitch
- A GPU or an NVSwitch that needs a hardware inspection

#### Warning `for: 0m`
```
sum by (host_name, site, id, hw_error_type) (
  increase(hw_errors_total{hw_type="network", device_type="NVLink", hw_error_type=~"crc|ecc|flit"}[1h])
) > 0
and
sum by (host_name, site, id, hw_error_type) (
  resets(hw_errors_total{hw_type="network", device_type="NVLink", hw_error_type=~"crc|ecc|flit"}[1h])
) == 0
```

#### Critical `for: 0m`
```
sum by (host_name, site, id, hw_error_type) (
  increase(hw_errors_total{hw_type="network", device_type="NVLink", hw_error_type=~"replay|recovery"}[1h])
) > 0
and
sum by (host_name, site, id, hw_error_type) (
  resets(hw_errors_total{hw_type="network", device_type="NVLink", hw_error_type=~"replay|recovery"}[1h])
) == 0
```

### Port Saturation

#### Purpose
Identify NVLink ports where sustained traffic may limit communication between GPUs.

#### What it detects
The rule checks transmit and receive traffic separately. The connector estimates each port's bandwidth from the model of the GPU at the other end of the link.

#### When does it fire
- Traffic in either direction stays at or above 90% of estimated bandwidth for 15 minutes.

#### This may indicate
- Traffic has shifted to this link because another link on the same GPU is down
- A workload that needs more NVLink capacity than the server has

#### Info `for: 15m`
```
max by (host_name, site, id, direction) (
  hw_network_bandwidth_utilization_ratio{device_type="NVLink"}
  and topk by (host_name, site, id, direction) (1, timestamp(hw_network_bandwidth_utilization_ratio{device_type="NVLink"}))
) >= 0.9
```

### Missing Error Counters

#### Purpose
Identify servers where missing telemetry prevents NVLink port error alerts from detecting faults.

#### What it detects
The rule checks whether any port error counter is present for a host and site. A single counter is enough to clear the alert, even when other ports or error types have no data.

#### When does it fire
- The server reports NVLink ports for 6 hours without reporting any port error counters.

#### This may indicate
- Missing NVLink port error fields in the DCGM exporter counters file
- A DCGM exporter container running without the required `SYS_ADMIN` capability

#### Info `for: 6h`
```
count by (host_name, site) (hw_network_up{device_type="NVLink"})
unless on (host_name, site)
count by (host_name, site) (hw_errors_total{hw_type="network", device_type="NVLink"})
```

## NVSwitches

### Temperature Forecast

#### Purpose
Warn when an NVSwitch's projected temperature reaches its slowdown limit. The hardware temperature alerts use the measured temperature.

#### What it detects
The rule uses temperature samples from the last 30 minutes to estimate the temperature one hour ahead. A steady switch that is already at or above the limit can also trigger the alert.

#### When does it fire
- The projection stays at or above the slowdown limit for 10 minutes. The rule also needs a current temperature reading and a slowdown limit for that sensor.
- The forecast clears if the projection falls below the limit, the reading or limit becomes unavailable, or a hardware temperature warning or critical alert fires for the same sensor. Because the forecast is advisory, it can clear while the hardware alert is still pending.

#### This may indicate
- A fan failure or blocked airflow in the server
- A problem with data center cooling
- A heavy workload on a server that is already warm

#### Warning `for: 10m`
```
(
  predict_linear(hw_temperature_celsius{name=~"NVSwitch.*"}[30m], 3600)
  >= ignoring(limit_type)
  hw_temperature_limit_celsius{name=~"NVSwitch.*", limit_type="high.degraded"}
)
and hw_temperature_celsius{name=~"NVSwitch.*"}
unless on (host_name, site, id) ALERTS{alertstate="firing", alertname=~"MetricsHub-Hardware-Temperature-High-(Warning|Critical)"}
```

## Prerequisites and Limits

1. Load `metricshub-hardware-rules.yaml` in the same Prometheus instance. A GPU or NVLink alert can overlap its matching hardware alert for one rule evaluation after the hardware alert starts firing.
2. Each alert needs its input metrics. The `NvidiaDCGMExporter` connector documentation lists which fields to enable in the DCGM exporter counters file.
   - For XID errors, enable both `DCGM_EXP_XID_ERRORS_COUNT` and `DCGM_EXP_XID_ERRORS_TOTAL`. The total requires dcgm-exporter 4.8.3 or later.
   - Recovery actions, the fabric configuration state, pending row remaps, the SRAM threshold flag and spare row counts need additional fields. The default counters file includes the row remap failure flag.
   - Port and GPU NVLink error counters also need additional fields. Collecting port error counters requires the `SYS_ADMIN` capability.
3. Two samples showing an increase can trigger an error alert even during the first hour of collection. After an observed counter decrease, the rule suppresses that error type for up to an hour.
4. While the critical hardware status alert is firing for a GPU's memory subsystem, it suppresses the critical uncorrected memory error and row remap failure alerts for that GPU, even if new errors occur.
5. The flap alerts count state changes the exporter observes. A port that is already down at exporter startup may never appear.
6. Use one collector per GPU and port. Set `site` when different sites use the same host names.
