package org.metricshub.engine.connector.update;

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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.FILE_PATTERN;

import io.jawk.AwkProgram;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.awk.AwkExecutor;
import org.metricshub.engine.awk.AwkNames;
import org.metricshub.engine.awk.MappingExpression;
import org.metricshub.engine.awk.MetricsHubExtensionForJawk;
import org.metricshub.engine.common.JobInfo;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ContentTypeFinding;
import org.metricshub.engine.connector.model.ContentTypeFinding.Rule;
import org.metricshub.engine.connector.model.common.ContentType;
import org.metricshub.engine.connector.model.common.EmbeddedFile;
import org.metricshub.engine.connector.model.monitor.SimpleMonitorJob;
import org.metricshub.engine.connector.model.monitor.StandardMonitorJob;
import org.metricshub.engine.connector.model.monitor.task.AbstractMonitorTask;
import org.metricshub.engine.connector.model.monitor.task.Mapping;
import org.metricshub.engine.connector.model.monitor.task.source.JawkSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Awk;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.strategy.source.OrderedSources;
import org.metricshub.engine.strategy.utils.EmbeddedFileHelper;

/**
 * The load-time AWK checks (#1684): the mapping values that are expressions (<code>= ...</code>), the names that
 * constants and connector variables take in AWK, and the connector values that awk sources and computes read. A
 * finding names where and which rule, an AWK identifier at most, never a value of the connector.
 */
@Slf4j
final class AwkCheck {

	private static final String NONE = "none";
	private static final String LOAD = "(load)";
	private static final String TEXT_SOURCE = "text source";
	private static final String CONNECTOR_VARIABLE = "connector variable";
	private static final String CONSTANT = "constant";

	/**
	 * Compiles the awk sources, as the Jawk source extension does
	 */
	private static final io.jawk.Awk SOURCE_ENGINE = AwkExecutor.newAwk(
		null,
		MetricsHubExtensionForJawk.builder().build()
	);

	private final Connector connector;
	private final String connectorId;
	private final Function<Source, ContentType> typeOf;
	private final Map<String, String> constants = new LinkedHashMap<>();
	private final Set<String> variables = new HashSet<>();
	private final List<ContentTypeFinding> findings = new ArrayList<>();

	private AwkCheck(final Connector connector, final String connectorId, final Function<Source, ContentType> typeOf) {
		this.connector = connector;
		this.connectorId = connectorId;
		this.typeOf = typeOf;
	}

	/**
	 * Checks the AWK rules of the connector.
	 *
	 * @param connector   The connector, after the update chain
	 * @param connectorId The identifier of the connector in the findings
	 * @param typeOf      The static type of what a source's chain ends in
	 * @return the findings
	 */
	static List<ContentTypeFinding> check(
		final Connector connector,
		final String connectorId,
		final Function<Source, ContentType> typeOf
	) {
		final AwkCheck check = new AwkCheck(connector, connectorId, typeOf);
		check.run();
		return check.findings;
	}

	private void run() {
		if (connector.getConstants() != null) {
			connector.getConstants().forEach((name, value) -> {
				checkReserved("constants." + name, name);
				if (AwkNames.isNameable(name)) {
					constants.put(name, value);
				}
			});
		}
		if (connector.getConnectorIdentity() != null && connector.getConnectorIdentity().getVariables() != null) {
			connector.getConnectorIdentity().getVariables().keySet().forEach(name -> {
				checkReserved("connector.variables." + name, name);
				if (AwkNames.isNameable(name)) {
					variables.add(name);
				}
			});
		}

		if (connector.getMonitors() != null) {
			connector.getMonitors().forEach((name, job) -> {
				if (job instanceof StandardMonitorJob standard) {
					checkTask(name, "discovery", standard.getDiscovery());
					checkTask(name, "collect", standard.getCollect());
				} else if (job instanceof SimpleMonitorJob simple) {
					checkTask(name, "simple", simple.getSimple());
				}
			});
		}
		checkScripts("beforeAll", "beforeAll", connector.getBeforeAll(), null, connector.getBeforeAllSourceDep());
		checkScripts("afterAll", "afterAll", connector.getAfterAll(), null, connector.getAfterAllSourceDep());
		if (connector.getTools() != null) {
			connector.getTools().forEach((name, tool) -> {
				if (tool != null) {
					checkScripts(
						"tools." + name,
						"tools." + name,
						tool.getSources(),
						tool.getExecutionOrder(),
						tool.getSourceDep()
					);
				}
			});
		}
	}

