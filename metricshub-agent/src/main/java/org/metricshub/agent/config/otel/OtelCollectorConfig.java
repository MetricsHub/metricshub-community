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

import static com.fasterxml.jackson.annotation.Nulls.SKIP;
import static org.metricshub.agent.config.otel.OtelCollectorOutput.LOG;
import static org.metricshub.agent.helper.AgentConstants.OTEL_DIRECTORY_NAME;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.metricshub.agent.helper.ConfigHelper;
import org.metricshub.agent.helper.OtelConfigHelper;
import org.metricshub.agent.process.config.ProcessConfig;
import org.metricshub.engine.common.helpers.LocalOsHandler;
import org.metricshub.engine.common.helpers.MapHelper;
import org.metricshub.engine.deserialization.TimeDeserializer;

/**
 * Configuration class for the OpenTelemetry Collector.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OtelCollectorConfig {

	protected static final List<String> DEFAULT_COMMAND_LINE = buildDefaultCommandLine();
	protected static final String DEFAULT_WORKING_DIR = ConfigHelper.getSubPath(OTEL_DIRECTORY_NAME).toString();
	protected static final Map<String, String> DEFAULT_ENVIRONMENT;

	static {
		DEFAULT_ENVIRONMENT = new HashMap<>();
		if (LocalOsHandler.isWindows()) {
			// When the application starts as Windows Service, the process may fail with this error:
			// 'The service process could not connect to the service controller'.
			// In this case the NO_WINDOWS_SERVICE environment variable needs be set to `1` to force the collector
			// to be started as if it were running in an interactive terminal, without attempting to run as a Windows service.
			DEFAULT_ENVIRONMENT.put("NO_WINDOWS_SERVICE", "1");
		}
	}

	/**
	 * Default executable output ID for the OpenTelemetry Collector.
	 */
	public static final String EXECUTABLE_OUTPUT_ID = "otelcol";

	@Default
	@JsonSetter(nulls = SKIP)
	private List<String> commandLine = DEFAULT_COMMAND_LINE;

	@Default
	@JsonSetter(nulls = SKIP)
	private Map<String, String> environment = DEFAULT_ENVIRONMENT;

	@Default
	@JsonSetter(nulls = SKIP)
	private OtelCollectorOutput output = LOG;

	@Default
	@JsonSetter(nulls = SKIP)
	private String workingDir = DEFAULT_WORKING_DIR;

	@Default
	private boolean disabled = false;

	@Default
	@JsonDeserialize(using = TimeDeserializer.class)
	private long startupDelay = 10;

	/**
	 * Build the default command line of the OpenTelemetry Collector
	 *
	 * @return List of elements constructing the command line
	 */
	private static List<String> buildDefaultCommandLine() {
		final List<String> commandLine = new ArrayList<>();

		// Default executable file name for the OpenTelemetry Collector.
		final String executableFileName = LocalOsHandler.isWindows() ? "otelcol-contrib.exe" : "otelcol-contrib";

		// Executable path
		commandLine.add(ConfigHelper.getSubPath(OTEL_DIRECTORY_NAME + "/" + executableFileName).toString());

		// Configuration path
		commandLine.add("--config");

		// Get the default configuration file path located under the otel directory
		final var defaultConfigFilePath = OtelConfigHelper.getDefaultOtelConfigFilePath().toString();

		commandLine.add(
			LocalOsHandler.isWindows() ? String.format("\"%s\"", defaultConfigFilePath) : defaultConfigFilePath
		);

		return commandLine;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		OtelCollectorConfig other = (OtelCollectorConfig) obj;
		// CHECKSTYLE:OFF
		return (
			Objects.equals(commandLine, other.commandLine) &&
			disabled == other.disabled &&
			MapHelper.areEqual(environment, other.environment) &&
			output == other.output &&
			Objects.equals(workingDir, other.workingDir)
		);
		// CHECKSTYLE:ON
	}

	@Override
	public int hashCode() {
		return Objects.hash(commandLine, disabled, environment, output, workingDir);
	}

	/**
	 * Build a new {@link ProcessConfig} from actual information
	 *
	 * @return new {@link ProcessConfig} instance
	 */
	public ProcessConfig toProcessConfig() {
		return ProcessConfig
			.builder()
			.commandLine(getCommandLine())
			.environment(getEnvironment())
			.output(getOutput().getProcessOutputSupplier().get())
			.workingDir(new File(getWorkingDir()))
			.build();
	}
}
