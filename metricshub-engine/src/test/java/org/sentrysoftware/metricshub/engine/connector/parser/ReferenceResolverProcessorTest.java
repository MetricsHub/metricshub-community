package org.sentrysoftware.metricshub.engine.connector.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class ReferenceResolverProcessorTest {

	@Test
	void testProcessNode() throws IOException {
		final String json =
			"""
			pre:
			  source1:
			    type: http
			    leftTable: ${source::pre.source4} # ${source::pre.source4}
			  source2:
			    type: tableJoin
			    rightTable: ${source::source(1)} # ${source::pre.source(1)}
			monitors:
			  enclosure: # <object>
			    discovery: # <object> | <job> key possible values [ discovery, collect, simple ]
			      # Sources
			      sources: # <source-object>
			        # Http Source
			        httpSource1: # <source-object>
			          type: http
			        httpSource2: # <source-object>
			          # ${source::monitors.enclosure.discovery.sources.httpSource(1)}/${source::monitors.enclosure.discovery.sources.httpSource(1)}
			          type1: ${source::httpSource(1)}/${source::httpSource(1)}
			        http.source3: # <source-object>
			          # Reference remains unchanged
			          type1: ${source::monitors.enclosure.discovery.sources.httpSource(1)}
			        http-source4: # <source-object>
			          # ${source::monitors.enclosure.discovery.sources.http.source3}
			          type1: ${source::http.source3}
			        http_source5: # <source-object>
			          # ${source::monitors.enclosure.discovery.sources.http-source4}
			          type1: ${source::http-source4}
			        http.source6: # <source-object>
			          # ${source::monitors.enclosure.discovery.sources.http_source5}
			          type1: ${source::http_source5}
			        http.source7: # <source-object>
			          # ${source::monitors.enclosure.discovery.sources.http.source6}/${source::monitors.enclosure.discovery.sources.http-source4}
			          type1: ${source::http.source6}/${source::http-source4}
			      mapping:
			        source1: ${source::httpSource2} # ${source::monitors.enclosure.discovery.sources.httpSource2}
			    collect: # <object> | <job> key possible values [ discovery, collect, simple]
			      # Sources
			      sources: # <source-object>
			        # Http Source
			        httpSource1: # <source-object>
			          type: http
			        httpSource2: # <source-object>
			          type1: ${source::httpSource1} # ${source::monitors.enclosure.discovery.sources.httpSource1}
			      mapping:
			        source1: ${source::httpSource2} # ${source::monitors.enclosure.discovery.sources.httpSource2}
			  disk: # <object>
			    discovery: # <object> | <job> key possible values [ discovery, collect, simple ]
			      # Sources
			      sources: # <source-object>
			        # Http Source
			        httpSource1: # <source-object>
			          type2: http
			        httpSource2: # <source-object>
			          type1: ${source::monitors.disk.discovery.sources.httpSource1} # ${source::monitors.disk.discovery.sources.httpSource1}
			      mapping:
			        source1: ${source::monitors.disk.discovery.sources.httpSource2} # ${source::monitors.disk.discovery.sources.httpSource2}
			""";

		final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
		final JsonNode rootNode = objectMapper.readTree(json);

		JsonNode processedNode = ReferenceResolverProcessor
			.builder()
			.next(new ConstantsProcessor())
			.build()
			.processNode(rootNode);

		// Check that the relative source references are correctly replaced under the "pre" section
		assertEquals("${source::pre.source4}", processedNode.get("pre").get("source1").get("leftTable").asText());
		assertEquals("${source::pre.source(1)}", processedNode.get("pre").get("source2").get("rightTable").asText());

		// Check that the relative source references are correctly replaced under the "enclosure" monitor section
		assertEquals(
			"${source::monitors.enclosure.discovery.sources.httpSource(1)}/${source::monitors.enclosure.discovery.sources.httpSource(1)}",
			processedNode
				.get("monitors")
				.get("enclosure")
				.get("discovery")
				.get("sources")
				.get("httpSource2")
				.get("type1")
				.asText()
		);
		assertEquals(
			"${source::monitors.enclosure.discovery.sources.httpSource2}",
			processedNode.get("monitors").get("enclosure").get("discovery").get("mapping").get("source1").asText()
		);

		assertEquals(
			"${source::monitors.enclosure.discovery.sources.httpSource(1)}",
			processedNode
				.get("monitors")
				.get("enclosure")
				.get("discovery")
				.get("sources")
				.get("http.source3")
				.get("type1")
				.asText()
		);

		assertEquals(
			"${source::monitors.enclosure.discovery.sources.http.source3}",
			processedNode
				.get("monitors")
				.get("enclosure")
				.get("discovery")
				.get("sources")
				.get("http-source4")
				.get("type1")
				.asText()
		);

		assertEquals(
			"${source::monitors.enclosure.discovery.sources.http-source4}",
			processedNode
				.get("monitors")
				.get("enclosure")
				.get("discovery")
				.get("sources")
				.get("http_source5")
				.get("type1")
				.asText()
		);

		assertEquals(
			"${source::monitors.enclosure.discovery.sources.http_source5}",
			processedNode
				.get("monitors")
				.get("enclosure")
				.get("discovery")
				.get("sources")
				.get("http.source6")
				.get("type1")
				.asText()
		);

		assertEquals(
			"${source::monitors.enclosure.discovery.sources.http.source6}/${source::monitors.enclosure.discovery.sources.http-source4}",
			processedNode
				.get("monitors")
				.get("enclosure")
				.get("discovery")
				.get("sources")
				.get("http.source7")
				.get("type1")
				.asText()
		);

		assertEquals(
			"${source::monitors.enclosure.collect.sources.httpSource1}",
			processedNode
				.get("monitors")
				.get("enclosure")
				.get("collect")
				.get("sources")
				.get("httpSource2")
				.get("type1")
				.asText()
		);

		assertEquals(
			"${source::monitors.enclosure.collect.sources.httpSource2}",
			processedNode.get("monitors").get("enclosure").get("collect").get("mapping").get("source1").asText()
		);

		// Check that the relative source references are correctly replaced under the "disk" monitor section
		assertEquals(
			"${source::monitors.disk.discovery.sources.httpSource1}",
			processedNode.get("monitors").get("disk").get("discovery").get("sources").get("httpSource2").get("type1").asText()
		);
		assertEquals(
			"${source::monitors.disk.discovery.sources.httpSource2}",
			processedNode.get("monitors").get("disk").get("discovery").get("mapping").get("source1").asText()
		);
	}
}
