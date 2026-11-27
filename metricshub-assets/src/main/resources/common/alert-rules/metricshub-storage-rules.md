# Storage Alert Rules — MetricsHub / Prometheus

## Storage Pools

### Pool Close to Saturation (Oversubscribed Only)

#### Purpose
Detect storage pools that are approaching their physical capacity limit and are oversubscribed, where thin-provisioning commitments exceed physical capacity. The combination that most directly risks write failures.

#### What it detects
Pools where the ratio of used bytes to total capacity exceeds safe operating thresholds, while subscribed capacity also exceeds physical capacity (limit).

#### When does it fire
- **Warning**: pool used capacity exceeds 85% of its total limit and subscribed capacity exceeds 100% of physical capacity.
- **Critical**: pool used capacity exceeds 95% of its total limit and subscribed capacity exceeds 100% of physical capacity.

#### This may indicate
- Imminent risk of write failures as thin-provisioned space can no longer be honored
- Missing thin-provisioning reclamation cycles on a pool that has already over-committed
- Immediate need for volume migration, reclamation, or pool expansion

#### Warning `for: 10m`
```
(
  storage_usage_bytes{storage_type="pool", storage_provisioning_state="used"}
/
  ignoring(storage_provisioning_state)
  storage_limit_bytes{storage_type="pool"}
) > 0.85
and ignoring(storage_provisioning_state)
(
  storage_provisioning_bytes{storage_type="pool", storage_provisioning_state="subscribed"}
/
  ignoring(storage_provisioning_state)
  storage_limit_bytes{storage_type="pool"}
) > 1
```

#### Critical `for: 10m`
```
(
  storage_usage_bytes{storage_type="pool", storage_provisioning_state="used"}
/
  ignoring(storage_provisioning_state)
  storage_limit_bytes{storage_type="pool"}
) > 0.95
and ignoring(storage_provisioning_state)
(
  storage_provisioning_bytes{storage_type="pool", storage_provisioning_state="subscribed"}
/
  ignoring(storage_provisioning_state)
  storage_limit_bytes{storage_type="pool"}
) > 1
```

### Pool Saturation Risk

#### Purpose
Predict future pool exhaustion by extrapolating the current growth trend, giving time to act before the pool actually fills.

#### What it detects
Pools whose current linear growth rate, if sustained, will hit 98% capacity within the alerting horizon.

#### When does it fire
- **Warning**: pool is projected to reach 98% capacity within 14 days, and its usage is currently growing (positive derivative).
- **Critical**: pool is projected to reach 98% capacity within 3 days, and its usage is currently growing.

#### This may indicate
- An unplanned workload has landed on the pool
- Reclamation or expansion must be scheduled urgently

#### Warning `for: 30m`
```
(
  predict_linear(
    storage_usage_bytes{storage_type="pool", storage_provisioning_state="used"}[1d],
    14 * 24 * 3600
  )
  >
  ignoring(storage_provisioning_state)
  storage_limit_bytes{storage_type="pool"} * 0.98
)
and
(
  deriv(storage_usage_bytes{storage_type="pool", storage_provisioning_state="used"}[1d]) > 0
)
```

#### Critical `for: 15m`
```
(
  predict_linear(
    storage_usage_bytes{storage_type="pool", storage_provisioning_state="used"}[1d],
    3 * 24 * 3600
  )
  >
  ignoring(storage_provisioning_state)
  storage_limit_bytes{storage_type="pool"} * 0.98
)
and
(
  deriv(storage_usage_bytes{storage_type="pool", storage_provisioning_state="used"}[1d]) > 0
)
```

### Pool Abnormal Growth Spike

#### Purpose
Catch sudden, large capacity increases within a short window that would not yet be visible to trend-based alerts.

#### What it detects
Pools whose used capacity grew by more than 1% of total pool size within a single hour.

#### When does it fire
- The absolute growth in used bytes over the last hour exceeds 1% of total pool capacity.

