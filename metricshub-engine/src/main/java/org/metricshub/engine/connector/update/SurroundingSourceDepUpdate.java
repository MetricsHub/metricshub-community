package org.metricshub.engine.connector.update;

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

import java.util.Map;
import java.util.regex.Pattern;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.monitor.task.source.Source;

/**
 * Implementation of {@link SourceConnectorUpdateChain} for updating pre-source dependencies in the connector.
 */
public class SurroundingSourceDepUpdate extends SourceConnectorUpdateChain {

	@Override
	void doUpdate(Connector connector) {
		// Update beforeAll source dependencies
		final Map<String, Source> beforeAllSources = connector.getBeforeAll();
		if (beforeAllSources != null) {
			connector.setBeforeAllSourceDep(
				updateSourceDependency(
					beforeAllSources,
					Pattern.compile(
						String.format(
							"\\s*(\\$\\{source::((?i)beforeAll)\\.(%s)\\})\\s*",
							getSourceIdentifiersRegex(beforeAllSources)
						),
						Pattern.MULTILINE
					),
					3
				)
			);
		}
		// Update afterAll source dependencies
		final Map<String, Source> afterAllSources = connector.getAfterAll();
		if (afterAllSources != null) {
			connector.setAfterAllSourceDep(
				updateSourceDependency(
					afterAllSources,
					Pattern.compile(
						String.format(
							"\\s*(\\$\\{source::((?i)afterAll)\\.(%s)\\})\\s*",
							getSourceIdentifiersRegex(afterAllSources)
						),
						Pattern.MULTILINE
					),
					3
				)
			);
		}
	}
}
