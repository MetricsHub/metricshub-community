package org.metricshub.extension.win.detection;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Win Extension Common
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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.metricshub.extension.win.IWinConfiguration;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.WmiCriterion;
import org.sentrysoftware.metricshub.engine.strategy.detection.CriterionTestResult;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;

/**
 * A class responsible for processing WMI criteria to evaluate WMI queries against specified criteria.
 * It provides methods to perform WMI operations, finds possible WMI namespaces, detects WMI namespaces, evaluates the results
 * against expected outcomes, and generates criterion test results accordingly.
 */
@Slf4j
@RequiredArgsConstructor
public class WmiCriterionProcessor {

	/**
	 * WMI query used to get available WMI namespaces
	 */
	static final String NAMESPACE_WQL = "SELECT Name FROM __NAMESPACE";

	/**
	 * Root WMI namespace
	 */
	static final String ROOT_NAMESPACE = "root";

	/**
	 * Namespace prefix used for filtering
	 */
	private static final String ROOT_SLASH = "root/";

	/**
	 * Ignored WMI namespaces
	 */
	private static final Set<String> IGNORED_WMI_NAMESPACES = Set.of(
		"SECURITY",
		"RSOP",
		"Cli",
		"aspnet",
		"SecurityCenter",
		"WMI",
		"Policy",
		"DEFAULT",
		"directory",
		"subscription",
		"vm",
		"perform",
		"MSCluster",
		"MicrosoftActiveDirectory",
		"MicrosoftNLB",
		"Microsoft",
		"ServiceModel",
		"nap"
	);

	@NonNull
	private WmiDetectionService wmiDetectionService;

	@NonNull
	private Function<TelemetryManager, IWinConfiguration> configurationRetriever;

	@NonNull
	private String connectorId;

	/**
	 * Processes a WMI criterion by executing a WMI request and evaluating the result.
	 * The method retrieves Win configuration, executes the WMI request,
	 * and checks the result against the expected value defined in the criterion. It then returns a
	 * {@link CriterionTestResult} indicating the success or failure of the criterion evaluation.
	 *
	 * @param wmiCriterion     The criterion including the WMI query and the expected result.
	 * @param telemetryManager The telemetry manager providing access to host configuration and WMI/WinRm credentials.
	 * @return A {@link CriterionTestResult} representing the outcome of the criterion evaluation.
	 */
	public CriterionTestResult process(final WmiCriterion wmiCriterion, final TelemetryManager telemetryManager) {
		// Sanity check
		if (wmiCriterion == null) {
			return CriterionTestResult.error(wmiCriterion, "Malformed criterion. Cannot perform detection.");
		}
		// Find the configured Windows protocol (WMI or WinRM)
		final IWinConfiguration winConfiguration = configurationRetriever.apply(telemetryManager);

		if (winConfiguration == null) {
			return CriterionTestResult.error(wmiCriterion, "Neither WMI nor WinRM credentials are configured for this host.");
		}

		// Retrieve the hostname from the IWinConfiguration, otherwise from the telemetryManager
		final String hostname = telemetryManager.getHostname(List.of(winConfiguration.getClass()));

		// If namespace is specified as "Automatic"
		if (AUTOMATIC_NAMESPACE.equalsIgnoreCase(wmiCriterion.getNamespace())) {
			final String cachedNamespace = telemetryManager
				.getHostProperties()
				.getConnectorNamespace(connectorId)
				.getAutomaticWmiNamespace();

			// If not detected already, find the namespace
			if (cachedNamespace == null) {
				return findNamespace(connectorId, telemetryManager, hostname, winConfiguration, wmiCriterion);
			}

			// Update the criterion with the cached namespace
			final WmiCriterion cachedNamespaceCriterion = wmiCriterion.copy();
			cachedNamespaceCriterion.setNamespace(cachedNamespace);

			// Run the test
			return wmiDetectionService.performDetectionTest(hostname, winConfiguration, cachedNamespaceCriterion);
		}

		// Run the test
		return wmiDetectionService.performDetectionTest(hostname, winConfiguration, wmiCriterion);
	}

