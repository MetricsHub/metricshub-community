package org.metricshub.engine.strategy.detection;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
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

import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;

/**
 * This class stages connector identifiers based on configured directives.
 * It facilitates the identification of connectors for automatic detection and forced inclusion or exclusion.
 */
@Slf4j
public class ConnectorStagingManager {

	/**
	 * Regular expression pattern for parsing configured connector directives.
	 */
	private static final Pattern CONFIGURED_CONNECTOR_PATTERN = Pattern.compile("^((?!!#)([#\\+!]?)|((!#)?))(.+)$");

	/**
	 * Regular expression pattern for matching included connectors for automatic detection.
	 */
	private static final Pattern INCLUDED_CONNECTORS_PATTERN = Pattern.compile("^(?![+!])(#?)(.+)$");

	/**
	 * The host name of the resource we currently monitor
	 */
	private String hostname;

	/**
	 * Whether logging is enabled for this manager.
	 */
	private boolean isLoggingEnabled;

	/**
	 * Constructs a ConnectorStagingManager with the given hostname and optional logging.
	 *
	 * @param hostname         The host name of the resource we currently monitor
	 * @param isLoggingEnabled Indicates whether logging is enabled for this manager.
	 */
	private ConnectorStagingManager(String hostname, boolean isLoggingEnabled) {
		this.hostname = hostname;
		this.isLoggingEnabled = isLoggingEnabled;
	}

	/**
	 * Constructs a ConnectorStagingManager with the given hostname and enables logging by default.
	 *
	 * @param hostname The host name of the resource we currently monitor
	 */
	public ConnectorStagingManager(String hostname) {
		this(hostname, true);
	}

	/**
	 * Constructs a ConnectorStagingManager with default settings and disable logging by default.
	 */
	public ConnectorStagingManager() {
		this(null, false);
	}

	/**
	 * Stage connector identifiers based on configured directives.
	 *
	 * @param connectorStore                The store containing connectors.
	 * @param configuredConnectorDirectives Set of configured directives for connectors.
	 * @return Staged connector identifiers for automatic detection and forced inclusion.
	 */
	public StagedConnectorIdentifiers stage(
		@NonNull final ConnectorStore connectorStore,
		final Set<String> configuredConnectorDirectives
	) {
		final Map<String, Connector> store = connectorStore.getStore();
		if (store == null || store.isEmpty()) {
			logIfEnabled(() -> log.error("Hostname {} - No connector available in the store. Detection will stop.", hostname)
			);
			return StagedConnectorIdentifiers.empty();
		}

		final Set<String> connectorIdentifiers = store.keySet();
		final Set<String> autoDetectionConnectorIds = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		final Set<String> forcedConnectorIds = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		final Set<String> excludedAutoDetectionConnectorIds = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

		// No connector is configured? The whole store will be used.
		if (configuredConnectorDirectives == null || configuredConnectorDirectives.isEmpty()) {
			autoDetectionConnectorIds.addAll(connectorIdentifiers);
		} else {
			// Do we have a tag or connectors to include?
			final boolean isIncludedConnectors = configuredConnectorDirectives
				.stream()
				.anyMatch(directive -> INCLUDED_CONNECTORS_PATTERN.matcher(directive).matches());

			// Loop over each configured connector build the
			// collection of connector identifiers that will be processed
			// by the detection
			for (String directive : configuredConnectorDirectives) {
				final Matcher matcher = CONFIGURED_CONNECTOR_PATTERN.matcher(directive);
				if (matcher.matches()) {
					final String prefix = matcher.group(1);
					final String configuredConnectorIdOrTag = matcher.group(5);

					excludedAutoDetectionConnectorIds.addAll(
						stageConnectorIdsAndGetRemovals(
							store,
							autoDetectionConnectorIds,
							forcedConnectorIds,
							prefix,
							configuredConnectorIdOrTag,
							isIncludedConnectors
						)
					);
				}
			}
		}

		// Remove excluded connectors
		autoDetectionConnectorIds.removeAll(excludedAutoDetectionConnectorIds);

		return new StagedConnectorIdentifiers(autoDetectionConnectorIds, forcedConnectorIds);
	}