#### This may indicate
- A bulk data load, backup restore, or snapshot clone that was not anticipated
- An application writing large amounts of data unexpectedly
- A misconfigured thin-provisioned volume that has started over-allocating

#### Warning `for: 20m`
```
(
  delta(storage_usage_bytes{storage_type="pool", storage_provisioning_state="used"}[1h])
  /
  ignoring(storage_provisioning_state)
  storage_limit_bytes{storage_type="pool"}
) > 0.01
```

### Pool Oversubscription

#### Purpose
Identify pools where the total subscribed (promised) capacity far exceeds what is physically available, and where remaining free space is getting low: a combination that risks write failures on thin-provisioned volumes.

#### What it detects
Pools that are subscribed to more than 150% of their physical capacity while retaining less than 15% of free physical capacity.

#### When does it fire
- Subscribed capacity is more than 150% of physical capacity and free capacity is below 15%.

#### This may indicate
- Thin-provisioning over-commitment reaching a dangerous level
- Imminent risk of volume write failures as thin-provisioned space cannot be honored
- Need for emergency capacity addition or volume reclamation

#### Warning `for: 1h`
```
(
  storage_provisioning_bytes{storage_type="pool", storage_provisioning_state="subscribed"}
  /
  ignoring(storage_provisioning_state)
  storage_limit_bytes{storage_type="pool"}
) > 1.5
and ignoring(storage_provisioning_state)
(
  storage_usage_bytes{storage_type="pool", storage_provisioning_state="free"}
  /
  ignoring(storage_provisioning_state)
  storage_limit_bytes{storage_type="pool"}
) < 0.15
```

## Volumes

### Volume Latency Under Load

#### Purpose
Detect volumes experiencing elevated I/O response times while under meaningful load, distinguishing genuine performance degradation from idle-volume noise.

#### What it detects
Volumes whose average I/O latency exceeds safe thresholds while also sustaining at least 50 IOPS, ensuring the alert only fires on active volumes.

#### When does it fire
- **Warning**: average I/O latency exceeds 20ms with more than 50 IOPS.
- **Critical**: average I/O latency exceeds 50ms with more than 50 IOPS.

#### This may indicate
- Backend storage contention (pool, disk, or controller bottleneck)
- A hot volume receiving disproportionate I/O
- Hardware or path degradation affecting throughput
- Application response time degradation or timeout risk for hosts using these volumes

#### Warning `for: 10m`
```
(
  rate(storage_operation_time_seconds_total{storage_type="volume"}[5m])
  /
  clamp_min(rate(storage_operations_total{storage_type="volume"}[5m]), 1)
) > 0.020
and
rate(storage_operations_total{storage_type="volume"}[5m]) > 50
```

#### Critical `for: 10m`
```
(
  rate(storage_operation_time_seconds_total{storage_type="volume"}[5m])
  /
  clamp_min(rate(storage_operations_total{storage_type="volume"}[5m]), 1)
) > 0.050
and
rate(storage_operations_total{storage_type="volume"}[5m]) > 50
```

### Stalled Volume

#### Purpose
Detect volumes that were recently busy but have come to a near-complete halt, with the few remaining operations showing extreme latency.

#### What it detects
A volume that had sustained high IOPS (> 200 on average over 30 minutes) but is now processing fewer than 10 IOPS, with those remaining operations taking more than 50ms each.

#### When does it fire
- Average IOPS over the last 30 minutes was above 200, current IOPS is below 10, and current average I/O latency exceeds 50ms.

#### This may indicate
- A storage path or fabric failure blocking I/O
- A controller or backend disk entering a degraded state
- A hung I/O queue causing commands to pile up

#### Warning `for: 5m`

Volume was busy, suddenly stopped, and the few remaining operations are extremely slow.
```
(
  avg_over_time(rate(storage_operations_total{storage_type="volume"}[5m])[30m:])
) > 200
and
rate(storage_operations_total{storage_type="volume"}[5m]) < 10
and
(
  rate(storage_operation_time_seconds_total{storage_type="volume"}[5m])
  /
  clamp_min(rate(storage_operations_total{storage_type="volume"}[5m]), 1)
) > 0.050
```

