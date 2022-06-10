package com.sentrysoftware.hardware.agent.service.opentelemetry.mapping;

import static java.nio.file.StandardOpenOption.*;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.sentrysoftware.hardware.agent.mapping.opentelemetry.MetricsMapping;
import com.sentrysoftware.hardware.agent.mapping.opentelemetry.dto.AbstractIdentifyingAttribute;
import com.sentrysoftware.hardware.agent.mapping.opentelemetry.dto.MetricInfo;
import com.sentrysoftware.matrix.connector.model.monitor.MonitorType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Generates the metrics.md file to be injested by
 * hws-otel-collector:maven-site-plugin.
 * 
 * @author brasseur
 *
 */
@Slf4j
public class MetricReport {

	private static final String MONITORS = "*Monitors*";
	private static final String PROJECT_NAME = "**${project.name}**";

	private static final String NAME_HEADING = "Metric Name";
	private static final String UNIT_HEADING = "Unit";
	private static final String ATTRIBUTES_HEADING = "Attributes";
	private static final String TYPE_HEADING = "Metric Type";
	private static final String DESCRIPTION_HEADING = "Description";

	private static final String METRIC_TABLE_DESCRIPTION = "The table below provides detailed information about the metrics scrapped by **${project.name}** for each Monitor and metric type.";

	private static final String DESCRIPTION_META = "description: How ${project.name} exposes hardware metrics.";

	private static final String KEYWORDS = "keywords: hardware, metrics, output";

	private static final String SECTION_HEADING_1 = "# ";

	private static final String SECTION_HEADING_2 = "## ";

	private static final String METRICS_HEADING = SECTION_HEADING_1 + "Metrics";

	private static final String NEWLINE = System.lineSeparator();

	private static final String FIXED_ATTRIBUTES_PREAMBLE = "> Note: these *Attributes* apply to all monitors: ";
	private static final String FIXED_ATTRIBUTES = "`agent.host.name`, `host.id`, `host.name`, `host.type`, `os.type`";

	private final File outputDirectory;

	private MetricReport(String outputDirectory) {
		this.outputDirectory = new File(outputDirectory);
	}

	public static void main(String[] args) {

		MetricReport report = new MetricReport(
				(args != null && args.length > 0) ? args[0] : "./hws-otel-collector/src/site/markdown/");
		try {
			report.createMetricReference();
		} catch (Exception e) {
			log.error(e.toString());
		}
	}

	/**
	 * Generates the metrics reference in markdown format to be consumed by the site
	 * generator
	 * 
	 * @throws IllegalArgumentException
	 * @throws IOException
	 */
	private void createMetricReference() throws IllegalArgumentException, IOException {

		// Do we have outputDirectory?
		if (outputDirectory == null) {
			String message = "outputDirectory is not defined";
			throw new IllegalArgumentException(message);
		}

		// Need to create outputDirectory?
		if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
			String message = "Could not create outputDirectory: " + outputDirectory.getAbsolutePath();
			throw new IOException(message);
		}

		// Assemble document
		String s = KEYWORDS + NEWLINE +
				DESCRIPTION_META + NEWLINE +
				NEWLINE +
				METRICS_HEADING + NEWLINE +
				NEWLINE +
				createMetricsDescription() + NEWLINE +
				FIXED_ATTRIBUTES_PREAMBLE + FIXED_ATTRIBUTES + NEWLINE + NEWLINE +
				METRIC_TABLE_DESCRIPTION +
				createMonitorTables();

		byte[] data = s.getBytes();

