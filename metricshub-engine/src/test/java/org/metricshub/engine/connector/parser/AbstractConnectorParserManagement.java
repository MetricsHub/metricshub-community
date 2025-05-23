package org.metricshub.engine.connector.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Function;
import org.metricshub.engine.connector.deserializer.DeserializerTest;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.IntermediateConnector;
import org.metricshub.engine.connector.model.RawConnector;

public abstract class AbstractConnectorParserManagement extends DeserializerTest {

	protected final ConnectorParser parser;
	protected final String relativePath;
	protected final Path connectorDirectory;

	public AbstractConnectorParserManagement(String relativePath, Function<Path, ConnectorParser> parserProducer) {
		this.relativePath = relativePath;
		this.connectorDirectory = Path.of(getResourcePath());
		this.parser = parserProducer.apply(connectorDirectory);
	}

	/**
	 * Parse the test.yaml connector and test the expected connector
	 *
	 * @throws IOException
	 */
	protected void test() throws IOException {
		final Connector test = parse("test");
		final Connector expected = getConnector("expected");

		expected.getConnectorIdentity().setCompiledFilename("test");

		assertEquals(expected, test);
	}

	protected Connector parse(String testFile) throws IOException {
		final ConnectorParser connectorParser = ConnectorParser.withNodeProcessor(
			connectorDirectory.resolve(testFile + ".yaml").getParent()
		);
		final RawConnector rawConnector = connectorParser.parseRaw(connectorDirectory.resolve(testFile + ".yaml").toFile());
		return ConnectorStoreComposer
			.builder()
			.withDeserializer(parser.getDeserializer())
			.withUpdateChain(parser.getConnectorUpdateChain())
			.build()
			.parseConnector(new IntermediateConnector(testFile, rawConnector));
	}
}