### Orphan Volume

#### Purpose
Detect volumes that are reported as unmapped but still retain allocated capacity and stored data.

#### What it detects
Volumes in an unmapped state, and therefore not attached to a consumer, but still contain data

#### When does it fire
Volume is unmapped, has a non-zero size, and contains data.

#### This may indicate
- A decommissioned host or application that was not fully cleaned up
- A forgotten snapshot clone or test volume consuming real capacity
- Data that may need to be preserved, archived, or deleted as part of a formal decommission process

#### Info `for: 6h`
```
storage_limit_bytes{storage_type="volume", storage_consumer_state="unmapped"} > 0
and ignoring(storage_provisioning_state)
storage_usage_bytes{storage_type="volume", storage_provisioning_state="used"} > 0
```

### Mapped Volume Without Consumer Identity

#### Purpose
Detect volumes that are reported as mapped but have no associated consumer identity, indicating incomplete or inconsistent mapping metadata.

#### What it detects
Volumes that are mapped, but no identifiable consumer is associated.

#### When does it fire
Volume is mapped but both: `storage_consumer_naa_id` and `storage_consumer_name` are empty.

#### This may indicate
- Consumer resolution failure
- Stale or inconsistent configuration state

#### Info `for: 6h`
```
storage_limit_bytes{
  storage_type="volume",
  storage_consumer_state="mapped",
  storage_consumer_naa_id="",
  storage_consumer_name=""
} > 0
```

### Volume Status

#### Purpose
Immediately surface volumes that the storage system itself has flagged as unhealthy, ensuring hardware-reported degradation is visible in the monitoring stack without delay.

#### What it detects
Volumes reported by the array as degraded or failed through the native storage status metric.

#### When does it fire
- **Warning**: the array reports the volume state as `degraded`.
- **Critical**: the array reports the volume state as `failed`.

#### This may indicate
- An underlying RAID group losing redundancy
- A hardware or firmware fault on a backing disk or controller
- Possible imminent data loss if the failed state is not addressed immediately

#### Warning `for: 5m`
```
storage_status{storage_type="volume", state="degraded"} == 1
```

#### Critical `for: 0m`
```
storage_status{storage_type="volume", state="failed"} == 1
```

### Volume Latency Anomaly

#### Purpose
Detect statistically unusual latency spikes on individual volumes relative to their own recent history, catching degradation that would not cross a fixed absolute threshold.

#### What it detects
Volumes whose current average I/O latency is more than 4 standard deviations above their own 6-hour baseline, while still under meaningful load.

#### When does it fire
- Current latency Z-score exceeds 4 compared to the 6-hour rolling baseline, and the volume is sustaining more than 50 IOPS.

#### This may indicate
- A sudden change in access pattern or workload mix on a specific volume
- Interference from a noisy-neighbor volume on the same pool or disk group
- An early symptom of a developing hardware fault not yet reported by the array

#### Warning `for: 10m`
```
(
  (
    (rate(storage_operation_time_seconds_total{storage_type="volume"}[5m])
     /
     clamp_min(rate(storage_operations_total{storage_type="volume"}[5m]), 1)
    )
    -
    avg_over_time(
      (rate(storage_operation_time_seconds_total{storage_type="volume"}[5m])
       /
       clamp_min(rate(storage_operations_total{storage_type="volume"}[5m]), 1)
      )[6h:]
    )
  )
  /
  clamp_min(stddev_over_time(
    (rate(storage_operation_time_seconds_total{storage_type="volume"}[5m])
     /
     clamp_min(rate(storage_operations_total{storage_type="volume"}[5m]), 1)
    )[6h:]
  ), 0.001)
) > 4
and
rate(storage_operations_total{storage_type="volume"}[5m]) > 50
```

