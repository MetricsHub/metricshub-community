package org.metricshub.engine.connector.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.configuration.AdditionalConnector;
import org.metricshub.engine.connector.deserializer.ConnectorDeserializer;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.RawConnectorStore;
import org.metricshub.engine.connector.model.monitor.SimpleMonitorJob;
import org.metricshub.engine.connector.model.monitor.task.source.compute.AbstractMatchingLines;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;

class ConnectorStoreComposerTest {

	private static final String MATCH_ALL_REGEX = ".*";

	private static ConnectorStoreComposer composer;

	/**
	 * Initializes the {@link ConnectorStoreComposer} using test connectors and the provided additional connector configurations.
	 *
	 * @param additionalConnectors a map of additional connector configurations keyed by their connector IDs
	 */
	void initComposer(final Map<String, AdditionalConnector> additionalConnectors) {
		final Path yamlTestPath = Paths.get("src", "test", "resources", "connectorStoreComposer");
		final RawConnectorStore rawConnectorStore = new RawConnectorStore(yamlTestPath);

		composer =
			ConnectorStoreComposer
				.builder()
				.withRawConnectorStore(rawConnectorStore)
				.withUpdateChain(ConnectorParser.createUpdateChain())
				.withDeserializer(new ConnectorDeserializer(JsonHelper.buildYamlMapper()))
				.withAdditionalConnectors(additionalConnectors)
				.build();
	}

	/**
	 * This test is conducted on three connectors:
	 * (a) Which does not contain variables
	 * (b) Which do contain variables
	 * (c) Which does not contain variables, but it's embedded file contains variables
	 *
	 */
	@Test
	void testGenerateStaticConnectorStore() {
		final Path yamlTestPath = Paths.get("src", "test", "resources", "connectorStoreComposer");
		final RawConnectorStore rawConnectorStore = new RawConnectorStore(yamlTestPath);

		// Raw connector store contain all the directory connectors.
		assertEquals(3, rawConnectorStore.getStore().size());

		initComposer(null);

		final ConnectorStore connectorStore = composer.generateStaticConnectorStore();

		// Static connector store does not contain connectors with variables
		// as the will be processed later for each resource
		final Map<String, Connector> store = connectorStore.getStore();
		assertEquals(1, store.size());
		assertTrue(connectorStore.getStore().containsKey("NoConnectorVariables"));

		final List<String> connectorsWithVariables = connectorStore.getConnectorsWithVariables();

		// All connectors containing a variable in their JsonNode or in their embedded files should be found
		assertEquals(2, connectorsWithVariables.size());
		assertTrue(connectorsWithVariables.containsAll(List.of("ConnectorVariables", "EmbeddedConnectorVariables")));
	}

