package org.metricshub.cli.service;

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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_CONNECTOR_ID;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_NAME;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_PARENT_ID;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.WHITE_SPACE;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.Ansi.Attribute;
import org.metricshub.agent.context.MetricDefinitions;
import org.metricshub.agent.helper.ConfigHelper;
import org.metricshub.engine.common.helpers.KnownMonitorType;
import org.metricshub.engine.common.helpers.NumberHelper;
import org.metricshub.engine.connector.model.metric.MetricDefinition;
import org.metricshub.engine.telemetry.MetricFactory;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.telemetry.metric.AbstractMetric;
import org.metricshub.engine.telemetry.metric.NumberMetric;
import org.metricshub.engine.telemetry.metric.StateSetMetric;

/**
 * Service class for printing the telemetry data obtained from the {@link TelemetryManager} in a human-readable format.
 *
 * <p>The {@code PrettyPrinterService} class provides methods to print monitor data, including attributes and metrics,
 * in a well-formatted manner. It supports different types of monitors, such as hosts, connectors, enclosures, blades,
 * disk controllers, and CPUs. The printing is done using ANSI escape codes for color and formatting.
 * </p>
 */
@Data
public class PrettyPrinterService {

	/**
	 * The header for the attributes section
	 */
	static final String ATTRIBUTES_HEADER = "Attributes:";

	// @formatter:off
	/**
	 * Defines the priority order for different monitor types
	 */
	public static final Map<String, Integer> MONITOR_TYPE_PRIORITY = Map.of(
		KnownMonitorType.HOST.getKey(), 1,
		KnownMonitorType.CONNECTOR.getKey(), 2,
		KnownMonitorType.ENCLOSURE.getKey(), 3,
		KnownMonitorType.BLADE.getKey(), 4,
		KnownMonitorType.DISK_CONTROLLER.getKey(), 5,
		KnownMonitorType.CPU.getKey(), 6
	);

	// @formatter:on

	@NonNull
	private TelemetryManager telemetryManager;

	@NonNull
	private PrintWriter printWriter;

	private MetricDefinitions hostMetricDefinitions;

	/**
	 * Constructs a new {@code PrettyPrinterService} instance with the specified {@link TelemetryManager} and {@link PrintWriter}.
	 *
	 * @param telemetryManager The {@code TelemetryManager} instance for retrieving telemetry data.
	 * @param printWriter      The {@code PrintWriter} instance for writing the formatted output.
	 * @throws IOException If an I/O error occurs while reading metric definitions for host monitors.
	 */
	public PrettyPrinterService(@NonNull TelemetryManager telemetryManager, @NonNull PrintWriter printWriter)
		throws IOException {
		this.telemetryManager = telemetryManager;
		this.printWriter = printWriter;
		hostMetricDefinitions = ConfigHelper.readHostMetricDefinitions();
	}

	/**
	 * Prints the current {@link TelemetryManager} result in a human-readable way.
	 *
	 * @param monitorTypes monitorTypes The set of monitor types to print.
	 *  			If null, all monitors are printed.
	 */
	public void print(Set<String> monitorTypes) {
		final Set<String> includedMonitorTypes = new HashSet<>();
		final Set<String> excludedMonitorTypes = new HashSet<>();

		if (monitorTypes != null) {
			for (String type : monitorTypes) {
				if (type.startsWith("+")) {
					includedMonitorTypes.add(type.substring(1));
				} else if (type.startsWith("!")) {
					excludedMonitorTypes.add(type.substring(1));
				}
			}
		}

		// Build monitor children hierarchy
		final MonitorChildren root = MonitorChildren.build(
			telemetryManager.getEndpointHostMonitor(),
			telemetryManager
				.getMonitors()
				.values()
				.stream()
				.map(Map::values)
				.flatMap(Collection::stream)
				.filter(monitor -> !monitor.isEndpointHost())
				.filter(monitor -> includedMonitorTypes.isEmpty() || includedMonitorTypes.contains(monitor.getType()))
				.filter(monitor -> !excludedMonitorTypes.contains(monitor.getType()))
				.toList()
		);

		// Run the print over the root element
		print(root, 0);
	}

