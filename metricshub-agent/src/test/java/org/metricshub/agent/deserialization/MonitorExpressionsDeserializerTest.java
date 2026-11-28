package org.metricshub.agent.deserialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;

import com.fasterxml.jackson.core.json.JsonReadContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.agent.config.ResourceConfig;
import org.metricshub.agent.helper.ConfigHelper;
import org.metricshub.engine.connector.model.monitor.MonitorJob;
import org.metricshub.engine.connector.model.monitor.SimpleMonitorJob;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The mapping values of the monitors of a resource that start with "=" are AWK expressions (#1684), checked at load as
 * those of a connector.
 */
@ExtendWith(MockitoExtension.class)
class MonitorExpressionsDeserializerTest {

	@Mock
	private YAMLParser yamlParserMock;

	@Mock
	private JsonReadContext jsonReadContext;

	/**
	 * The monitors of a resource: a grafana simple job whose mapping holds the given name value.
	 */
	private static JsonNode monitors(final String name) throws IOException {
		return ConfigHelper.newObjectMapper().readTree("""
			grafana:
			  simple:
			    sources:
			      health:
			        type: http
			        url: https://localhost/api/health
			        computes:
			          - type: toTable
			    mapping:
			      source: ${source::monitors.grafana.simple.sources.health}
			      attributes:
			        id: = $1
			        name: %s
			""".formatted(name));
	}

	@Test
	void expressionsDeserialize() throws IOException {
		doReturn(monitors("'= $1 \" \" $2'")).when(yamlParserMock).readValueAs(JsonNode.class);
		doReturn(jsonReadContext).when(yamlParserMock).getParsingContext();
		doReturn(ResourceConfig.builder().build()).when(jsonReadContext).getCurrentValue();

		final Map<String, MonitorJob> result = new MonitorJobsDeserializer().deserialize(yamlParserMock, null);

		final Map<String, String> attributes = ((SimpleMonitorJob) result.get("grafana")).getSimple()
			.getMapping()
			.getAttributes();
		assertEquals("= $1", attributes.get("id"));
		assertEquals("= $1 \" \" $2", attributes.get("name"));
	}

	@Test
	void anExpressionThatDoesNotCompileIsRefused() throws IOException {
		doReturn(monitors("= foo($1)")).when(yamlParserMock).readValueAs(JsonNode.class);

		final IOException error = assertThrows(
			IOException.class,
			() -> new MonitorJobsDeserializer().deserialize(yamlParserMock, null)
		);
		assertTrue(error.getMessage().contains("EXPRESSION_SYNTAX"), error.getMessage());
	}
}
