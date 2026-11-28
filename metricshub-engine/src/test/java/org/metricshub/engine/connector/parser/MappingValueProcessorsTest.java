package org.metricshub.engine.connector.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.connector.deserializer.ConnectorDeserializer;
import org.metricshub.engine.connector.model.Connector;

class MappingValueProcessorsTest {

	private static final ObjectMapper MAPPER = JsonHelper.buildYamlMapper();

	/**
	 * A connector whose mapping and compute use the given reference (<code>${constant::X}</code>,
	 * <code>${var::X}</code>), X holding <code>2</code> and EQ holding <code>=x</code>.
	 */
	private static String connector(final String referencePattern) {
		final String x = referencePattern.formatted("X");
		final String eq = referencePattern.formatted("EQ");
		return """
			connector:
			  displayName: Test
			  detection:
			    appliesTo: [ linux ]
			constants:
			  X: 2
			  EQ: "=x"
			  L: [ a, b ]
			  F: ${file::missing.awk}
			monitors:
			  m:
			    discovery:
			      sources:
			        s:
			          type: commandLine
			          commandLine: echo
			          computes:
			          - type: keepOnlyMatchingLines
			            column: 1
			            regExp: "=%1$s"
			      mapping:
			        source: ${source::monitors.m.discovery.sources.s}
			        attributes:
			          id: "= $1 * %1$s"
			          eq: "%2$s"
			          text: "a%1$s"
			""".formatted(x, eq);
	}

	private static JsonNode mapping(final JsonNode node) {
		return node.at("/monitors/m/discovery/mapping/attributes");
	}

	private static String compute(final JsonNode node) {
		return node.at("/monitors/m/discovery/sources/s/computes/0/regExp").asText();
	}

	@Test
	void constantsProcessor() throws IOException {
		final JsonNode node = new ConstantsProcessor(null).process(MAPPER.readTree(connector("${constant::%s}")));

		assertEquals("= $1 * ${constant::X}", mapping(node).get("id").asText());
		assertEquals("= \"=x\"", mapping(node).get("eq").asText());
		assertEquals("a2", mapping(node).get("text").asText());
		assertEquals("=2", compute(node));

		// The constants are kept, scalars as texts, a list left out, a reference as a placeholder that no embedded
		// file resolution loads
		assertEquals("2", node.at("/constants/X").textValue());
		assertNull(node.get("constants").get("L"));
		assertEquals("${reference::}", node.at("/constants/F").textValue());
		final Connector connector = new ConnectorDeserializer(MAPPER).deserialize(node);
		assertEquals(Map.of("X", "2", "EQ", "=x", "F", "${reference::}"), connector.getConstants());
	}

	@Test
	void connectorVariableProcessor() throws IOException {
		final JsonNode node = ConnectorVariableProcessor.builder()
			.connectorVariables(Map.of("X", "2", "EQ", "=x"))
			.build()
			.process(MAPPER.readTree(connector("${var::%s}")));

		assertEquals("= $1 * ${var::X}", mapping(node).get("id").asText());
		assertEquals("= \"=x\"", mapping(node).get("eq").asText());
		assertEquals("a2", mapping(node).get("text").asText());
		assertEquals("=2", compute(node));
	}

	@Test
	void environmentProcessor() throws IOException {
		final JsonNode node = new EnvironmentProcessor().process(MAPPER.readTree("""
			resources:
			  r:
			    attributes:
			      host: ${env::MH_UNSET_1684:-1}
			    monitors:
			      m:
			        simple:
			          mapping:
			            attributes:
			              a: ${env::MH_UNSET_1684:-=x}
			              b: = ${env::MH_UNSET_1684:-1}
			"""));

		final JsonNode attributes = node.at("/resources/r/monitors/m/simple/mapping/attributes");
		assertEquals("= \"=x\"", attributes.get("a").asText());
		assertEquals("= ${env::MH_UNSET_1684:-1}", attributes.get("b").asText());
		assertEquals("1", node.at("/resources/r/attributes/host").asText());
	}
}