	/**
	 * Print all children specified in the given monitor children
	 * <p>
	 * @param monitorChildren monitor children instances to be printed
	 * @param indentation How much indentation should be used to print these children
	 */
	private void print(final MonitorChildren monitorChildren, final int indentation) {
		// Get the monitor whose data must be printed
		final Monitor monitor = monitorChildren.getMonitor();
		final String monitorType = monitor.getType();
		addMargin(indentation);
		printWriter.print("- ");
		printWriter.print(monitorType);
		printWriter.print(": ");
		printWriter.println(Ansi.ansi().fgCyan().a(getMonitorDisplayName(monitor)).reset().toString());

		// Attributes
		printAttributes(monitor.getAttributes(), indentation + 2, ATTRIBUTES_HEADER);

		// Metrics
		printMetrics(monitor, indentation + 2);

		// Text Parameters
		printTextParameters(monitor, indentation + 2);

		// Add a new line
		printWriter.println();

		// Flush the stream
		// If the stream has saved any characters from the various write() methods in a buffer,
		// write them immediately to their intended destination
		printWriter.flush();

		// Generate a comparator to print monitors based on the predefined priority
		final Comparator<Entry<String, Set<MonitorChildren>>> comparator = Comparator.comparing(entry ->
			MONITOR_TYPE_PRIORITY.getOrDefault(entry.getKey(), Integer.MAX_VALUE)
		);
		// Modify the comparator to arrange other children using lexicographic order based on their type
		comparator.thenComparing(Entry::getKey);

		// Iterate through the children and use recursion to print each child
		monitorChildren
			.getChildren()
			.entrySet()
			.stream()
			.sorted(comparator)
			.forEach(entry -> {
				addMargin(indentation + 4);
				printWriter.println(Ansi.ansi().bold().a(entry.getKey()).boldOff().a(" monitors:").toString());
				entry.getValue().forEach(children -> print(children, indentation + 4));
			});
	}

	/**
	 * Print the metrics associated to the given monitor.
	 *
	 * @param monitor     Monitor instance whose metrics must be printed.
	 * @param indentation How much indentation to use.
	 */
	void printMetrics(final Monitor monitor, final int indentation) {
		// Get the metrics
		final Map<String, AbstractMetric> metrics = monitor.getMetrics();

		if (metrics == null || metrics.isEmpty()) {
			return;
		}

		// Print a new line
		printWriter.println();

		// Add the margin
		addMargin(indentation);

		// Add the header
		printWriter.println("Metrics:");

		// Store the maximum length of metric names
		final int metricMaxLength = metrics.keySet().stream().mapToInt(String::length).max().orElseThrow();

		// Generate a format string for formatting metric names.
		// For a metric name of 20 characters, include a padding of 5 white
		// spaces, resulting in the final format string: %-25s
		final String metricNameFormat = String.format("%%-%ds", metricMaxLength + 5);

		final Map<String, MetricDefinition> metricDefinitions;
		// Get host monitor's metric definitions
		if (monitor.isEndpointHost()) {
			metricDefinitions = hostMetricDefinitions.metrics();
		} else {
			// Retrieves the metric definitions for any other monitor
			metricDefinitions =
				ConfigHelper.fetchMetricDefinitions(
					telemetryManager.getConnectorStore(),
					monitor.getAttribute(MONITOR_ATTRIBUTE_CONNECTOR_ID)
				);
		}

		// Iterate through the metrics, printing each metric name along with its corresponding value
		metrics
			.entrySet()
			.stream()
			.filter(entry -> shouldDisplayKey(entry.getKey()))
			.filter(entry -> entry.getValue() != null)
			.sorted((entry1, entry2) -> entry1.getKey().compareToIgnoreCase(entry2.getKey()))
			.forEach(e -> {
				final String metricName = e.getKey();
				final AbstractMetric metric = e.getValue();

				// Format the metric name
				final String formattedMetricName = String.format(metricNameFormat, metricName);

				// Add margin
				addMargin(indentation);

				if (metric instanceof NumberMetric numberMetric) {
					final Optional<String> maybeUnit = fetchUnit(MetricFactory.extractName(metricName), metricDefinitions);
					// Handle NumberMetric
					printNumberMetric(formattedMetricName, numberMetric, maybeUnit);
				} else if (metric instanceof StateSetMetric stateSetMetric) {
					// Handle StateSetMetric
					printStateSetMetric(formattedMetricName, stateSetMetric);
				}
			});
	}

	/**
	 * Print the formatted metric name and its state value
	 *
	 * @param formattedMetricName Metric name formatted with right-padding
	 * @param stateSetMetric      Instance of {@link StateSetMetric} that specifies the state value to be printed
	 */
	private void printStateSetMetric(final String formattedMetricName, StateSetMetric stateSetMetric) {
		final String state = stateSetMetric.getValue();
		if (state == null) {
			return;
		}

		// Print the right color for this state
		switch (state) {
			case "ok":
				printWriter.println(Ansi.ansi().a(formattedMetricName).bold().fgBrightGreen().a(state).reset().toString());
				break;
			case "degraded":
				printWriter.println(
					Ansi.ansi().a(Attribute.INTENSITY_FAINT).a(formattedMetricName).bold().fgYellow().a(state).reset().toString()
				);
				break;
			case "failed":
				printWriter.println(
					Ansi.ansi().a(Attribute.INTENSITY_FAINT).a(formattedMetricName).bold().fgRed().a(state).reset().toString()
				);
				break;
			default:
				printWriter.println(
					Ansi.ansi().a(Attribute.INTENSITY_FAINT).a(formattedMetricName).bold().a(state).reset().toString()
				);
				break;
		}
	}

