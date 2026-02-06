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
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
import org.metricshub.agent.context.MetricDefinitions;
import org.metricshub.agent.security.PasswordEncrypt;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.common.helpers.LocalOsHandler;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
import org.metricshub.engine.common.helpers.ResourceHelper;
import org.metricshub.engine.configuration.AdditionalConnector;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.connector.deserializer.ConnectorDeserializer;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.RawConnectorStore;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.identity.ConnectorIdentity;
import org.metricshub.engine.connector.model.metric.MetricDefinition;
import org.metricshub.engine.connector.model.monitor.MonitorJob;
import org.metricshub.engine.connector.parser.AdditionalConnectorsParsingResult;
import org.metricshub.engine.connector.parser.ConnectorParser;
import org.metricshub.engine.connector.parser.ConnectorStoreComposer;
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
	 * On Windows, the output directory is the local logs folder (relative to the working directory)
	 * if it is writable. If not, and if the LOCALAPPDATA path is not valid, then the output directory will be located
	 * under the installation directory.<br>
	 * On Linux, the output directory is located under the installation directory.
	 *
	 * @return {@link Path} instance
	 */
	public static Path getDefaultOutputDirectory() {
		if (LocalOsHandler.isWindows()) {
			try {
				final var subDirectory = getSubDirectory(LOG_DIRECTORY_NAME, true);
				// This should be always writable after directory creation
				// but let's double check in case of ACL changes after MetricsHub restart
				if (Files.isWritable(subDirectory)) {
					return subDirectory;
				}
			} catch (Exception ignored) {}

			final String localAppDataPath = System.getenv("LOCALAPPDATA");

			// Make sure the LOCALAPPDATA path is valid
			if (localAppDataPath != null && !localAppDataPath.isBlank()) {
				return createDirectories(Paths.get(localAppDataPath, PRODUCT_WIN_DIR_NAME, LOG_DIRECTORY_NAME));
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
	 * Find the application's configuration directory (E.g. /config).<br>
	 * <ol>
	 *   <li>If the user has configured the configDirectory via <em>--config=$Dir</em> then it is the chosen directory</li>
	 *   <li>Else if <em>config/</em> path exists, the resulting directory is the one representing this path</li>
	 *   <li>
	 *        In any case we copy <em>config/metricshub-example.yaml</em> to the host file <em>config/metricshub.yaml</em>
	 *        only if the configuration directory is empty
	 *    </li>
	 * </ol>
	 *
	 * The program fails if
	 * <ul>
	 *   <li>The configured directory path doesn't exist</li>
	 *   <li>config/metricshub-example.yaml is not present</li>
	 *   <li>If an I/O error occurs</li>
	 * </ul>
	 *
	 * @param userConfigDirectory The configuration directory passed by the user. E.g. --config=/opt/SOME_DIR
	 * @return {@link Path} instance
	 * @throws IOException  This exception is thrown is the directory is not found
	 */
	public static Path findConfigDirectory(final String userConfigDirectory) throws IOException {
		final Path configDirectory;
		// The user has configured a configuration directory path
		if (userConfigDirectory != null && !userConfigDirectory.isBlank()) {
			configDirectory = Path.of(userConfigDirectory);
			if (!Files.exists(configDirectory)) {
				throw new IllegalStateException(
					String.format(
						"Cannot find %s. Please make sure the configuration directory exists on your system",
						userConfigDirectory
					)
				);
			}
		} else {
			// The user has not configured a configuration directory path
			// The solution will start with the default configuration directory
			configDirectory = getDefaultConfigDirectoryPath();
		}

		// Generate the configuration file config/metricshub.yaml if not present
		generateDefaultConfigurationFileIfEmptyDir(configDirectory);

		// Set the configuration files write-permissions on Windows to allow writing
		setUserPermissionsOnWindows(configDirectory);

		return configDirectory;
	}

	/**
	 * Get the default configuration file path either in the Windows <em>ProgramData\MetricsHub\config</em>
	 * directory or under the installation directory <em>/opt/metricshub/lib/config</em> on Linux systems.
	 *
	 * @return new {@link Path} instance
	 */
	public static Path getDefaultConfigDirectoryPath() {
		if (LocalOsHandler.isWindows()) {
			return getProgramDataConfigDirectory();
		}
		return ConfigHelper.getSubPath(CONFIG_DIRECTORY_NAME);
	}

	/**
	 * Get the configuration directory under the ProgramData windows directory.<br>
	 * If the ProgramData path is not valid then the configuration file will be located
	 * under the installation directory.
	 *
	 * @return new {@link Path} instance
	 */
	static Path getProgramDataConfigDirectory() {
		return getProgramDataPath()
			.stream()
			.map(path -> createDirectories(Paths.get(path, PRODUCT_WIN_DIR_NAME, CONFIG_DIRECTORY_NAME)).toAbsolutePath())
			.findFirst()
			.orElseGet(() -> ConfigHelper.getSubPath(CONFIG_DIRECTORY_NAME));
	}

	/**
	 * Generate the default configuration file if the configuration directory is empty.
	 * This method copies the example configuration file to the configuration directory
	 * if the directory is empty.
	 *
	 * @param configDirectory Directory of the configuration files.
	 *                        (e.g. /opt/metricshub/lib/config or %PROGRAMDATA%/metricshub/config)
	 * @throws IOException if the copy fails
	 */
	public static void generateDefaultConfigurationFileIfEmptyDir(final Path configDirectory) throws IOException {
		// Check if the configuration directory is not empty, means there are already configuration files
		try (Stream<Path> stream = Files.list(configDirectory).filter(Files::isRegularFile)) {
			// Check if the configuration directory is empty
			if (stream.count() > 0) {
				// The configuration directory is not empty! do nothing
				return;
			}
		}

		// Now we will proceed with a copy of the example file (e.g. metricshub-example.yaml to config/metricshub.yaml)
		final var exampleConfigPath = ConfigHelper.getSubPath(
			String.format(FILE_PATH_FORMAT, CONFIG_DIRECTORY_NAME, CONFIG_EXAMPLE_FILENAME)
		);

		// The actual configuration file
		final var targetConfigPath = configDirectory.resolve(DEFAULT_CONFIG_FILENAME);

		// User has messed up the installation directory and deleted the example file
		if (!Files.exists(exampleConfigPath)) {
			throw new IllegalStateException(
				String.format(
					"Cannot find '%s'. Please create the configuration file '%s' before starting the MetricsHub Agent.",
					exampleConfigPath.toAbsolutePath(),
					targetConfigPath.toAbsolutePath()
				)
			);
		}

		// Copy the example configuration file to the configuration directory
		Files.copy(exampleConfigPath, targetConfigPath, StandardCopyOption.REPLACE_EXISTING).toFile();
	}

	/**
	 * Set user write permissions on the configuration files
	 *
	 * @param configDirectory the configuration directory where all the configuration files are located
	 *
	 * @throws IOException if the permissions cannot be set
	 */
	static void setUserPermissionsOnWindows(final Path configDirectory) throws IOException {
		if (LocalOsHandler.isWindows()) {
			// Set write permissions configuration files on Windows
			try (Stream<Path> stream = Files.list(configDirectory).filter(Files::isRegularFile)) {
				// Set write permissions for all files in the configuration directory
				stream.forEach(ConfigHelper::setUserPermissions);
			}
		}
	}

	/**
	 * Finds the Windows "Users" group name for the current user by executing
	 * the {@code whoami /groups} command and searching for the well-known
	 * SID {@code S-1-5-32-545}.
	 *
	 * @return the localized "Users" group name if found, or {@code null} if not found or on error
	 */
	static String findUserGroup() {
		Process process = null;
		try {
			// Run the Windows "whoami /groups" command
			process = Runtime.getRuntime().exec("whoami /groups");

			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				// Filter for the "Users" group SID and extract the group name
				String group = reader
					.lines()
					.filter(line -> line.contains("S-1-5-32-545")) // SID for "Users"
					.map(line -> line.split("\\s+")[0]) // extract localized group name
					.findFirst()
					.orElse(null);

				process.waitFor();
				return group;
			}
		} catch (IOException e) {
			log.error("Failed to execute 'whoami /groups' command", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt(); // restore interrupted status
			log.error("Process was interrupted while waiting for completion", e);
		} catch (Exception e) {
			log.error("Unexpected error while finding user group", e);
		} finally {
			if (process != null) {
				process.destroy();
			}
		}
		return null;
	}

	/**
	 * Set write permission for this configuration file identified by its path.
	 *
	 * @param configPath the configuration file absolute path
	 */
	static void setUserPermissions(final Path configPath) {
		try {
			final GroupPrincipal users = configPath
				.getFileSystem()
				.getUserPrincipalLookupService()
				.lookupPrincipalByGroupName(findUserGroup());

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
			log.error("Could not set write permissions to file: {}. Error: {}", configPath.toString(), e.getMessage());
			log.debug("Exception: ", e);
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

		resourceConfig.setEnrichments(mergeEnrichments(agentConfig.getEnrichments(), resourceConfig.getEnrichments()));

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

		resourceConfig.setEnrichments(
			mergeEnrichments(resourceGroupConfig.getEnrichments(), resourceConfig.getEnrichments())
		);

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

		resourceGroupConfig.setEnrichments(
			mergeEnrichments(agentConfig.getEnrichments(), resourceGroupConfig.getEnrichments())
		);
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
	 * Resolve enrichment identifiers using override semantics.
	 *
	 * @param parentEnrichments enrichments defined at a higher level
	 * @param childEnrichments enrichments defined at a lower level
	 * @return child enrichments when set, otherwise parent enrichments
	 */
	public static List<String> mergeEnrichments(
		final List<String> parentEnrichments,
		final List<String> childEnrichments
	) {
		if (childEnrichments != null && !childEnrichments.isEmpty()) {
			return new ArrayList<>(childEnrichments);
		}
		return parentEnrichments != null ? new ArrayList<>(parentEnrichments) : new ArrayList<>();
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

			// Retrieve the resource additional connectors configuration
			final Map<String, AdditionalConnector> additionalConnectors = resourceConfig.getAdditionalConnectors();

			/*
				Normalize additionalConnectors configuration. If on or many fields aren't specified, this method will
				complete them according to the business logic.
			 */
			normalizeAdditionalConnectors(additionalConnectors);

			log.debug("Creating a store for resource {}:", resourceKey);

			// Retrieve the raw connector store
			final RawConnectorStore rawConnectorStore = resourceConnectorStore.getRawConnectorStore();

			// Read connectors with configuration variables safely
			final AdditionalConnectorsParsingResult additionalConnectorsParsingResult = ConnectorStoreComposer
				.builder()
				.withRawConnectorStore(rawConnectorStore)
				.withUpdateChain(ConnectorParser.createUpdateChain())
				.withDeserializer(new ConnectorDeserializer(rawConnectorStore.getMapperFromSubtypes()))
				.withAdditionalConnectors(additionalConnectors)
				.build()
				.resolveConnectorStoreVariables(resourceConnectorStore);

			// Overwrite resourceConnectorStore
			resourceConnectorStore.addMany(additionalConnectorsParsingResult.getCustomConnectorsMap());

			// Add custom connectors to the host configuration.
			hostConfiguration.getConnectors().addAll(additionalConnectorsParsingResult.getResourceConnectors());

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
	 * Updates the additional connectors configuration by ensuring that each entry has a valid
	 * {@code AdditionalConnector} object. If the connector or its {@code uses} field is null, it is set
	 * to the connector Id.
	 */
	public static void normalizeAdditionalConnectors(final Map<String, AdditionalConnector> additionalConnectors) {
		additionalConnectors
			.entrySet()
			.forEach(entry -> {
				final String connectorId = entry.getKey();
				final AdditionalConnector additionalConnector = entry.getValue();

				/*
					If the user only configures a key referencing the connector identifier, and the user does not provide
					a complete variables configuration for this connector, create a new object with default information.
				*/
				if (additionalConnector == null) {
					entry.setValue(AdditionalConnector.builder().uses(connectorId).build());
					return;
				}

				// If uses() is null, set it to the connectorId
				if (additionalConnector.getUses() == null) {
					additionalConnector.setUses(connectorId);
				}
			});
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
			.attributes(attributes)
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
		final String connectorId,
		final String monitorType
	) {
		final Map<String, MetricDefinition> metricDefinitions = new HashMap<>();

		if (connectorStore != null && connectorId != null) {
			final Connector connector = connectorStore.getStore().get(connectorId);
			if (connector != null) {
				final Map<String, MetricDefinition> connectorMetricDefinitions = connector.getMetrics();
				if (connectorMetricDefinitions != null) {
					metricDefinitions.putAll(connectorMetricDefinitions);
				}
				final MonitorJob monitorJob = connector.getMonitors().get(monitorType);
				// For Connector and Host monitor types, there are no monitor jobs
				if (monitorJob != null) {
					final Map<String, MetricDefinition> monitorMetrics = monitorJob.getMetrics();
					if (monitorMetrics != null) {
						metricDefinitions.putAll(monitorMetrics);
					}
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
	 * Calculates the MD5 checksum of the specified directory safely.
	 *
	 * @param dir         The directory for which the MD5 checksum is to be calculated.
	 * @param filesFilter A filter to apply to the files in the directory.
	 * @return The MD5 checksum as a hexadecimal string or <code>null</code> if the calculation has failed.
	 */
	public static String calculateDirectoryMD5ChecksumSafe(final Path dir, final Predicate<Path> filesFilter) {
		try {
			return calculateDirectoryMD5Checksum(dir, filesFilter);
		} catch (Exception e) {
			log.error("Error calculating checksum for directory: {}. Error: {}", dir, e.getMessage());
			log.debug("Exception: ", e);
			return null;
		}
	}

	/**
	 * Calculates the MD5 checksum of the specified directory.
	 *
	 * @param dir         The directory for which the MD5 checksum is to be calculated.
	 * @param filesFilter A filter to apply to the files in the directory.
	 * @return The MD5 checksum as a hexadecimal string.
	 * @throws NoSuchAlgorithmException if the MD5 algorithm is not available.
	 * @throws IOException              if an I/O error occurs while reading the directory.
	 */
	private static String calculateDirectoryMD5Checksum(final Path dir, final Predicate<Path> filesFilter)
		throws NoSuchAlgorithmException, IOException {
		var digest = MessageDigest.getInstance("MD5");

		final List<Path> files = new ArrayList<>();
		try (var stream = Files.walk(dir)) {
			files.addAll(stream.filter(Files::isRegularFile).filter(filesFilter).sorted().toList());
		}

		for (Path file : files) {
			// Include relative path in digest
			digest.update(dir.relativize(file).toString().getBytes(StandardCharsets.UTF_8));

			// Read file content and update digest
			byte[] content = Files.readAllBytes(file);
			digest.update(content);
		}

		return toHexString(digest.digest());
	}

	/**
	 * Converts a byte array to a hexadecimal string representation.
	 *
	 * @param hash The byte array to convert.
	 * @return The hexadecimal string representation of the byte array.
	 */
	private static String toHexString(byte[] hash) {
		var sb = new StringBuilder();
		for (byte b : hash) {
			sb.append(String.format("%02x", b));
		}
		return sb.toString();
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
	 * Builds a {@link ConnectorStore} by aggregating connectors from multiple sources:
	 * <ul>
	 *   <li>Extension-based connector stores managed by the provided {@link ExtensionManager}</li>
	 *   <li>Connectors located in the default internal subdirectory (e.g., <code>connectors/</code>)</li>
	 *   <li>Optionally, connectors found in a user-defined path</li>
	 * </ul>
	 *
	 * The method first aggregates all extension-based connectors into a central {@link RawConnectorStore},
	 * then supplements it with additional connectors from the connectors subdirectory and optional user path.
	 * Finally, it generates a {@link ConnectorStore} from the aggregated raw connector store.
	 *
	 * @param extensionManager     The manager responsible for discovering and providing connector extensions.
	 * @param connectorsPatchPath  An optional filesystem path pointing to additional user-defined connectors.
	 * @return A fully composed {@link ConnectorStore} containing all loaded connectors.
	 */
	public static ConnectorStore buildConnectorStore(
		final ExtensionManager extensionManager,
		final String connectorsPatchPath
	) {
		// Get extension raw connector stores
		final RawConnectorStore rawConnectorStore = extensionManager.aggregateExtensionRawConnectorStores();

		// Parse and add connectors from a specific subdirectory
		rawConnectorStore.addMany(new RawConnectorStore(getSubDirectory("connectors", false)).getStore());

		// Add user's connectors if the connectors patch path is specified.
		if (connectorsPatchPath != null) {
			rawConnectorStore.addMany(new RawConnectorStore(Path.of(connectorsPatchPath)).getStore());
		}

		// Generate the connector store from the raw connector store.
		final ConnectorStore connectorStore = ConnectorStoreComposer
			.builder()
			.withRawConnectorStore(rawConnectorStore)
			.withUpdateChain(ConnectorParser.createUpdateChain())
			.withDeserializer(new ConnectorDeserializer(rawConnectorStore.getMapperFromSubtypes()))
			.build()
			.generateStaticConnectorStore();

		log.info("Global Connector Store size: {}", connectorStore.getStore().size());
		log.info("Global Raw Connector Store size: {}", connectorStore.getRawConnectorStore().getStore().size());

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
