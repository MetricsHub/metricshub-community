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

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Map;
import org.metricshub.engine.connector.model.monitor.task.source.Source;

/**
 * Custom deserializer for deserializing a map of {@link Source} objects.
 */
public class SourcesDeserializer extends AbstractLinkedHashMapDeserializer<Source> {

	@Override
	protected String messageOnInvalidMap(String nodeKey) {
		return String.format("The source key referenced by '%s' cannot be empty.", nodeKey);
	}

	@Override
	protected boolean isValidMap(Map<String, Source> map) {
		return map
			.keySet()
			.stream()
			.noneMatch(key -> key == null || key.isBlank());
	}

	@Override
	protected TypeReference<Map<String, Source>> getTypeReference() {
		return new TypeReference<Map<String, Source>>() {};
	}
}
