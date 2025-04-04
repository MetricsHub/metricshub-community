package org.metricshub.engine.connector.model.monitor.task.source.compute;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2024 Sentry Software
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
import java.util.StringJoiner;
import java.util.function.UnaryOperator;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.metricshub.engine.connector.deserializer.custom.TranslationTableDeserializer;
import org.metricshub.engine.connector.model.common.ITranslationTable;
import org.metricshub.engine.strategy.source.compute.IComputeProcessor;

/**
 * Represents an ArrayTranslate computation task for monitoring.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ArrayTranslate extends Compute {

	private static final long serialVersionUID = 1L;

	/**
	 * The column index used in the ArrayTranslate computation.
	 */
	@NonNull
	@JsonSetter(nulls = FAIL)
	private Integer column;

	/**
	 * The translation table for mapping values in the specified column.
	 */
	@NonNull
	@JsonSetter(nulls = FAIL)
	@JsonDeserialize(using = TranslationTableDeserializer.class)
	private ITranslationTable translationTable;

	/**
	 * The array separator used in the computation.
	 */
	private String arraySeparator;
	/**
	 * The result separator used in the computation.
	 */
	private String resultSeparator;

	/**
	 * Construct a new instance of ArrayTranslate.
	 *
	 * @param type              The type of the computation task.
	 * @param column            The column index used in the computation.
	 * @param translationTable  The translation table for mapping values.
	 * @param arraySeparator    The array separator used in the computation.
	 * @param resultSeparator   The result separator used in the computation.
	 */
	@Builder
	@JsonCreator
	public ArrayTranslate(
		@JsonProperty("type") String type,
		@JsonProperty(value = "column", required = true) @NonNull Integer column,
		@JsonProperty(value = "translationTable", required = true) @NonNull ITranslationTable translationTable,
		@JsonProperty("arraySeparator") String arraySeparator,
		@JsonProperty("resultSeparator") String resultSeparator
	) {
		super(type);
		this.column = column;
		this.translationTable = translationTable;
		this.arraySeparator = arraySeparator;
		this.resultSeparator = resultSeparator;
	}

	@Override
	public String toString() {
		final StringJoiner stringJoiner = new StringJoiner(NEW_LINE);

		stringJoiner.add(super.toString());

		addNonNull(stringJoiner, "- column=", column);
		addNonNull(stringJoiner, "- translationTable=", translationTable);
		addNonNull(stringJoiner, "- arraySeparator=", arraySeparator);
		addNonNull(stringJoiner, "- resultSeparator=", resultSeparator);

		return stringJoiner.toString();
	}

	@Override
	public ArrayTranslate copy() {
		return ArrayTranslate
			.builder()
			.type(type)
			.column(column)
			.translationTable(translationTable)
			.arraySeparator(arraySeparator)
			.resultSeparator(resultSeparator)
			.build();
	}

	@Override
	public void update(UnaryOperator<String> updater) {
		arraySeparator = updater.apply(arraySeparator);
		resultSeparator = updater.apply(resultSeparator);
		translationTable.update(updater);
	}

	@Override
	public void accept(IComputeProcessor computeProcessor) {
		computeProcessor.process(this);
	}
}
