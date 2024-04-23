package org.sentrysoftware.metricshub.engine.strategy.detection;

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

import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.AUTOMATIC_NAMESPACE;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.SUCCESSFUL_OS_DETECTION_MESSAGE;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.TABLE_SEP;

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sentrysoftware.metricshub.engine.client.ClientsExecutor;
import org.sentrysoftware.metricshub.engine.common.helpers.LocalOsHandler;
import org.sentrysoftware.metricshub.engine.common.helpers.VersionHelper;
import org.sentrysoftware.metricshub.engine.configuration.IWinConfiguration;
import org.sentrysoftware.metricshub.engine.configuration.WbemConfiguration;
import org.sentrysoftware.metricshub.engine.connector.model.common.DeviceKind;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.CommandLineCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.DeviceTypeCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.HttpCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.IpmiCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.ProcessCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.ProductRequirementsCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.ServiceCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.SnmpGetCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.SnmpGetNextCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.WbemCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.WmiCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.WqlCriterion;
import org.sentrysoftware.metricshub.engine.extension.ExtensionManager;
import org.sentrysoftware.metricshub.engine.extension.IProtocolExtension;
import org.sentrysoftware.metricshub.engine.strategy.utils.CriterionProcessVisitor;
import org.sentrysoftware.metricshub.engine.strategy.utils.PslUtils;
import org.sentrysoftware.metricshub.engine.strategy.utils.WqlDetectionHelper;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;

/**
 * The `CriterionProcessor` class is responsible for processing various criteria,
 * facilitating detection operations related to different aspects such as IPMI, HTTP, SNMP, etc.
 * <p>
 * This class integrates with ClientsExecutor and TelemetryManager to execute criterion-specific
 * tests and log relevant information. It also utilizes a WqlDetectionHelper for Windows Management
 * Instrumentation (WMI) queries.
 * </p>
 * <p>
 * The class includes methods for processing different types of criteria, such as IpmiCriterion, HttpCriterion,
 * DeviceTypeCriterion.
 * </p>
 *
 */
@Slf4j
@Data
@NoArgsConstructor
public class CriterionProcessor implements ICriterionProcessor {

	private static final String NEITHER_WMI_NOR_WINRM_ERROR =
		"Neither WMI nor WinRM credentials are configured for this host.";
	private static final String WMI_QUERY = "SELECT Description FROM ComputerSystem";
	private static final String WMI_NAMESPACE = "root\\hardware";
	private static final String CONFIGURE_OS_TYPE_MESSAGE = "Configured OS type : ";

	private ClientsExecutor clientsExecutor;

	private ExtensionManager extensionManager;

	private TelemetryManager telemetryManager;

	private String connectorId;

	private WqlDetectionHelper wqlDetectionHelper;

	/**
	 * Constructor for the CriterionProcessor class.
	 *
	 * @param clientsExecutor The ClientsExecutor instance.
	 * @param telemetryManager      The TelemetryManager instance.
	 * @param connectorId           The connector ID.
	 */
	public CriterionProcessor(
		final ClientsExecutor clientsExecutor,
		final TelemetryManager telemetryManager,
		final String connectorId,
		final ExtensionManager extensionManager
	) {
		this.clientsExecutor = clientsExecutor;
		this.telemetryManager = telemetryManager;
		this.connectorId = connectorId;
		this.wqlDetectionHelper = new WqlDetectionHelper(clientsExecutor);
		this.extensionManager = extensionManager;
	}

	/**
	 * Process the given {@link DeviceTypeCriterion} and return the {@link CriterionTestResult}
	 *
	 * @param deviceTypeCriterion The DeviceTypeCriterion to process.
	 * @return New {@link CriterionTestResult} instance.
	 */
	@WithSpan("Criterion DeviceType Exec")
	public CriterionTestResult process(@SpanAttribute("criterion.definition") DeviceTypeCriterion deviceTypeCriterion) {
		if (deviceTypeCriterion == null) {
			log.error(
				"Hostname {} - Malformed DeviceType criterion {}. Cannot process DeviceType criterion detection.",
				telemetryManager.getHostConfiguration().getHostname(),
				deviceTypeCriterion
			);
			return CriterionTestResult.empty();
		}

		final DeviceKind deviceKind = telemetryManager.getHostConfiguration().getHostType();

		if (!isDeviceKindIncluded(Collections.singletonList(deviceKind), deviceTypeCriterion)) {
			return CriterionTestResult
				.builder()
				.message("Failed OS detection operation")
				.result(CONFIGURE_OS_TYPE_MESSAGE + deviceKind.name())
				.success(false)
				.build();
		}

		return CriterionTestResult
			.builder()
			.message(SUCCESSFUL_OS_DETECTION_MESSAGE)
			.result(CONFIGURE_OS_TYPE_MESSAGE + deviceKind.name())
			.success(true)
			.build();
	}