### Noisy Neighbor Volume

#### Purpose
Identify the specific volume responsible for an abnormal IOPS spike on its parent pool, naming the culprit directly in the alert label.

#### What it detects
Volumes that are consuming more than 60% of their parent pool's total IOPS at a time when the pool itself is experiencing a statistically abnormal spike relative to its own 6-hour baseline.

#### When does it fire
- A single volume is responsible for more than 60% of its parent pool's total IOPS, while the pool is simultaneously experiencing an unusual spike well above its normal activity level over the last 6 hours.

#### This may indicate
- A single volume monopolizing pool throughput and degrading latency for all other volumes on the same pool

#### Warning `for: 10m`
```
(
  rate(storage_operations_total{storage_type="volume", storage_parent_type="pool"}[5m])
  /
  on(host_name, storage_parent_id) group_left()
  sum by (host_name, storage_parent_id) (
    rate(storage_operations_total{storage_type="volume", storage_parent_type="pool"}[5m])
  )
) > 0.60
and on(host_name, storage_parent_id)
(
  (
    sum by (host_name, storage_parent_id) (
      rate(storage_operations_total{storage_type="volume", storage_parent_type="pool"}[5m])
    )
    -
    avg_over_time(
      sum by (host_name, storage_parent_id) (
        rate(storage_operations_total{storage_type="volume", storage_parent_type="pool"}[5m])
      )[6h:]
    )
  )
  /
  clamp_min(
    stddev_over_time(
      sum by (host_name, storage_parent_id) (
        rate(storage_operations_total{storage_type="volume", storage_parent_type="pool"}[5m])
      )[6h:]
    ),
    1
  )
) > 3
```

## CPU

### CPU Saturation

#### Purpose
Detect storage controllers whose processing capacity is approaching saturation, and corroborate with I/O latency to avoid false positives from short bursts.

#### What it detects
Controllers where CPU utilization is high and I/O operations are simultaneously experiencing elevated latency, indicating the CPU is likely the bottleneck rather than just handling background tasks.

#### When does it fire
- **Warning**: controller CPU utilization exceeds 80% and average I/O latency exceeds 20ms.
- **Critical**: controller CPU utilization exceeds 90% and average I/O latency exceeds 30ms.

#### This may indicate
- The controller is likely the bottleneck for the current workload
- Load-balancing between controllers may be uneven

#### Warning `for: 10m`
```
system_cpu_utilization_ratio{storage_type="controller"} > 0.8
and on(host_name, id, storage_type)
(
  (
    rate(storage_operation_time_seconds_total{storage_type="controller"}[5m])
    /
    clamp_min(rate(storage_operations_total{storage_type="controller"}[5m]), 1)
  ) > 0.02
)
```

#### Critical `for: 10m`
```
system_cpu_utilization_ratio{storage_type="controller"} > 0.9
and on(host_name, id, storage_type)
(
  (
    rate(storage_operation_time_seconds_total{storage_type="controller"}[5m])
    /
    clamp_min(rate(storage_operations_total{storage_type="controller"}[5m]), 1)
  ) > 0.03
)
```

## Network

### Network High Latency

#### Purpose
Detect elevated response times on storage network interfaces under load, distinguishing genuine network saturation from idle-link noise.

#### What it detects
Network interfaces where the average I/O latency exceeds 5ms while sustaining more than 500 IOPS, indicating congestion rather than statistical noise on a lightly loaded link.

#### When does it fire
- Average I/O latency exceeds 5ms with more than 500 IOPS sustained.

#### This may indicate
- Fabric congestion or buffer credit exhaustion on Fibre Channel
- Possible Ethernet network saturation

#### Warning `for: 10m`
```
(
  rate(storage_operation_time_seconds_total{storage_type="network"}[5m])
  /
  clamp_min(rate(storage_operations_total{storage_type="network"}[5m]), 10)
) > 0.005
and
rate(storage_operations_total{storage_type="network"}[5m]) > 500
```

