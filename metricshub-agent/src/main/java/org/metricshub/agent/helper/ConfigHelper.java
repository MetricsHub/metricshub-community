package org.metricshub.agent.helper;

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

import static org.metricshub.agent.helper.AgentConstants.CONFIG_DIRECTORY_NAME;
import static org.metricshub.agent.helper.AgentConstants.CONFIG_EXAMPLE_FILENAME;
import static org.metricshub.agent.helper.AgentConstants.DEFAULT_CONFIG_FILENAME;
import static org.metricshub.agent.helper.AgentConstants.FILE_PATH_FORMAT;
import static org.metricshub.agent.helper.AgentConstants.LOG_DIRECTORY_NAME;
import static org.metricshub.agent.helper.AgentConstants.PRODUCT_WIN_DIR_NAME;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.GroupPrincipal;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.ThreadContext;
import org.metricshub.agent.config.AgentConfig;
import org.metricshub.agent.config.AlertingSystemConfig;
import org.metricshub.agent.config.ResourceConfig;
import org.metricshub.agent.config.ResourceGroupConfig;
import org.metricshub.agent.config.StateSetMetricCompression;
import org.metricshub.agent.connector.AdditionalConnectorsParsingResult;
import org.metricshub.agent.connector.ConnectorVariablesLibraryParser;
import org.metricshub.agent.context.MetricDefinitions;
import org.metricshub.agent.security.PasswordEncrypt;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.common.helpers.LocalOsHandler;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
import org.metricshub.engine.common.helpers.ResourceHelper;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.identity.ConnectorIdentity;
import org.metricshub.engine.connector.model.metric.MetricDefinition;
import org.metricshub.engine.extension.ExtensionLoader;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.security.SecurityManager;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.springframework.core.io.ClassPathResource;