	/**
	 * Detect the WMI namespace applicable to the specified WMI criterion.
	 * <br>
	 * The namespace in the criterion must be "Automatic".
	 * <br>
	 *
	 * @param hostname           The host name
	 * @param configuration      Win configuration (credentials, timeout)
	 * @param wmiCriterion       WMI detection properties (WQL, expected result, namespace must be "Automatic")
	 * @param possibleNamespaces The possible namespaces to execute the WQL on
	 * @return A {@link NamespaceResult} wrapping the detected namespace
	 * and the error message if the detection fails.
	 */
	public NamespaceResult detectNamespace(
		final String hostname,
		final IWinConfiguration configuration,
		final WmiCriterion wmiCriterion,
		final Set<String> possibleNamespaces
	) {
		// Run the query on each namespace and check if the result match the criterion
		final Map<String, CriterionTestResult> namespaces = new TreeMap<>();
		final WmiCriterion tentativeCriterion = wmiCriterion.copy();

		// Loop over each namespace and run the WBEM query and check if the result matches
		for (final String namespace : possibleNamespaces) {
			// Update the criterion with the current namespace that needs to be tested
			tentativeCriterion.setNamespace(namespace);

			// Do the request
			final CriterionTestResult testResult = wmiDetectionService.performDetectionTest(
				hostname,
				configuration,
				tentativeCriterion
			);

			// If the result matched then the namespace is selected
			if (testResult.isSuccess()) {
				namespaces.put(namespace, testResult);
			} else {
				// If the test failed with an exception, we probably don't need to go further
				Throwable e = testResult.getException();
				if (e != null && !wmiDetectionService.getWinRequestExecutor().isAcceptableException(e)) {
					// This error indicates that the CIM server will probably never respond to anything
					// (timeout, or bad credentials), so there's no point in pursuing our efforts here.
					log.debug(
						"Hostname {} - Does not respond to {} requests. {}: {}\nCancelling namespace detection.",
						hostname,
						wmiCriterion.getClass().getSimpleName(),
						e.getClass().getSimpleName(),
						e.getMessage()
					);

					return NamespaceResult.builder().result(testResult).build();
				}
			}
		}

		// No namespace => failure
		if (namespaces.isEmpty()) {
			String formattedNamespaceList = possibleNamespaces.stream().collect(Collectors.joining("\n- "));
			return NamespaceResult
				.builder()
				.result(
					CriterionTestResult.failure(
						wmiCriterion,
						"None of the possible namespaces match the criterion:" + formattedNamespaceList
					)
				)
				.build();
		}

		// So, now we have a list of working namespaces.
		// We'd better have only one, but you never know, so try to be clever here...
		// If we have several matching namespaces, including root/cimv2, then exclude this one
		// because it's one that we find in many places and not necessarily with anything useful in it
		// especially if there are other matching namespaces.
		if (namespaces.size() > 1) {
			namespaces.remove("root/cimv2");
			namespaces.remove("root\\cimv2");
		}

		// Okay, so even if we have several, select a single one
		final String detectedNamespace = namespaces.keySet().stream().findFirst().orElseThrow();

		return NamespaceResult.builder().namespace(detectedNamespace).result(namespaces.get(detectedNamespace)).build();
	}

