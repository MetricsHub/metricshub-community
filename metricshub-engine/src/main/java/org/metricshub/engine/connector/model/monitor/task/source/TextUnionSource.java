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
import static org.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;
import static org.metricshub.engine.common.helpers.StringHelper.addNonNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.UnaryOperator;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.metricshub.engine.connector.deserializer.custom.TextsDeserializer;
import org.metricshub.engine.connector.model.common.ContentType;
import org.metricshub.engine.connector.model.common.ExecuteForEachEntryOf;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.strategy.source.ISourceProcessor;
import org.metricshub.engine.strategy.source.SourceTable;

/**
 * Represents a source that concatenates the texts of other sources: each non-empty member, its CRLF converted to LF,
 * starts on a new line, and the blank lines of the members are kept. Its result is a text.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TextUnionSource extends Source {

	private static final long serialVersionUID = 1L;

	/**
	 * The references of the text sources to concatenate, in order.
	 */
	@NonNull
	@JsonSetter(nulls = FAIL)
	@JsonDeserialize(using = TextsDeserializer.class)
	private List<String> texts = new ArrayList<>();

	/**
	 * Builder for creating instances of {@code TextUnionSource}.
	 *
	 * @param type                  The type of the source.
	 * @param computes              List of computations to be applied to the source.
	 * @param forceSerialization    Flag indicating whether to force serialization.
	 * @param texts                 The references of the text sources to concatenate.
	 * @param key                   The key associated with the source.
	 * @param executeForEachEntryOf The execution context for each entry of the source.
	 */
	@Builder
	@JsonCreator
	public TextUnionSource(
		@JsonProperty("type") String type,
		@JsonProperty("computes") List<Compute> computes,
		@JsonProperty("forceSerialization") boolean forceSerialization,
		@JsonProperty(value = "texts", required = true) @NonNull List<String> texts,
		@JsonProperty("key") String key,
		@JsonProperty("executeForEachEntryOf") ExecuteForEachEntryOf executeForEachEntryOf
	) {
		super(type, computes, forceSerialization, key, executeForEachEntryOf);
		this.texts = texts;
	}

	@Override
	public TextUnionSource copy() {
		return TextUnionSource.builder()
			.type(type)
			.key(key)
			.forceSerialization(forceSerialization)
			.computes(getComputes() != null ? new ArrayList<>(getComputes()) : null)
			.executeForEachEntryOf(executeForEachEntryOf != null ? executeForEachEntryOf.copy() : null)
			.texts(new ArrayList<>(texts))
			.build();
	}

	@Override
	public void update(UnaryOperator<String> updater) {
		// The members are source references, resolved when the union runs
	}

	@Override
	public ContentType getOutputContentType() {
		return ContentType.TEXT;
	}

	@Override
	public String toString() {
		final StringJoiner stringJoiner = new StringJoiner(NEW_LINE);

		stringJoiner.add(super.toString());

		addNonNull(stringJoiner, "- texts=", texts);

		return stringJoiner.toString();
	}

	@Override
	public SourceTable accept(final ISourceProcessor sourceProcessor) {
		return sourceProcessor.process(this);
	}
}