	/**
	 * Return true if the deviceKind in the deviceKindList is included in the DeviceTypeCriterion detection.
	 *
	 * @param deviceKindList      The list of DeviceKind values to check.
	 * @param deviceTypeCriterion The DeviceTypeCriterion for detection.
	 * @return True if the deviceKind in the deviceKindList is included; otherwise, false.
	 */
	public boolean isDeviceKindIncluded(
		final List<DeviceKind> deviceKindList,
		final DeviceTypeCriterion deviceTypeCriterion
	) {
		final Set<DeviceKind> keepOnly = deviceTypeCriterion.getKeep();
		final Set<DeviceKind> exclude = deviceTypeCriterion.getExclude();

		if (keepOnly != null && deviceKindList.stream().anyMatch(keepOnly::contains)) {
			return true;
		}

		if (exclude != null && deviceKindList.stream().anyMatch(exclude::contains)) {
			return false;
		}

		// If no osType is in KeepOnly or Exclude, then return true if KeepOnly is null or empty.
		return keepOnly == null || keepOnly.isEmpty();
	}

	/**
	 * Process the given {@link HttpCriterion} through Client and return the {@link CriterionTestResult}
	 *
	 * @param httpCriterion The HTTP criterion to process.
	 * @return New {@link CriterionTestResult} instance.
	 */
	@WithSpan("Criterion HTTP Exec")
	public CriterionTestResult process(@SpanAttribute("criterion.definition") HttpCriterion httpCriterion) {
		final Optional<IProtocolExtension> maybeExtension = extensionManager.findCriterionExtension(
			httpCriterion,
			telemetryManager
		);
		return maybeExtension
			.map(extension -> extension.processCriterion(httpCriterion, connectorId, telemetryManager))
			.orElseGet(CriterionTestResult::empty);
	}

	/**
	 * Process the given {@link IpmiCriterion} through Client and return the {@link CriterionTestResult}
	 *
	 * @param ipmiCriterion The IPMI criterion to process.
	 * @return CriterionTestResult instance.
	 */
	@WithSpan("Criterion IPMI Exec")
	public CriterionTestResult process(@SpanAttribute("criterion.definition") IpmiCriterion ipmiCriterion) {
		final DeviceKind hostType = telemetryManager.getHostConfiguration().getHostType();

		if (DeviceKind.WINDOWS.equals(hostType)) {
			return processWindowsIpmiDetection(ipmiCriterion);
		} else if (DeviceKind.LINUX.equals(hostType) || DeviceKind.SOLARIS.equals(hostType)) {
			return processUnixIpmiDetection(hostType, ipmiCriterion);
		} else if (DeviceKind.OOB.equals(hostType)) {
			return processOutOfBandIpmiDetection(ipmiCriterion);
		}

		return CriterionTestResult
			.builder()
			.message(
				String.format(
					"Hostname %s - Failed to perform IPMI detection. %s is an unsupported OS for IPMI.",
					telemetryManager.getHostConfiguration().getHostname(),
					hostType.name()
				)
			)
			.success(false)
			.build();
	}

	/**
	 * Process IPMI detection for the Windows (NT) system
	 *
	 * @param ipmiCriterion instance of IpmiCriterion
	 * @return new {@link CriterionTestResult} instance
	 */
	private CriterionTestResult processWindowsIpmiDetection(final IpmiCriterion ipmiCriterion) {
		final String hostname = telemetryManager.getHostConfiguration().getHostname();

		// Find the configured protocol (WinRM or WMI)
		final IWinConfiguration configuration = telemetryManager.getWinConfiguration();

		if (configuration == null) {
			return CriterionTestResult.error(ipmiCriterion, NEITHER_WMI_NOR_WINRM_ERROR);
		}

		WmiCriterion ipmiWmiCriterion = WmiCriterion.builder().query(WMI_QUERY).namespace(WMI_NAMESPACE).build();

		return wqlDetectionHelper.performDetectionTest(hostname, configuration, ipmiWmiCriterion);
	}

