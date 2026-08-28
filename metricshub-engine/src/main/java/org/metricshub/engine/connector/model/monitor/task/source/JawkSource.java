package org.metricshub.engine.connector.model.monitor.task.source;

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

import static com.fasterxml.jackson.annotation.Nulls.FAIL;
import static com.fasterxml.jackson.annotation.Nulls.SKIP;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;
import static org.metricshub.engine.common.helpers.StringHelper.addNonNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.UnaryOperator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.metricshub.engine.connector.model.common.ExecuteForEachEntryOf;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.strategy.source.ISourceProcessor;
import org.metricshub.engine.strategy.source.SourceTable;

/**
 * Represents a source that executes an jawk script to retrieve data.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class JawkSource extends Source {

	private static final long serialVersionUID = 1L;

	/**
	 * The JAWK script to be executed for the computation task.
	 */
	@NonNull
	@JsonSetter(nulls = FAIL)
	private String script;

	/**
	 * The input on which to execute the JAWK task.
	 */
	@JsonSetter(nulls = SKIP)
	private String input;

	/**
	 * The separators parameter for the JAWK task.
	 */
	private String separators;

	/**
	 * The variables to expose to the JAWK script, indexed by variable name.
	 * <p>
	 * A value referencing a source, such as <code>${source::monitors.disk.discovery.sources.raw}</code>, is exposed to
	 * the script as an AWK array holding that source's table. Any other value is exposed as a scalar.
	 */
	@JsonSetter(nulls = SKIP)
	private Map<String, String> variables = new LinkedHashMap<>();

	/**
	 * Builder for creating instances of {@code JawkSource}.
	 *
	 * @param type                  The type of the source.
	 * @param computes              List of computations to be applied to the source.
	 * @param forceSerialization    Flag indicating whether to force serialization.
	 * @param key                   The key associated with the source.
	 * @param executeForEachEntryOf The execution context for each entry of the source.
	 * @param script                The script to execute.
	 * @param input                 The input on which to execute the JAWK task.
	 * @param separators            The separators parameter for the JAWK task.
	 * @param variables             The variables to expose to the JAWK script.
	 */
	@Builder
	@JsonCreator
	public JawkSource(
		@JsonProperty(value = "type") String type,
		@JsonProperty(value = "computes") List<Compute> computes,
		@JsonProperty(value = "forceSerialization") boolean forceSerialization,
		@JsonProperty(value = "key") String key,
		@JsonProperty(value = "executeForEachEntryOf") ExecuteForEachEntryOf executeForEachEntryOf,
		@JsonProperty(value = "script") String script,
		@JsonProperty(value = "input") final String input,
		@JsonProperty(value = "separators") final String separators,
		@JsonProperty(value = "variables") final Map<String, String> variables
	) {
		super(type, computes, forceSerialization, key, executeForEachEntryOf);
		this.script = script;
		this.input = input;
		this.separators = separators;
		this.variables = variables == null ? new LinkedHashMap<>() : variables;
	}

	@Override
	public Source copy() {
		return JawkSource.builder()
			.type(type)
			.key(key)
			.forceSerialization(forceSerialization)
			.computes(getComputes() != null ? new ArrayList<>(getComputes()) : null)
			.executeForEachEntryOf(executeForEachEntryOf != null ? executeForEachEntryOf.copy() : null)
			.script(script)
			.input(input)
			.separators(separators)
			.variables(variables != null ? new LinkedHashMap<>(variables) : null)
			.build();
	}

	@Override
	public void update(UnaryOperator<String> updater) {
		// The script is deliberately left out: it is code, not data. It is normally a ${file::...} reference already
		// resolved at parse time, and running the reference substitutions over a script body would rewrite AWK syntax.
		input = updater.apply(input);
		separators = updater.apply(separators);
		// The variables are deliberately not updated here: every reference they hold, source references included,
		// is resolved in one pass by AwkVariableHelper just before the script runs
	}

	@Override
	public SourceTable accept(ISourceProcessor sourceProcessor) {
		return sourceProcessor.process(this);
	}

	@Override
	public String toString() {
		final StringJoiner stringJoiner = new StringJoiner(NEW_LINE);

		stringJoiner.add(super.toString());

		addNonNull(stringJoiner, "- script=", script);
		addNonNull(stringJoiner, "- input=", input);
		addNonNull(stringJoiner, "- separators=", separators);
		addNonNull(stringJoiner, "- variables=", variables != null && variables.isEmpty() ? null : variables);

		return stringJoiner.toString();
	}
}