	private void checkReserved(final String location, final String name) {
		if (AwkNames.isReserved(name)) {
			add(NONE, location, Rule.RESERVED_NAME, "a name that AWK does not reserve", quote(name));
		}
	}

	private void checkTask(final String monitorType, final String job, final AbstractMonitorTask task) {
		if (task == null) {
			return;
		}
		checkScripts(monitorType, job, task.getSources(), task.getExecutionOrder(), task.getSourceDep());
		final Mapping mapping = task.getMapping();
		if (mapping == null) {
			return;
		}
		final Set<String> texts = new HashSet<>();
		final Set<String> tables = new HashSet<>();
		if (task.getSources() != null) {
			task.getSources().forEach((name, source) -> {
				if (source != null && AwkNames.isNameable(name)) {
					(typeOf.apply(source) == ContentType.TEXT ? texts : tables).add(name);
				}
			});
		}
		final String location = "monitors." + monitorType + "." + job + ".mapping.";
		checkSection(location + "attributes.", false, mapping.getAttributes(), texts, tables);
		checkSection(location + "metrics.", true, mapping.getMetrics(), texts, tables);
		checkSection(location + "conditionalCollection.", false, mapping.getConditionalCollection(), texts, tables);
		checkSection(location + "legacyTextParameters.", false, mapping.getLegacyTextParameters(), texts, tables);
	}

	private void checkSection(
		final String prefix,
		final boolean metrics,
		final Map<String, String> section,
		final Set<String> texts,
		final Set<String> tables
	) {
		if (section == null) {
			return;
		}
		section.forEach((key, value) -> {
			final String location = prefix + key;
			if (value == null) {
				add(NONE, location, Rule.NULL_MAPPING_VALUE, "a text or an expression", "nothing");
			} else if (MappingExpression.isExpression(value)) {
				checkExpression(location, MappingExpression.of(metrics, value), texts, tables);
			}
		});
	}

	private void checkExpression(
		final String location,
		final MappingExpression expression,
		final Set<String> texts,
		final Set<String> tables
	) {
		final String body = expression.getBody();
		if (expression.getKind() == MappingExpression.Kind.INVALID) {
			switch (expression.getProblem()) {
				case REFERENCE -> add(NONE, location, Rule.REFERENCE_IN_EXPRESSION, "no reference", "a reference");
				case ENVIRON -> add(NONE, location, Rule.ENVIRON_IN_EXPRESSION, "no ENVIRON", expression.getError());
				case METRICS_FUNCTION_OUTSIDE_METRICS ->
					add(
						NONE,
						location,
						Rule.MONITOR_FUNCTION_OUTSIDE_METRICS,
						"a value under metrics",
						"a function of the metrics section"
					);
				case NOT_WHOLE_VALUE ->
					add(
						NONE,
						location,
						Rule.STATE_FUNCTION_NOT_WHOLE_VALUE,
						"= " + expression.getFunction() + "(argument)",
						expression.getFunction()
					);
				default -> {
					add(NONE, location, Rule.EXPRESSION_SYNTAX, "one AWK expression", syntaxActual(expression.getProblem()));
					// The compiler message: connector code, never run-time data
					log.debug(
						"Connector {} - The expression at {} does not compile: {}",
						connectorId,
						location,
						expression.getError()
					);
				}
			}
			return;
		}

		for (final String name : expression.getReferencedNames()) {
			if (
				!checkName(NONE, location, name, expression.getArrayNames().contains(name), texts) &&
					!(expression.getArrayNames().contains(name) && fillsArray(body, name))
			) {
				add(
					NONE,
					location,
					Rule.EXPRESSION_UNKNOWN_NAME,
					"a constant, a connector variable or a text source of the job",
					quote(name) + (tables.contains(name) ? ", a table source" : "")
				);
			}
		}
	}

