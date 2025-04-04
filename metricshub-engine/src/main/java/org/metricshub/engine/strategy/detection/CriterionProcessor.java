package org.metricshub.engine.strategy.detection;

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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.SUCCESSFUL_OS_DETECTION_MESSAGE;

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.helpers.LocalOsHandler;
import org.metricshub.engine.common.helpers.VersionHelper;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.identity.criterion.CommandLineCriterion;
import org.metricshub.engine.connector.model.identity.criterion.Criterion;
import org.metricshub.engine.connector.model.identity.criterion.DeviceTypeCriterion;
import org.metricshub.engine.connector.model.identity.criterion.HttpCriterion;
import org.metricshub.engine.connector.model.identity.criterion.IpmiCriterion;
import org.metricshub.engine.connector.model.identity.criterion.ProcessCriterion;
import org.metricshub.engine.connector.model.identity.criterion.ProductRequirementsCriterion;
import org.metricshub.engine.connector.model.identity.criterion.ServiceCriterion;
import org.metricshub.engine.connector.model.identity.criterion.SnmpGetCriterion;
import org.metricshub.engine.connector.model.identity.criterion.SnmpGetNextCriterion;
import org.metricshub.engine.connector.model.identity.criterion.SqlCriterion;
import org.metricshub.engine.connector.model.identity.criterion.WbemCriterion;
import org.metricshub.engine.connector.model.identity.criterion.WmiCriterion;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.strategy.utils.CriterionProcessVisitor;
import org.metricshub.engine.telemetry.TelemetryManager;

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
public class CriterionProcessor {

	private static final String CONFIGURE_OS_TYPE_MESSAGE = "Configured OS type : ";

	private ClientsExecutor clientsExecutor;

	private ExtensionManager extensionManager;

	private TelemetryManager telemetryManager;

