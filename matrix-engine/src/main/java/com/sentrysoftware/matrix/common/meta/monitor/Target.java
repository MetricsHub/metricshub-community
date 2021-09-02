package com.sentrysoftware.matrix.common.meta.monitor;

import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.LOCATION;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.sentrysoftware.matrix.common.helpers.HardwareConstants;
import com.sentrysoftware.matrix.common.meta.parameter.MetaParameter;
import com.sentrysoftware.matrix.common.meta.parameter.ParameterType;
import com.sentrysoftware.matrix.connector.model.monitor.MonitorType;
import com.sentrysoftware.matrix.engine.strategy.IMonitorVisitor;
import com.sentrysoftware.matrix.model.alert.AlertRule;

public class Target implements IMetaMonitor {

	private static final List<String> METADATA = Collections.singletonList(LOCATION);

	public static final MetaParameter AMBIENT_TEMPERATURE = MetaParameter.builder()
			.basicCollect(false)
			.name(HardwareConstants.AMBIENT_TEMPERATURE_PARAMETER)
			.unit(HardwareConstants.TEMPERATURE_PARAMETER_UNIT)
			.type(ParameterType.NUMBER)
			.build();

	public static final MetaParameter CPU_TEMPERATURE = MetaParameter.builder()
			.basicCollect(false)
			.name(HardwareConstants.CPU_TEMPERATURE_PARAMETER)
			.unit(HardwareConstants.TEMPERATURE_PARAMETER_UNIT)
			.type(ParameterType.NUMBER)
			.build();

	public static final MetaParameter CPU_THERMAL_DISSIPATION_RATE = MetaParameter.builder()
			.basicCollect(false)
			.name(HardwareConstants.CPU_THERMAL_DISSIPATION_RATE_PARAMETER)
			.unit(HardwareConstants.EMPTY)
			.type(ParameterType.NUMBER)
			.build();

	private static final Map<String, MetaParameter> META_PARAMETERS;

	static {
		final Map<String, MetaParameter> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

		map.put(HardwareConstants.STATUS_PARAMETER, STATUS);
		map.put(HardwareConstants.HEATING_MARGIN_PARAMETER, HEATING_MARGIN);
		map.put(HardwareConstants.AMBIENT_TEMPERATURE_PARAMETER, AMBIENT_TEMPERATURE);
		map.put(HardwareConstants.CPU_TEMPERATURE_PARAMETER, CPU_TEMPERATURE);
		map.put(HardwareConstants.CPU_THERMAL_DISSIPATION_RATE_PARAMETER, CPU_THERMAL_DISSIPATION_RATE);
		map.put(HardwareConstants.ENERGY_PARAMETER, ENERGY);
		map.put(HardwareConstants.ENERGY_USAGE_PARAMETER, ENERGY_USAGE);
		map.put(HardwareConstants.POWER_CONSUMPTION_PARAMETER, POWER_CONSUMPTION);

		META_PARAMETERS = Collections.unmodifiableMap(map);
	}

	@Override
	public void accept(IMonitorVisitor monitorVisitor) {
		monitorVisitor.visit(this);
	}

	@Override
	public Map<String, MetaParameter> getMetaParameters() {
		return META_PARAMETERS;
	}

	@Override
	public MonitorType getMonitorType() {
		return MonitorType.TARGET;
	}

	@Override
	public List<String> getMetadata() {
		return METADATA;
	}

	@Override
	public Map<String, List<AlertRule>> getStaticAlertRules() {
		return Collections.emptyMap();
	}
}