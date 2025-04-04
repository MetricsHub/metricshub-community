package org.metricshub.engine.extension;

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

import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.strategy.source.SourceProcessor;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * Contract for composite source script extensions.<br>
 * Implementations of this interface are responsible for processing sources using scripts such as AWK.
 */
public interface ICompositeSourceScriptExtension {
	/**
	 * Executes a source operation based on the given source and configuration within the telemetry manager.
	 *
	 * @param source           The source to execute.
	 * @param connectorId      The connector Identifier.
	 * @param telemetryManager The telemetry manager to use for monitoring.
	 * @param sourceProcessor  The {@link SourceProcessor} that will be used to execute requests and commands.
	 * @return A {@link SourceTable} object representing the result of the source execution.
	 */
	SourceTable processSource(
		Source source,
		String connectorId,
		TelemetryManager telemetryManager,
		SourceProcessor sourceProcessor
	);

	/**
	 * Check if a {@link Source} is of the right sub type of source to be processed through the extension.
	 * @param source The source to check.
	 * @return True if the {@link Source} is of the right subtype of source, false if it's not.
	 */
	boolean isValidSource(Source source);
}