	private static String syntaxActual(final MappingExpression.Problem problem) {
		return switch (problem) {
			case SYNTAX -> "a syntax error";
			case UNKNOWN_FUNCTION -> "an unknown function or a semantic error";
			case ARGUMENTS -> "a wrong number of arguments";
			case SANDBOX -> "a command or a file";
			default -> "a compile error";
		};
	}

	/**
	 * Whether the expression fills the array itself: the array argument of <code>split</code>, <code>patsplit</code>
	 * or a 3-argument <code>match</code>. A text test, outside the string literals.
	 */
	private static boolean fillsArray(final String body, final String name) {
		return Pattern.compile(
			"\\b(?:split|patsplit|match)\\s*\\(.*,\\s*" + Pattern.quote(name) + "\\s*[,)]",
			Pattern.DOTALL
		).matcher(MappingExpression.withoutStringLiterals(body)).find();
	}

	/**
	 * The category of a connector value name: a text source wins over a connector variable, which wins over a
	 * constant.
	 *
	 * @return the category, or <code>null</code> when the name is none of them
	 */
	private String category(final String name, final Set<String> texts) {
		if (texts.contains(name)) {
			return TEXT_SOURCE;
		}
		if (variables.contains(name)) {
			return CONNECTOR_VARIABLE;
		}
		return constants.containsKey(name) ? CONSTANT : null;
	}

	/**
	 * Checks a name used in AWK against the connector values.
	 *
	 * @return whether the name is a connector value
	 */
	private boolean checkName(
		final String sourceKey,
		final String location,
		final String name,
		final boolean array,
		final Set<String> texts
	) {
		final String category = category(name, texts);
		if (category == null) {
			return false;
		}
		if (array) {
			add(sourceKey, location, Rule.NAME_USED_AS_ARRAY, "a scalar", quote(name) + " (" + category + ")");
		} else if (CONSTANT.equals(category) && AwkNames.holdsReference(constants.get(name))) {
			add(sourceKey, location, Rule.CONSTANT_HOLDS_REFERENCE, "a constant without reference", quote(name));
		}
		return true;
	}

	/**
	 * The scripts of one scope (a monitor job, beforeAll, afterAll, a tool): awk sources and awk computes.
	 */
	private void checkScripts(
		final String monitorType,
		final String job,
		final Map<String, Source> sources,
		final Set<String> executionOrder,
		final List<Set<String>> sourceDep
	) {
		if (sources == null || sources.isEmpty()) {
			return;
		}
		final List<Source> order = order(monitorType, job, sources, executionOrder, sourceDep);
		final Set<String> before = new HashSet<>();
		final Set<String> laterOrOwn = new HashSet<>();
		if (order != null) {
			order.forEach(source -> {
				final String name = textSourceName(source);
				if (name != null) {
					laterOrOwn.add(name);
				}
			});
		}
		final List<Source> walk = order == null ? new ArrayList<>(sources.values()) : order;
		for (final Source source : walk) {
			if (source == null) {
				continue;
			}
			if (source instanceof JawkSource jawk) {
				checkScript(source.getKey(), "", jawk.getScript(), jawk.getVariables(), SOURCE_ENGINE, before, laterOrOwn);
			}
			if (source.getComputes() != null) {
				for (int index = 0; index < source.getComputes().size(); index++) {
					final Compute compute = source.getComputes().get(index);
					if (compute instanceof Awk awk) {
						checkScript(
							source.getKey(),
							awk.getType() + " #" + index + " ",
							awk.getScript(),
							awk.getVariables(),
							AwkExecutor.getUtilityEngine(),
							before,
							laterOrOwn
						);
					}
				}
			}
			if (order != null) {
				final String name = textSourceName(source);
				if (name != null) {
					laterOrOwn.remove(name);
					before.add(name);
				}
			}
		}
	}

