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
		TEXT_FEEDS_TABLE("write a toTable"),
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
		KEYS_WITH_SIMPLE_MAPPING("remove the keys");

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
			rule.hint() +
			")";
	}
}
