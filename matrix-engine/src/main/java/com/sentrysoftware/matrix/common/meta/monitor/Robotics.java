package com.sentrysoftware.matrix.common.meta.monitor;

import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.IDENTIFYING_INFORMATION;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.DEVICE_ID;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.EMPTY;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.MODEL;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.ROBOTIC_TYPE;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.SERIAL_NUMBER;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.VENDOR;
import static com.sentrysoftware.matrix.common.helpers.NumberHelper.formatNumber;
import static com.sentrysoftware.matrix.common.helpers.StringHelper.getValue;
import static com.sentrysoftware.matrix.model.alert.AlertConditionsBuilder.STATUS_ALARM_CONDITION;
import static com.sentrysoftware.matrix.model.alert.AlertConditionsBuilder.PRESENT_ALARM_CONDITION;
import static com.sentrysoftware.matrix.model.alert.AlertConditionsBuilder.STATUS_WARN_CONDITION;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.sentrysoftware.matrix.common.helpers.HardwareConstants;
import com.sentrysoftware.matrix.common.meta.parameter.MetaParameter;
import com.sentrysoftware.matrix.common.meta.parameter.SimpleParamType;
import com.sentrysoftware.matrix.connector.model.monitor.MonitorType;
import com.sentrysoftware.matrix.engine.strategy.IMonitorVisitor;
import com.sentrysoftware.matrix.model.alert.AlertCondition;
import com.sentrysoftware.matrix.model.alert.AlertDetails;
import com.sentrysoftware.matrix.model.alert.AlertRule;
import com.sentrysoftware.matrix.model.monitor.Monitor;
import com.sentrysoftware.matrix.model.monitor.Monitor.AssertedParameter;
import com.sentrysoftware.matrix.model.parameter.DiscreteParam;
import com.sentrysoftware.matrix.model.parameter.NumberParam;
import com.sentrysoftware.matrix.model.alert.Severity;

public class Robotics implements IMetaMonitor {

	public static final MetaParameter MOVE_COUNT = MetaParameter.builder()
			.basicCollect(false)
			.name(HardwareConstants.MOVE_COUNT_PARAMETER)
			.unit(HardwareConstants.MOVE_COUNT_PARAMETER_UNIT)
			.type(SimpleParamType.NUMBER)
			.build();
	
	public static final MetaParameter ERROR_COUNT = MetaParameter.builder()
			.basicCollect(false)
			.name(HardwareConstants.ERROR_COUNT_PARAMETER)
			.unit(HardwareConstants.ERROR_COUNT_PARAMETER_UNIT)
			.type(SimpleParamType.NUMBER)
			.build();

	private static final List<String> METADATA = List.of(DEVICE_ID, SERIAL_NUMBER, VENDOR, MODEL, ROBOTIC_TYPE, IDENTIFYING_INFORMATION);

	public static final AlertRule PRESENT_ALERT_RULE = new AlertRule(Robotics::checkMissingCondition,
			PRESENT_ALARM_CONDITION,
			Severity.ALARM);
	public static final AlertRule STATUS_WARN_ALERT_RULE = new AlertRule(Robotics::checkStatusWarnCondition,
			STATUS_WARN_CONDITION,
			Severity.WARN);
	public static final AlertRule STATUS_ALARM_ALERT_RULE = new AlertRule(Robotics::checkStatusAlarmCondition,
			STATUS_ALARM_CONDITION,
			Severity.ALARM);

	private static final Map<String, MetaParameter> META_PARAMETERS;
	private static final Map<String, List<AlertRule>> ALERT_RULES;

	static {
		final Map<String, MetaParameter> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

		map.put(HardwareConstants.STATUS_PARAMETER, STATUS);
		map.put(HardwareConstants.PRESENT_PARAMETER, PRESENT);
		map.put(HardwareConstants.ERROR_COUNT_PARAMETER, ERROR_COUNT);
		map.put(HardwareConstants.MOVE_COUNT_PARAMETER, MOVE_COUNT);
		map.put(HardwareConstants.ENERGY_PARAMETER, ENERGY);
		map.put(HardwareConstants.ENERGY_USAGE_PARAMETER, ENERGY_USAGE);
		map.put(HardwareConstants.POWER_CONSUMPTION_PARAMETER, POWER_CONSUMPTION);

		META_PARAMETERS = Collections.unmodifiableMap(map);

		final Map<String, List<AlertRule>> alertRulesMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

		alertRulesMap.put(HardwareConstants.PRESENT_PARAMETER, Collections.singletonList(PRESENT_ALERT_RULE));
		alertRulesMap.put(HardwareConstants.STATUS_PARAMETER, List.of(STATUS_WARN_ALERT_RULE, STATUS_ALARM_ALERT_RULE));

		ALERT_RULES = Collections.unmodifiableMap(alertRulesMap);

	}

