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

import static com.fasterxml.jackson.annotation.Nulls.SKIP;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.SEMICOLON;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.TABLE_SEP;
import static org.metricshub.engine.common.helpers.StringHelper.addNonNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.metricshub.engine.connector.deserializer.custom.StringOrListDeserializer;
import org.metricshub.engine.connector.model.common.ContentType;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.strategy.source.compute.IComputeProcessor;

/**
 * The <code>json2Table</code> compute: one row per element of the entry key, one cell per property. The type
 * <code>json2Csv</code> is a deprecated alias with the same behavior; <code>getType()</code> keeps the name written in
 * the connector, which the findings of the content-type check name.
 * <p>
 * The <code>parsingMode</code> property, once how jflat read the document, is deprecated and ignored: a document of
 * {@link org.metricshub.engine.client.ClientsExecutor#JSON_EVENTS_MIN_LENGTH} characters or more is read as a stream of
 * events, a smaller one as a tree (#1593). A connector declaring it still deserializes, its value dropped, and the
 * content-type check rejects it (<code>parsingModeDeclared</code>).
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Json2Csv extends Compute {

	private static final long serialVersionUID = 1L;

	/**
	 * The entry key for Json2Csv computation.
	 */
	@JsonSetter(nulls = SKIP)
	private String entryKey = "/";

	/**
	 * The properties, one per column after the entry identifier. Written in the connector as a YAML list, each item
	 * one property, or as a string (legacy form) split on <code>;</code> when the compute is built, one trailing
	 * empty item dropped. Null when the connector sets none.
	 */
	private List<String> properties;

	/**
	 * Deprecated, ignored: the compute builds its rows from the JSON values, with one cell per property, so no
	 * separator splits them. Still accepted so that the connectors setting it load.
	 */
	private String separator;

	/**
	 * Whether the connector writes the deprecated, ignored <code>parsingMode</code> property, which the content-type
	 * check reports. Never serialized, not part of the equality, not copied.
	 */
	@JsonIgnore
	@EqualsAndHashCode.Exclude
	private boolean parsingModeDeclared;

	/**
	 * Records that the connector writes the deprecated <code>parsingMode</code> property, whose value is ignored.
	 *
	 * @param parsingMode The ignored value
	 */
	@JsonSetter("parsingMode")
	private void declareParsingMode(final Object parsingMode) {
		parsingModeDeclared = true;
	}

	/**
	 * Construct a new instance of Json2Csv.
	 *
	 * @param type        The type of the computation task.
	 * @param entryKey    The entry key for Json2Csv computation.
	 * @param properties  The properties: a {@link List} of strings, or a {@link String} split on <code>;</code>
	 *                    (legacy form).
	 * @param separator   Deprecated, ignored by the compute; still accepted so that the connectors setting it load.
	 */
	@Builder
	@JsonCreator
	public Json2Csv(
		@JsonProperty("type") String type,
		@JsonProperty("entryKey") String entryKey,
		@JsonProperty("properties") @JsonDeserialize(using = StringOrListDeserializer.Properties.class) Object properties,
		@JsonProperty("separator") String separator
	) {
		super(type);
		this.entryKey = entryKey == null ? "/" : entryKey;
		if (properties instanceof List<?> items) {
			this.properties = items.stream().map(String.class::cast).collect(Collectors.toCollection(ArrayList::new));
		} else if (properties != null) {
			this.properties = SourceTable.lineToList((String) properties, SEMICOLON);
		}
		this.separator = separator == null ? TABLE_SEP : separator;
	}

	@Override
	public ContentType getInputContentType() {
		return ContentType.TEXT;
	}

	@Override
	public ContentType getOutputContentType() {
		return ContentType.TABLE;
	}

	@Override
	public String toString() {
		final StringJoiner stringJoiner = new StringJoiner(NEW_LINE);

		stringJoiner.add(super.toString());

		addNonNull(stringJoiner, "- entryKey=", entryKey);
		addNonNull(stringJoiner, "- properties=", properties);
		addNonNull(stringJoiner, "- separator=", separator);

		return stringJoiner.toString();
	}

	@Override
	public Json2Csv copy() {
		return Json2Csv.builder().type(type).entryKey(entryKey).properties(properties).separator(separator).build();
	}

	@Override
	public void update(UnaryOperator<String> updater) {
		entryKey = updater.apply(entryKey);
		separator = updater.apply(separator);
		if (properties != null) {
			properties.replaceAll(updater);
		}
	}

	@Override
	public void accept(IComputeProcessor computeProcessor) {
		computeProcessor.process(this);
	}
}