	/**
	 * Print the formatted metric name along with its numerical value and unit if present.
	 *
	 * @param formattedMetricName Metric name formatted with right-padding
	 * @param numberMetric        Instance of {@link NumberMetric} that specifies the number value to be printed
	 * @param maybeUnit           {@link Optional} that defines a unit for this metric
	 */
	private void printNumberMetric(
		final String formattedMetricName,
		final NumberMetric numberMetric,
		final Optional<String> maybeUnit
	) {
		final Double value = numberMetric.getValue();
		if (value == null) {
			return;
		}

		// Construct ANSI formatting for the metric name and value
		Ansi ansi = Ansi
			.ansi()
			.a(Attribute.INTENSITY_FAINT)
			.a(formattedMetricName)
			.bold()
			.a(NumberHelper.formatNumber(value));

		// Include the unit if present
		if (maybeUnit.isPresent()) {
			ansi = ansi.a(WHITE_SPACE).a(maybeUnit.get());
		}
		// Print the metric value
		printWriter.println(ansi.boldOff().reset().toString());
	}

	/**
	 * Get the display name of the given monitor
	 *
	 * @param monitor Monitor instance as defined by the core engine
	 * @return String value
	 */
	private String getMonitorDisplayName(final Monitor monitor) {
		String displayName = monitor.getAttribute(MONITOR_ATTRIBUTE_NAME);
		displayName = displayName != null ? displayName : monitor.formatIdentifyingAttributes();
		return displayName != null ? displayName : monitor.getId();
	}

	/**
	 * Print the given attributes.
	 *
	 * @param attributes  The attributes to be printed.
	 * @param indentation How much indentation to use.
	 * @param header      The header to be printed.
	 */
	void printAttributes(final Map<String, String> attributes, final int indentation, final String header) {
		// Print a new line
		printWriter.println();

		// Add the margin
		addMargin(indentation);

		// Add the header
		printWriter.println(header);

		// Iterate through the attributes, printing each attribute name along with its corresponding value
		attributes
			.entrySet()
			.stream()
			.filter(entry -> shouldDisplayKey(entry.getKey()))
			.filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
			.sorted((entry1, entry2) -> entry1.getKey().compareToIgnoreCase(entry2.getKey()))
			.forEach(e -> {
				addMargin(indentation);
				printWriter.println(
					Ansi.ansi().a(Attribute.INTENSITY_FAINT).a(e.getKey()).a(": ").reset().a(e.getValue().trim()).toString()
				);
			});
	}

	/**
	 * Print the text parameters associated to the given monitor
	 *
	 * @param monitor     The monitor whose text parameters must be printed
	 * @param indentation How much indentation to use
	 */
	void printTextParameters(final Monitor monitor, final int indentation) {
		final Map<String, String> textParameters = monitor.getLegacyTextParameters();
		if (textParameters.isEmpty()) {
			return;
		}

		// Print a new line
		printWriter.println();

		// Add the margin
		addMargin(indentation);

		// Add the header
		printWriter.println("Text parameters:");

		// Iterate through the text parameters, printing each parameter name along with its corresponding value
		textParameters
			.entrySet()
			.stream()
			.filter(entry -> shouldDisplayKey(entry.getKey()))
			.filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
			.sorted((entry1, entry2) -> entry1.getKey().compareToIgnoreCase(entry2.getKey()))
			.forEach(e -> {
				addMargin(indentation + 2);
				printWriter.println(
					Ansi
						.ansi()
						.a(Attribute.INTENSITY_FAINT)
						.a(e.getKey())
						.a(": ")
						.reset()
						.a("\n")
						.a(e.getValue().trim().indent(indentation + 2))
						.toString()
				);
			});
	}

	/**
	 * Should we display the given key
	 *
	 * @param key a identifier of the metric or the attribute
	 * @return boolean value
	 */
	private boolean shouldDisplayKey(final String key) {
		return !key.startsWith("__");
	}

	/**
	 * Prints the specified margin
	 *
	 * @param indentation Number of chars in indentation
	 */
	void addMargin(int indentation) {
		printWriter.print(WHITE_SPACE.repeat(indentation));
	}

