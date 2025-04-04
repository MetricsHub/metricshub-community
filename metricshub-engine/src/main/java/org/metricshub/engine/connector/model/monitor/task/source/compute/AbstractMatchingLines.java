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

import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.StringJoiner;
import java.util.function.UnaryOperator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * An abstract class extending {@link Compute} that represents operations involving matching lines based on specified criteria.
 * It provides common fields such as column, regular expression, and value list, along with methods for updating and converting to a string.
 *
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public abstract class AbstractMatchingLines extends Compute {

	private static final long serialVersionUID = 1L;

	/**
	 * The column on which the matching is performed.
	 */
	@NonNull
	@JsonSetter(nulls = FAIL)
	protected Integer column;

	/**
	 * The regular expression used for matching.
	 */
	protected String regExp;
	/**
	 * The list of values for matching.
	 */
	protected String valueList;

	protected AbstractMatchingLines(String type, Integer column, String regExp, String valueList) {
		super(type);
		this.column = column;
		this.regExp = regExp;
		this.valueList = valueList;
	}

	@Override
	public String toString() {
		final StringJoiner stringJoiner = new StringJoiner(NEW_LINE);

		stringJoiner.add(super.toString());

		addNonNull(stringJoiner, "- column=", column);
		addNonNull(stringJoiner, "- regExp=", regExp);
		addNonNull(stringJoiner, "- valueList=", valueList);

		return stringJoiner.toString();
	}

	@Override
	public void update(UnaryOperator<String> updater) {
		regExp = updater.apply(regExp);
		valueList = updater.apply(valueList);
	}
}
