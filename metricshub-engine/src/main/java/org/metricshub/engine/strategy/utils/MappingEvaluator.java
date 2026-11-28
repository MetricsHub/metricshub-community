package org.metricshub.engine.strategy.utils;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2026 MetricsHub
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

import io.jawk.backend.AVM;
import io.jawk.backend.SandboxedAVM;
import io.jawk.ext.GawkExtension;
import io.jawk.jrt.AwkNumberFormatException;
import io.jawk.util.AwkSettings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.awk.AwkExecutor;
import org.metricshub.engine.awk.AwkNames;
import org.metricshub.engine.awk.MappingExpression;
import org.metricshub.engine.awk.MappingExpression.Kind;
import org.metricshub.engine.awk.MappingExtensionForJawk;
import org.metricshub.engine.awk.MappingExtensionForJawk.NoValue;
import org.metricshub.engine.awk.TableInputSource;
import org.metricshub.engine.awk.UtilityExtensionForJawk;
import org.metricshub.engine.common.JobInfo;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.monitor.task.Mapping;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * Evaluates the values of a mapping that start with <code>=</code> (AWK expressions) for one table on one thread. The
 * other values are left to the legacy {@link MappingProcessor}, through {@link #getLegacyMapping()}. Not thread-safe.
 * <p>
 * A value that has no value (a function that cannot answer, a failed evaluation, an invalid or empty expression) is
 * left out of the result, except under <code>conditionalCollection</code> where it is the empty text. The logs never
 * hold a cell, a text source, a connector value or an exception message.
 */
@Slf4j
public class MappingEvaluator {

	/**
	 * The four sections of a mapping.
	 */
	public enum Section {
		/**
		 * attributes
		 */
		ATTRIBUTES("attributes"),
		/**
		 * metrics
		 */
		METRICS("metrics"),
		/**
		 * conditionalCollection
		 */
		CONDITIONAL_COLLECTION("conditionalCollection"),
		/**
		 * legacyTextParameters
		 */
		LEGACY_TEXT_PARAMETERS("legacyTextParameters");

		private final String yamlName;

		Section(final String yamlName) {
			this.yamlName = yamlName;
		}
	}

	private static final String FIELD_SEPARATOR = ";";

	private static final GawkExtension GAWK = new GawkExtension();

	private final JobInfo jobInfo;
	private final Mapping legacyMapping;
	private final Map<Section, List<Map.Entry<String, MappingExpression>>> expressions = new EnumMap<>(Section.class);
	private final Map<String, String> injected = new HashMap<>();
	private final MappingExtensionForJawk extension;
	private final Set<String> warned = new HashSet<>();
	private AVM avm;

	/**
	 * Builds the evaluator of one table.
	 *
	 * @param telemetryManager The host
	 * @param connector        The connector (the variant of this resource): its constants and connector variables
	 * @param mapping          The mapping of the job
	 * @param jobInfo          Hostname, connector id, monitor type and job name, for the logs
	 * @param collectTime      The strategy time
	 * @param textSources      The text sources of this run of the job, by name, <code>null</code> for none
	 */
	public MappingEvaluator(
		final TelemetryManager telemetryManager,
		final Connector connector,
		final Mapping mapping,
		final JobInfo jobInfo,
		final long collectTime,
		final Map<String, String> textSources
	) {
		this.jobInfo = jobInfo;
		this.extension = new MappingExtensionForJawk(
			telemetryManager,
			jobInfo.getHostname(),
			jobInfo.getConnectorId(),
			collectTime
		);

		final Set<String> referencedNames = new HashSet<>();
		for (final Section section : Section.values()) {
			final List<Map.Entry<String, MappingExpression>> sectionExpressions = new ArrayList<>();
			final Map<String, String> values = valuesOf(mapping, section);
			if (values != null) {
				values.forEach((key, value) -> {
					if (MappingExpression.isExpression(value)) {
						final MappingExpression expression = MappingExpression.of(section == Section.METRICS, value);
						sectionExpressions.add(Map.entry(key, expression));
						if (expression.getKind() == Kind.EVAL) {
							referencedNames.addAll(expression.getReferencedNames());
						}
					}
				});
			}
			expressions.put(section, sectionExpressions);
		}

		legacyMapping = expressions.values().stream().allMatch(List::isEmpty)
			? mapping
			: Mapping.builder()
				.type(mapping.getType())
				.source(mapping.getSource())
				.attributes(withoutExpressions(mapping.getAttributes()))
				.metrics(withoutExpressions(mapping.getMetrics()))
				.conditionalCollection(withoutExpressions(mapping.getConditionalCollection()))
				.legacyTextParameters(withoutExpressions(mapping.getLegacyTextParameters()))
				.build();

		if (!referencedNames.isEmpty()) {
			AwkNames.values(connector.getConstants(), connector.getVariableValues(), textSources).forEach((name, value) -> {
				if (referencedNames.contains(name)) {
					injected.put(name, value);
				}
			});
		}
	}

	/**
	 * The mapping as the legacy {@link MappingProcessor} must see it: without its <code>=</code> values.
	 *
	 * @return the mapping itself when it has no <code>=</code> value, else a copy without them
	 */
	public Mapping getLegacyMapping() {
		return legacyMapping;
	}

	/**
	 * Evaluates the <code>=</code> values of the section on the row.
	 *
	 * @param section The section
	 * @param row     The row of the table
	 * @param monitor The monitor, <code>null</code> while attributes are evaluated
	 * @return a new map holding the values that have one, the empty text for a <code>conditionalCollection</code>
	 *         value that has none; under {@link Section#METRICS}, <code>__&lt;key&gt;.raw_power_share</code> for a
	 *         <code>computePowerShareRatio</code> value
	 */
	public Map<String, String> evaluate(final Section section, final List<String> row, final Monitor monitor) {
		final Map<String, String> result = new HashMap<>();
		for (final Map.Entry<String, MappingExpression> entry : expressions.get(section)) {
			final String key = entry.getKey();
			final String text = evaluate(section, key, entry.getValue(), row, monitor);
			if (text != null) {
				result.put(key, text);
			} else if (section == Section.CONDITIONAL_COLLECTION) {
				result.put(key, "");
			}
			if (section == Section.METRICS && entry.getValue().getKind() == Kind.EVAL && extension.getPowerShare() != null) {
				result.put(MappingFunctions.powerShareName(key), MappingExpression.toText(extension.getPowerShare()));
			}
		}
		return result;
	}

	/**
	 * Evaluates one value.
	 *
	 * @return its text, <code>null</code> when it has no value
	 */
	private String evaluate(
		final Section section,
		final String key,
		final MappingExpression expression,
		final List<String> row,
		final Monitor monitor
	) {
		final String text;
		switch (expression.getKind()) {
			case FIELD:
				final int index = expression.getFieldIndex();
				if (index > row.size() || row.get(index - 1) == null) {
					warnOnce(section, key, "reads column " + index + " of a row of " + row.size() + " cells: the value is empty");
					return "";
				}
				return row.get(index - 1);
			case CONSTANT:
				text = expression.getConstantText();
				break;
			case EVAL:
				extension.prepare(section == Section.ATTRIBUTES ? null : monitor, key);
				try {
					text = MappingExpression.toText(
						avm().eval(expression.getExpression(), TableInputSource.ofRow(row, FIELD_SEPARATOR))
					);
				} catch (Exception e) {
					logFailure(section, key, e);
					return null;
				}
				break;
			default:
				warnOnce(section, key, "is an invalid expression (" + expression.getProblem() + "): it has no value");
				return null;
		}
		if (text == null) {
			warnOnce(section, key, "is an empty expression: it has no value");
		}
		return text;
	}

	/**
	 * The AVM of the evaluator, built at the first value evaluated on it.
	 */
	private AVM avm() {
		if (avm == null) {
			final AwkSettings settings = AwkExecutor.newAwkSettings(FIELD_SEPARATOR);
			settings.setStrictNumbers(true);
			injected.forEach(settings::putVariable);
			avm = new SandboxedAVM(
				settings,
				Map.of(
					MappingExtensionForJawk.class.getName(),
					extension,
					UtilityExtensionForJawk.class.getName(),
					UtilityExtensionForJawk.INSTANCE,
					GawkExtension.class.getName(),
					GAWK
				)
			);
		}
		return avm;
	}

	/**
	 * Logs a failed evaluation: nothing for a silent {@link NoValue}, DEBUG for another {@link NoValue} or a strict
	 * number error, WARN once for a second state call or any other exception.
	 */
	private void logFailure(final Section section, final String key, final Exception failure) {
		final NoValue noValue = find(failure, NoValue.class);
		if (noValue != null && noValue.isSilent()) {
			return;
		}
		if (noValue != null && noValue.isSecondStateCall()) {
			warnOnce(section, key, "has no value: " + describe(failure));
		} else if (noValue != null || find(failure, AwkNumberFormatException.class) != null) {
			log.debug(
				"Hostname {} - Connector {} - {} {} mapping: the {} value {} has no value: {}.",
				jobInfo.getHostname(),
				jobInfo.getConnectorId(),
				jobInfo.getMonitorType(),
				jobInfo.getJobName(),
				section.yamlName,
				key,
				describe(failure)
			);
		} else {
			warnOnce(section, key, "failed with " + describe(failure) + ": it has no value");
		}
	}

	/**
	 * Logs a WARN about a value, once per evaluator, section and key.
	 *
	 * @param what What happened to the value: never a cell, a text source, a connector value or a message
	 */
	private void warnOnce(final Section section, final String key, final String what) {
		if (warned.add(section.yamlName + ":" + key)) {
			log.warn(
				"Hostname {} - Connector {} - {} {} mapping: the {} value {} {}.",
				jobInfo.getHostname(),
				jobInfo.getConnectorId(),
				jobInfo.getMonitorType(),
				jobInfo.getJobName(),
				section.yamlName,
				key,
				what
			);
		}
	}

	/**
	 * Whether the evaluator built its AVM.
	 *
	 * @return <code>true</code> once an {@link Kind#EVAL} value was evaluated
	 */
	boolean hasAvm() {
		return avm != null;
	}

	/**
	 * The names set as AWK variables.
	 *
	 * @return the injected names
	 */
	Set<String> getInjectedNames() {
		return Collections.unmodifiableSet(injected.keySet());
	}

	/**
	 * The only text about a failure the evaluator logs: the {@link NoValue} reason (a constant), or the simple class
	 * name of the exception and of its cause. Never a message.
	 *
	 * @param failure The failure
	 * @return its description
	 */
	static String describe(final Throwable failure) {
		final NoValue noValue = find(failure, NoValue.class);
		if (noValue != null) {
			return noValue.getMessage();
		}
		final Throwable cause = failure.getCause();
		return cause == null
			? failure.getClass().getSimpleName()
			: failure.getClass().getSimpleName() + " (" + cause.getClass().getSimpleName() + ")";
	}

	private static <T extends Throwable> T find(final Throwable failure, final Class<T> type) {
		for (Throwable t = failure; t != null; t = t.getCause()) {
			if (type.isInstance(t)) {
				return type.cast(t);
			}
		}
		return null;
	}

	private static Map<String, String> valuesOf(final Mapping mapping, final Section section) {
		return switch (section) {
			case ATTRIBUTES -> mapping.getAttributes();
			case METRICS -> mapping.getMetrics();
			case CONDITIONAL_COLLECTION -> mapping.getConditionalCollection();
			case LEGACY_TEXT_PARAMETERS -> mapping.getLegacyTextParameters();
		};
	}

	private static Map<String, String> withoutExpressions(final Map<String, String> values) {
		if (values == null) {
			return null;
		}
		final Map<String, String> texts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		values.forEach((key, value) -> {
			if (!MappingExpression.isExpression(value)) {
				texts.put(key, value);
			}
		});
		return texts;
	}
}
