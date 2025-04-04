package org.metricshub.engine.connector.model.identity.criterion;

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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.HashSet;
import java.util.Set;
import java.util.StringJoiner;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.metricshub.engine.connector.deserializer.custom.DeviceKindSetDeserializer;
import org.metricshub.engine.connector.model.common.DeviceKind;

/**
 * Criterion for specifying device types to keep or exclude.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DeviceTypeCriterion extends Criterion {

	private static final long serialVersionUID = 1L;

	/**
	 * Set of device types to keep.
	 */
	@JsonDeserialize(using = DeviceKindSetDeserializer.class)
	private Set<DeviceKind> keep = new HashSet<>();

	/**
	 * Set of device types to exclude.
	 */
	@JsonDeserialize(using = DeviceKindSetDeserializer.class)
	private Set<DeviceKind> exclude = new HashSet<>();

	/**
	 * Constructor with builder for creating an instance of DeviceTypeCriterion.
	 *
	 * @param type               Type of the criterion.
	 * @param forceSerialization Flag indicating whether serialization should be forced.
	 * @param keep               Set of device types to keep.
	 * @param exclude            Set of device types to exclude.
	 */
	@Builder
	public DeviceTypeCriterion(String type, boolean forceSerialization, Set<DeviceKind> keep, Set<DeviceKind> exclude) {
		super(type, forceSerialization);
		this.keep = keep == null ? new HashSet<>() : keep;
		this.exclude = exclude == null ? new HashSet<>() : exclude;
	}

	@Override
	public String toString() {
		final StringJoiner stringJoiner = new StringJoiner(NEW_LINE);
		stringJoiner.add(new StringBuilder("- Keep: ").append(keep));
		stringJoiner.add(new StringBuilder("- Exclude: ").append(exclude));
		return stringJoiner.toString();
	}
}