	/**
	 * Process IPMI detection for the Unix system
	 *
	 * @param hostType
	 * @return new {@link CriterionTestResult} instance
	 */
	private CriterionTestResult processUnixIpmiDetection(final DeviceKind hostType, IpmiCriterion ipmiCriterion) {
		final Optional<IProtocolExtension> maybeExtension = extensionManager.findCriterionExtension(
			ipmiCriterion,
			telemetryManager
		);
		return maybeExtension
			.map(extension -> extension.processCriterion(ipmiCriterion, connectorId, telemetryManager))
			.orElseGet(CriterionTestResult::empty);
	}

	/**
	 * Process IPMI detection for the Out-Of-Band device
	 *
	 * @return {@link CriterionTestResult} wrapping the status of the criterion execution
	 */
	private CriterionTestResult processOutOfBandIpmiDetection(IpmiCriterion ipmiCriterion) {
		final Optional<IProtocolExtension> maybeExtension = extensionManager.findCriterionExtension(
			ipmiCriterion,
			telemetryManager
		);
		return maybeExtension
			.map(extension -> extension.processCriterion(ipmiCriterion, connectorId, telemetryManager))
			.orElseGet(CriterionTestResult::empty);
	}

	/**
	 * Process the given {@link CommandLineCriterion} through Client and return the {@link CriterionTestResult}
	 *
	 * @param commandLineCriterion The {@link CommandLineCriterion} to process.
	 * @return {@link CriterionTestResult} instance.
	 */
	@WithSpan("Criterion OS Command Exec")
	public CriterionTestResult process(@SpanAttribute("criterion.definition") CommandLineCriterion commandLineCriterion) {
		final Optional<IProtocolExtension> maybeExtension = extensionManager.findCriterionExtension(
			commandLineCriterion,
			telemetryManager
		);
		return maybeExtension
			.map(extension -> extension.processCriterion(commandLineCriterion, connectorId, telemetryManager))
			.orElseGet(CriterionTestResult::empty);
	}

	/**
	 * Process the given {@link ProcessCriterion} through Client and return the {@link CriterionTestResult}
	 *
	 * @param processCriterion The {@link ProcessCriterion} to process.
	 * @return {@link CriterionTestResult} instance.
	 */
	@WithSpan("Criterion Process Exec")
	public CriterionTestResult process(@SpanAttribute("criterion.definition") ProcessCriterion processCriterion) {
		final String hostname = telemetryManager.getHostConfiguration().getHostname();

		if (processCriterion == null) {
			log.error(
				"Hostname {} - Malformed process criterion {}. Cannot process process detection.",
				hostname,
				processCriterion
			);
			return CriterionTestResult.empty();
		}

		if (processCriterion.getCommandLine().isEmpty()) {
			log.debug("Hostname {} - Process Criterion, Process Command Line is empty.", hostname);
			return CriterionTestResult
				.builder()
				.success(true)
				.message("Process presence check: No test will be performed.")
				.result(null)
				.build();
		}

		if (!telemetryManager.getHostProperties().isLocalhost()) {
			log.debug("Hostname {} - Process criterion, not localhost.", hostname);
			return CriterionTestResult
				.builder()
				.success(true)
				.message("Process presence check: No test will be performed remotely.")
				.result(null)
				.build();
		}

		final Optional<LocalOsHandler.ILocalOs> maybeLocalOS = LocalOsHandler.getOS();
		if (maybeLocalOS.isEmpty()) {
			log.debug("Hostname {} - Process criterion, unknown local OS.", hostname);
			return CriterionTestResult
				.builder()
				.success(true)
				.message("Process presence check: OS unknown, no test will be performed.")
				.result(null)
				.build();
		}

		final CriterionProcessVisitor localOSVisitor = new CriterionProcessVisitor(
			processCriterion.getCommandLine(),
			wqlDetectionHelper,
			hostname
		);

		maybeLocalOS.get().accept(localOSVisitor);
		return localOSVisitor.getCriterionTestResult();
	}

	/**
	 * Process the given {@link ProductRequirementsCriterion} and return the {@link CriterionTestResult}.
	 *
	 * @param productRequirementsCriterion The {@link ProductRequirementsCriterion} to process.
	 * @return {@link CriterionTestResult} instance.
	 */
	@WithSpan("Criterion ProductRequirements Exec")
	public CriterionTestResult process(
		@SpanAttribute("criterion.definition") ProductRequirementsCriterion productRequirementsCriterion
	) {
		// If there is no requirement, then no check is needed
		if (
			productRequirementsCriterion == null ||
			productRequirementsCriterion.getEngineVersion() == null ||
			productRequirementsCriterion.getEngineVersion().isBlank()
		) {
			return CriterionTestResult.builder().success(true).build();
		}

		return CriterionTestResult
			.builder()
			.success(
				VersionHelper.isVersionLessThanOtherVersion(
					productRequirementsCriterion.getEngineVersion(),
					VersionHelper.getClassVersion()
				)
			)
			.build();
	}

