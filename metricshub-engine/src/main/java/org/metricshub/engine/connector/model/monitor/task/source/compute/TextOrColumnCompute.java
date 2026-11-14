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

/**
 * A compute that works on a text as a whole or on a column of a table, and leaves the type of the content as it is:
 * <code>append</code>, <code>prepend</code>, <code>replace</code>, <code>substring</code>.
 * <p>
 * The content decides, not the compute: on a text the compute has no <code>column</code>, on a table it needs one.
 * The content-type check reports a <code>column</code> on a text and a missing <code>column</code> on a table when
 * the connector loads.
 */
public interface TextOrColumnCompute {
	/**
	 * The column the compute works on.
	 *
	 * @return the 1-based index of the column, null when the compute has none
	 */
	Integer getColumn();
}
