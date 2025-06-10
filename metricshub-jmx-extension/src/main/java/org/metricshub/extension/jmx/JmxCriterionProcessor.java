package org.metricshub.extension.jmx;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub JMX Extension
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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.management.ObjectName;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.identity.criterion.Criterion;
import org.metricshub.engine.connector.model.identity.criterion.JmxCriterion;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.utils.PslUtils;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * Reads zero or more attributes from one MBean and compares each against an optional PSL regex.
 */
@Slf4j
public class JmxCriterionProcessor {

	private final JmxRequestExecutor jmxExecutor;

	public JmxCriterionProcessor() {
		this.jmxExecutor = new JmxRequestExecutor();
	}

	public CriterionTestResult process(
		final Criterion criterion,
		final String connectorId,
		final TelemetryManager telemetryManager
	) {
		if (!(criterion instanceof JmxCriterion jmxCriterion)) {
			throw new IllegalArgumentException(
				"Expected JmxCriterion, got " + (criterion == null ? "<null>" : criterion.getClass().getSimpleName())
			);
		}

		HostConfiguration hostConfig = telemetryManager.getHostConfiguration();
		if (hostConfig == null || !hostConfig.getConfigurations().containsKey(JmxConfiguration.class)) {
			log.debug("JMX not configured; cannot process criterion {}.", jmxCriterion);
			return CriterionTestResult.empty();
		}
		JmxConfiguration jmxConfig = (JmxConfiguration) hostConfig.getConfigurations().get(JmxConfiguration.class);

		List<List<String>> fetched;
		ObjectName parsedName;
		try {
			parsedName = new ObjectName(jmxCriterion.getObjectName());
			fetched =
				jmxExecutor.fetchBeanInfo(
					jmxConfig,
					jmxCriterion.getObjectName(),
					jmxCriterion.getAttributes(),
					parsedName.getKeyPropertyList().keySet().stream().toList()
				);
		} catch (Exception e) {
			String err = String.format(
				"Connector %s - error fetching attributes %s from MBean \"%s\" on %s:%d: %s",
				connectorId,
				jmxCriterion.getAttributes(),
				jmxCriterion.getObjectName(),
				jmxConfig.getHostname(),
				jmxConfig.getPort(),
				e.getMessage()
			);
			log.warn(err, e);
			return CriterionTestResult.error(jmxCriterion, e);
		}

		int keyCount = parsedName.getKeyPropertyList().size();
		List<String> attrs = jmxCriterion.getAttributes() == null ? List.of() : jmxCriterion.getAttributes();
		List<String> patterns = jmxCriterion.getExpectedPatterns() == null ? List.of() : jmxCriterion.getExpectedPatterns();

		List<String> messages = new ArrayList<>();
		boolean overallSuccess = true;

		// Evaluate each attribute against its pattern
		for (int i = 0; i < attrs.size(); i++) {
			String attrName = attrs.get(i);
			String value = (fetched.size() > i && fetched.get(i).size() > keyCount + i)
				? fetched.get(i).get(keyCount + i)
				: null;
			String pattern = (i < patterns.size()) ? patterns.get(i) : null;

			if (pattern == null || pattern.isBlank()) {
				if (value == null) {
					overallSuccess = false;
					messages.add(String.format("Attribute \"%s\" was not fetched (null).", attrName));
				} else {
					messages.add(String.format("Attribute \"%s\" fetched: %s", attrName, value));
				}
			} else if (value == null) {
				overallSuccess = false;
				messages.add(String.format("Attribute \"%s\" is null; cannot match pattern \"%s\".", attrName, pattern));
			} else {
				Pattern regex = Pattern.compile(PslUtils.psl2JavaRegex(pattern), Pattern.CASE_INSENSITIVE);
				if (regex.matcher(value).find()) {
					messages.add(String.format("Attribute \"%s\" matched pattern \"%s\" → value: %s", attrName, pattern, value));
				} else {
					overallSuccess = false;
					messages.add(
						String.format("Attribute \"%s\" did NOT match pattern \"%s\" → value: %s", attrName, pattern, value)
					);
				}
			}
		}

		// Build result string of name=value pairs
		List<String> resultPairs = new ArrayList<>();
		for (int i = 0; i < attrs.size(); i++) {
			String a = attrs.get(i);
			String v = (fetched.size() > i && fetched.get(i).size() > keyCount + i)
				? fetched.get(i).get(keyCount + i)
				: "<null>";
			resultPairs.add(String.format("%s=%s", a, v));
		}
		String resultString = String.join("; ", resultPairs);
		String humanMessage = String.join(" | ", messages);

		return CriterionTestResult.builder().result(resultString).message(humanMessage).success(overallSuccess).build();
	}
}