	/**
	 * The name of a source in AWK when it is a text source: its local name, the last part of its key.
	 */
	private String textSourceName(final Source source) {
		if (source == null || typeOf.apply(source) != ContentType.TEXT) {
			return null;
		}
		return AwkNames.textSourceName(source.getKey());
	}

	/**
	 * The sources of the scope in run order, <code>null</code> when the order cannot be built (the run reports it).
	 */
	private List<Source> order(
		final String monitorType,
		final String job,
		final Map<String, Source> sources,
		final Set<String> executionOrder,
		final List<Set<String>> sourceDep
	) {
		final List<String> orderList;
		if (executionOrder != null && !executionOrder.isEmpty()) {
			orderList = new ArrayList<>(executionOrder);
		} else if (sourceDep != null && !sourceDep.isEmpty()) {
			orderList = sourceDep.stream().filter(set -> set != null).flatMap(Collection::stream).toList();
		} else {
			orderList = null;
		}
		if (orderList != null && (orderList.size() != sources.size() || !orderList.containsAll(sources.keySet()))) {
			return null;
		}
		try {
			return OrderedSources.builder()
				.sources(
					sources,
					orderList,
					null,
					JobInfo.builder().hostname(LOAD).connectorId(connectorId).monitorType(monitorType).jobName(job).build()
				)
				.build()
				.getSources();
		} catch (Exception e) {
			return null;
		}
	}

	private void checkScript(
		final String sourceKey,
		final String prefix,
		final String script,
		final Map<String, ?> scriptVariables,
		final io.jawk.Awk engine,
		final Set<String> before,
		final Set<String> laterOrOwn
	) {
		final AwkProgram program = compile(script, engine);
		if (program == null) {
			return;
		}
		final Set<String> declared = scriptVariables == null ? Set.of() : scriptVariables.keySet();
		final Map<String, Boolean> arrays = program.getGlobalVariableAarrayMap();
		final String location = prefix + "script";
		for (final String name : program.getGlobalVariableOffsetMap().keySet()) {
			if (declared.contains(name)) {
				continue;
			}
			if (
				!checkName(sourceKey, location, name, arrays != null && Boolean.TRUE.equals(arrays.get(name)), before) &&
					laterOrOwn.contains(name)
			) {
				add(sourceKey, location, Rule.TEXT_SOURCE_NOT_YET_RUN, "a text source that ran before", quote(name));
			}
		}
		if (program.getFunctionNameSet() != null) {
			for (final String name : program.getFunctionNameSet()) {
				final String category = category(name, before);
				if (category != null) {
					add(
						sourceKey,
						location,
						Rule.NAME_DEFINED_AS_FUNCTION,
						"no function of this name",
						quote(name) + " (" + category + ")"
					);
				}
			}
		}
		for (final String name : declared) {
			final String category = category(name, before);
			if (category != null) {
				add(
					sourceKey,
					prefix + "variables." + name,
					Rule.NAME_DECLARED_IN_VARIABLES,
					"a name of its own",
					quote(name) + " (" + category + ")"
				);
			}
		}
	}

	/**
	 * The script as the run finds it (the text itself, or its one embedded file), compiled; <code>null</code> when it
	 * cannot be found or does not compile: the run reports it.
	 */
	private AwkProgram compile(final String script, final io.jawk.Awk engine) {
		if (script == null) {
			return null;
		}
		try {
			final Optional<EmbeddedFile> file = FILE_PATTERN.matcher(script).find()
				? EmbeddedFileHelper.findEmbeddedFile(script, connector.getEmbeddedFiles(), LOAD, connectorId)
				: Optional.of(EmbeddedFile.fromString(script));
			return file.isEmpty() ? null : AwkExecutor.compile(file.get().getContentAsString(), engine);
		} catch (Exception e) {
			return null;
		}
	}

	private static String quote(final String name) {
		return "'" + name + "'";
	}

	private void add(
		final String sourceKey,
		final String location,
		final Rule rule,
		final String expected,
		final String actual
	) {
		findings.add(new ContentTypeFinding(connectorId, sourceKey, location, rule, expected, actual));
	}
}
