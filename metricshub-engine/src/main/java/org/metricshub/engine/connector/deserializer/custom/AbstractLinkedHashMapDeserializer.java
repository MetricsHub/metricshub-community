package org.metricshub.engine.connector.deserializer.custom;

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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An abstract class providing deserialization support for custom types using Jackson's ObjectMapper
 * and representing the deserialization of a {@link LinkedHashMap} instance.
 *
 * @param <T> The type of values stored in the map.
 */
public abstract class AbstractLinkedHashMapDeserializer<T> extends AbstractMapDeserializer<T> {

	@Override
	protected Map<String, T> emptyMap() {
		return new LinkedHashMap<>();
	}

	@Override
	protected Map<String, T> fromMap(Map<String, T> map) {
		return new LinkedHashMap<>(map);
	}

	@Override
	protected boolean isExpectedInstance(Map<String, T> map) {
		return map instanceof LinkedHashMap;
	}
}
