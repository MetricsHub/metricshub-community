package org.metricshub.extension.emulation.jmx;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Emulation Extension
 * ჻჻჻჻჻჻
 * Copyright (C) 2023 - 2026 MetricsHub
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

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Root container for the JMX emulation image.yaml file.
 *
 * <p>Expected YAML shape:
 * <pre>
 * image:
 *   - request:
 *       objectName: &lt;JMX object name pattern&gt;
 *       attributes:
 *         - &lt;attribute name&gt;
 *       keyProperties:
 *         - &lt;key property&gt;
 *     response: &lt;response file name&gt;
 * </pre>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class JmxEmulationImage {

	/**
	 * Recorded JMX request/response mappings to replay.
	 */
	private List<JmxEmulationEntry> image;
}
