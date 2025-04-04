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

import java.io.Serializable;
import java.util.function.UnaryOperator;

/**
 * Represents an interface for translation tables used in connector models.
 * Translation tables are used to map key-value pairs for data transformation.
 */
public interface ITranslationTable extends Serializable {
	/**
	 * Copy the {@link ITranslationTable} instance
	 *
	 * @return {@link ITranslationTable} deep copy
	 */
	ITranslationTable copy();

	/**
	 * Update the given translation table
	 *
	 * @param updater An operation on a single operand that produces a result of the
	 *                same type as its operand.
	 */
	void update(UnaryOperator<String> updater);
}