		try (OutputStream out = new BufferedOutputStream(
				Files.newOutputStream(Paths.get(outputDirectory.getPath() + "/metrics.md"), CREATE))) {
			out.write(data, 0, data.length);
		}
	}

	/**
	 * creates the string containing all the monitor metric tables
	 * 
	 * @return markdown formatted monitor tables
	 */
	private String createMonitorTables() {

		StringBuilder monitorTables = new StringBuilder();

		Stream.of(MonitorType.values()).forEach(monitorType -> {

			Map<String, List<MetricInfo>> metrics = MetricsMapping.getMatrixParamToMetricMap().get(monitorType);
			Set<String> attributes = MetricsMapping.getMonitorTypeToAttributeMap().get(monitorType).keySet();
			Map<String, List<MetricInfo>> metaToMetrics = MetricsMapping.getMatrixMetadataToMetricMap()
					.getOrDefault(monitorType, Collections.emptyMap());

			Set<String> formattedAttributes = formatAttributes(attributes);

			monitorTables.append(NEWLINE);
			monitorTables.append(NEWLINE);
			monitorTables.append(createMonitorSectionTitle(monitorType));
			monitorTables.append(NEWLINE);
			monitorTables.append(NEWLINE);

			Map<String, MetricData> metricNameToInfo = new HashMap<>();
			metrics.entrySet().forEach(entry -> {
				final List<MetricInfo> metricList = entry.getValue();
				for (MetricInfo metric : metricList) {
					if (metric != null) {
						metricNameToInfo.put(metric.getName(),
								new MetricData(metric, formattedAttributes.stream().collect(Collectors.joining(", "))
										+ getIdentifyingAttributes(metric)));
					}
				}
			});

			metaToMetrics.entrySet().stream().forEach(entry -> {
				final List<MetricInfo> metricList = entry.getValue();
				for (MetricInfo metric : metricList) {
					if (metric != null) {
						metricNameToInfo.put(metric.getName(),
								new MetricData(metric, formattedAttributes.stream().collect(Collectors.joining(", "))
										+ getIdentifyingAttributes(metric)));
					}
				}
			});

			Map<String, Integer> headerSizes = getHeaderSizes(metricNameToInfo);

			monitorTables.append(createTableHeader(headerSizes));

			monitorTables.append(createTableRows(headerSizes, metricNameToInfo));
		});

		return monitorTables.toString();
	}

	/**
	 * applies the monospace formatting to attributes
	 * 
	 * @param attributes
	 * @return markdown formatted attributes
	 */
	private Set<String> formatAttributes(Set<String> attributes) {
		Set<String> formattedAttributes = new HashSet<>();

		for (String attribute : attributes) {
			formattedAttributes.add('`' + attribute + '`');
		}
		return formattedAttributes;
	}

	/**
	 * creates the rows of the tables
	 * 
	 * @param headerSizes      formatting
	 * @param metricNameToInfo metrics for which we need rows
	 * @return markdown formatted table rows
	 */
	private String createTableRows(Map<String, Integer> headerSizes, Map<String, MetricData> metricNameToInfo) {
		StringBuilder row = new StringBuilder();

		for (Entry<String, MetricData> entry : metricNameToInfo.entrySet()) {
			row.append(NEWLINE);
			row.append(
					createMetricRow(entry.getValue().getMetricInfo(), entry.getValue().getAttributes(), headerSizes));
		}

		return row.toString();
	}

	/**
	 * create the metric description
	 * 
	 * @return markdown formatted metric description
	 */
	private String createMetricsDescription() {
		return PROJECT_NAME + " collects the health metrics of all the hardware components that compose your "
				+
				"servers, network switches, or storage systems and exposes them as " + MONITORS
				+ " in your monitoring platform(s)."
				+
				" Information specific to each *Monitor* is provided as *Attributes* to help you distinguish *Monitor instances*. "
				+
				"`device_id`, `host.name`, `vendor`, `serial_number`, `model` are for example some of the *Attributes* available for physical disks.";
	}

	/**
	 * creates the identifying attribute string for a metric
	 * 
	 * @param metric
	 * @return markdown formatted attributes string
	 */
	private static String getIdentifyingAttributes(MetricInfo metric) {

		StringBuilder attributes = new StringBuilder();
		if (metric.getIdentifyingAttributes() != null) {
			List<AbstractIdentifyingAttribute> identifyingAttr = metric.getIdentifyingAttributes();
			for (AbstractIdentifyingAttribute a : identifyingAttr) {
				attributes.append(", `" + a.getKey() + "`");
			}
		}
		return attributes.toString();
	}

	/**
	 * creates the section title 
	 * @param monitorType
	 * @return markdown formatted section title
	 */
	private String createMonitorSectionTitle(MonitorType monitorType) {
		return SECTION_HEADING_2
				+ (MonitorType.OTHER_DEVICE.equals(monitorType) ? "Other Device" : monitorType.getDisplayName());
	}

	/**
	 * creates the table header 
	 * @param headerSizes
	 * @return markdown formatted table header
	 */
	private String createTableHeader(Map<String, Integer> headerSizes) {
		return createHeaderCell(TYPE_HEADING, headerSizes.get(TYPE_HEADING)) +
				createHeaderCell(NAME_HEADING, headerSizes.get(NAME_HEADING)) +
				createHeaderCell(UNIT_HEADING, headerSizes.get(UNIT_HEADING)) +
				createHeaderCell(DESCRIPTION_HEADING, headerSizes.get(DESCRIPTION_HEADING)) +
				createHeaderCell(ATTRIBUTES_HEADING, headerSizes.get(ATTRIBUTES_HEADING)) + "|" + NEWLINE +
				createHeaderBottom(headerSizes);
	}

	/**
	 * gets the minimum size of table column widths to properly format table
	 * @param metricNameToInfo
	 * @return headersizes
	 */
	private Map<String, Integer> getHeaderSizes(@NonNull Map<String, MetricData> metricNameToInfo) {

		Map<String, Integer> headerSizes = new HashMap<>();

		headerSizes.put(TYPE_HEADING, TYPE_HEADING.length());
		headerSizes.put(NAME_HEADING, NAME_HEADING.length());
		headerSizes.put(UNIT_HEADING, UNIT_HEADING.length());
		headerSizes.put(DESCRIPTION_HEADING, DESCRIPTION_HEADING.length());
		headerSizes.put(ATTRIBUTES_HEADING, ATTRIBUTES_HEADING.length());

		metricNameToInfo.entrySet().forEach(entry -> {
			MetricData data = entry.getValue();

			if (data != null) {
				MetricInfo metric = data.metricInfo;
				String attributes = data.attributes;
				if (metric != null) {
					if (metric.getType().toString().length() > headerSizes.get(TYPE_HEADING)) {
						headerSizes.put(TYPE_HEADING, metric.getType().toString().length());
					}

					if (metric.getName().length() > headerSizes.get(NAME_HEADING)) {
						headerSizes.put(NAME_HEADING, metric.getName().length());
					}

					if (metric.getUnit().length() > headerSizes.get(UNIT_HEADING)) {
						headerSizes.put(UNIT_HEADING, metric.getUnit().length());
					}

					if (metric.getDescription().length() > headerSizes.get(DESCRIPTION_HEADING)) {
						headerSizes.put(DESCRIPTION_HEADING, metric.getDescription().length());
					}

					if (attributes.length() > headerSizes.get(ATTRIBUTES_HEADING)) {
						headerSizes.put(ATTRIBUTES_HEADING, attributes.length());
					}
				}
			}
		});

		return headerSizes;
	}

	/**
	 * creates the dashed bottom of table header
	 * @param headerSizes
	 * @return markdown formatted table header bottom
	 */
	private String createHeaderBottom(Map<String, Integer> headerSizes) {
		StringBuilder headerBottom = new StringBuilder("|");
		headerBottom.append(" " + createPad('-', headerSizes.get(TYPE_HEADING).intValue()) + " |");
		headerBottom.append(" " + createPad('-', headerSizes.get(NAME_HEADING).intValue()) + " |");
		headerBottom.append(" " + createPad('-', headerSizes.get(UNIT_HEADING).intValue()) + " |");
		headerBottom.append(" " + createPad('-', headerSizes.get(DESCRIPTION_HEADING).intValue()) + " |");
		headerBottom.append(" " + createPad('-', headerSizes.get(ATTRIBUTES_HEADING).intValue()) + " |");
		return headerBottom.toString();
	}

	private String createHeaderCell(String header, int minPadding) {

		int headerLength = header.length();
		int padding = minPadding - headerLength;

		return "| " + header + createPad(' ', padding) + " ";
	}

	/**
	 * creates a pad, with your choice of what the padding characted is
	 * 
	 * @param stuffing the char to stuff with
	 * @param padding  the amount of padding you need
	 * @return string of padding
	 */
	private String createPad(char stuffing, int padding) {
		StringBuilder pad = new StringBuilder();
		for (int i = 0; i < padding; i++) {
			pad.append(stuffing);
		}

		return pad.toString();
	}

	/**
	 * creates a table row for one metric
	 * 
	 * @param metric      the metric to print
	 * @param attributes  relevent attributes
	 * @param headerSizes to format the document
	 * @return formatted table row
	 */
	private String createMetricRow(MetricInfo metric, String attributes, Map<String, Integer> headerSizes) {
		String metricType = getMetricTypeDisplayName(metric);

		StringBuilder row = new StringBuilder();

		row.append(createTableCell(metricType, headerSizes.get(TYPE_HEADING)));
		row.append(createTableCell(metric.getName(), headerSizes.get(NAME_HEADING)));
		row.append(createTableCell(metric.getUnit(), headerSizes.get(UNIT_HEADING)));
		row.append(createTableCell(metric.getDescription(), headerSizes.get(DESCRIPTION_HEADING)));
		row.append(createTableCell(attributes, headerSizes.get(ATTRIBUTES_HEADING)));
		row.append("|");
		return row.toString();
	}

	/**
	 * gets the display safe version of MetricType
	 * @param metric
	 * @return
	 */
	private String getMetricTypeDisplayName(MetricInfo metric) {
		String metricType;
		if (MetricInfo.MetricType.GAUGE.equals(metric.getType())) {
			metricType = "Gauge";
		} else if (MetricInfo.MetricType.COUNTER.equals(metric.getType())) {
			metricType = "Counter";
		} else if (MetricInfo.MetricType.UP_DOWN_COUNTER.equals(metric.getType())) {
			metricType = "UpDownCounter";
		} else {
			throw new IllegalStateException("Unknown metric type : " + metric.getType());
		}
		return metricType;
	}

	private String createTableCell(String text, int padding) {
		return "| " + text + createPad(' ', padding - text.length()) + " ";
	}

	@Data
	@AllArgsConstructor
	static class MetricData {
		private MetricInfo metricInfo;
		private String attributes;

	}
}