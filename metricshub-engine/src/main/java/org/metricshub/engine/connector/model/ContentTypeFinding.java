package org.metricshub.engine.connector.model;

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

import java.io.Serializable;

/**
 * One finding of the load-time content-type check ({@link org.metricshub.engine.connector.update.ContentTypeCheck}):
 * a rule of the strict content types (#1560) that the connector breaks. Step 1 only reports it, step 3 rejects it.
 *
 * @param connectorId The identifier of the connector
 * @param sourceKey   The key of the source holding the finding, or the reference a mapping or a tool reads
 * @param location    Where: a compute (<code>json2Table #2</code>), a field (<code>tables[1]</code>,
 *                    <code>header</code>), a mapping or a tool result, followed by <code>-&gt; reference</code> when
 *                    a source reference is at fault
 * @param rule        The rule
 * @param expected    What the rule expects, e.g. <code>TABLE</code>
 * @param actual      What the connector has, e.g. <code>TEXT</code>
 */
public record ContentTypeFinding(
	String connectorId,
	String sourceKey,
	String location,
	Rule rule,
	String expected,
	String actual
) implements Serializable {
	/**
	 * The rules of the strict content types.
	 */
	public enum Rule {
		/** A text feeds a table consumer: a join, a union member, a mapping, a tool with columns, a column compute. */
		TEXT_FEEDS_TABLE("write a toTable, or output: table on an awk or snmpGet source"),
		/** A table feeds a text consumer: a text compute, a <code>textUnion</code> member. */
		TABLE_FEEDS_TEXT("write a toText"),
		/** A <code>tableUnion</code> whose members are all texts. */
		TABLE_UNION_OF_TEXTS("use textUnion"),
		/** A <code>tableUnion</code> of texts and tables. */
		MIXED_UNION("convert the members to one type"),
		/**
		 * A string in <code>tables</code>, <code>leftTable</code> or <code>rightTable</code> that is not exactly one source
		 * reference.
		 */
		STRING_LITERAL_ROWS("write a list of rows, or exactly one source reference per table"),
		/** Anything but exactly one source reference where the engine reads a source: a mapping, a tool result, a copy. */
		NOT_A_REFERENCE("reference a source, a static source holds a hard-coded table"),
		/** <code>${source::x}</code> of a table interpolated in a string field. */
		TABLE_INTERPOLATED("interpolate a text"),
		/** <code>${source::x}</code> of a source the connector does not have. */
		UNRESOLVED_REFERENCE("reference an existing source"),
		/**
		 * A deprecated syntax: the <code>json2Csv</code> type, the ignored <code>json2Table</code> <code>parsingMode</code>,
		 * the removed <code>json2Table</code> <code>separator</code>, awk <code>input</code> and <code>inputFormat</code>,
		 * and the removed awk <code>keep</code>, <code>exclude</code>, <code>separators</code> and
		 * <code>selectColumns</code>.
		 */
		DEPRECATED_SYNTAX("remove or rename it"),
		/** A <code>column</code> on an append, prepend, replace or substring that is fed a text. */
		COLUMN_ON_TEXT("remove the column, or write a toTable before"),
		/** No <code>column</code> on an append, prepend, replace or substring that is fed a table. */
		NO_COLUMN_ON_TABLE("set the column, or write a toText before"),
		/** A mapping of the default type <code>table</code> without a <code>source</code>. */
		TABLE_MAPPING_WITHOUT_SOURCE("set the source, or write type: simple"),
		/** A <code>source</code> in a <code>type: simple</code> mapping, which reads no table. */
		SOURCE_IN_SIMPLE_MAPPING("remove the source, or the type: simple"),
		/** A column (<code>$1</code>) in a value of a simple mapping: it has no row. */
		COLUMN_IN_SIMPLE_MAPPING("write a table mapping with a source, or reference a text source"),
		/** A monitor job whose mappings do not all have the same type. */
		MIXED_MAPPING_TYPES("give every mapping of the job the same type"),
		/** <code>keys</code> on a job with a simple mapping: its one instance has no key attribute. */
		KEYS_WITH_SIMPLE_MAPPING("remove the keys"),
		/** A mapping value that is a YAML null. */
		NULL_MAPPING_VALUE("write a text, or = and an AWK expression; or remove the key"),
		/** A mapping expression (<code>= ...</code>) that does not compile. */
		EXPRESSION_SYNTAX(
			"a text is written without =, a text that starts with = is written = \"=text\"; function names are case-sensitive"
		),
		/** A function that needs the monitor in a mapping expression outside <code>metrics</code>. */
		MONITOR_FUNCTION_OUTSIDE_METRICS(
			"rate, fakeCounter, accumulate, computePowerShareRatio, legacyLedStatus and legacyPowerSupplyUtilization need the monitor: write them under metrics"
		),
		/** fakeCounter, accumulate or computePowerShareRatio that is not the whole mapping expression. */
		STATE_FUNCTION_NOT_WHOLE_VALUE(
			"write = fakeCounter(...), = accumulate(...) or = computePowerShareRatio(...) alone, the arithmetic inside the argument"
		),
		/** A name in a mapping expression that is no constant, connector variable or text source of the job. */
		EXPRESSION_UNKNOWN_NAME(
			"write a text in quotes, or the name of a constant, a connector variable or a text source of the job"
		),
		/** A reference (<code>${source::</code>, <code>${constant::</code>...) in a mapping expression. */
		REFERENCE_IN_EXPRESSION(
			"write the name of the constant, the connector variable or the text source, without ${...}"
		),
		/** <code>ENVIRON</code> or <code>SYMTAB</code> in a mapping expression. */
		ENVIRON_IN_EXPRESSION("the environment of the agent is not readable"),
		/** The name of a constant that holds a reference, used in AWK. */
		CONSTANT_HOLDS_REFERENCE("use ${constant::NAME} in a text: a constant that holds a reference has no AWK name"),
		/** A constant or a connector variable named like an AWK keyword, built-in function or special variable. */
		RESERVED_NAME("rename it: AWK keywords, built-in functions and special variables are reserved"),
		/** A constant, a connector variable or a text source used as an array. */
		NAME_USED_AS_ARRAY("rename the array: this name is a text variable"),
		/** A function of a script named like a constant, a connector variable or a text source. */
		NAME_DEFINED_AS_FUNCTION("rename the function"),
		/** A <code>variables:</code> entry of a script named like a constant, a connector variable or a text source. */
		NAME_DECLARED_IN_VARIABLES("remove the entry: the script already has this name; or rename it"),
		/** A script that reads a text source of its scope that runs after it, or its own source. */
		TEXT_SOURCE_NOT_YET_RUN("run the source before this one (executionOrder or a reference), or rename the variable");

		private final String hint;

		Rule(final String hint) {
			this.hint = hint;
		}

		/**
		 * @return how to comply, constant text
		 */
		public String hint() {
			return hint;
		}
	}

	/**
	 * How to comply: the hint of the rule, and for a text read as the source of a mapping, also the simple mapping
	 * (#342), which reads a text in its values.
	 *
	 * @return constant text
	 */
	public String hint() {
		return rule == Rule.TEXT_FEEDS_TABLE && location.endsWith(".mapping")
			? "write a toTable, output: table on an awk or snmpGet source, or type: simple"
			: rule.hint();
	}

	@Override
	public String toString() {
		return rule +
			" " +
			sourceKey +
			" at " +
			location +
			": expected " +
			expected +
			", actual " +
			actual +
			" (" +
			hint() +
			")";
	}
}
