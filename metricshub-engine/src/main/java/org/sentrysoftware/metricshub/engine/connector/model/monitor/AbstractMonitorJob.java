package org.sentrysoftware.metricshub.engine.connector.model.monitor;

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
import static com.fasterxml.jackson.annotation.Nulls.SKIP;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.DEFAULT_KEYS;

import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AbstractMonitorJob implements MonitorJob {

	/**
	 * Initializes an {@code AbstractMonitorJob} with the specified set of keys.
	 *
	 * @param keys The set of keys for the monitor job. It will be stored in a {@link LinkedHashSet} to preserve order.
	 */
	public AbstractMonitorJob(final Set<String> keys) {
		this.keys = new LinkedHashSet<>(keys);
	}

	/**
	 * The monitor job keys needed to build the monitor id
	 */
	@JsonSetter(nulls = SKIP)
	private LinkedHashSet<String> keys = DEFAULT_KEYS;
}