	/**
	 * Check missing Robotics condition.
	 * 
	 * @param monitor The monitor we wish to check
	 * @param conditions The conditions used to determine the abnormality
	 * @return {@link AlertDetails} if the abnormality is detected otherwise null
	 */
	public static AlertDetails checkMissingCondition(Monitor monitor, Set<AlertCondition> conditions) {
		final AssertedParameter<DiscreteParam> assertedPresent = monitor.assertPresentParameter(conditions);
		if (assertedPresent.isAbnormal()) {

			return AlertDetails.builder()
					.problem("These robotics are no longer detected.")
					.consequence(TapeDrive.TAPE_LIBRARY_CONSEQUENCE)
					.recommendedAction("Check that the robotics are still present, online and responding.")
					.build();
		}

		return null;
	}

	/**
	 * Condition when the monitor status is in WARN state
	 * 
	 * @param monitor    The monitor we wish to check its status
	 * @param conditions The conditions used to detect abnormality
	 * @return {@link AlertDetails} if the abnormality is detected otherwise null
	 */
	public static AlertDetails checkStatusWarnCondition(Monitor monitor, Set<AlertCondition> conditions) {
		final AssertedParameter<DiscreteParam> assertedStatus = monitor.assertStatusParameter(HardwareConstants.STATUS_PARAMETER, conditions);
		if (assertedStatus.isAbnormal()) {

			return AlertDetails.builder()
					.problem("These robotics are degraded." + IMetaMonitor.getStatusInformationMessage(monitor))
					.consequence(TapeDrive.TAPE_LIBRARY_CONSEQUENCE)
					.recommendedAction("Check the robotics for any visible problem and replace it if necessary.")
					.build();

		}
		return null;
	}

	/**
	 * Condition when the monitor status is in ALARM state
	 * 
	 * @param monitor    The monitor we wish to check its status
	 * @param conditions The conditions used to detect abnormality
	 * @return {@link AlertDetails} if the abnormality is detected otherwise null
	 */
	public static AlertDetails checkStatusAlarmCondition(Monitor monitor, Set<AlertCondition> conditions) {
		final AssertedParameter<DiscreteParam> assertedStatus = monitor.assertStatusParameter(HardwareConstants.STATUS_PARAMETER, conditions);
		if (assertedStatus.isAbnormal()) {

			return AlertDetails.builder()
					.problem("These robotics may be mechanically failed or broken." +  IMetaMonitor.getStatusInformationMessage(monitor))
					.consequence(TapeDrive.TAPE_LIBRARY_CONSEQUENCE)
					.recommendedAction("Replace or repair the faulty robotics.")
					.build();

		}
		return null;
	}

	/**
	 * Check condition when the monitor error count parameter is abnormal.
	 * 
	 * @param monitor    The monitor we wish to check its error count
	 * @param conditions The condition used to check the error count parameter value
	 * @return {@link AlertDetails} if the abnormality is detected otherwise null
	 */
	public static AlertDetails checkErrorCountCondition(Monitor monitor, Set<AlertCondition> conditions) {
		final AssertedParameter<NumberParam> assertedErrorCount = monitor.assertNumberParameter(HardwareConstants.ERROR_COUNT_PARAMETER, conditions);
		if (assertedErrorCount.isAbnormal()) {

			return AlertDetails.builder()
					.problem(String.format("These robotics encountered errors (%s).",
							getValue(() -> formatNumber(assertedErrorCount.getParameter().getValue()), EMPTY)))
					.consequence(TapeDrive.TAPE_LIBRARY_CONSEQUENCE)
					.recommendedAction("Replace or repair the faulty robotics.")
					.build();
		}

		return null;
	}

	/**
	 * Check condition when the monitor error count parameter too high.
	 * 
	 * @param monitor    The monitor we wish to check its error count
	 * @param conditions The condition used to check the error count parameter value
	 * @return {@link AlertDetails} if the abnormality is detected otherwise null
	 */
	public static AlertDetails checkHighErrorCountCondition(Monitor monitor, Set<AlertCondition> conditions) {
		final AssertedParameter<NumberParam> assertedErrorCount = monitor.assertNumberParameter(HardwareConstants.ERROR_COUNT_PARAMETER, conditions);
		if (assertedErrorCount.isAbnormal()) {

			return AlertDetails.builder()
					.problem(String.format("These robotics encountered too many errors (%s).",
							getValue(() -> formatNumber(assertedErrorCount.getParameter().getValue()), EMPTY)))
					.consequence(TapeDrive.TAPE_LIBRARY_CONSEQUENCE)
					.recommendedAction("Replace or repair the faulty robotics as soon as possible to avoid a system crash.")
					.build();
		}

		return null;
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
		return MonitorType.ROBOTICS;
	}

	@Override
	public List<String> getMetadata() {
		return METADATA;
	}

	@Override
	public Map<String, List<AlertRule>> getStaticAlertRules() {
		return ALERT_RULES;
	}
}