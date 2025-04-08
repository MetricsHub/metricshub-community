package org.metricshub.engine.alert;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2025 MetricsHub
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
import org.metricshub.engine.common.helpers.StringHelper;
import org.metricshub.engine.telemetry.Monitor;

/**
 * Represents an alert rule used for monitoring conditions and triggering alerts based on those conditions.
 */
@Data
@NoArgsConstructor
@Slf4j
public class AlertRule {

	@JsonIgnore
	private BiFunction<Monitor, Set<AlertCondition>, AlertDetails> conditionsChecker;

	private long period;
	private Set<AlertCondition> conditions;
	private Severity severity;
	private Long firstTriggerTimestamp;
	private AlertDetails details;
	private AlertRuleState active = AlertRuleState.INACTIVE;
	private AlertRuleType type;

	@JsonIgnore
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	private AlertInfo alertInfo;

	@JsonIgnore
	private Consumer<AlertInfo> trigger;

	private boolean triggered;

	/**
	 * Creates an AlertRule instance with the specified conditionsChecker, conditions, period, severity, and type.
	 *
	 * @param conditionsChecker A function that checks conditions and returns alert details.
	 * @param conditions        A set of alert conditions.
	 * @param period            The time period for the alert rule.
	 * @param severity          The severity level of the alert rule.
	 * @param type              The type of the alert rule.
	 */
	public AlertRule(
		@NonNull BiFunction<Monitor, Set<AlertCondition>, AlertDetails> conditionsChecker,
		@NonNull Set<AlertCondition> conditions,
		long period,
		@NonNull Severity severity,
		@NonNull AlertRuleType type
	) {
		this.conditionsChecker = conditionsChecker;
		this.conditions = conditions;
		this.period = period;
		this.severity = severity;
		this.type = type;
	}

	/**
	 * Creates an AlertRule instance with the specified conditionsChecker, conditions, severity, and type.
	 *
	 * @param conditionsChecker A function that checks conditions and returns alert details.
	 * @param conditions        A set of alert conditions.
	 * @param severity          The severity level of the alert rule.
	 * @param type              The type of the alert rule.
	 */
	public AlertRule(
		@NonNull BiFunction<Monitor, Set<AlertCondition>, AlertDetails> conditionsChecker,
		@NonNull Set<AlertCondition> conditions,
		@NonNull Severity severity,
		@NonNull AlertRuleType type
	) {
		this(conditionsChecker, conditions, 0, severity, type);
	}

	/**
	 * Creates an AlertRule instance with the specified conditionsChecker, conditions, period, and severity.
	 *
	 * @param conditionsChecker A function that checks conditions and returns alert details.
	 * @param conditions        A set of alert conditions.
	 * @param period            The time period for the alert rule.
	 * @param severity          The severity level of the alert rule.
	 */
	public AlertRule(
		@NonNull BiFunction<Monitor, Set<AlertCondition>, AlertDetails> conditionsChecker,
		@NonNull Set<AlertCondition> conditions,
		long period,
		@NonNull Severity severity
	) {
		this(conditionsChecker, conditions, period, severity, AlertRuleType.STATIC);
	}

	/**
	 * Creates an AlertRule instance with the specified conditionsChecker, conditions, and severity.
	 *
	 * @param conditionsChecker A function that checks conditions and returns alert details.
	 * @param conditions        A set of alert conditions.
	 * @param severity          The severity level of the alert rule.
	 */
	public AlertRule(
		@NonNull BiFunction<Monitor, Set<AlertCondition>, AlertDetails> conditionsChecker,
		@NonNull Set<AlertCondition> conditions,
		@NonNull Severity severity
	) {
		this(conditionsChecker, conditions, 0, severity, AlertRuleType.STATIC);
	}

	/**
	 * Evaluate the current {@link AlertRule}
	 *
	 * @param monitor The monitor on wish we apply the condition
	 */
	public void evaluate(Monitor monitor) {
		details = conditionsChecker.apply(monitor, conditions);
		refresh();
	}

	/**
	 * Get the alert rule state. This method refreshes the current AlertRule before returning its state
	 *
	 * @return {@link AlertRuleState}: INACTIVE, PENDING or ACTIVE
	 */
	public AlertRuleState getActive() {
		refresh();
		return active;
	}

	/**
	 * Check if the current AlertRule is active
	 *
	 * @return <code>true</code> or <code>false</code>
	 */
	public boolean isActive() {
		refresh();
		return AlertRuleState.ACTIVE.equals(active);
	}

	/**
	 * Refresh the current {@link AlertRule} and trigger the alert if the alert rule
	 * state is active and not triggered yet
	 */
	private void refresh() {
		// We have a details ? means the condition returned true (unfortunately)
		if (details != null) {
			long currentTimeMillis = System.currentTimeMillis();
			firstTriggerTimestamp = firstTriggerTimestamp == null ? currentTimeMillis : firstTriggerTimestamp;

			// If we reach the time limit defined by the period then the AlertRule becomes ACTIVE otherwise it is PENDING
			if (currentTimeMillis - firstTriggerTimestamp >= period) {
				active = AlertRuleState.ACTIVE;

				// If the alert is not triggered yet then trigger a new one
				if (!isTriggered() && trigger != null && alertInfo != null) {
					try {
						// Trigger the alert by consuming the alert information
						trigger.accept(alertInfo);
					} catch (Exception e) {
						log.debug(
							"Hostname {} - Exception detected when triggering alert.",
							StringHelper.getValue(() -> alertInfo.getHostname(), MetricsHubConstants.EMPTY),
							e
						);
					}

					// Set triggered to true to avoid triggering the same alert in future collects
					triggered = true;
				}
			} else {
				active = AlertRuleState.PENDING;
			}
		} else {
			// Reset the first trigger time stamp as we are not in an abnormality
			firstTriggerTimestamp = null;

			// Release the trigger for a future abnormality
			triggered = false;
		}
	}

	/**
	 * Copy the current alert rule and build a new one
	 *
	 * @return a new alert rule
	 */
	public AlertRule copy() {
		return new AlertRule(
			conditionsChecker,
			conditions
				.stream()
				.filter(Objects::nonNull)
				.map(AlertCondition::copy)
				.collect(Collectors.toCollection(HashSet::new)),
			period,
			severity,
			type
		);
	}

	/**
	 * Check if the given object is the same as the current AlertRule instance. <br>
	 * Must match the three fields <em>conditions</em>, <em>period</em> and <em>severity</em>
	 *
	 * @param obj The object we wish to compare
	 * @return true or false
	 */
	public boolean same(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		AlertRule other = (AlertRule) obj;
		if (conditions == null) {
			if (other.conditions != null) {
				return false;
			}
		} else if (!conditions.equals(other.conditions)) {
			return false;
		}
		if (period != other.period) {
			return false;
		}
		return severity == other.severity;
	}
}