	/**
	 * Process the given {@link ServiceCriterion} through Client and return the {@link CriterionTestResult}
	 *
	 * @param serviceCriterion The {@link ServiceCriterion} to process.
	 * @return {@link CriterionTestResult} instance.
	 */
	@WithSpan("Criterion Service Exec")
	public CriterionTestResult process(@SpanAttribute("criterion.definition") ServiceCriterion serviceCriterion) {
		// Sanity checks
		if (serviceCriterion == null) {
			return CriterionTestResult.error(serviceCriterion, "Malformed Service criterion.");
		}

		// Find the configured protocol (WinRM or WMI)
		final IWinConfiguration winConfiguration = telemetryManager.getWinConfiguration();

		if (winConfiguration == null) {
			return CriterionTestResult.error(serviceCriterion, NEITHER_WMI_NOR_WINRM_ERROR);
		}

		// The host system must be Windows
		if (!DeviceKind.WINDOWS.equals(telemetryManager.getHostConfiguration().getHostType())) {
			return CriterionTestResult.error(serviceCriterion, "Host OS is not Windows. Skipping this test.");
		}

		// Our local system must be Windows
		if (!LocalOsHandler.isWindows()) {
			return CriterionTestResult.success(serviceCriterion, "Local OS is not Windows. Skipping this test.");
		}

		// Check the service name
		final String serviceName = serviceCriterion.getName();
		if (serviceName.isBlank()) {
			return CriterionTestResult.success(serviceCriterion, "Service name is not specified. Skipping this test.");
		}

		final String hostname = telemetryManager.getHostConfiguration().getHostname();

		// Build a new WMI criterion to check the service existence
		WmiCriterion serviceWmiCriterion = WmiCriterion
			.builder()
			.query(String.format("SELECT Name, State FROM Win32_Service WHERE Name = '%s'", serviceName))
			.namespace(WMI_NAMESPACE)
			.build();

		// Perform this WMI test
		CriterionTestResult wmiTestResult = wqlDetectionHelper.performDetectionTest(
			hostname,
			winConfiguration,
			serviceWmiCriterion
		);
		if (!wmiTestResult.isSuccess()) {
			return wmiTestResult;
		}

		// The result contains ServiceName;State
		final String result = wmiTestResult.getResult();

		// Check whether the reported state is "Running"
		if (result != null && result.toLowerCase().contains(TABLE_SEP + "running")) {
			return CriterionTestResult.success(
				serviceCriterion,
				String.format("The %s Windows Service is currently running.", serviceName)
			);
		}

		// We're here: no good!
		return CriterionTestResult.failure(
			serviceWmiCriterion,
			String.format("The %s Windows Service is not reported as running:\n%s", serviceName, result) //NOSONAR
		);
	}

	/**
	 * Process the given {@link SnmpGetCriterion} through Client and return the {@link CriterionTestResult}
	 *
	 * @param snmpGetCriterion The SNMP Get criterion to process.
	 * @return The result of the criterion test.
	 */
	@WithSpan("Criterion SNMP Get Exec")
	public CriterionTestResult process(@SpanAttribute("criterion.definition") SnmpGetCriterion snmpGetCriterion) {
		final Optional<IProtocolExtension> maybeExtension = extensionManager.findCriterionExtension(
			snmpGetCriterion,
			telemetryManager
		);
		return maybeExtension
			.map(extension -> extension.processCriterion(snmpGetCriterion, connectorId, telemetryManager))
			.orElseGet(CriterionTestResult::empty);
	}

	/**
	 * Process the given {@link SnmpGetNextCriterion} through Client and return the {@link CriterionTestResult}
	 *
	 * @param snmpGetNextCriterion The SNMP GetNext criterion to process.
	 * @return The result of the criterion test.
	 */
	@WithSpan("Criterion SNMP GetNext Exec")
	public CriterionTestResult process(@SpanAttribute("criterion.definition") SnmpGetNextCriterion snmpGetNextCriterion) {
		final Optional<IProtocolExtension> maybeExtension = extensionManager.findCriterionExtension(
			snmpGetNextCriterion,
			telemetryManager
		);
		return maybeExtension
			.map(extension -> extension.processCriterion(snmpGetNextCriterion, connectorId, telemetryManager))
			.orElseGet(CriterionTestResult::empty);
	}

