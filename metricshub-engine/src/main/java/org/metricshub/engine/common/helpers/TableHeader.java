package org.metricshub.engine.common.helpers;

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

import lombok.Getter;
import org.springframework.util.Assert;

/**
 * Represents the header of a table, including a title and the data type of the text it contains.
 */
@Getter
public class TableHeader {

	private final String title;
	private final TextDataType type;

	/**
	 * Constructs a new {@code TableHeader} with the specified title and text data type.
	 *
	 * @param title The title of the table header.
	 * @param type The data type of the text in the table header.
	 * @throws IllegalArgumentException if either {@code title} or {@code type} is {@code null}.
	 */
	public TableHeader(String title, TextDataType type) {
		Assert.notNull(title, "title cannot be null.");
		Assert.notNull(type, "type cannot be null.");

		this.title = title;
		this.type = type;
	}
}
