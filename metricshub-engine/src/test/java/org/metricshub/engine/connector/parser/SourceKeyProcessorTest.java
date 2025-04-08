package org.metricshub.engine.connector.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.monitor.StandardMonitorJob;
import org.metricshub.engine.connector.model.monitor.task.source.Source;

class SourceKeyProcessorTest {

	@Test
	void testProcess() throws IOException {
		final Path yamlTestPath = Paths.get("src", "test", "resources", "test-files", "processor");
		final Map<String, Connector> connectors = new ConnectorLibraryParser()
			.parseConnectorsFromAllYamlFiles(yamlTestPath);

		final Connector connector = connectors.get("Connector");
		final StandardMonitorJob monitorJob = (StandardMonitorJob) connector.getMonitors().get("system");
		final Map<String, Source> discoverySources = monitorJob.getDiscovery().getSources();
		assertEquals("${source::monitors.system.discovery.sources.source_1}", discoverySources.get("source_1").getKey());
		assertEquals("${source::monitors.system.discovery.sources.source_2}", discoverySources.get("source_2").getKey());
		final Map<String, Source> collectSources = monitorJob.getCollect().getSources();
		assertEquals("${source::monitors.system.collect.sources.source_1}", collectSources.get("source_1").getKey());
		assertEquals("${source::monitors.system.collect.sources.source_2}", collectSources.get("source_2").getKey());
	}
}
