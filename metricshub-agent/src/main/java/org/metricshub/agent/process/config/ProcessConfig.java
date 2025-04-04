package org.metricshub.agent.process.config;

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

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NonNull;

/**
 * Defines the configuration of a specific process.<br>
 * You need to configure the command line used to run the process.<br>
 * If you wish to process the STDOUT and STDERR, provide the
 * {@link ProcessOutput} configuration. A set of predefined configurations are
 * already defined by {@link ProcessOutput} class such as
 * {@link ProcessOutput#log(org.slf4j.Logger)}, {@link ProcessOutput#silent()} and
 * {@link ProcessOutput#namedConsole(String)} but you can provide your own
 * implementation to process the output.<br> The process environment map can also be
 * defined, subsequently the started subprocesses will use this map as their
 * environment.
 */
@Data
@Builder
public class ProcessConfig {

	@NonNull
	private List<String> commandLine;

	private ProcessOutput output;

	@Default
	@NonNull
	private Map<String, String> environment = new HashMap<>();

	private File workingDir;

	/**
	 * Update the current ProcessConfig
	 *
	 * @param processConfig the new process configuration
	 */
	public void update(@NonNull final ProcessConfig processConfig) {
		setCommandLine(processConfig.getCommandLine());
		setEnvironment(processConfig.getEnvironment());
		setOutput(processConfig.getOutput());
		setWorkingDir(processConfig.getWorkingDir());
	}
}
