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
 * One finding of the content-type desugaring step ({@link org.metricshub.engine.connector.update.ContentTypeDesugaring})
 * on a source of a connector: an inserted conversion, or a legacy idiom that stage 2 of the content-type migration
 * rewrites. The list of entries of a connector is its worklist.
 *
 * @param sourceKey The key of the source, e.g. <code>${source::monitors.disk.discovery.sources.raw}</code>
 * @param kind      What was found
 * @param detail    Where and what, e.g. the compute type and index, the value holding the separator
 */
public record ContentTypeReportEntry(String sourceKey, Kind kind, String detail) implements Serializable {
	/**
	 * The kinds of findings.
	 */
	public enum Kind {
		/** A <code>toText</code> was inserted before an <code>awk</code> compute fed by a table. */
		TO_TEXT_INSERTED,
		/** A <code>toTable</code> was inserted before a table compute fed by text. */
		TO_TABLE_INSERTED,
		/** A <code>toTable</code> was appended to a chain ending in text from a legacy text producer. */
		TO_TABLE_APPENDED,
		/**
		 * A table compute follows a pure text producer (<code>http</code>, <code>file</code>, <code>ipmi</code>):
		 * it reads no rows and gives an empty table. No conversion is inserted, so that it stays that way.
		 */
		TABLE_COMPUTE_ON_TEXT_SOURCE,
		/**
		 * A member of a table union ends in a text that is not converted (a pure text producer, an explicit
		 * <code>toText</code>): it brings no rows. No conversion is inserted.
		 */
		TEXT_MEMBER_OF_TABLE_UNION,
		/**
		 * A text compute that parses a text (<code>json2Csv</code>, <code>xml2Csv</code>) follows a table producer:
		 * it finds nothing to parse and leaves the table unchanged. No conversion is inserted.
		 */
		TEXT_COMPUTE_ON_TABLE_SOURCE,
		/** An <code>awk</code> compute is fed a table (through the inserted <code>toText</code>). */
		AWK_ON_TABLE,
		/** A <code>tableJoin</code> declares a <code>defaultRightLine</code> as a <code>;</code> string. */
		DEFAULT_RIGHT_LINE,
		/** A <code>static</code> source declares its table as a <code>;</code> string. */
		STATIC_CSV
	}

	@Override
	public String toString() {
		return sourceKey + " [" + kind + "] " + detail;
	}
}
