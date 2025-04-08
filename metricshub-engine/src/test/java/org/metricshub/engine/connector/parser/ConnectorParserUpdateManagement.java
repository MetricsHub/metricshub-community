package org.metricshub.engine.connector.parser;

public class ConnectorParserUpdateManagement extends AbstractConnectorParserManagement {

	public ConnectorParserUpdateManagement(String relativePath) {
		super(relativePath, dir -> ConnectorParser.withNodeProcessorAndUpdateChain(dir));
	}

	@Override
	public String getResourcePath() {
		return String.format("src/test/resources/test-files/%s/", relativePath);
	}
}