	/**
	 * Find the namespace to use for the execution of the given {@link WmiCriterion}.
	 *
	 * @param telemetryManager The telemetry manager providing access to host configuration and WMI credentials.
	 * @param hostname         The hostname of the device
	 * @param winConfiguration The WMI protocol configuration (credentials, etc.)
	 * @param wqlCriterion     The WMI criterion with an "Automatic" namespace
	 * @return A {@link CriterionTestResult} telling whether we found the proper namespace for the specified WQL
	 */
	CriterionTestResult findNamespace(
		final String connectorId,
		final TelemetryManager telemetryManager,
		final String hostname,
		final IWinConfiguration winConfiguration,
		final WmiCriterion wqlCriterion
	) {
		// Get the list of possible namespaces on this host
		Set<String> possibleWmiNamespaces = telemetryManager.getHostProperties().getPossibleWmiNamespaces();

		// Only one thread at a time must be figuring out the possible namespaces on a given host
		synchronized (possibleWmiNamespaces) {
			if (possibleWmiNamespaces.isEmpty()) {
				// If we don't have this list already, figure it out now
				final PossibleNamespacesResult possibleWmiNamespacesResult = findPossibleNamespaces(hostname, winConfiguration);

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
		NamespaceResult namespaceResult = detectNamespace(
			hostname,
			winConfiguration,
			wqlCriterion,
			Collections.unmodifiableSet(possibleWmiNamespaces)
		);

		// If that was successful, remember it in HostMonitoring, so we don't perform this
		// (costly) detection again
		if (namespaceResult.getResult().isSuccess()) {
			telemetryManager
				.getHostProperties()
				.getConnectorNamespace(connectorId)
				.setAutomaticWmiNamespace(namespaceResult.getNamespace());
		}

		return namespaceResult.getResult();
	}

	/**
	 * Find the possible WMI namespaces on specified hostname with specified credentials.
	 *
	 * @param hostname         The hostname of the device.
	 * @param winConfiguration Wmi configuration (credentials, timeout)
	 * @return A {@link PossibleNamespacesResult} wrapping the success state, the message in case of errors
	 * and the possibleWmiNamespaces {@link Set}.
	 */
	public PossibleNamespacesResult findPossibleNamespaces(
		final String hostname,
		final IWinConfiguration winConfiguration
	) {
		// If the user specified a namespace, we return it as if it was the only namespace available
		// and for which we're going to test our connector
		if (winConfiguration.getNamespace() != null && !winConfiguration.getNamespace().isBlank()) {
			return PossibleNamespacesResult
				.builder()
				.possibleNamespaces(Collections.singleton(winConfiguration.getNamespace()))
				.success(true)
				.build();
		}

		// Possible namespace will be stored in this set
		Set<String> possibleWmiNamespaces = new TreeSet<>();

		try {
			wmiDetectionService
				.getWinRequestExecutor()
				.executeWmi(hostname, winConfiguration, NAMESPACE_WQL, ROOT_NAMESPACE)
				.stream()
				.filter(row -> !row.isEmpty())
				.map(row -> row.get(0))
				.filter(Objects::nonNull)
				.filter(namespace -> !namespace.isBlank())
				.filter(namespace -> !namespace.toLowerCase().contains("interop"))
				.filter(namespace -> !IGNORED_WMI_NAMESPACES.contains(namespace))
				.filter(namespace ->
					IGNORED_WMI_NAMESPACES
						.stream()
						.noneMatch(ignoredNamespace -> (ROOT_SLASH + ignoredNamespace).equalsIgnoreCase(namespace))
				)
				.filter(namespace ->
					IGNORED_WMI_NAMESPACES
						.stream()
						.noneMatch(ignoredNamespace -> ("root\\" + ignoredNamespace).equalsIgnoreCase(namespace))
				)
				.map(namespace -> ROOT_SLASH + namespace)
				.forEach(possibleWmiNamespaces::add);
		} catch (final Exception e) {
			// Get the cause in the exception
			Throwable cause = e.getCause();

			String message = String.format(
				"Hostname %s - Does not respond to WMI requests. %s: %s%nCancelling namespace detection.",
				hostname,
				cause != null ? cause.getClass().getSimpleName() : e.getClass().getSimpleName(),
				cause != null ? cause.getMessage() : e.getMessage()
			);

			log.debug(message);

			return PossibleNamespacesResult.builder().errorMessage(message).success(false).build();
		}

		if (possibleWmiNamespaces.isEmpty()) {
			return PossibleNamespacesResult
				.builder()
				.errorMessage("No suitable namespace could be found to query host " + hostname + ".")
				.success(false)
				.build();
		}

		return PossibleNamespacesResult.builder().possibleNamespaces(possibleWmiNamespaces).success(true).build();
	}

	/**
	 * Data class representing the result of querying for possible namespaces.
	 * Provides information about the possible namespaces, success status, and an error message if applicable.
	 */
	@Data
	@Builder
	public static class PossibleNamespacesResult {

		private Set<String> possibleNamespaces;
		private boolean success;
		private String errorMessage;
	}

	/**
	 * Data class representing the result for a specific namespace.
	 * Contains information about the namespace itself and a CriterionTestResult.
	 */
	@Data
	@Builder
	public static class NamespaceResult {

		private String namespace;
		private CriterionTestResult result;
	}
}
