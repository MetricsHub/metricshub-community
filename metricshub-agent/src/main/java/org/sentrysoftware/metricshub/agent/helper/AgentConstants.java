package org.sentrysoftware.metricshub.agent.helper;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * AgentConstants contains constant values and configurations used across the MetricsHub agent.
 * It includes file names, directory names, default output directory, file path format, and attribute information constants.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AgentConstants {

	// Application YAML properties
	/**
	 * Application YAML file name
	 */
	public static final String APPLICATION_YAML_FILE_NAME = "application.yaml";

	// Configuration file
	/**
	 * Product code
	 */
	public static final String PRODUCT_CODE = "metricshub";
	/**
	 * Configuration example filename
	 */
	public static final String CONFIG_EXAMPLE_FILENAME = PRODUCT_CODE + "-example.yaml";
	/**
	 * Default configuration filename
	 */
	public static final String DEFAULT_CONFIG_FILENAME = PRODUCT_CODE + ".yaml";
	/**
	 * Default Open Telemetry configuration filename
	 */
	public static final String DEFAULT_OTEL_CONFIG_FILENAME = "otel-config.yaml";
	/**
	 * Default Open Telemetry (.crt) filename
	 */
	public static final String DEFAULT_OTEL_CRT_FILENAME = "otel.crt";
	/**
	 * Log directory name
	 */
	public static final String LOG_DIRECTORY_NAME = "logs";
	/**
	 * Open Telemetry directory name
	 */
	public static final String OTEL_DIRECTORY_NAME = "otel";
	/**
	 * Configuration directory name
	 */
	public static final String CONFIG_DIRECTORY_NAME = "config";
	/**
	 * Security directory name
	 */
	public static final String SECURITY_DIRECTORY_NAME = "security";
	/**
	 * Default output directory
	 */
	public static final Path DEFAULT_OUTPUT_DIRECTORY = ConfigHelper.getDefaultOutputDirectory();
	/**
	 * File path format
	 */
	public static final String FILE_PATH_FORMAT = "%s/%s";

	// Agent attribute information constants
	/**
	 * Agent info community connectors version number attribute key
	 */
	public static final String AGENT_INFO_CC_VERSION_NUMBER_ATTRIBUTE_KEY = "cc_version";
	/**
	 * Agent info build date number attribute key
	 */
	public static final String AGENT_INFO_BUILD_DATE_NUMBER_ATTRIBUTE_KEY = "build_date";
	/**
	 * Agent info build number attribute key
	 */
	public static final String AGENT_INFO_BUILD_NUMBER_ATTRIBUTE_KEY = "build_number";
	/**
	 * Agent info version attribute key
	 */
	public static final String AGENT_INFO_VERSION_ATTRIBUTE_KEY = "version";
	/**
	 * Agent info name attribute key
	 */
	public static final String AGENT_INFO_NAME_ATTRIBUTE_KEY = "name";

	// Object Mapper
	/**
	 * Object mapper
	 */
	public static final ObjectMapper OBJECT_MAPPER = ConfigHelper.newObjectMapper();
}