	/**
	 * Process the given {@link WmiCriterion} through Client and return the {@link CriterionTestResult}
	 *
	 * @param wmiCriterion The WMI criterion to process.
	 * @return The result of the criterion test processing.
	 */
	@WithSpan("Criterion WMI Exec")
	public CriterionTestResult process(@SpanAttribute("criterion.definition") WmiCriterion wmiCriterion) {
		// Sanity check
		if (wmiCriterion == null) {
			return CriterionTestResult.error(wmiCriterion, "Malformed criterion. Cannot perform detection.");
		}

		final String hostname = telemetryManager.getHostConfiguration().getHostname();

		// Find the configured protocol (WinRM or WMI)
		final IWinConfiguration winConfiguration = telemetryManager.getWinConfiguration();

		if (winConfiguration == null) {
			return CriterionTestResult.error(wmiCriterion, NEITHER_WMI_NOR_WINRM_ERROR);
		}

		// If namespace is specified as "Automatic"
		if (AUTOMATIC_NAMESPACE.equalsIgnoreCase(wmiCriterion.getNamespace())) {
			final String cachedNamespace = telemetryManager
				.getHostProperties()
				.getConnectorNamespace(connectorId)
				.getAutomaticWmiNamespace();

			// If not detected already, find the namespace
			if (cachedNamespace == null) {
				return findNamespace(hostname, winConfiguration, wmiCriterion);
			}

			// Update the criterion with the cached namespace
			WqlCriterion cachedNamespaceCriterion = wmiCriterion.copy();
			cachedNamespaceCriterion.setNamespace(cachedNamespace);

			// Run the test
			return wqlDetectionHelper.performDetectionTest(hostname, winConfiguration, cachedNamespaceCriterion);
		}

		// Run the test
		return wqlDetectionHelper.performDetectionTest(hostname, winConfiguration, wmiCriterion);
	}

	/**
	 * Find the namespace to use for the execution of the given {@link WqlCriterion}.
	 *
	 * @param hostname         The hostname of the device
	 * @param winConfiguration The Win protocol configuration (credentials, etc.)
	 * @param wqlCriterion     The WQL criterion with an "Automatic" namespace
	 * @return A {@link CriterionTestResult} telling whether we found the proper namespace for the specified WQL
	 */
	CriterionTestResult findNamespace(
		final String hostname,
		final IWinConfiguration winConfiguration,
		final WqlCriterion wqlCriterion
	) {
		// Get the list of possible namespaces on this host
		Set<String> possibleWmiNamespaces = telemetryManager.getHostProperties().getPossibleWmiNamespaces();

		// Only one thread at a time must be figuring out the possible namespaces on a given host
		synchronized (possibleWmiNamespaces) {
			if (possibleWmiNamespaces.isEmpty()) {
				// If we don't have this list already, figure it out now
				final WqlDetectionHelper.PossibleNamespacesResult possibleWmiNamespacesResult =
					wqlDetectionHelper.findPossibleNamespaces(hostname, winConfiguration);

				// If we can't detect the namespace then we must stop
				if (!possibleWmiNamespacesResult.isSuccess()) {
					return CriterionTestResult.error(wqlCriterion, possibleWmiNamespacesResult.getErrorMessage());
				}

				// Store the list of possible namespaces in HostMonitoring, for next time we need it
				possibleWmiNamespaces.clear();
				possibleWmiNamespaces.addAll(possibleWmiNamespacesResult.getPossibleNamespaces());
			}
		}

		// Perform a namespace detection
		WqlDetectionHelper.NamespaceResult namespaceResult = wqlDetectionHelper.detectNamespace(
			hostname,
			winConfiguration,
			wqlCriterion,
			Collections.unmodifiableSet(possibleWmiNamespaces)
		);

		// If that was successful, remember it in HostMonitoring, so we don't perform this
		// (costly) detection again
		if (namespaceResult.getResult().isSuccess()) {
			if (wqlCriterion instanceof WmiCriterion) {
				telemetryManager
					.getHostProperties()
					.getConnectorNamespace(connectorId)
					.setAutomaticWmiNamespace(namespaceResult.getNamespace());
			} else {
				telemetryManager
					.getHostProperties()
					.getConnectorNamespace(connectorId)
					.setAutomaticWbemNamespace(namespaceResult.getNamespace());
			}
		}

		return namespaceResult.getResult();
	}