### Interface Flap

#### Purpose
Counts up/down state changes per interface in a rolling window. A flapping link produces a spike, fast diagnosis of bad SFP or cable.

#### What it detects
Network interfaces that have changed state (up → down or down → up) more than once in the past hour, indicating an unstable link rather than a clean single failover event.

#### When does it fire
- **Info**: more than 1 state change in the last hour (possible intermittent issue).
- **Warning**: more than 2 state changes in the last hour (confirmed flapping behavior).

#### This may indicate
- A faulty SFP, GBIC, or DAC cable causing repeated link drops
- A switch port experiencing hardware or negotiation issues
- A misconfigured auto-negotiation setting causing the link to cycle

#### Info `for: 0m`
```
changes(hw_network_up{network_type!="", role!=""}[1h]) > 1
```

#### Warning `for: 0m`
```
changes(hw_network_up{network_type!="", role!=""}[1h]) > 2
```

## Physical Disks

### High Latency

#### Purpose
Detect individual physical disks whose I/O response time is elevated while under meaningful load, as a precursor to disk failure or a sign of a developing hardware fault.

#### What it detects
Physical disks where the average I/O latency exceeds 50ms while sustaining more than 100 IOPS.

#### When does it fire
- Disk average I/O latency exceeds 50ms with more than 100 IOPS sustained.

#### This may indicate
- A disk entering a pre-failure state with increasing seek or spin-up times
- A path or controller issue causing retries that inflate latency
- Disk contention from a rebuild or background verification process

#### Warning `for: 15m`
```
(
  rate(storage_operation_time_seconds_total{storage_type="physical_disk"}[5m])
  /
  clamp_min(rate(storage_operations_total{storage_type="physical_disk"}[5m]), 1)
) > 0.050
and
rate(storage_operations_total{storage_type="physical_disk"}[5m]) > 100
```

### High Latency Outliers

#### Purpose
Detect physical disks whose current average I/O latency is abnormally high relative to peer disks on the same host.

#### What it detects
A disk that is much slower than its peers on the same machine. That is useful for spotting:
- A degraded physical disk
- A controller/path issue affecting one disk
- A disk under unusual contention relative to the others

#### When does it fire
For each host, compare each physical disk's 5-minute average I/O latency to the other disks on that same host. Fires when a disk's latency Z-score relative to its host peers exceeds 4 standard deviations.

#### This may indicate
- A disk entering early failure — it is still serving I/O but at a fraction of the speed of its peers
- A cabling or expander issue creating a slow path to a specific disk
- A disk under rebuild-induced contention that is masking a hardware fault

#### Warning `for: 15m`
```
(
  max by(host_name, id, name, model, manufacturer, vendor, serial_number) (
    rate(storage_operation_time_seconds_total{storage_type="physical_disk"}[5m])
    /
    clamp_min(rate(storage_operations_total{storage_type="physical_disk"}[5m]), 1)
  )
  -
  on(host_name) group_left()
  avg by(host_name) (
    max by(host_name, id) (
      rate(storage_operation_time_seconds_total{storage_type="physical_disk"}[5m])
      /
      clamp_min(rate(storage_operations_total{storage_type="physical_disk"}[5m]), 1)
    )
  )
)
/
on(host_name) group_left()
clamp_min(
  stddev by(host_name) (
    max by(host_name, id) (
      rate(storage_operation_time_seconds_total{storage_type="physical_disk"}[5m])
      /
      clamp_min(rate(storage_operations_total{storage_type="physical_disk"}[5m]), 1)
    )
  ),
  0.001
) > 4
```

## Storage Consumers

### Active Consumers Gone Idle

#### Purpose
Detect a consumer that was recently doing meaningful I/O but is now inactive.

#### What it detects
Consumers that had sustained recent volume-level operation activity and then dropped to near-zero aggregate operation rate.

#### When does it fire
- The consumer's average aggregate IOPS over the last 2 hours was greater than 50 IOPS and,
- The consumer's current aggregate IOPS is below 1 IOPS

