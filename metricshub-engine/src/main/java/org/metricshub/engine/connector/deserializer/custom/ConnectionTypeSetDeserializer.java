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

import java.util.Collection;
import java.util.HashSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.metricshub.engine.connector.model.identity.ConnectionType;

/**
 * Custom deserializer for converting JSON arrays to a set of {@link ConnectionType}.
 */
public class ConnectionTypeSetDeserializer extends AbstractCollectionDeserializer<ConnectionType> {

	@Override
	protected Function<String, ConnectionType> valueExtractor() {
		return ConnectionType::detect;
	}

	@Override
	protected Collection<ConnectionType> emptyCollection() {
		return new HashSet<>();
	}

	@Override
	protected Collector<ConnectionType, ?, Collection<ConnectionType>> collector() {
		return Collectors.toCollection(HashSet::new);
	}

	@Override
	protected Predicate<ConnectionType> getFilterPredicate() {
		return type -> true;
	}
}
