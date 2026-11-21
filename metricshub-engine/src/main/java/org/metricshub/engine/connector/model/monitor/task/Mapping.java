package org.metricshub.engine.connector.model.monitor.task;

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

import static com.fasterxml.jackson.annotation.Nulls.SKIP;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.Serializable;
import java.util.Map;
import java.util.TreeMap;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.metricshub.engine.connector.deserializer.custom.CaseInsensitiveTreeMapDeserializer;

/**
 * A class representing the mapping configuration for a monitor task. It includes mappings for source attributes,
 * metrics, conditional collections, and legacy text parameters. The mappings are case-insensitive.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Mapping implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * The type of a mapping, read from <code>type</code> in YAML.
	 */
	public enum Type {
		/** The default: one monitor instance per row of the table that the mapping <code>source</code> references. */
		TABLE,
		/**
		 * No <code>source</code>: exactly one monitor instance of the monitor type for the connector (a singleton), whose
		 * values are constants and references to text sources. It has no key attribute: the engine identifies it by the
		 * connector and the monitor type, and its attributes (<code>id</code> included) are plain attributes.
		 */
		SIMPLE
	}

	/**
	 * The type of the mapping, {@link Type#TABLE} by default.
	 */
	@Default
	@JsonSetter(nulls = SKIP)
	private Type type = Type.TABLE;

	/**
	 * The reference of the table source a {@link Type#TABLE} mapping reads, one monitor instance per row. A
	 * {@link Type#SIMPLE} mapping has none.
	 */
	private String source;

	/**
	 * A case-insensitive map of attributes associated with the source.
	 */
	@Default
	@JsonDeserialize(using = CaseInsensitiveTreeMapDeserializer.class)
	@JsonSetter(nulls = SKIP)
	private Map<String, String> attributes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

	/**
	 * A case-insensitive map of metrics associated with the source.
	 */
	@Default
	@JsonDeserialize(using = CaseInsensitiveTreeMapDeserializer.class)
	@JsonSetter(nulls = SKIP)
	private Map<String, String> metrics = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

	/**
	 * A case-insensitive map of conditional collections associated with the source.
	 */
	@Default
	@JsonDeserialize(using = CaseInsensitiveTreeMapDeserializer.class)
	@JsonSetter(nulls = SKIP)
	private Map<String, String> conditionalCollection = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

	/**
	 * A case-insensitive map of legacy text parameters associated with the source.
	 */
	@Default
	@JsonDeserialize(using = CaseInsensitiveTreeMapDeserializer.class)
	@JsonSetter(nulls = SKIP)
	private Map<String, String> legacyTextParameters = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
}
