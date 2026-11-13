package org.metricshub.engine.connector.model.monitor.task.source.compute;

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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.jawk.AwkProgram;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.UnaryOperator;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.metricshub.engine.connector.deserializer.custom.AwkVariablesDeserializer;
import org.metricshub.engine.connector.model.common.ContentType;
import org.metricshub.engine.strategy.source.compute.IComputeProcessor;

/**
 * Represents an Awk computation task for monitoring.
 * <p>
 * The <code>input</code> property, once how the script read the source result, is deprecated and ignored: the
 * script reads the source result as it is (#1559). A connector declaring it still loads, its value is dropped and
 * the content-type check reports it (<code>inputDeclared</code>).
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Awk extends Compute {

	private static final long serialVersionUID = 1L;

	/**
	 * The AWK script to be executed for the computation task.
	 */
	@NonNull
	@JsonSetter(nulls = FAIL)
	private String script;

	/**
	 * The exclude parameter for the AWK task.
	 */
	private String exclude;
	/**
	 * The keep parameter for the AWK task.
	 */
	private String keep;
	/**
	 * The separators parameter for the AWK task.
	 */
	private String separators;
	/**
	 * The selectColumns parameter for the AWK task.
	 */
	private String selectColumns;

	/**
	 * The variables to expose to the AWK script, indexed by variable name.
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
	 * The script compiled when the connector loads (see
	 * {@link org.metricshub.engine.connector.update.AwkCompilationUpdate}), null when the compute was built without a
	 * connector or when the script does not compile: it is then compiled when it runs. Never read from nor written to
	 * YAML or JSON, not part of the equality.
	 */
	@JsonIgnore
	@EqualsAndHashCode.Exclude
	private AwkProgram program;

	/**
	 * Whether the connector writes the deprecated, ignored <code>input</code> property, which the content-type check
	 * reports. Never serialized, not part of the equality, not copied.
	 */
	@JsonIgnore
	@EqualsAndHashCode.Exclude
	private boolean inputDeclared;

	/**
	 * Records that the connector writes the deprecated <code>input</code> property, whose value is ignored.
	 *
	 * @param input The ignored value
	 */
	@JsonSetter("input")
	private void declareInput(final Object input) {
		inputDeclared = true;
	}

	/**
	 * Construct a new instance of Awk.
	 *
	 * @param type          The type of the computation task.
	 * @param script        The AWK script to be executed.
	 * @param exclude       The exclude parameter for the AWK task.
	 * @param keep          The keep parameter for the AWK task.
	 * @param separators    The separators parameter for the AWK task.
	 * @param selectColumns The selectColumns parameter for the AWK task.
	 * @param variables     The variables to expose to the AWK script.
	 * @param output        What the script produces, null for text.
	 */
	@Builder
	@JsonCreator
	public Awk(
		@JsonProperty("type") String type,
		@JsonProperty(value = "script", required = true) @NonNull String script,
		@JsonProperty("exclude") String exclude,
		@JsonProperty("keep") String keep,
		@JsonProperty("separators") String separators,
		@JsonProperty("selectColumns") String selectColumns,
		@JsonProperty("variables") @JsonDeserialize(using = AwkVariablesDeserializer.class) Map<String, ?> variables,
		@JsonProperty("output") ContentType output
	) {
		super(type);
		this.script = script;
		this.exclude = exclude;
		this.keep = keep;
		this.separators = separators;
		this.selectColumns = selectColumns;
		this.variables = variables == null ? new LinkedHashMap<>() : AwkVariablesDeserializer.copy(variables);
		this.output = output == null ? ContentType.TEXT : output;
	}

	/**
	 * None: the script reads the source result as it is at run time, a text the traditional way (the lines are the
	 * records, split on <code>FS</code>), a table structured (each row is a record whose cells are
	 * <code>$1..$NF</code>, nothing is serialized). To read a table as text, write a <code>toText</code> before.
	 *
	 * @return null
	 */
	@Override
	public ContentType getInputContentType() {
		return null;
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
		addNonNull(stringJoiner, "- exclude=", exclude);
		addNonNull(stringJoiner, "- keep=", keep);
		addNonNull(stringJoiner, "- separators=", separators);
		addNonNull(stringJoiner, "- selectColumns=", selectColumns);
		addNonNull(stringJoiner, "- variables=", variables != null && variables.isEmpty() ? null : variables);
		addNonNull(stringJoiner, "- output=", output == ContentType.TABLE ? output : null);

		return stringJoiner.toString();
	}

	@Override
	public Awk copy() {
		final Awk copy = Awk.builder()
			.type(type)
			.script(script)
			.exclude(exclude)
			.keep(keep)
			.separators(separators)
			.selectColumns(selectColumns)
			.variables(variables)
			.output(output)
			.build();
		copy.setProgram(program);
		return copy;
	}

	@Override
	public void update(UnaryOperator<String> updater) {
		// The script is deliberately left out: it is code, compiled when the connector loads, and no reference is
		// replaced in it. A script receives a value (${param::days}, ${attribute::id}, a source) through its variables
		exclude = updater.apply(exclude);
		keep = updater.apply(keep);
		separators = updater.apply(separators);
		selectColumns = updater.apply(selectColumns);
		// The variables are deliberately not updated here: every reference they hold, source references included,
		// is resolved in one pass by AwkVariableHelper just before the script runs
	}

	@Override
	public void accept(IComputeProcessor computeProcessor) {
		computeProcessor.process(this);
	}
}