/**
 * Helper class for managing configuration-related operations in the MetricsHub agent.
 * This class provides methods for retrieving directories, creating paths, and handling configuration files.
 * It also includes utility methods for encryption and other configuration-related tasks.
 * The class is designed with a private constructor and static utility methods.
 *
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class ConfigHelper {

	public static final String TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY = "metricshub-top-level-rg";

	/**
	 * Get the default output directory for logging.<br>
	 * On Windows, if the LOCALAPPDATA path is not valid then the output directory will be located
	 * under the installation directory.<br>
	 * On Linux, the output directory is located under the installation directory.
	 *
	 * @return {@link Path} instance
	 */
	public static Path getDefaultOutputDirectory() {
		if (LocalOsHandler.isWindows()) {
			final String localAppDataPath = System.getenv("LOCALAPPDATA");

			// Make sure the LOCALAPPDATA path is valid
			if (localAppDataPath != null && !localAppDataPath.isBlank()) {
				return createDirectories(Paths.get(localAppDataPath, PRODUCT_WIN_DIR_NAME, "logs"));
			}
		}

		return getSubDirectory(LOG_DIRECTORY_NAME, true);
	}

	/**
	 * Get a sub directory under the install directory
	 *
	 * @param dir    the directory assumed under the product directory. E.g. logs
	 *               assumed under /opt/metricshub
	 * @param create indicate if we should create the sub directory or not
	 * @return The absolute path of the sub directory
	 */
	public static Path getSubDirectory(@NonNull final String dir, boolean create) {
		Path subDirectory = getSubPath(dir);
		if (!create) {
			return subDirectory;
		}

		return createDirectories(subDirectory);
	}

	/**
	 * Create directories of the given path
	 *
	 * @param path Directories path
	 * @return {@link Path} instance
	 */
	public static Path createDirectories(final Path path) {
		try {
			return Files.createDirectories(path).toRealPath();
		} catch (IOException e) {
			throw new IllegalStateException("Could not create directory '" + path + "'.", e);
		}
	}

	/**
	 * Get the sub path under the home directory. E.g.
	 * <em>/opt/metricshub/lib/app/../config</em> on linux install
	 *
	 * @param subPath sub path to the directory or the file
	 * @return {@link Path} instance
	 */
	public static Path getSubPath(@NonNull final String subPath) {
		final File sourceDirectory = getSourceDirectory();

		final Path path = sourceDirectory.getAbsoluteFile().toPath();

		Path parentLibPath = path.getParent();

		// No parent? let's work with the current directory
		if (parentLibPath == null) {
			parentLibPath = path;
		}

		return parentLibPath.resolve("../" + subPath);
	}

	/**
	 * Retrieves the directory containing the current source file, whether it's located
	 * within a JAR file or a regular directory.<br>
	 *
	 * This method attempts to locate the source directory associated with the calling class, which can be
	 * helpful for accessing resources and configuration files.
	 *
	 * @return A {@link File} instance representing the source directory.
	 *
	 * @throws IllegalStateException if the source directory cannot be determined.
	 */
	public static File getSourceDirectory() {
		final File sourceDirectory;
		try {
			sourceDirectory = ResourceHelper.findSourceDirectory(ConfigHelper.class);
		} catch (Exception e) {
			throw new IllegalStateException("Error detected when getting local source file: ", e);
		}

		if (sourceDirectory == null) {
			throw new IllegalStateException("Could not get the local source file.");
		}
		return sourceDirectory;
	}

	/**
	 * Get the default configuration file path either in the Windows <em>ProgramData\metricshub</em>
	 * directory or under the install directory <em>/opt/metricshub</em> on Linux systems.
	 *
	 * @param directory      Directory of the configuration file. (e.g. config or otel)
	 * @param configFilename Configuration file name (e.g. metricshub.yaml or otel-config.yaml)
	 * @return new {@link Path} instance
	 */
	public static Path getDefaultConfigFilePath(final String directory, final String configFilename) {
		if (LocalOsHandler.isWindows()) {
			return getProgramDataConfigFile(directory, configFilename);
		}
		return ConfigHelper.getSubPath(String.format(FILE_PATH_FORMAT, directory, configFilename));
	}

	/**
	 * Get the configuration file under the ProgramData windows directory.<br>
	 * If the ProgramData path is not valid then the configuration file will be located
	 * under the install directory.
	 *
	 * @param directory      Directory of the configuration file. (e.g. config or otel)
	 * @param configFilename Configuration file name (e.g. metricshub.yaml or otel-config.yaml)
	 * @return new {@link Path} instance
	 */
	static Path getProgramDataConfigFile(final String directory, final String configFilename) {
		return getProgramDataPath()
			.stream()
			.map(path ->
				Paths.get(
					createDirectories(Paths.get(path, PRODUCT_WIN_DIR_NAME, directory)).toAbsolutePath().toString(),
					configFilename
				)
			)
			.findFirst()
			.orElseGet(() -> ConfigHelper.getSubPath(String.format(FILE_PATH_FORMAT, directory, configFilename)));
	}

	/**
	 * Get the <em>%PROGRAMDATA%</em> path. If the ProgramData path is not valid
	 * then <code>Optional.empty()</code> is returned.
	 *
	 * @return {@link Optional} containing a string value (path)
	 */
	public static Optional<String> getProgramDataPath() {
		final String programDataPath = System.getenv("ProgramData");
		if (programDataPath != null && !programDataPath.isBlank()) {
			return Optional.of(programDataPath);
		}

		return Optional.empty();
	}

	/**
	 * Decrypt the given encrypted password.
	 *
	 * @param encrypted    The encrypted password
	 * @return char array  The decrypted password
	 */
	public static char[] decrypt(final char[] encrypted) {
		try {
			return SecurityManager.decrypt(encrypted, PasswordEncrypt.getKeyStoreFile(false));
		} catch (Exception e) {
			// This is a real problem, let's log the error
			log.error("Could not decrypt password: {}", e.getMessage());
			log.debug("Exception", e);
			return encrypted;
		}
	}

	/**
	 * Find the application's configuration file (metricshub.yaml).<br>
	 * <ol>
	 *   <li>If the user has configured the configFilePath via <em>--config=$filePath</em> then it is the chosen file</li>
	 *   <li>Else if <em>config/metricshub.yaml</em> path exists, the resulting File is the one representing this path</li>
	 *   <li>Else we copy <em>config/metricshub-example.yaml</em> to the host file <em>config/metricshub.yaml</em> then we return the resulting host file</li>
	 * </ol>
	 *
	 * The program fails if
	 * <ul>
	 *   <li>The configured file path doesn't exist</li>
	 *   <li>config/metricshub-example.yaml is not present</li>
	 *   <li>If an I/O error occurs</li>
	 * </ul>
	 *
	 * @param configFilePath The configuration file passed by the user. E.g. --config=/opt/PRODUCT-CODE/config/my-metricshub.yaml
	 * @return {@link File} instance
	 * @throws IOException  This exception is thrown is the file is not found
	 */
	public static File findConfigFile(final String configFilePath) throws IOException {
		// The user has configured a configuration file path
		if (configFilePath != null && !configFilePath.isBlank()) {
			final File configFile = new File(configFilePath);
			if (configFile.exists()) {
				return configFile;
			}
			throw new IllegalStateException(
				String.format("Cannot find %s. Please make sure the file exists on your system", configFilePath)
			);
		}

		// Get the configuration file config/metricshub.yaml
		return getDefaultConfigFile(CONFIG_DIRECTORY_NAME, DEFAULT_CONFIG_FILENAME, CONFIG_EXAMPLE_FILENAME);
	}

	/**
	 * Get the default configuration file.
	 *
	 * @param directory             Directory of the configuration file. (e.g. config or otel)
	 * @param configFilename        Configuration file name (e.g. metricshub.yaml or otel-config.yaml)
	 * @param configFilenameExample Configuration file name example (e.g. metricshub-example.yaml)
	 * @return {@link File} instance
	 * @throws IOException if the copy fails
	 */
	public static File getDefaultConfigFile(
		final String directory,
		final String configFilename,
		final String configFilenameExample
	) throws IOException {
		// Get the configuration file absolute path
		final Path configPath = getDefaultConfigFilePath(directory, configFilename);

		// If it exists then we are good we can just return the resulting File
		if (Files.exists(configPath)) {
			// At this time, we don't know who created the configuration file and what permissions are applied.
			// So let's skip the error logging to avoid unnecessary noise. That's why we call the method with
			// logError = false
			setUserPermissionsOnWindows(configPath, false);

			return configPath.toFile();
		}

		// Now we will proceed with a copy of the example file (e.g. metricshub-example.yaml to config/metricshub.yaml)
		final Path exampleConfigPath = ConfigHelper.getSubPath(
			String.format(FILE_PATH_FORMAT, directory, configFilenameExample)
		);

		// Bad configuration
		if (!Files.exists(exampleConfigPath)) {
			throw new IllegalStateException(
				String.format(
					"Cannot find '%s' . Please create the configuration file '%s' before starting the MetricsHub Agent.",
					exampleConfigPath.toAbsolutePath(),
					configPath.toAbsolutePath()
				)
			);
		}

		final File configFile = Files.copy(exampleConfigPath, configPath, StandardCopyOption.REPLACE_EXISTING).toFile();

		setUserPermissionsOnWindows(configPath, true);

		return configFile;
	}

	/**
	 * Set write permissions for metricshub.yaml deployed on a Windows machine running the agent
	 *
	 * @param configPath  the configuration file absolute path
	 * @param logError    whether we should log the error or not. If logError is false, an info message is logged.
	 */
	private static void setUserPermissionsOnWindows(final Path configPath, boolean logError) {
		if (LocalOsHandler.isWindows()) {
			setUserPermissions(configPath, logError);
		}
	}

	/**
	 * Set write permission for metricshub.yaml
	 *
	 * @param configPath the configuration file absolute path
	 * @param logError   whether we should log the error or not. If logError is false, an info message is logged.
	 */
	private static void setUserPermissions(final Path configPath, boolean logError) {
		try {
			final GroupPrincipal users = configPath
				.getFileSystem()
				.getUserPrincipalLookupService()
				.lookupPrincipalByGroupName("Users");

			// get view
			final AclFileAttributeView view = Files.getFileAttributeView(configPath, AclFileAttributeView.class);

			// create ACE to give "Users" access
			final AclEntry entry = AclEntry
				.newBuilder()
				.setType(AclEntryType.ALLOW)
				.setPrincipal(users)
				.setPermissions(
					AclEntryPermission.WRITE_DATA,
					AclEntryPermission.WRITE_ATTRIBUTES,
					AclEntryPermission.WRITE_ACL,
					AclEntryPermission.WRITE_OWNER,
					AclEntryPermission.WRITE_NAMED_ATTRS,
					AclEntryPermission.READ_DATA,
					AclEntryPermission.READ_ACL,
					AclEntryPermission.READ_ATTRIBUTES,
					AclEntryPermission.READ_NAMED_ATTRS,
					AclEntryPermission.DELETE,
					AclEntryPermission.APPEND_DATA,
					AclEntryPermission.DELETE
				)
				.build();

			// read ACL, insert ACE, re-write ACL
			final List<AclEntry> acl = view.getAcl();

			// insert before any DENY entries
			acl.add(0, entry);
			view.setAcl(acl);
		} catch (Exception e) {
			if (logError) {
				log.error("Could not set write permissions to file: {}. Error: {}", configPath.toString(), e.getMessage());
				log.error("Exception: ", e);
			} else {
				log.info("Could not set write permissions to file: {}. Message: {}", configPath.toString(), e.getMessage());
			}
		}
	}

	/**
	 * Creates and configures a new instance of the Jackson ObjectMapper for handling YAML data.
	 *
	 * @return A configured ObjectMapper instance.
	 */
	public static JsonMapper newObjectMapper() {
		return JsonMapper
			.builder(new YAMLFactory())
			.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
			.enable(SerializationFeature.INDENT_OUTPUT)
			.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
			.configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)
			.build();
	}

	/**
	 * Normalizes the agent configuration and sets global values if no specific
	 * values are specified on the resource groups or resources
	 *
	 * @param agentConfig    The whole configuration of the MetricsHub agent
	 */
	public static void normalizeAgentConfiguration(final AgentConfig agentConfig) {
		// Normalize the top level resources using agent configuration
		agentConfig
			.getResources()
			.entrySet()
			.forEach(resourceConfigEntry -> normalizeResourceConfigUsingAgentConfig(agentConfig, resourceConfigEntry));

		// Normalize the resources using resource groups
		agentConfig
			.getResourceGroups()
			.entrySet()
			.forEach(resourceGroupConfigEntry -> {
				final ResourceGroupConfig resourceGroupConfig = resourceGroupConfigEntry.getValue();
				normalizeResourceGroupConfig(agentConfig, resourceGroupConfig);
				resourceGroupConfig
					.getResources()
					.entrySet()
					.forEach(resourceConfigEntry -> normalizeResourceConfig(resourceGroupConfigEntry, resourceConfigEntry));
			});
	}

	/**
	 * Normalizes the top level resource configuration and sets global values if no specific
	 * values are specified on the agent configuration
	 * @param agentConfig MetricsHub agent configuration
	 * @param resourceConfigEntry A given resource configuration entry (resourceKey, resourceConfig)
	 */
	private static void normalizeResourceConfigUsingAgentConfig(
		final AgentConfig agentConfig,
		final Entry<String, ResourceConfig> resourceConfigEntry
	) {
		final ResourceConfig resourceConfig = resourceConfigEntry.getValue();
		// Set agent configuration's collect period if there is no specific collect period on the resource configuration
		if (resourceConfig.getCollectPeriod() == null) {
			resourceConfig.setCollectPeriod(agentConfig.getCollectPeriod());
		}

		// Set agent configuration's discovery cycle if there is no specific collect period on the resource group
		if (resourceConfig.getDiscoveryCycle() == null) {
			resourceConfig.setDiscoveryCycle(agentConfig.getDiscoveryCycle());
		}

		// Set agent configuration's logger level in the resource configuration
		if (resourceConfig.getLoggerLevel() == null) {
			resourceConfig.setLoggerLevel(agentConfig.getLoggerLevel());
		}

		// Set agent configuration's output directory in the resource configuration
		if (resourceConfig.getOutputDirectory() == null) {
			resourceConfig.setOutputDirectory(agentConfig.getOutputDirectory());
		}

		// Set agent configuration's sequential flag in the resource configuration
		if (resourceConfig.getSequential() == null) {
			resourceConfig.setSequential(agentConfig.isSequential());
		}

		// Set global enableSelfMonitoring flag in the resource configuration
		if (resourceConfig.getEnableSelfMonitoring() == null) {
			resourceConfig.setEnableSelfMonitoring(agentConfig.isEnableSelfMonitoring());
		}

		// Set agent configuration's monitors filter in the resource configuration
		if (resourceConfig.getMonitorFilters() == null) {
			resourceConfig.setMonitorFilters(agentConfig.getMonitorFilters());
		}

		final AlertingSystemConfig resourceGroupAlertingSystemConfig = agentConfig.getAlertingSystemConfig();

		final AlertingSystemConfig alertingSystemConfig = resourceConfig.getAlertingSystemConfig();
		// Set agent configuration's alerting system in the resource configuration
		if (alertingSystemConfig == null) {
			resourceConfig.setAlertingSystemConfig(resourceGroupAlertingSystemConfig);
		} else if (alertingSystemConfig.getProblemTemplate() == null) {
			// Set the problem template of the alerting system
			alertingSystemConfig.setProblemTemplate(resourceGroupAlertingSystemConfig.getProblemTemplate());
		} else if (alertingSystemConfig.getDisable() == null) {
			// Set the disable flag of the altering system
			alertingSystemConfig.setDisable(resourceGroupAlertingSystemConfig.getDisable());
		}

		// Set the resolve host name to FQDN flag
		if (resourceConfig.getResolveHostnameToFqdn() == null) {
			resourceConfig.setResolveHostnameToFqdn(agentConfig.isResolveHostnameToFqdn());
		}

		// Set the job timeout value
		if (resourceConfig.getJobTimeout() == null) {
			resourceConfig.setJobTimeout(agentConfig.getJobTimeout());
		}

		// Set the state set compression
		if (resourceConfig.getStateSetCompression() == null) {
			resourceConfig.setStateSetCompression(agentConfig.getStateSetCompression());
		}

		// Set agent attributes in the agent configuration attributes map
		final Map<String, String> attributes = new HashMap<>();
		mergeAttributes(agentConfig.getAttributes(), attributes);
		mergeAttributes(resourceConfig.getAttributes(), attributes);
		resourceConfig.setAttributes(attributes);

		// Create an identity for the configured connector
		normalizeConfiguredConnector(
			TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY,
			resourceConfigEntry.getKey(),
			resourceConfig.getConnector()
		);
	}

	/**
	 * Normalizes the resource configuration and sets resource group configuration values if no specific
	 * values are specified on this resource configuration.<br>
	 * If a new connector is configured then it is automatically added to the connector store.
	 *
	 * @param resourceGroupConfigEntry The resource group configuration entry
	 * @param resourceConfigEntry      The individual resource configuration entry
	 */
	private static void normalizeResourceConfig(
		final Entry<String, ResourceGroupConfig> resourceGroupConfigEntry,
		final Entry<String, ResourceConfig> resourceConfigEntry
	) {
		final ResourceGroupConfig resourceGroupConfig = resourceGroupConfigEntry.getValue();
		final ResourceConfig resourceConfig = resourceConfigEntry.getValue();

		// Set resource group configuration's collect period if there is no specific collect period on the resource configuration
		if (resourceConfig.getCollectPeriod() == null) {
			resourceConfig.setCollectPeriod(resourceGroupConfig.getCollectPeriod());
		}

		// Set resource group configuration's discovery cycle if there is no specific collect period on the resource group
		if (resourceConfig.getDiscoveryCycle() == null) {
			resourceConfig.setDiscoveryCycle(resourceGroupConfig.getDiscoveryCycle());
		}

		// Set resource group configuration's logger level in the resource configuration
		if (resourceConfig.getLoggerLevel() == null) {
			resourceConfig.setLoggerLevel(resourceGroupConfig.getLoggerLevel());
		}

		// Set resource group configuration's output directory in the resource configuration
		if (resourceConfig.getOutputDirectory() == null) {
			resourceConfig.setOutputDirectory(resourceGroupConfig.getOutputDirectory());
		}

		// Set resource group configuration's sequential flag in the resource configuration
		if (resourceConfig.getSequential() == null) {
			resourceConfig.setSequential(resourceGroupConfig.getSequential());
		}

		// Set resource group configuration's enableSelfMonitoring flag in the resource configuration
		if (resourceConfig.getEnableSelfMonitoring() == null) {
			resourceConfig.setEnableSelfMonitoring(resourceGroupConfig.getEnableSelfMonitoring());
		}

		// Set resource group configuration's monitors filter in the resource configuration
		if (resourceConfig.getMonitorFilters() == null) {
			resourceConfig.setMonitorFilters(resourceGroupConfig.getMonitorFilters());
		}

		final AlertingSystemConfig resourceGroupAlertingSystemConfig = resourceGroupConfig.getAlertingSystemConfig();

		final AlertingSystemConfig alertingSystemConfig = resourceConfig.getAlertingSystemConfig();
		// Set resource group configuration's alerting system in the resource configuration
		if (alertingSystemConfig == null) {
			resourceConfig.setAlertingSystemConfig(resourceGroupAlertingSystemConfig);
		} else if (alertingSystemConfig.getProblemTemplate() == null) {
			// Set the problem template of the alerting system
			alertingSystemConfig.setProblemTemplate(resourceGroupAlertingSystemConfig.getProblemTemplate());
		} else if (alertingSystemConfig.getDisable() == null) {
			// Set the disable flag of the altering system
			alertingSystemConfig.setDisable(resourceGroupAlertingSystemConfig.getDisable());
		}

		// Set the resolve host name to FQDN flag
		if (resourceConfig.getResolveHostnameToFqdn() == null) {
			resourceConfig.setResolveHostnameToFqdn(resourceGroupConfig.getResolveHostnameToFqdn());
		}

		// Set the job timeout value
		if (resourceConfig.getJobTimeout() == null) {
			resourceConfig.setJobTimeout(resourceGroupConfig.getJobTimeout());
		}

		// Set the state set compression
		if (resourceConfig.getStateSetCompression() == null) {
			resourceConfig.setStateSetCompression(resourceGroupConfig.getStateSetCompression());
		}

		// Set agent attributes in the resource group attributes map
		final Map<String, String> attributes = new HashMap<>();
		mergeAttributes(resourceGroupConfig.getAttributes(), attributes);
		mergeAttributes(resourceConfig.getAttributes(), attributes);
		resourceConfig.setAttributes(attributes);

		// Create an identity for the configured connector
		normalizeConfiguredConnector(
			resourceGroupConfigEntry.getKey(),
			resourceConfigEntry.getKey(),
			resourceConfig.getConnector()
		);
	}

	/**
	 * Normalizes the resource group configuration and sets agent configuration's values if no specific
	 * values are specified on this resource group configuration
	 *
	 * @param agentConfig         The whole configuration the MetricsHub agent
	 * @param resourceGroupConfig The individual resource group configuration
	 */
	private static void normalizeResourceGroupConfig(
		final AgentConfig agentConfig,
		final ResourceGroupConfig resourceGroupConfig
	) {
		// Set global collect period if there is no specific collect period on the resource group configuration
		if (resourceGroupConfig.getCollectPeriod() == null) {
			resourceGroupConfig.setCollectPeriod(agentConfig.getCollectPeriod());
		}

		// Set global discovery cycle if there is no specific collect period on the resource group configuration
		if (resourceGroupConfig.getDiscoveryCycle() == null) {
			resourceGroupConfig.setDiscoveryCycle(agentConfig.getDiscoveryCycle());
		}

		// Set the global level in the resource group configuration
		if (resourceGroupConfig.getLoggerLevel() == null) {
			resourceGroupConfig.setLoggerLevel(agentConfig.getLoggerLevel());
		}

		// Set the global output directory in the resource group configuration
		if (resourceGroupConfig.getOutputDirectory() == null) {
			resourceGroupConfig.setOutputDirectory(agentConfig.getOutputDirectory());
		}

		// Set global sequential flag in the resource group configuration
		if (resourceGroupConfig.getSequential() == null) {
			resourceGroupConfig.setSequential(agentConfig.isSequential());
		}

		// Set global enableSelfMonitoring flag in the resource group configuration
		if (resourceGroupConfig.getEnableSelfMonitoring() == null) {
			resourceGroupConfig.setEnableSelfMonitoring(agentConfig.isEnableSelfMonitoring());
		}

		// Set global configuration's monitors filter in the resource group configuration
		if (resourceGroupConfig.getMonitorFilters() == null) {
			resourceGroupConfig.setMonitorFilters(agentConfig.getMonitorFilters());
		}

		final AlertingSystemConfig alertingSystemConfig = resourceGroupConfig.getAlertingSystemConfig();
		final AlertingSystemConfig globalAlertingSystemConfig = agentConfig.getAlertingSystemConfig();

		// Set global configuration's alerting system in the resource group configuration
		if (alertingSystemConfig == null) {
			resourceGroupConfig.setAlertingSystemConfig(globalAlertingSystemConfig);
		} else if (alertingSystemConfig.getProblemTemplate() == null) {
			// Set the problem template of the alerting system
			alertingSystemConfig.setProblemTemplate(globalAlertingSystemConfig.getProblemTemplate());
		} else if (alertingSystemConfig.getDisable() == null) {
			// Set the disable flag of the altering system
			alertingSystemConfig.setDisable(globalAlertingSystemConfig.getDisable());
		}

		// Set the resolve host name to FQDN flag
		if (resourceGroupConfig.getResolveHostnameToFqdn() == null) {
			resourceGroupConfig.setResolveHostnameToFqdn(agentConfig.isResolveHostnameToFqdn());
		}

		// Set the job timeout value
		if (resourceGroupConfig.getJobTimeout() == null) {
			resourceGroupConfig.setJobTimeout(agentConfig.getJobTimeout());
		}

		// Set the state set compression
		if (resourceGroupConfig.getStateSetCompression() == null) {
			resourceGroupConfig.setStateSetCompression(agentConfig.getStateSetCompression());
		}

		// Set agent attributes in the resource group attributes map
		final Map<String, String> attributes = new HashMap<>();
		mergeAttributes(agentConfig.getAttributes(), attributes);
		mergeAttributes(resourceGroupConfig.getAttributes(), attributes);
		resourceGroupConfig.setAttributes(attributes);
	}

	/**
	 * Merge the given attributes into the destination attributes
	 *
	 * @param attributes            Map of key-pair values defining the attributes at a certain level
	 * @param destinationAttributes Map of key-pair values defining the destination
	 */
	public static void mergeAttributes(
		final Map<String, String> attributes,
		final Map<String, String> destinationAttributes
	) {
		destinationAttributes.putAll(attributes);
	}

	/**
	 * Configure the 'org.metricshub' logger based on the user's command.<br>
	 * See src/main/resources/log4j2.xml
	 *
	 * @param loggerLevelStr     Logger level from the configuration as {@link String}
	 * @param outputDirectory The output directory as String
	 */
	public static void configureGlobalLogger(final String loggerLevelStr, final String outputDirectory) {
		final Level loggerLevel = getLoggerLevel(loggerLevelStr);

		ThreadContext.put("logId", "metricshub-agent-global");
		ThreadContext.put("loggerLevel", loggerLevel.toString());

		if (outputDirectory != null) {
			ThreadContext.put("outputDirectory", outputDirectory);
		}
	}

	/**
	 * Get the Log4j log level from the configured logLevel string
	 *
	 * @param loggerLevel string value from the configuration (e.g. off, debug, info, warn, error, trace, all)
	 * @return log4j {@link Level} instance
	 */
	public static Level getLoggerLevel(final String loggerLevel) {
		final Level level = loggerLevel != null ? Level.getLevel(loggerLevel.toUpperCase()) : null;

		return level != null ? level : Level.OFF;
	}

	/**
	 * Build the {@link TelemetryManager} map.
	 *
	 * @param agentConfig    Wraps the agent configuration for all the resources
	 * @param connectorStore Wraps all the connectors
	 * @return Map of {@link TelemetryManager} instances indexed by group id then by resource id
	 */
	public static Map<String, Map<String, TelemetryManager>> buildTelemetryManagers(
		@NonNull final AgentConfig agentConfig,
		@NonNull final ConnectorStore connectorStore
	) {
		final Map<String, Map<String, TelemetryManager>> telemetryManagers = new HashMap<>();

		agentConfig
			.getResourceGroups()
			.forEach((resourceGroupKey, resourceKeyGroupConfig) -> {
				final Map<String, TelemetryManager> resourceGroupTelemetryManagers = new HashMap<>();
				telemetryManagers.put(resourceGroupKey, resourceGroupTelemetryManagers);
				resourceKeyGroupConfig
					.getResources()
					.forEach((resourceKey, resourceConfig) ->
						updateResourceGroupTelemetryManagers(
							resourceGroupTelemetryManagers,
							resourceGroupKey,
							resourceKey,
							resourceConfig,
							connectorStore
						)
					);
			});

		// Initialize top level resources telemetry managers map
		final Map<String, TelemetryManager> topLevelResourcesTelemetryManagers = new HashMap<>();

		// Update top level resources telemetry managers
		agentConfig
			.getResources()
			.forEach((resourceKey, resourceConfig) ->
				updateResourceGroupTelemetryManagers(
					topLevelResourcesTelemetryManagers,
					TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY,
					resourceKey,
					resourceConfig,
					connectorStore
				)
			);

		// Put the top level resources map in the main/common telemetry managers map
		telemetryManagers.put(TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY, topLevelResourcesTelemetryManagers);

		return telemetryManagers;
	}

	/**
	 * Update the given resource group {@link TelemetryManager} map if the configuration is valid
	 *
	 * @param resourceGroupTelemetryManagers {@link Map} of {@link TelemetryManager} per resource group configuration
	 * @param resourceGroupKey               The unique identifier of the resource group
	 * @param resourceKey                    The unique identifier of the resource
	 * @param resourceConfig                 The resource configuration
	 * @param connectorStore                 Wraps all the connectors
	 */
	private static void updateResourceGroupTelemetryManagers(
		@NonNull final Map<String, TelemetryManager> resourceGroupTelemetryManagers,
		@NonNull final String resourceGroupKey,
		@NonNull final String resourceKey,
		final ResourceConfig resourceConfig,
		@NonNull final ConnectorStore connectorStore
	) {
		if (resourceConfig == null) {
			return;
		}

		try {
			// Create a new connector store for this resource configuration
			final ConnectorStore resourceConnectorStore = connectorStore.newConnectorStore();

			final HostConfiguration hostConfiguration = buildHostConfiguration(
				resourceConfig,
				resourceConfig.getConnectors(),
				resourceKey
			);

			// Validate protocols and update the configuration's hostname if required.
			validateAndNormalizeProtocols(resourceKey, resourceConfig, hostConfiguration.getHostname());

			// Read the configured connector for the current resource
			addConfiguredConnector(resourceConnectorStore, resourceConfig.getConnector());

			// Read connectors with configuration variables safely
			final AdditionalConnectorsParsingResult additionalConnectorsParsingResult =
				readConnectorsWithConfigurationVariablesSafe(resourceGroupKey, resourceKey, resourceConfig);

			// Overwrite resourceConnectorStore
			updateConnectorStore(resourceConnectorStore, additionalConnectorsParsingResult.getCustomConnectorsMap());

			// Add custom connectors to the host configuration.
			hostConfiguration.getConnectors().addAll(additionalConnectorsParsingResult.getHostConnectors());

			resourceGroupTelemetryManagers.putIfAbsent(
				resourceKey,
				TelemetryManager.builder().connectorStore(resourceConnectorStore).hostConfiguration(hostConfiguration).build()
			);
		} catch (Exception e) {
			log.warn(
				"Resource {} - Under the resource group configuration {}, the resource configuration {}" +
				" has been staged as invalid. Reason: {}",
				resourceKey,
				resourceGroupKey,
				resourceKey,
				e.getMessage()
			);
		}
	}

	/**
	 * Parse the connectors having configuration variables.
	 *
	 * @param resourceGroupKey The resource group key under which the resource is configured for logging purposes.
	 * @param resourceKey      The resource key for logging purposes.
	 * @param resourceConfig   The resource configuration.
	 * @return an AdditionalConnectorsParserResult which contains a map of connectors to force and a map of custom connectors.
	 */
	private static AdditionalConnectorsParsingResult readConnectorsWithConfigurationVariablesSafe(
		final String resourceGroupKey,
		final String resourceKey,
		final ResourceConfig resourceConfig
	) {
		// Call ConnectorVariablesLibraryParser and parse the additional connectors
		final ConnectorVariablesLibraryParser connectorVariablesLibraryParser = new ConnectorVariablesLibraryParser();

		try {
			return connectorVariablesLibraryParser.parse(
				ConfigHelper.getSubDirectory("connectors", false),
				resourceConfig.getAdditionalConnectors()
			);
		} catch (Exception e) {
			log.warn(
				"Resource {} - Under the resource group configuration {}, the resource configuration {} will not load connectors with configuration variables." +
				" Reason: {}.",
				resourceKey,
				resourceGroupKey,
				resourceKey,
				e.getMessage()
			);
			return new AdditionalConnectorsParsingResult();
		}
	}

	/**
	 * Add the custom connectors to the resource's connector store.
	 *
	 * @param resourceConnectorStore The connector store of the resource
	 * @param customConnectors       Map of customized connectors. E.g. Custom connectors that contain template variables
	 */
	protected static void updateConnectorStore(
		final ConnectorStore resourceConnectorStore,
		final Map<String, Connector> customConnectors
	) {
		// Add custom connectors
		resourceConnectorStore.addMany(customConnectors);
	}

	/**
	 * Normalizes the configuration of a configured connector by creating a
	 * unique identifier for it.
	 *
	 * @param resourceGroupKey    The resource group key.
	 * @param resourceKey         The resource key.
	 * @param configuredConnector The configured connector to be normalized.
	 */
	static void normalizeConfiguredConnector(
		final String resourceGroupKey,
		final String resourceKey,
		final Connector configuredConnector
	) {
		// Check if a configured connector exists
		if (configuredConnector != null) {
			// Create a unique connector identifier based on resource keys
			final ConnectorIdentity identity = configuredConnector.getOrCreateConnectorIdentity();
			final String connectorId = String.format("MetricsHub-Configured-Connector-%s-%s", resourceGroupKey, resourceKey);
			final String connectorName = String.format(
				"Configured Connector on resource %s (Group %s)",
				resourceKey,
				resourceGroupKey
			);

			// Set the compiled filename of the connector to the unique identifier
			identity.setCompiledFilename(connectorId);
			// Set the display name of the connector
			identity.setDisplayName(connectorName);
		}
	}

	/**
	 * Add the configured connector to the resource's connector store.
	 *
	 * @param resourceConnectorStore The resource's ConnectorStore
	 * @param configuredConnector    Configured connector
	 */
	static void addConfiguredConnector(final ConnectorStore resourceConnectorStore, final Connector configuredConnector) {
		// Check if a configured connector is available
		if (configuredConnector != null) {
			// Add the configured connector
			resourceConnectorStore.addOne(
				configuredConnector.getConnectorIdentity().getCompiledFilename(),
				configuredConnector
			);
		}
	}

	/**
	 * Validates the protocols configured under the given {@link ResourceConfig} instance.
	 * Also, it normalizes the configuration's hostname by duplicating the hostname attribute on each configuration.
	 * This duplication is done only if the configuration's hostname is null.
	 *
	 * @param resourceKey    Resource unique identifier
	 * @param resourceConfig {@link ResourceConfig} instance configured by the user.
	 * @param hostname       The hostname that will be duplicated on each configuration if required.
	 * @throws InvalidConfigurationException thrown if a configuration validation fails.
	 */
	private static void validateAndNormalizeProtocols(
		@NonNull final String resourceKey,
		final ResourceConfig resourceConfig,
		final String hostname
	) throws InvalidConfigurationException {
		final Map<String, IConfiguration> protocols = resourceConfig.getProtocols();
		if (protocols == null) {
			return;
		}

		for (Map.Entry<String, IConfiguration> entry : protocols.entrySet()) {
			IConfiguration protocolConfig = entry.getValue();
			if (protocolConfig != null) {
				if (protocolConfig.getHostname() == null) {
					protocolConfig.setHostname(hostname);
				}
				protocolConfig.validateConfiguration(resourceKey);
			}
		}
	}

	/**
	 * Build the {@link HostConfiguration} expected by the internal engine
	 *
	 * @param resourceConfig          User's resource configuration
	 * @param connectorsConfiguration User's connectors configuration directives.
	 * @param resourceKey             Resource unique identifier
	 * @return new {@link HostConfiguration} instance
	 */
	static HostConfiguration buildHostConfiguration(
		final ResourceConfig resourceConfig,
		final Set<String> connectorsConfiguration,
		final String resourceKey
	) {
		final Map<String, IConfiguration> protocols = resourceConfig.getProtocols();
		final Map<Class<? extends IConfiguration>, IConfiguration> protocolConfigurations = protocols == null
			? new HashMap<>()
			: protocols
				.values()
				.stream()
				.filter(Objects::nonNull)
				.collect(Collectors.toMap(IConfiguration::getClass, Function.identity()));

		final Map<String, String> attributes = resourceConfig.getAttributes();

		// Get the host name and make sure it is always set because the engine needs a hostname
		String hostname = attributes.get(MetricsHubConstants.HOST_NAME);
		if (hostname == null) {
			hostname = resourceKey;
		}

		// If we haven't a host.id then it will be set to the resource key
		String hostId = attributes.get("host.id");
		if (hostId == null) {
			hostId = resourceKey;
		}

		// Manage the device kind
		final DeviceKind hostType;
		String hostTypeAttribute = attributes.get("host.type");
		if (hostTypeAttribute == null) {
			hostType = DeviceKind.OTHER;
		} else {
			hostType = detectHostTypeFromAttribute(hostTypeAttribute);
		}

		String configuredConnectorId = null;
		// Retrieve the connector specified by the user in the metricshub.yaml configuration
		final Connector configuredConnector = resourceConfig.getConnector();
		// Check if a custom connector is defined
		if (configuredConnector != null) {
			// The custom connector is considered a selected connector from the engine's perspective
			configuredConnectorId = configuredConnector.getCompiledFilename();
		}

		Set<String> includedMonitors = null;
		Set<String> excludedMonitors = null;
		final Set<String> monitorFilters = resourceConfig.getMonitorFilters();

		if (monitorFilters != null && !monitorFilters.isEmpty()) {
			for (final String monitorFilter : monitorFilters) {
				if (monitorFilter != null && monitorFilter.length() > 1) {
					if (monitorFilter.startsWith("+")) {
						if (includedMonitors == null) {
							includedMonitors = new HashSet<>();
						}
						includedMonitors.add(monitorFilter.substring(1));
					} else if (monitorFilter.startsWith("!")) {
						if (excludedMonitors == null) {
							excludedMonitors = new HashSet<>();
						}
						excludedMonitors.add(monitorFilter.substring(1));
					}
				}
			}
		}

		return HostConfiguration
			.builder()
			.strategyTimeout(resourceConfig.getJobTimeout())
			.configurations(protocolConfigurations)
			.connectors(connectorsConfiguration)
			.hostname(hostname)
			.hostId(hostId)
			.hostType(hostType)
			.sequential(Boolean.TRUE.equals(resourceConfig.getSequential()))
			.enableSelfMonitoring(Boolean.TRUE.equals(resourceConfig.getEnableSelfMonitoring()))
			.includedMonitors(includedMonitors)
			.excludedMonitors(excludedMonitors)
			.configuredConnectorId(configuredConnectorId)
			.connectorVariables(resourceConfig.getConnectorVariables())
			.resolveHostnameToFqdn(resourceConfig.getResolveHostnameToFqdn())
			.build();
	}

	/**
	 * Try to detect the {@link DeviceKind} from the user input
	 *
	 * @param hostTypeAttribute
	 * @return {@link DeviceKind} enumeration value
	 */
	private static DeviceKind detectHostTypeFromAttribute(String hostTypeAttribute) {
		try {
			return DeviceKind.detect(hostTypeAttribute);
		} catch (Exception e) {
			return DeviceKind.OTHER;
		}
	}

	/**
	 * Utility method to get the directory path of the given file.
	 *
	 * @param file The file for which the directory path is needed.
	 * @return A {@link Path} instance representing the directory path of the given file.
	 */
	public static Path getDirectoryPath(final File file) {
		return file.getAbsoluteFile().toPath().getParent();
	}

	/**
	 * Read {@link MetricDefinitions} for the root monitor instance (Endpoint)
	 * which is automatically created by the MetricsHub engine
	 * This method deserializes the metrics configuration from the "metricshub-host-metrics.yaml" file.
	 *
	 * @return A new {@link MetricDefinitions} instance representing the host metric definitions.
	 * @throws IOException If an I/O error occurs while reading the configuration file.
	 */
	public static MetricDefinitions readHostMetricDefinitions() throws IOException {
		return JsonHelper.deserialize(
			newObjectMapper(),
			new ClassPathResource("metricshub-host-metrics.yaml").getInputStream(),
			MetricDefinitions.class
		);
	}

	/**
	 * Retrieves the metric definition map associated with the specified connector identifier
	 * within the provided {@link ConnectorStore}.<br>
	 * This method ensures that the metric for connector status is always included in the
	 * returned map, regardless of whether the specified connector has additional metrics defined or not.
	 *
	 * @param connectorStore Wrapper for all connectors.
	 * @param connectorId    The unique identifier of the connector.
	 * @return A Map of metric names to their definitions.
	 */
	public static Map<String, MetricDefinition> fetchMetricDefinitions(
		final ConnectorStore connectorStore,
		final String connectorId
	) {
		final Map<String, MetricDefinition> metricDefinitions = new HashMap<>();

		if (connectorStore != null && connectorId != null) {
			final Connector connector = connectorStore.getStore().get(connectorId);
			if (connector != null) {
				final Map<String, MetricDefinition> connectorMetricDefinitions = connector.getMetrics();
				if (connectorMetricDefinitions != null) {
					metricDefinitions.putAll(connectorMetricDefinitions);
				}
			}
		}

		// If the connector status metric is not already associated with a definition,
		// attempt to compute its definition.
		metricDefinitions.computeIfAbsent(
			MetricsHubConstants.CONNECTOR_STATUS_METRIC_KEY,
			key -> MetricsHubConstants.CONNECTOR_STATUS_METRIC_DEFINITION
		);

		return metricDefinitions;
	}

	/**
	 * Calculates the MD5 checksum of the specified file.
	 *
	 * @param file The file for which the MD5 checksum is to be calculated.
	 * @return The MD5 checksum as a hexadecimal string or <code>null</code> if the calculation has failed.
	 */
	public static String calculateMD5Checksum(final File file) {
		try {
			byte[] data = Files.readAllBytes(file.toPath());
			byte[] hash = MessageDigest.getInstance("MD5").digest(data);
			return new BigInteger(1, hash).toString(16);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Load the {@link ExtensionManager} instance from the extensions directory.
	 *
	 * @return new {@link ExtensionManager} instance.
	 */
	public static ExtensionManager loadExtensionManager() {
		try {
			return new ExtensionLoader(getSubDirectory("extensions", false).toFile()).load();
		} catch (Throwable e) {
			throw new IllegalStateException("Cannot load extensions.", e);
		}
	}

	/**
	 * Constructs and populates a {@link ConnectorStore} by aggregating connector
	 * stores from various extensions managed by the provided {@link ExtensionManager}
	 * and from a specific subdirectory defined for connectors. This method first
	 * aggregates all extension-based connector stores into one central store and
	 * then adds additional connectors found in a designated subdirectory.
	 *
	 * @param extensionManager       The manager responsible for handling all
	 *                               extension-based connector stores.
	 * @param connectorsPatchPath    The connectors Patch Path.
	 * @return A fully populated {@link ConnectorStore} containing connectors from
	 *         various sources.
	 */
	public static ConnectorStore buildConnectorStore(
		final ExtensionManager extensionManager,
		final String connectorsPatchPath
	) {
		// Get extension connector stores
		final ConnectorStore connectorStore = extensionManager.aggregateExtensionConnectorStores();

		// Parse and add connectors from a specific subdirectory
		connectorStore.addMany(new ConnectorStore(getSubDirectory("connectors", false)).getStore());

		// Add user's connectors if the connectors patch path is specified.
		if (connectorsPatchPath != null) {
			connectorStore.addMany(new ConnectorStore(Path.of(connectorsPatchPath)).getStore());
		}

		return connectorStore;
	}

	/**
	 * Checks if the given compression is suppress zeros.
	 *
	 * @param compression the compression to check.
	 * @return true if the compression is suppress zeros, false otherwise.
	 */
	public static boolean isSuppressZerosCompression(final String compression) {
		return StateSetMetricCompression.SUPPRESS_ZEROS.equalsIgnoreCase(compression);
	}
}
