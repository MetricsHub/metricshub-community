package org.metricshub.engine.connector.model.common;

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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.io.Serializable;

/**
 * Represents an interface for entry concatenation methods used in connector models.
 * Implementing classes should provide methods to create a copy of the instance and retrieve a description.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION, defaultImpl = EntryConcatMethod.class)
@JsonSubTypes(@JsonSubTypes.Type(value = CustomConcatMethod.class))
public interface IEntryConcatMethod extends Serializable {
	/**
	 * Creates a deep copy of the current {@link IEntryConcatMethod} instance.
	 *
	 * @return A new instance of {@link IEntryConcatMethod} representing a copy of the original instance.
	 */
	IEntryConcatMethod copy();

	/**
	 * Gets a human-readable description of the entry concatenation method.
	 *
	 * @return A string representing the description of the entry concatenation method.
	 */
	String getDescription();
}
