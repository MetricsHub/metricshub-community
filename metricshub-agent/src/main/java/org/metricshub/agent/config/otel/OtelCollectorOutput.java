package org.metricshub.agent.config.otel;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
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

import static org.metricshub.agent.config.otel.OtelCollectorConfig.EXECUTABLE_OUTPUT_ID;

import io.grpc.netty.shaded.io.netty.util.internal.shaded.org.jctools.queues.MessagePassingQueue.Supplier;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.metricshub.agent.process.config.ProcessOutput;
import org.metricshub.agent.service.OtelCollectorProcessService;
import org.slf4j.LoggerFactory;

/**
 * Enumeration representing different output options for the OpenTelemetry Collector.
 */
@AllArgsConstructor
public enum OtelCollectorOutput {
	/**
	 * Silent output, no logging or console output.
	 */
	SILENT(ProcessOutput::silent),
	/**
	 * Console output.
	 */
	CONSOLE(() -> ProcessOutput.namedConsoleOutput(EXECUTABLE_OUTPUT_ID)),
	/**
	 * Logging output using SLF4J.
	 */
	LOG(() -> ProcessOutput.logOutput(LoggerFactory.getLogger(OtelCollectorProcessService.class)));

	@Getter
	private Supplier<ProcessOutput> processOutputSupplier;
}
