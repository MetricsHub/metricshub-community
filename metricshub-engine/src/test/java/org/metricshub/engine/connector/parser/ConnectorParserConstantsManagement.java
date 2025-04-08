package org.metricshub.engine.connector.parser;

public class ConnectorParserConstantsManagement extends AbstractConnectorParserManagement {

	public ConnectorParserConstantsManagement(String relativePath) {
		super(relativePath, dir -> ConnectorParser.withNodeProcessor(dir));
	}

	@Override
	public String getResourcePath() {
		return String.format("src/test/resources/test-files/constants/%s/", relativePath);
	}
}