	/**
	 * Retrieves the unit associated with the specified metric name from the provided metric definition map.
	 *
	 * @param metricName          The name of the metric, e.g., "hw.energy".
	 * @param metricDefinitionMap {@link Map} containing metric definitions.
	 * @return An {@link Optional} of {@link String} representing the unit of the metric;
	 *         an empty optional if the metric definition is absent, the metric is not found, or the unit is blank.
	 */
	static Optional<String> fetchUnit(final String metricName, final Map<String, MetricDefinition> metricDefinitionMap) {
		return Optional
			.ofNullable(metricDefinitionMap.get(metricName))
			.map(MetricDefinition::getUnit)
			.filter(unit -> Objects.nonNull(unit) && !unit.isBlank());
	}

	@Data
	@RequiredArgsConstructor
	private static class MonitorChildren implements Comparable<MonitorChildren> {

		private static final String MONITOR_ATTRIBUTE_PARENT_TYPE = "parent.type";
		private static final String MONITOR_ID_FORMAT = "%s_%s";

		@NonNull
		private Monitor monitor;

		private Map<String, Set<MonitorChildren>> children = new TreeMap<>();

		/**
		 * Add one instance of {@link MonitorChildren} to current set of children
		 *
		 * @param monitorChildren Monitor children instance
		 */
		private void addOne(final MonitorChildren monitorChildren) {
			children.computeIfAbsent(monitorChildren.getMonitor().getType(), t -> new TreeSet<>()).add(monitorChildren);
		}

		/**
		 * Build the MonitorChildren hierarchy based on the given list of
		 * monitors.
		 *
		 * @param rootMonitor  The root monitor instance
		 * @param monitors     The list of monitors
		 * @return The root of the hierarchy
		 */
		public static MonitorChildren build(final Monitor rootMonitor, final List<Monitor> monitors) {
			final MonitorChildren root = new MonitorChildren(rootMonitor);
			final Map<String, MonitorChildren> monitorMap = new HashMap<>();

			// Create MonitorChildren for each Monitor and put them in the map
			for (final Monitor monitorInstance : monitors) {
				final MonitorChildren monitorChildren = new MonitorChildren(monitorInstance);
				final String attributeId = monitorInstance.formatIdentifyingAttributes();
				final String monitorType = monitorInstance.getType();
				monitorMap.put(String.format(MONITOR_ID_FORMAT, monitorType, attributeId), monitorChildren);
			}

			// Loop over the children and build the hierarchy
			for (final MonitorChildren monitorChildren : monitorMap.values()) {
				final Optional<MonitorChildren> maybeParent = lookupParent(monitorMap, monitorChildren);
				maybeParent.ifPresentOrElse(parent -> parent.addOne(monitorChildren), () -> root.addOne(monitorChildren));
			}

			return root;
		}

		/**
		 * Looks up the parent {@link MonitorChildren} instance in the monitor map.
		 *
		 * @param monitorChildrenMap Map of {@link MonitorChildren} instances indexed by the monitor id
		 * @param monitorChildren    {@link MonitorChildren} instance representing the child
		 * @return {@link Optional} of {@link MonitorChildren} instance representing the parent
		 */
		private static Optional<MonitorChildren> lookupParent(
			final Map<String, MonitorChildren> monitorChildrenMap,
			final MonitorChildren monitorChildren
		) {
			String parentId = monitorChildren.getMonitor().getAttribute(MONITOR_ATTRIBUTE_PARENT_ID);
			if (parentId == null) {
				// Search any other attribute containing "parent.id" in the key
				parentId =
					monitorChildren
						.getMonitor()
						.getAttributes()
						.entrySet()
						.stream()
						.filter(attribute -> attribute.getKey().contains(MONITOR_ATTRIBUTE_PARENT_ID))
						.findFirst()
						.map(Entry::getValue)
						.orElse(null);
			}

			String parentType = monitorChildren.getMonitor().getAttribute(MONITOR_ATTRIBUTE_PARENT_TYPE);
			if (parentType == null) {
				// Search any other attribute containing "parent.type" in the key
				parentType =
					monitorChildren
						.getMonitor()
						.getAttributes()
						.entrySet()
						.stream()
						.filter(attribute -> attribute.getKey().contains(MONITOR_ATTRIBUTE_PARENT_TYPE))
						.findFirst()
						.map(Entry::getValue)
						.orElse(null);
			}

			return parentId != null && parentType != null
				? Optional.ofNullable(monitorChildrenMap.get(String.format(MONITOR_ID_FORMAT, parentType, parentId)))
				: Optional.empty();
		}

		@Override
		public int compareTo(final MonitorChildren other) {
			final String currentName = monitor.getAttribute(MONITOR_ATTRIBUTE_NAME);
			final String otherName = other.getMonitor().getAttribute(MONITOR_ATTRIBUTE_NAME);
			if (currentName != null && otherName != null) {
				return currentName.compareTo(otherName);
			}

			return monitor.getId().compareTo(other.getMonitor().getId());
		}
	}
}
