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
import org.metricshub.engine.connector.model.common.ContentType;

/**
 * An abstract class representing a computation operation for concatenation. Subclasses should provide specific
 * implementations for column and value settings. This class extends the {@link Compute} class and includes
 * settings for the column index and a value to concatenate.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public abstract class AbstractConcat extends Compute implements TextOrColumnCompute {

	private static final long serialVersionUID = 1L;

	/**
	 * The column index for concatenation, null to work on the whole text of a text result.
	 */
	protected Integer column;

	/**
	 * The value to concatenate.
	 */
	@NonNull
	@JsonSetter(nulls = FAIL)
	protected String value;

	/**
	 * Returns a string representation of the AbstractConcat instance, including the type, column index, and value.
	 *
	 * @return A string representation of the object.
	 */
	protected AbstractConcat(String type, Integer column, String value) {
		super(type);
		this.column = column;
		this.value = value;
	}

	/**
	 * None without a <code>column</code>: the compute works on the content it is fed. With a <code>column</code>, it
	 * needs a table, as it always did: a legacy text is still converted for it (see
	 * {@link org.metricshub.engine.connector.update.ContentTypeDesugaring}).
	 *
	 * @return null, or a table when the compute has a column
	 */
	@Override
	public ContentType getInputContentType() {
		return column == null ? null : ContentType.TABLE;
	}

	/**
	 * The compute does not change the type of the content: a text stays a text, a table stays a table. (A compute
	 * with a <code>column</code> fed a text that is not converted gives an empty table, as it always did.)
	 */
	@Override
	public ContentType getOutputContentType(final ContentType input) {
		return column == null ? input : ContentType.TABLE;
	}

	@Override
	public String toString() {
		final StringJoiner valueJoiner = new StringJoiner(NEW_LINE);

		valueJoiner.add(super.toString());

		addNonNull(valueJoiner, "- column=", column);
		addNonNull(valueJoiner, "- value=", value);

		return valueJoiner.toString();
	}

	@Override
	public void update(UnaryOperator<String> updater) {
		value = updater.apply(value);
	}
}
