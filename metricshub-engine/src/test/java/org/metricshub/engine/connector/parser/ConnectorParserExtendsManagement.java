package org.metricshub.engine.connector.parser;

public class ConnectorParserExtendsManagement extends AbstractConnectorParserManagement {

	public ConnectorParserExtendsManagement(String relativePath) {
		super(relativePath, dir -> ConnectorParser.withNodeProcessor(dir));
	}

	@Override
	public String getResourcePath() {
		return String.format("src/test/resources/test-files/extends/management/%s/", relativePath);
	}
}
