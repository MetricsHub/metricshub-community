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
		/** A <code>;</code>-joined string literal in <code>tables</code>, <code>leftTable</code>, <code>rightTable</code>. */
		STRING_LITERAL_ROWS("write a list of rows"),
		/** <code>${source::x}</code> of a table interpolated in a string field. */
		TABLE_INTERPOLATED("interpolate a text"),
		/** <code>${source::x}</code> of a source the connector does not have. */
		UNRESOLVED_REFERENCE("reference an existing source"),
		/** A deprecated syntax: the <code>json2Csv</code> type, the ignored awk <code>input</code> and <code>inputFormat</code>. */
		DEPRECATED_SYNTAX("remove or rename it"),
		/** A <code>column</code> on an append, prepend, replace or substring that is fed a text. */
		COLUMN_ON_TEXT("remove the column, or write a toTable before"),
		/** No <code>column</code> on an append, prepend, replace or substring that is fed a table. */
		NO_COLUMN_ON_TABLE("set the column, or write a toText before");

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
