package org.metricshub.engine.connector.model.common;

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

import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.metricshub.engine.common.helpers.MetricsHubConstants;

/**
 * Represents a translation table based on a reference in connector models.
 * It implements the {@link ITranslationTable} interface for mapping key-value pairs during data transformation.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReferenceTranslationTable implements ITranslationTable {

	private static final long serialVersionUID = 1L;

	/**
	 * The reference associated with the translation table.
	 */
	private String reference;

	/**
	 * The unique identifier for the translation table.
	 */
	private String tableId;

	/**
	 * Constructs a {@link ReferenceTranslationTable} with the given reference.
	 *
	 * @param reference The reference used to create the translation table.
	 */
	public ReferenceTranslationTable(final String reference) {
		this.reference = reference;
		final Matcher matcher = MetricsHubConstants.TRANSLATION_REF_PATTERN.matcher(reference);
		if (matcher.find()) {
			tableId = matcher.group(1);
		} else {
			tableId = reference;
		}
	}

	@Override
	public ReferenceTranslationTable copy() {
		return new ReferenceTranslationTable(reference);
	}

	@Override
	public void update(UnaryOperator<String> updater) {
		reference = updater.apply(reference);
		tableId = updater.apply(tableId);
	}
}