	private String connectorId;

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
				telemetryManager.getHostname(),
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
				.criterion(deviceTypeCriterion)
				.build();
		}

		return CriterionTestResult
			.builder()
			.message(SUCCESSFUL_OS_DETECTION_MESSAGE)
			.result(CONFIGURE_OS_TYPE_MESSAGE + deviceKind.name())
			.success(true)
			.criterion(deviceTypeCriterion)
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
		return processCriterionThroughExtension(httpCriterion);
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

		if (
			DeviceKind.WINDOWS.equals(hostType) ||
			DeviceKind.LINUX.equals(hostType) ||
			DeviceKind.SOLARIS.equals(hostType) ||
			DeviceKind.OOB.equals(hostType)
		) {
			return processCriterionThroughExtension(ipmiCriterion);
		}

		return CriterionTestResult
			.builder()
			.message(
				String.format(
					"Hostname %s - Failed to perform IPMI detection. %s is an unsupported OS for IPMI.",
					telemetryManager.getHostname(),
					hostType.name()
				)
			)
			.success(false)
			.build();
	}

	/**
	 * Process the given {@link CommandLineCriterion} through Client and return the {@link CriterionTestResult}
	 *
	 * @param commandLineCriterion The {@link CommandLineCriterion} to process.
	 * @return {@link CriterionTestResult} instance.
	 */
	@WithSpan("Criterion OS Command Exec")
	public CriterionTestResult process(@SpanAttribute("criterion.definition") CommandLineCriterion commandLineCriterion) {
		return processCriterionThroughExtension(commandLineCriterion);
	}

	/**
	 * Process the given {@link ProcessCriterion} through Client and return the {@link CriterionTestResult}
	 *
	 * @param processCriterion The {@link ProcessCriterion} to process.
	 * @return {@link CriterionTestResult} instance.
	 */
	@WithSpan("Criterion Process Exec")
	public CriterionTestResult process(@SpanAttribute("criterion.definition") ProcessCriterion processCriterion) {
		final String hostname = telemetryManager.getHostname();

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
				.criterion(processCriterion)
				.build();
		}

		if (!telemetryManager.getHostProperties().isLocalhost()) {
			log.debug("Hostname {} - Process criterion, not localhost.", hostname);
			return CriterionTestResult
				.builder()
				.success(true)
				.message("Process presence check: No test will be performed remotely.")
				.result(null)
				.criterion(processCriterion)
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
				.criterion(processCriterion)
				.build();
		}

		final CriterionProcessVisitor localOSVisitor = new CriterionProcessVisitor(
			extensionManager,
			processCriterion,
			hostname
		);

		maybeLocalOS.get().accept(localOSVisitor);
		final CriterionTestResult result = localOSVisitor.getCriterionTestResult();

		if (result != null) {
			result.setCriterion(processCriterion);
			return result;
		} else {
			return CriterionTestResult.error(
				processCriterion,
				"Process presence check: No result returned by the criterion processor."
			);
		}
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
			return CriterionTestResult.builder().success(true).criterion(productRequirementsCriterion).build();
		}

		return CriterionTestResult
			.builder()
			.success(
				VersionHelper.isVersionLessThanOtherVersion(
					productRequirementsCriterion.getEngineVersion(),
					VersionHelper.getClassVersion()
				)
			)
			.criterion(productRequirementsCriterion)
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
		return processCriterionThroughExtension(serviceCriterion);
	}

	/**
	 * Process the given {@link SnmpGetCriterion} through Client and return the {@link CriterionTestResult}
	 *
	 * @param snmpGetCriterion The SNMP Get criterion to process.
	 * @return The result of the criterion test.
	 */
	@WithSpan("Criterion SNMP Get Exec")
	public CriterionTestResult process(@SpanAttribute("criterion.definition") SnmpGetCriterion snmpGetCriterion) {
		return processCriterionThroughExtension(snmpGetCriterion);
	}

	/**
	 * Processes the given {@link Criterion} by attempting to find a suitable {@link IProtocolExtension} via the
	 * {@link ExtensionManager}. If an extension is found, it processes the criterion accordingly; otherwise,
	 * it returns an empty {@link CriterionTestResult}.
	 *
	 * @param criterion The criterion to be evaluated.
	 * @return A {@link CriterionTestResult} containing the outcome of the criterion processing, or an empty result if no suitable extension is found.
	 */
	@WithSpan("Criterion Exec Through Extension")
	public CriterionTestResult processCriterionThroughExtension(
		@SpanAttribute("criterion.definition") Criterion criterion
	) {
		final Optional<IProtocolExtension> maybeExtension = extensionManager.findCriterionExtension(
			criterion,
			telemetryManager
		);
		return maybeExtension
			.map(extension -> {
				CriterionTestResult result = extension.processCriterion(criterion, connectorId, telemetryManager);
				if (result != null) {
					result.setCriterion(criterion);
				}
				return result;
			})
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
		return processCriterionThroughExtension(snmpGetNextCriterion);
	}

	/**
	 * Process the given {@link WmiCriterion} through Client and return the {@link CriterionTestResult}
	 *
	 * @param wmiCriterion The WMI criterion to process.
	 * @return The result of the criterion test processing.
	 */
	@WithSpan("Criterion WMI Exec")
	public CriterionTestResult process(@SpanAttribute("criterion.definition") WmiCriterion wmiCriterion) {
		return processCriterionThroughExtension(wmiCriterion);
	}

	/**
	 * Process the given {@link WbemCriterion} through Client and return the {@link CriterionTestResult}
	 *
	 * @param wbemCriterion The WBEM criterion to process.
	 * @return The result of the criterion test processing.
	 */
	@WithSpan("Criterion WBEM Exec")
	public CriterionTestResult process(@SpanAttribute("criterion.definition") WbemCriterion wbemCriterion) {
		return processCriterionThroughExtension(wbemCriterion);
	}

	/**
	 * Process the given {@link SqlCriterion} through Client and return the {@link CriterionTestResult}
	 *
	 * @param sqlCriterion The SQL criterion to process.
	 * @return The result of the criterion test processing.
	 */
	@WithSpan("Criterion SQL Exec")
	public CriterionTestResult process(@SpanAttribute("criterion.definition") SqlCriterion sqlCriterion) {
		return processCriterionThroughExtension(sqlCriterion);
	}

	/**
	 * Test the given criterion and return the result.
	 *
	 * @param criterion The criterion to test.
	 * @return The result of the criterion test.
	 */
	public CriterionTestResult test(final Criterion criterion) {
		return CriterionProcessorRegistry.getProcessor(criterion).apply(criterion, this);
	}
}