	@Test
	void testResolveConnectorStoreVariables() {
		final AdditionalConnector linuxProcessGeneric = AdditionalConnector.builder().uses("ConnectorVariables").build();

		final AdditionalConnector linuxProcessMetricsHub = AdditionalConnector
			.builder()
			.uses("ConnectorVariables")
			.variables(Map.of("matchName", "MetricsHub"))
			.build();

		final AdditionalConnector linuxProcessComplete = AdditionalConnector
			.builder()
			.uses("ConnectorVariables")
			.variables(Map.of("matchName", "MetricsHub", "matchCommand", "./MetricsHubServiceManager", "matchUser", "admin"))
			.build();

		final AdditionalConnector windowsGeneric = AdditionalConnector
			.builder()
			.uses("EmbeddedConnectorVariables")
			.force(false)
			.build();

		final AdditionalConnector windowsSpecific = AdditionalConnector
			.builder()
			.uses("EmbeddedConnectorVariables")
			.variables(Map.of("columns", "Caption,Version,buildnumber,osarchitecture,csname"))
			.build();

		final Map<String, AdditionalConnector> additionalConnectors = Map.of(
			"LinuxProcessGeneric",
			linuxProcessGeneric,
			"LinuxProcessMetricsHub",
			linuxProcessMetricsHub,
			"LinuxProcessComplete",
			linuxProcessComplete,
			"WindowsGeneric",
			windowsGeneric,
			"WindowsSpecific",
			windowsSpecific
		);

		initComposer(additionalConnectors);

		// Generate static connector store
		final ConnectorStore store = composer.generateStaticConnectorStore();

		// Resolve connector store variables
		final AdditionalConnectorsParsingResult results = composer.resolveConnectorStoreVariables(store);

		// Verify that the store contains all connectors
		final Map<String, Connector> addedConnectors = results.getCustomConnectorsMap();

		// In addition to the five connectors that use LinusProcess and Windows with different variables values,
		// both LinuxProcess, and Windows are created using defaultValues. This ensures that if the user forces
		// these connectors, they will be available to use.
		assertEquals(7, addedConnectors.size());

		// Additional Connectors Parsing result must contain all the configured Additional Connectors
		assertTrue(
			addedConnectors
				.keySet()
				.containsAll(
					List.of(
						"LinuxProcessGeneric",
						"LinuxProcessMetricsHub",
						"LinuxProcessComplete",
						"WindowsGeneric",
						"WindowsSpecific",
						"EmbeddedConnectorVariables",
						"ConnectorVariables"
					)
				)
		);

		// Verify that connectors are correctly forced.
		assertTrue(
			results
				.getResourceConnectors()
				.containsAll(
					List.of(
						"+LinuxProcessGeneric",
						"+LinuxProcessMetricsHub",
						"+LinuxProcessComplete",
						"WindowsGeneric",
						"+WindowsSpecific"
					)
				)
		);

		// Connector with default variables only
		Connector connector = addedConnectors.get("LinuxProcessGeneric");
		List<Compute> computes = getLinuxProcessComputes(connector);

		// Check every regexp of the process compute
		assertEquals(MATCH_ALL_REGEX, ((AbstractMatchingLines) computes.get(1)).getRegExp());
		assertEquals(MATCH_ALL_REGEX, ((AbstractMatchingLines) computes.get(2)).getRegExp());
		assertEquals(MATCH_ALL_REGEX, ((AbstractMatchingLines) computes.get(3)).getRegExp());

		// Connector with some user variables, and some default variables.
		connector = addedConnectors.get("LinuxProcessMetricsHub");
		computes = getLinuxProcessComputes(connector);

		assertEquals("MetricsHub", ((AbstractMatchingLines) computes.get(1)).getRegExp());
		assertEquals(MATCH_ALL_REGEX, ((AbstractMatchingLines) computes.get(2)).getRegExp());
		assertEquals(MATCH_ALL_REGEX, ((AbstractMatchingLines) computes.get(3)).getRegExp());

		// Connector with all
		connector = addedConnectors.get("LinuxProcessComplete");
		computes = getLinuxProcessComputes(connector);

		assertEquals("MetricsHub", ((AbstractMatchingLines) computes.get(1)).getRegExp());
		assertEquals("./MetricsHubServiceManager", ((AbstractMatchingLines) computes.get(2)).getRegExp());
		assertEquals("admin", ((AbstractMatchingLines) computes.get(3)).getRegExp());

		// Connector with a variable in the embedded file.
		// Default value will be used as the connector configuration didn't include custom value.
		// default value : '*'
		connector = addedConnectors.get("WindowsGeneric");
		assertEquals("SELECT * FROM Win32_OperatingSystem", connector.getEmbeddedFiles().get(1).getContentAsString());

		// Connector with a variable in the embedded file.
		// User value: Caption,Version,buildnumber,osarchitecture,csname
		connector = addedConnectors.get("WindowsSpecific");
		assertEquals(
			"SELECT Caption,Version,buildnumber,osarchitecture,csname FROM Win32_OperatingSystem",
			connector.getEmbeddedFiles().get(1).getContentAsString()
		);
	}

	private List<Compute> getLinuxProcessComputes(final Connector connector) {
		return ((SimpleMonitorJob) connector.getMonitors().get("process")).getSimple()
			.getSources()
			.get("process")
			.getComputes();
	}
}
