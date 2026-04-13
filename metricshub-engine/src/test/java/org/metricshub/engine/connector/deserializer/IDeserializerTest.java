package org.metricshub.engine.connector.deserializer;

import java.io.IOException;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.connector.model.Connector;

interface IDeserializerTest {
	public Connector getConnector(String file) throws IOException;

	public String getResourcePath();

	static final String RESOURCE_EXT = ".yaml";

	static final String JSON_MAPPING_EXCEPTION_MSG = "Expected a JsonMappingException to be thrown";
	static final String INVALID_FORMAT_EXCEPTION_MSG = "Expected an InvalidFormatException to be thrown";
	static final String INVALID_NULL_EXCEPTION_MSG = "Expected an InvalidNullException to be thrown";
	static final String MISMATCHED_EXCEPTION_MSG = "Expected a MismatchedInputException to be thrown";
	static final String IO_EXCEPTION_MSG = "Expected an IOException to be thrown";

	static final ConnectorDeserializer deserializer = new ConnectorDeserializer(JsonHelper.buildYamlMapper());
}