	/**
	 * Stages connector identifiers for automatic detection or forced inclusion/exclusion based on configured directives.<br>
	 * Then returns the connector identifiers to be removed from the automatic detection connector identifiers set.
	 *
	 * @param store                       The map containing connectors, keyed by identifier.
	 * @param autoDetectionConnectorIds   Set for automatic detection connector identifiers.
	 * @param forcedConnectorIds          Set for forced inclusion connector identifiers.
	 * @param prefix                      Prefix indicating the type of directive (#, !#, +, !).
	 * @param configuredConnectorIdOrTag  Connector identifier or tag from configured directives.
	 * @param isIncludedConnectors        Whether existing directives include a tag or some connectors.
	 * @return Connector identifiers to be removed from the automatic detection connector identifiers set.
	 */
	private Set<String> stageConnectorIdsAndGetRemovals(
		final Map<String, Connector> store,
		final Set<String> autoDetectionConnectorIds,
		final Set<String> forcedConnectorIds,
		final String prefix,
		final String configuredConnectorIdOrTag,
		final boolean isIncludedConnectors
	) {
		// Connector selected for auto detection?
		if (prefix.isEmpty()) {
			// Add the connector identifier to the automatic detection connector identifiers set
			addConnectorIdToSet(store, autoDetectionConnectorIds, configuredConnectorIdOrTag);
		} else if ("#".equals(prefix)) {
			// Handle Connector Tag
			// For each connector check if it defines the configured tag
			// If yes, add its identifier to the automatic detection connector identifiers set
			autoDetectionConnectorIds.addAll(
				fetchConnectorIdsUsingPredicate(
					store,
					connectorEntry -> connectorEntry.getValue().hasTag(configuredConnectorIdOrTag)
				)
			);
		} else if ("!#".equals(prefix)) {
			// No tags or connectors to be included?
			if (!isIncludedConnectors) {
				// Keep all the connectors except those having the current tag
				autoDetectionConnectorIds.addAll(
					fetchConnectorIdsUsingPredicate(
						store,
						connectorEntry -> !connectorEntry.getValue().hasTag(configuredConnectorIdOrTag)
					)
				);
			}

			// Return the connector identifiers to remove
			// Make sure the connectors having the current tag will be removed
			return fetchConnectorIdsUsingPredicate(
				store,
				connectorEntry -> connectorEntry.getValue().hasTag(configuredConnectorIdOrTag)
			);
		} else if ("+".equals(prefix)) {
			// Add the connector to the set of forced connectors
			addConnectorIdToSet(store, forcedConnectorIds, configuredConnectorIdOrTag);

			// Make sure this connector id is removed from the automatic detection connector identifiers set
			return Set.of(configuredConnectorIdOrTag);
		} else if ("!".equals(prefix)) {
			// No tags or connectors to be included?
			if (!isIncludedConnectors) {
				// Keep all the connectors except the excluded connector
				autoDetectionConnectorIds.addAll(
					fetchConnectorIdsUsingPredicate(
						store,
						connectorEntry -> !connectorEntry.getKey().equalsIgnoreCase(configuredConnectorIdOrTag)
					)
				);
			}

			// Remove the connector id from the automatic detection connector identifiers set
			return Set.of(configuredConnectorIdOrTag);
		}

		return Collections.emptySet();
	}

	/**
	 * Retrieves connector identifiers from the provided store based on the specified predicate.
	 *
	 * @param store      The map containing connectors, keyed by identifier.
	 * @param predicate  The predicate used to filter connectors. Only connectors satisfying the predicate will be included.
	 * @return           A {@link Set} of connector identifiers that match the specified predicate.
	 */
	private Set<String> fetchConnectorIdsUsingPredicate(
		final Map<String, Connector> store,
		final Predicate<Entry<String, Connector>> predicate
	) {
		return store.entrySet().stream().filter(predicate::test).map(Entry::getKey).collect(Collectors.toSet());
	}

	/**
	 * Adds a Connector identifier to a set if present in the specified store; otherwise, logs a warning.
	 *
	 * @param store        The map containing Connectors, keyed by identifier.
	 * @param connectorSet The set to which the Connector will be added if present in the store.
	 * @param connectorId  The identifier of the configured Connector to be added.
	 */
	private void addConnectorIdToSet(
		final Map<String, Connector> store,
		final Set<String> connectorSet,
		final String connectorId
	) {
		if (store.containsKey(connectorId)) {
			connectorSet.add(connectorId);
		} else {
			logIfEnabled(() ->
				log.warn(
					"Hostname {} - The connector associated with {} is not present in the store. Detection will skip this connector.",
					hostname,
					connectorId
				)
			);
		}
	}

	/**
	 * Logs a message if logging is enabled.
	 *
	 * @param loggingTask The LoggingTask to execute if logging is enabled.
	 */
	private void logIfEnabled(final LoggingTask loggingTask) {
		if (isLoggingEnabled) {
			loggingTask.log();
		}
	}

	/**
	 * Represents the staged connector identifiers for automatic detection and forced inclusion.
	 */
	@Data
	@AllArgsConstructor
	public static class StagedConnectorIdentifiers {

		/**
		 * Set of connector identifiers for automatic detection.
		 */
		private Set<String> autoDetectionConnectorIds;

		/**
		 * Set of connector identifiers for forced inclusion.
		 */
		private Set<String> forcedConnectorIds;

		/**
		 * Creates an instance of StagedConnectorIdentifiers with empty sets.
		 *
		 * @return Empty StagedConnectorIdentifiers instance.
		 */
		private static StagedConnectorIdentifiers empty() {
			return new StagedConnectorIdentifiers(Collections.emptySet(), Collections.emptySet());
		}

		/**
		 * Checks if connectors have been staged for automatic detection.
		 *
		 * @return True if connectors have been staged for automatic detection; otherwise, false.
		 */
		public boolean isAutoDetectionStaged() {
			return !autoDetectionConnectorIds.isEmpty();
		}

		/**
		 * Checks if connectors have been staged as forced connectors.
		 *
		 * @return True if connectors have been staged as forced connectors; otherwise, false.
		 */
		public boolean isForcedStaging() {
			return !forcedConnectorIds.isEmpty();
		}
	}

	/**
	 * A functional interface for logging tasks.
	 */
	@FunctionalInterface
	private interface LoggingTask {
		/**
		 * Logs a message.
		 */
		void log();
	}
}