	/**
	 * Find the namespace to use for the execution of the given {@link WbemCriterion}.
	 *
	 * @param hostname          The hostname of the host device
	 * @param wbemConfiguration The WBEM protocol configuration (port, credentials, etc.)
	 * @param wbemCriterion     The WQL criterion with an "Automatic" namespace
	 * @return A {@link CriterionTestResult} telling whether we found the proper namespace for the specified WQL
	 */
	private CriterionTestResult findNamespace(
		final String hostname,
		final WbemConfiguration wbemConfiguration,
		final WbemCriterion wbemCriterion
	) {
		// Get the list of possible namespaces on this host
		Set<String> possibleWbemNamespaces = telemetryManager.getHostProperties().getPossibleWbemNamespaces();

		// Only one thread at a time must be figuring out the possible namespaces on a given host
		synchronized (possibleWbemNamespaces) {
			if (possibleWbemNamespaces.isEmpty()) {
				// If we don't have this list already, figure it out now
				final WqlDetectionHelper.PossibleNamespacesResult possibleWbemNamespacesResult =
					wqlDetectionHelper.findPossibleNamespaces(hostname, wbemConfiguration);

				// If we can't detect the namespace then we must stop
				if (!possibleWbemNamespacesResult.isSuccess()) {
					return CriterionTestResult.error(wbemCriterion, possibleWbemNamespacesResult.getErrorMessage());
				}

				// Store the list of possible namespaces in HostMonitoring, for next time we need it
				possibleWbemNamespaces.clear();
				possibleWbemNamespaces.addAll(possibleWbemNamespacesResult.getPossibleNamespaces());
			}
		}

		// Perform a namespace detection
		WqlDetectionHelper.NamespaceResult namespaceResult = wqlDetectionHelper.detectNamespace(
			hostname,
			wbemConfiguration,
			wbemCriterion,
			Collections.unmodifiableSet(possibleWbemNamespaces)
		);

		// If that was successful, remember it in HostMonitoring, so we don't perform this
		// (costly) detection again
		if (namespaceResult.getResult().isSuccess()) {
			telemetryManager
				.getHostProperties()
				.getConnectorNamespace(connectorId)
				.setAutomaticWbemNamespace(namespaceResult.getNamespace());
		}

		return namespaceResult.getResult();
	}

	/**
	 * Process the given {@link WbemCriterion} through Client and return the {@link CriterionTestResult}
	 *
	 * @param wbemCriterion The WBEM criterion to process.
	 * @return The result of the criterion test processing.
	 */
	@WithSpan("Criterion WBEM Exec")
	public CriterionTestResult process(@SpanAttribute("criterion.definition") WbemCriterion wbemCriterion) {
		// Sanity check
		if (wbemCriterion == null) {
			return CriterionTestResult.error(wbemCriterion, "Malformed criterion. Cannot perform detection.");
		}

		// Gather the necessary info on the test that needs to be performed
		final String hostname = telemetryManager.getHostConfiguration().getHostname();

		final WbemConfiguration wbemConfiguration = (WbemConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(WbemConfiguration.class);
		if (wbemConfiguration == null) {
			return CriterionTestResult.error(wbemCriterion, "The WBEM credentials are not configured for this host.");
		}

		// If namespace is specified as "Automatic"
		if (AUTOMATIC_NAMESPACE.equalsIgnoreCase(wbemCriterion.getNamespace())) {
			final String cachedNamespace = telemetryManager
				.getHostProperties()
				.getConnectorNamespace(connectorId)
				.getAutomaticWbemNamespace();

			// If not detected already, find the namespace
			if (cachedNamespace == null) {
				return findNamespace(hostname, wbemConfiguration, wbemCriterion);
			}

			// Update the criterion with the cached namespace
			WqlCriterion cachedNamespaceCriterion = wbemCriterion.copy();
			cachedNamespaceCriterion.setNamespace(cachedNamespace);

			// Run the test
			return wqlDetectionHelper.performDetectionTest(hostname, wbemConfiguration, cachedNamespaceCriterion);
		}

		// Run the test
		return wqlDetectionHelper.performDetectionTest(hostname, wbemConfiguration, wbemCriterion);
	}
}
