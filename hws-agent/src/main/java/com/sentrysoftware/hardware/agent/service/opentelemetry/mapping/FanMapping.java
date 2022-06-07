package com.sentrysoftware.hardware.agent.service.opentelemetry.mapping;

import static com.sentrysoftware.hardware.agent.service.opentelemetry.mapping.MappingConstants.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.sentrysoftware.hardware.agent.dto.metric.MetricInfo;
import com.sentrysoftware.hardware.agent.dto.metric.MetricInfo.MetricType;
import com.sentrysoftware.hardware.agent.dto.metric.StaticIdentifyingAttribute;
import com.sentrysoftware.matrix.common.meta.monitor.Fan;
import com.sentrysoftware.matrix.common.meta.monitor.IMetaMonitor;

import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.ALARM_THRESHOLD;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.WARNING_THRESHOLD;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.PERCENT_ALARM_THRESHOLD;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.PERCENT_WARNING_THRESHOLD;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FanMapping {

	private static final String STATUS_METRIC_NAME = "hw.fan.status";
	private static final String MONITOR_TYPE = "fan";
	private static final String STATUS_METRIC_DESCRIPTION = createStatusDescription(
		MONITOR_TYPE,
		STATE_ATTRIBUTE_KEY,
		OK_ATTRIBUTE_VALUE, DEGRADED_ATTRIBUTE_VALUE, FAILED_ATTRIBUTE_VALUE, PRESENT_ATTRIBUTE_VALUE
	);
	private static final String SPEED_LIMIT_METRIC_DESCRIPTION = createCustomDescriptionWithAttributes(
		"Speed of the corresponding fan (in revolutions/minute) that will generate a warning or an alarm when reached",
		LIMIT_TYPE_ATTRIBUTE_KEY,
		LOW_CRITICAL_ATTRIBUTE_VALUE, LOW_DEGRADED_ATTRIBUTE_VALUE
	);
	private static final String SPEED_RATIO_LIMIT_METRIC_DESCRIPTION = createCustomDescriptionWithAttributes(
		"Fan speed ratio that will generate a warning or an alarm when reached",
		LIMIT_TYPE_ATTRIBUTE_KEY,
		LOW_CRITICAL_ATTRIBUTE_VALUE, LOW_DEGRADED_ATTRIBUTE_VALUE
	);

	/**
	 * Build fan metrics map
	 *
	 * @return {@link Map} where the metrics are indexed by the matrix parameter name
	 */
	static Map<String, List<MetricInfo>> buildFanMetricsMapping() {
		final Map<String, List<MetricInfo>> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

		map.put(
			IMetaMonitor.STATUS.getName(),
			List.of(
				MetricInfo
					.builder()
					.name(STATUS_METRIC_NAME)
					.description(STATUS_METRIC_DESCRIPTION)
					.identifyingAttribute(
						StaticIdentifyingAttribute
							.builder()
							.key(STATE_ATTRIBUTE_KEY)
							.value(OK_ATTRIBUTE_VALUE)
							.build()
					)
					.predicate(OK_STATUS_PREDICATE)
					.type(MetricType.UP_DOWN_COUNTER)
					.build(),
				MetricInfo
					.builder()
					.name(STATUS_METRIC_NAME)
					.description(STATUS_METRIC_DESCRIPTION)
					.identifyingAttribute(
						StaticIdentifyingAttribute
							.builder()
							.key(STATE_ATTRIBUTE_KEY)
							.value(DEGRADED_ATTRIBUTE_VALUE)
							.build()
					)
					.predicate(DEGRADED_STATUS_PREDICATE)
					.type(MetricType.UP_DOWN_COUNTER)
					.build(),
				MetricInfo
					.builder()
					.name(STATUS_METRIC_NAME)
					.description(STATUS_METRIC_DESCRIPTION)
					.identifyingAttribute(
						StaticIdentifyingAttribute
							.builder()
							.key(STATE_ATTRIBUTE_KEY)
							.value(FAILED_ATTRIBUTE_VALUE)
							.build()
					)
					.predicate(FAILED_STATUS_PREDICATE)
					.type(MetricType.UP_DOWN_COUNTER)
					.build()
			)
		);

		map.put(
			IMetaMonitor.PRESENT.getName(),
			Collections.singletonList(
				MetricInfo
					.builder()
					.name(STATUS_METRIC_NAME)
					.description(STATUS_METRIC_DESCRIPTION)
					.identifyingAttribute(
						StaticIdentifyingAttribute
							.builder()
							.key(STATE_ATTRIBUTE_KEY)
							.value(PRESENT_ATTRIBUTE_VALUE)
							.build()
					)
					.predicate(PRESENT_PREDICATE)
					.type(MetricType.UP_DOWN_COUNTER)
					.build()
			)
		);

		map.put(
			IMetaMonitor.ENERGY.getName(),
			Collections.singletonList(
				MetricInfo
					.builder()
					.name("hw.fan.energy")
					.unit(JOULES_UNIT)
					.type(MetricType.COUNTER)
					.description(createEnergyDescription(MONITOR_TYPE))
					.build()
			)
		);

		map.put(
			Fan.POWER_CONSUMPTION.getName(), 
			Collections.singletonList(
				MetricInfo
					.builder()
					.name("hw.fan.power")
					.unit(WATTS_UNIT)
					.type(MetricType.GAUGE)
					.description(createPowerConsumptionDescription(MONITOR_TYPE))
					.build()
			)
		);

		map.put(
			Fan.SPEED.getName(),
			Collections.singletonList(
				MetricInfo
				.builder()
				.name("hw.fan.speed")
				.unit(RPM_UNIT)
				.type(MetricType.GAUGE)
				.description("Fan speed.")
				.build()
			)
		);

		map.put(
			Fan.SPEED_PERCENT.getName(),
			Collections.singletonList(
				MetricInfo
					.builder()
					.name("hw.fan.speed_ratio")
					.unit(RATIO_UNIT)
					.factor(RATIO_FACTOR)
					.type(MetricType.GAUGE)
					.description("Fan speed ratio.")
					.build()
			)
		);

		return map;
	}

	/**
	 * Create Fan Metadata to metrics map
	 * 
	 * @return {@link Map} of {@link MetricInfo} instances indexed by the matrix parameter names
	 */
	static Map<String, List<MetricInfo>> fanMetadataToMetrics() {
		final Map<String, List<MetricInfo>> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

		map.put(
			ALARM_THRESHOLD,
			Collections.singletonList(
				MetricInfo
					.builder()
					.name("hw.fan.speed.limit")
					.unit(RPM_UNIT)
					.description(SPEED_LIMIT_METRIC_DESCRIPTION)
					.identifyingAttribute(
						StaticIdentifyingAttribute
							.builder()
							.key(LIMIT_TYPE_ATTRIBUTE_KEY)
							.value(LOW_CRITICAL_ATTRIBUTE_VALUE)
							.build()
					)
					.build()
			)
		);

		map.put(
			WARNING_THRESHOLD,
			Collections.singletonList(
				MetricInfo
					.builder()
					.name("hw.fan.speed.limit")
					.description(SPEED_LIMIT_METRIC_DESCRIPTION)
					.unit(RPM_UNIT)
					.identifyingAttribute(
						StaticIdentifyingAttribute
							.builder()
							.key(LIMIT_TYPE_ATTRIBUTE_KEY)
							.value(LOW_DEGRADED_ATTRIBUTE_VALUE)
							.build()
					)
					.build()
			)
		);

		map.put(
			PERCENT_ALARM_THRESHOLD,
			Collections.singletonList(
				MetricInfo
					.builder()
					.name("hw.fan.speed_ratio.limit")
					.unit(RATIO_UNIT)
					.factor(RATIO_FACTOR)
					.description(SPEED_RATIO_LIMIT_METRIC_DESCRIPTION)
					.identifyingAttribute(
						StaticIdentifyingAttribute
							.builder()
							.key(LIMIT_TYPE_ATTRIBUTE_KEY)
							.value(LOW_CRITICAL_ATTRIBUTE_VALUE)
							.build()
					)
					.build()
			)
		);

		map.put(
			PERCENT_WARNING_THRESHOLD,
			Collections.singletonList(
				MetricInfo
					.builder()
					.name("hw.fan.speed_ratio.limit")
					.description(SPEED_RATIO_LIMIT_METRIC_DESCRIPTION)
					.unit(RATIO_UNIT)
					.factor(RATIO_FACTOR)
					.identifyingAttribute(
						StaticIdentifyingAttribute
							.builder()
							.key(LIMIT_TYPE_ATTRIBUTE_KEY)
							.value(LOW_DEGRADED_ATTRIBUTE_VALUE)
							.build()
					)
					.build()
			)
		);
		return map;
	}
}
