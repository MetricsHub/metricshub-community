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
import static org.metricshub.engine.common.helpers.MetricsHubConstants.SOURCE_REF_PATTERN;
import static org.metricshub.engine.common.helpers.StringHelper.addNonNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.jawk.AwkProgram;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.metricshub.engine.connector.deserializer.custom.AwkVariablesDeserializer;
import org.metricshub.engine.connector.model.common.ContentType;
import org.metricshub.engine.connector.model.common.ExecuteForEachEntryOf;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.strategy.source.ISourceProcessor;
import org.metricshub.engine.strategy.source.SourceTable;

/**
 * Represents a source that executes an jawk script to retrieve data.
 * <p>
 * The <code>inputFormat</code> property, once how the script read its input, is deprecated and ignored: an input
 * that is a single source reference is read as that source is (#1559). A connector declaring it still loads, its
 * value is dropped and the content-type check reports it (<code>inputFormatDeclared</code>).
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
	 * The input on which to execute the JAWK task: a text, or a single source reference, which the script reads as
	 * that source is at run time (a table structured, a text the traditional way).
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
	 * A value is a scalar ({@link String}), a list ({@link List}) or a map ({@link Map}), and lists and maps nest (see
	 * {@link AwkVariablesDeserializer}). A list is exposed to the script as an AWK array indexed from 0, a map as an AWK
	 * array indexed by its keys. A scalar referencing a source, such as
	 * <code>${source::monitors.disk.discovery.sources.raw}</code>, is exposed as an AWK array holding that source's
	 * table, any other scalar as a scalar (see {@link org.metricshub.engine.awk.AwkVariableHelper}).
	 */
	@JsonSetter(nulls = SKIP)
	private Map<String, Object> variables = new LinkedHashMap<>();

	/**
	 * What the script produces: {@link ContentType#TEXT} (the printed lines, re-parsed into a table) or
	 * {@link ContentType#TABLE} (each <code>print</code> is one row, the text of <code>printf</code> is split into rows
	* on <code>ORS</code> and into cells on <code>OFS</code>).
	 * Default text.
	 */
	@JsonSetter(nulls = SKIP)
	private ContentType output = ContentType.TEXT;

	/**
	 * The compiled script. The script is compiled the first time it runs, and the program is kept here, in the
	 * connector: every run works on a copy of the source, and the copies share this holder, so the program compiled
	 * by a run is the one the next runs take. Never read from nor written to YAML or JSON, not part of the equality.
	 */
	@JsonIgnore
	@EqualsAndHashCode.Exclude
	private AtomicReference<AwkProgram> program = new AtomicReference<>();

	/**
	 * The compiled script.
	 *
	 * @return the program, null as long as the script has not run (or does not compile)
	 */
	@JsonIgnore
	public AwkProgram getProgram() {
		return program.get();
	}

	/**
	 * Replaces the script. The program compiled from the former script no longer applies: this source gets a
	 * holder of its own, and the source it was copied from keeps its script and its program.
	 *
	 * @param script The new script
	 */
	public void setScript(@NonNull final String script) {
		this.script = script;
		this.program = new AtomicReference<>();
	}

	/**
	 * Keeps the compiled script, for this source and for every copy of it.
	 *
	 * @param compiled The program compiled from the script
	 */
	public void setProgram(final AwkProgram compiled) {
		program.set(compiled);
	}

	/**
	 * Whether the connector writes the deprecated, ignored <code>inputFormat</code> property, which the content-type
	 * check reports. Never serialized, not part of the equality, not copied.
	 */
	@JsonIgnore
	@EqualsAndHashCode.Exclude
	private boolean inputFormatDeclared;

	/**
	 * Records that the connector writes the deprecated <code>inputFormat</code> property, whose value is ignored.
	 *
	 * @param inputFormat The ignored value
	 */
	@JsonSetter("inputFormat")
	private void declareInputFormat(final Object inputFormat) {
		inputFormatDeclared = true;
	}

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
	 * @param output                What the script produces, null for text.
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
		@JsonProperty(value = "variables") @JsonDeserialize(
			using = AwkVariablesDeserializer.class
		) final Map<String, ?> variables,
		@JsonProperty(value = "output") final ContentType output
	) {
		super(type, computes, forceSerialization, key, executeForEachEntryOf);
		this.script = script;
		this.input = input;
		this.separators = separators;
		this.variables = variables == null ? new LinkedHashMap<>() : AwkVariablesDeserializer.copy(variables);
		this.output = output == null ? ContentType.TEXT : output;
	}

	@Override
	public Source copy() {
		final JawkSource copy = JawkSource.builder()
			.type(type)
			.key(key)
			.forceSerialization(forceSerialization)
			.computes(getComputes() != null ? new ArrayList<>(getComputes()) : null)
			.executeForEachEntryOf(executeForEachEntryOf != null ? executeForEachEntryOf.copy() : null)
			.script(script)
			.input(input)
			.separators(separators)
			.variables(variables)
			.output(output)
			.build();
		// The copy shares the holder: the program it compiles is kept in the connector
		copy.program = program;
		return copy;
	}

	@Override
	public void update(UnaryOperator<String> updater) {
		// The script is deliberately left out: it is code, not data. It is normally a ${file::...} reference already
		// resolved at parse time, and running the reference substitutions over a script body would rewrite AWK syntax.
		// An input that is a single source reference is resolved when the script runs, to the source as it is: the
		// source updater does not expand it into the source's text (see SourceUpdaterProcessor), the other
		// substitutions apply
		input = updater.apply(input);
		separators = updater.apply(separators);
		// The variables are deliberately not updated here: every reference they hold, source references included,
		// is resolved in one pass by AwkVariableHelper just before the script runs
	}

	@Override
	public SourceTable accept(ISourceProcessor sourceProcessor) {
		return sourceProcessor.process(this);
	}

	/**
	 * Whether the value is a single source reference, such as <code>${source::monitors.disk.discovery.sources.raw}</code>:
	 * an <code>input</code> the script reads as that source is at run time, a table structured, a text the
	 * traditional way.
	 *
	 * @param value The value, null meaning none
	 * @return whether the value is a single source reference
	 */
	public static boolean isSourceReference(final String value) {
		return value != null && SOURCE_REF_PATTERN.matcher(value.trim()).matches();
	}

	@Override
	public ContentType getOutputContentType() {
		return output == null ? ContentType.TEXT : output;
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