#### This may indicate
- Host down
- Pathing/masking break
- Workload stopped unexpectedly
- Consumer no longer accessing its mapped volumes

#### Warning `for: 10m`
```
avg_over_time(
  sum by (host_name, storage_consumer_name) (
    rate(storage_operations_total{storage_type="volume"}[5m])
  )[2h:]
) > 50
and
sum by (host_name, storage_consumer_name) (
  rate(storage_operations_total{storage_type="volume"}[5m])
) < 1
```

### Operations Spike

#### Purpose
Catch abnormal aggregate I/O bursts per consumer without needing to define a static threshold.

#### What it detects
Consumers whose current aggregate IOPS across all their mapped volumes is unusually high relative to their own normal behavior over the last 6 hours.

#### When does it fire
When a consumer's aggregate IOPS is more than 4 standard deviations above its 6-hour baseline and is generating at least 50 aggregate IOPS right now.

#### This may indicate
- Applications stuck in a loop
- Unplanned backup or snapshot
- Ransomware or malicious encryption — sudden mass writes across volumes

#### Warning `for: 10m`
```
(
  (
    sum by (host_name, storage_consumer_name) (
      rate(storage_operations_total{storage_type="volume"}[5m])
    )
    -
    avg_over_time(
      sum by (host_name, storage_consumer_name) (
        rate(storage_operations_total{storage_type="volume"}[5m])
      )[6h:]
    )
  )
  /
  clamp_min(
    stddev_over_time(
      sum by (host_name, storage_consumer_name) (
        rate(storage_operations_total{storage_type="volume"}[5m])
      )[6h:]
    ),
    1
  )
) > 4
and
sum by (host_name, storage_consumer_name) (
  rate(storage_operations_total{storage_type="volume"}[5m])
) > 50
```

### Over-provisioned Capacity

#### Purpose
Identify consumers that are using a significant allocated capacity on the storage system while holding almost no data, making them candidates for reclamation or right-sizing.

#### What it detects
A consumer for which the total mapped/provisioned volume capacity is large, but the total actually used bytes remain very small.

#### When does it fire
When a consumer’s aggregate mapped capacity exceeds 500 GiB and its aggregate used bytes remain below 5% of that mapped capacity.

#### This may indicate
- Wasted thin or thick provisioned capacity that could be reclaimed, reallocated, or decommissioned.

#### Info `for: 24h`
```
(
  sum by (host_name, storage_consumer_name) (
    storage_usage_bytes{storage_type="volume", storage_provisioning_state="used"}
  )
  /
  clamp_min(
    sum by (host_name, storage_consumer_name) (
      storage_limit_bytes{storage_type="volume"}
    ),
    1
  )
) < 0.05
and
sum by (host_name, storage_consumer_name) (
  storage_limit_bytes{storage_type="volume"}
) > 500 * 1024 * 1024 * 1024
```

### Consumer Growth Spike

#### Purpose
Detect a consumer whose aggregate used capacity is growing quickly relative to the total visible capacity allocated to it.

#### What it detects
Consumers whose aggregate used capacity across all their mapped volumes is growing at a rate that could exhaust their total provisioned size quickly if sustained.

#### When does it fire
A consumer's aggregate used capacity has grown by more than 2% of its total provisioned size within the last hour.

#### This may indicate
- A backup, restore, or bulk-write job running faster than expected on that consumer's volumes
- An application generating data at an unusual rate (logging loop, runaway process)
- A consumer approaching capacity exhaustion much sooner than planned

#### Warning `for: 20m`
```
(
  delta(
    sum by (host_name, storage_consumer_name) (
      storage_usage_bytes{storage_type="volume", storage_provisioning_state="used"}
    )[1h:]
  )
  /
  clamp_min(
    sum by (host_name, storage_consumer_name) (
      storage_limit_bytes{storage_type="volume"}
    ),
    1
  )
) > 0.02
```