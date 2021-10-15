package com.sentrysoftware.matrix.common.meta.monitor;

import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.ENERGY_PARAMETER;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.ENERGY_PARAMETER_UNIT;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.ENERGY_USAGE_PARAMETER;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.ENERGY_USAGE_PARAMETER_UNIT;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.ERROR_COUNT_PARAMETER;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.ERROR_COUNT_PARAMETER_UNIT;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.HEATING_MARGIN_PARAMETER;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.HEATING_MARGIN_PARAMETER_UNIT;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.POWER_CONSUMPTION_PARAMETER;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.POWER_CONSUMPTION_PARAMETER_UNIT;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.PREDICTED_FAILURE_PARAMETER;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.PREDICTED_FAILURE_PARAMETER_UNIT;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.PRESENT_PARAMETER;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.PRESENT_PARAMETER_UNIT;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.STATUS_PARAMETER;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.STATUS_PARAMETER_UNIT;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.STATUS_INFORMATION_PARAMETER;

import java.util.List;
import java.util.Map;

import com.sentrysoftware.matrix.common.meta.parameter.DiscreteParamType;
import com.sentrysoftware.matrix.common.meta.parameter.MetaParameter;
import com.sentrysoftware.matrix.common.meta.parameter.SimpleParamType;
import com.sentrysoftware.matrix.common.meta.parameter.state.PredictedFailure;
import com.sentrysoftware.matrix.common.meta.parameter.state.Present;
import com.sentrysoftware.matrix.common.meta.parameter.state.Status;
import com.sentrysoftware.matrix.connector.model.monitor.MonitorType;
import com.sentrysoftware.matrix.engine.strategy.IMonitorVisitor;
import com.sentrysoftware.matrix.model.alert.AlertRule;
import com.sentrysoftware.matrix.model.monitor.Monitor;
import com.sentrysoftware.matrix.model.parameter.TextParam;

public interface IMetaMonitor {

	MetaParameter STATUS = MetaParameter.builder()
			.basicCollect(true)
			.name(STATUS_PARAMETER)
			.unit(STATUS_PARAMETER_UNIT)
			.type(new DiscreteParamType(Status::interpret))
			.build();

	MetaParameter PRESENT = MetaParameter.builder()
			.basicCollect(false)
			.name(PRESENT_PARAMETER)
			.unit(PRESENT_PARAMETER_UNIT)
			.type(new DiscreteParamType(Present::interpret))
			.build();

	MetaParameter PREDICTED_FAILURE = MetaParameter.builder()
			.basicCollect(true)
			.name(PREDICTED_FAILURE_PARAMETER)
			.unit(PREDICTED_FAILURE_PARAMETER_UNIT)
			.type(new DiscreteParamType(PredictedFailure::interpret))
			.build();

	MetaParameter ERROR_COUNT = MetaParameter.builder()
			.basicCollect(true)
			.name(ERROR_COUNT_PARAMETER)
			.unit(ERROR_COUNT_PARAMETER_UNIT)
			.type(SimpleParamType.NUMBER)
			.build();

	MetaParameter ENERGY = MetaParameter.builder()
		.basicCollect(false)
		.name(ENERGY_PARAMETER)
		.unit(ENERGY_PARAMETER_UNIT)
		.type(SimpleParamType.NUMBER)
		.build();

	MetaParameter HEATING_MARGIN = MetaParameter.builder()
		.basicCollect(false)
		.name(HEATING_MARGIN_PARAMETER)
		.unit(HEATING_MARGIN_PARAMETER_UNIT)
		.type(SimpleParamType.NUMBER)
		.build();

	MetaParameter ENERGY_USAGE = MetaParameter.builder()
			.basicCollect(false)
			.name(ENERGY_USAGE_PARAMETER)
			.unit(ENERGY_USAGE_PARAMETER_UNIT)
			.type(SimpleParamType.NUMBER)
			.build();

	MetaParameter POWER_CONSUMPTION = MetaParameter.builder()
			.basicCollect(false)
			.name(POWER_CONSUMPTION_PARAMETER)
			.unit(POWER_CONSUMPTION_PARAMETER_UNIT)
			.type(SimpleParamType.NUMBER)
			.build();

	void accept(IMonitorVisitor monitorVisitor);

	Map<String, MetaParameter> getMetaParameters();

	MonitorType getMonitorType();

	List<String> getMetadata();

	Map<String, List<AlertRule>> getStaticAlertRules();

	default boolean hasPresentParameter() {

		return getMetaParameters().containsKey(PRESENT_PARAMETER);
	}

	/**
	 * 
	 * @param status {@link StatusParam} instance we wish to extract its status information
	 * @return {@link String} value if the status information is available otherwise null
	 */
	/**
	 * 
	 * @param status {@link StatusParam} instance we wish to extract its status information
	 * @return {@link String} value if the status information is available otherwise null
	 */
	static String getStatusInformationMessage(Monitor monitor) {
		TextParam statusInformation = monitor.getParameter(STATUS_INFORMATION_PARAMETER, TextParam.class);

		if (statusInformation != null && statusInformation.getValue() != null) {
			return String.format(" Reported status: %s.", statusInformation.getValue());
		}
		return "";
	}

}