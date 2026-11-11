package org.metricshub.engine.connector.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ContentTypeFinding;
import org.metricshub.engine.connector.model.ContentTypeFinding.Rule;
import org.metricshub.engine.strategy.source.compute.golden.ComputeChainGoldenCorpus;

/**
 * The load-time content-type check: one connector per rule, loaded as the agent loads it (parser, update chain,
 * check), and a compliant connector without finding.
 */
class ContentTypeCheckTest {

	private static final String HEADER = """
		connector:
		  displayName: Test
		  detection:
		    appliesTo: [ linux ]
		    criteria:
		    - type: deviceType
		      keep: [ linux ]
		""";

	private static final String THING = "${source::monitors.thing.simple.sources.";

	@TempDir
	Path directory;

	/**
	 * The findings of a connector made of the header and the given body.
	 */
	private List<ContentTypeFinding> findings(final String body) throws IOException {
		Files.writeString(directory.resolve("Test.yaml"), HEADER + body);
		final Connector connector = ComputeChainGoldenCorpus.loadConnectors(directory).getStore().get("Test");
		assertNotNull(connector, "the connector loads");
		connector.getContentTypeFindings().forEach(finding -> assertEquals("Test", finding.connectorId()));
		return connector.getContentTypeFindings();
	}

	/**
	 * The findings as "RULE location" lines.
	 */
	private static Set<String> lines(final List<ContentTypeFinding> findings) {
		return findings.stream().map(finding -> finding.rule() + " " + finding.location()).collect(Collectors.toSet());
	}

	@Test
	void textFeedsTable() throws IOException {
		final List<ContentTypeFinding> findings = findings("""
			monitors:
			  thing:
			    simple:
			      sources:
			        text:
			          type: commandLine
			          commandLine: echo
			        table:
			          type: snmpTable
			          oid: 1.3
			          selectColumns: "1,2"
			        computed:
			          type: commandLine
			          commandLine: echo
			          computes:
			          - type: keepColumns
			            columnNumbers: "1"
			        joined:
			          type: tableJoin
			          leftTable: ${source::text}
			          rightTable: ${source::table}
			          leftKeyColumn: 1
			          rightKeyColumn: 1
			      mapping:
			        source: ${source::text}
			        attributes:
			          id: $1
			tools:
			  list:
			    description: Lists
			    sources:
			      out:
			        type: commandLine
			        commandLine: echo
			    result:
			      source: ${source::out}
			      columns: [ a ]
			""");
		assertEquals(
			Set.of(
				"TEXT_FEEDS_TABLE keepColumns #0",
				"TEXT_FEEDS_TABLE leftTable -> " + THING + "text}",
				"TEXT_FEEDS_TABLE monitors.thing.simple.mapping",
				"TEXT_FEEDS_TABLE tools.list.result"
			),
			lines(findings)
		);
		final ContentTypeFinding compute = findings.stream()
			.filter(finding -> finding.location().equals("keepColumns #0"))
			.findFirst()
			.orElseThrow();
		assertEquals(THING + "computed}", compute.sourceKey());
		assertEquals("TABLE", compute.expected());
		assertEquals("TEXT", compute.actual());
	}

	@Test
	void tableUnionOfTextsIsOneFinding() throws IOException {
		final List<ContentTypeFinding> findings = findings("""
			monitors:
			  thing:
			    simple:
			      sources:
			        a:
			          type: commandLine
			          commandLine: echo a
			        b:
			          type: commandLine
			          commandLine: echo b
			        union:
			          type: tableUnion
			          tables: [ "${source::a}", "${source::b}" ]
			          computes:
			          - type: awk
			            script: "{ print $1 }"
			            output: table
			      mapping:
			        source: ${source::union}
			        attributes:
			          id: $1
			""");
		assertEquals(Set.of("TABLE_UNION_OF_TEXTS tables"), lines(findings));
		assertEquals(1, findings.size());
		assertEquals(THING + "union}", findings.get(0).sourceKey());
	}

	@Test
	void mixedUnion() throws IOException {
		assertEquals(Set.of("MIXED_UNION tables", "TEXT_FEEDS_TABLE tables[0] -> " + THING + "text}"), lines(findings("""
			monitors:
			  thing:
			    simple:
			      sources:
			        text:
			          type: commandLine
			          commandLine: echo
			          computes:
			          - type: awk
			            script: "{ print $1 }"
			        table:
			          type: snmpTable
			          oid: 1.3
			          selectColumns: "1,2"
			        union:
			          type: tableUnion
			          tables: [ "${source::text}", "${source::table}" ]
			      mapping:
			        source: ${source::union}
			        attributes:
			          id: $1
			""")));
	}

	@Test
	void tableFeedsText() throws IOException {
		assertEquals(
			Set.of("TABLE_FEEDS_TEXT json2Table #0", "TABLE_FEEDS_TEXT texts[0] -> " + THING + "table}"),
			lines(findings("""
				monitors:
				  thing:
				    simple:
				      sources:
				        table:
				          type: snmpTable
				          oid: 1.3
				          selectColumns: "1,2"
				        parsed:
				          type: snmpTable
				          oid: 1.4
				          selectColumns: "1"
				          computes:
				          - type: json2Table
				        texts:
				          type: textUnion
				          texts: [ "${source::table}" ]
				          computes:
				          - type: awk
				            script: "{ print $1 }"
				            output: table
				      mapping:
				        source: ${source::texts}
				        attributes:
				          id: $1
				"""))
		);
	}

	@Test
	void stringLiteralRows() throws IOException {
		assertEquals(Set.of("STRING_LITERAL_ROWS tables[0]", "STRING_LITERAL_ROWS rightTable"), lines(findings("""
			monitors:
			  thing:
			    simple:
			      sources:
			        table:
			          type: snmpTable
			          oid: 1.3
			          selectColumns: "1,2"
			        union:
			          type: tableUnion
			          tables:
			          - "a;b;"
			          - [ [ c, d ] ]
			          - ${source::table}
			        joined:
			          type: tableJoin
			          leftTable: ${source::union}
			          rightTable: "a;x;"
			          leftKeyColumn: 1
			          rightKeyColumn: 1
			        literalJoin:
			          type: tableJoin
			          leftTable: [ [ a, b ] ]
			          rightTable: ${source::joined}
			          leftKeyColumn: 1
			          rightKeyColumn: 1
			      mapping:
			        source: ${source::literalJoin}
			        attributes:
			          id: $1
			""")));
	}

	@Test
	void tableInterpolated() throws IOException {
		final String table = " -> " + THING + "table}";
		assertEquals(
			Set.of(
				"TABLE_INTERPOLATED header" + table,
				"TABLE_INTERPOLATED authenticationToken" + table,
				"TABLE_INTERPOLATED append #1 value" + table,
				"TABLE_INTERPOLATED input" + table,
				"TABLE_INTERPOLATED monitors.thing.simple.mapping.attributes.name" + table
			),
			lines(findings("""
				monitors:
				  thing:
				    simple:
				      sources:
				        table:
				          type: snmpTable
				          oid: 1.3
				          selectColumns: "1,2"
				        token:
				          type: awk
				          script: "BEGIN { print \\"t\\" }"
				        request:
				          type: http
				          method: get
				          path: /x
				          header: "X-Token: ${source::token}\\nX-Id: ${source::table}"
				          authenticationToken: ${source::table}
				          computes:
				          - type: json2Table
				          - type: append
				            column: 1
				            value: ${source::table}
				        template:
				          type: awk
				          script: "{ print }"
				          input: "MODE;${source::table}"
				          output: table
				        single:
				          type: awk
				          script: "{ print $1 }"
				          input: ${source::table}
				          output: table
				      mapping:
				        source: ${source::request}
				        attributes:
				          id: $1
				          name: ${source::table}
				"""))
		);
	}

	@Test
	void unresolvedReference() throws IOException {
		final String missing = " -> " + THING + "missing}";
		assertEquals(
			Set.of(
				"UNRESOLVED_REFERENCE url" + missing,
				"UNRESOLVED_REFERENCE computes[1].variables.rows" + missing,
				"UNRESOLVED_REFERENCE monitors.thing.simple.mapping.source" + missing
			),
			lines(findings("""
				monitors:
				  thing:
				    simple:
				      sources:
				        request:
				          type: http
				          method: get
				          url: http://host/${source::missing}
				          computes:
				          - type: json2Table
				          - type: awk
				            script: "{ print $1 }"
				            variables:
				              rows: ${source::missing}
				            output: table
				      mapping:
				        source: ${source::missing}
				        attributes:
				          id: $1
				"""))
		);
	}

	@Test
	void deprecatedSyntax() throws IOException {
		final List<ContentTypeFinding> findings = findings("""
			monitors:
			  thing:
			    simple:
			      sources:
			        request:
			          type: http
			          method: get
			          path: /x
			          computes:
			          - type: json2Csv
			          - type: awk
			            script: "{ print $1 }"
			            input: table
			            output: table
			        script:
			          type: awk
			          script: "{ print $1 }"
			          input: ${source::request}
			          inputFormat: table
			          output: table
			      mapping:
			        source: ${source::script}
			        attributes:
			          id: $1
			""");
		assertEquals(
			Set.of("DEPRECATED_SYNTAX json2Csv #0", "DEPRECATED_SYNTAX awk #1 input", "DEPRECATED_SYNTAX inputFormat"),
			lines(findings)
		);
		assertEquals(
			List.of("json2Table/json2Csv", "no input/input", "no inputFormat/inputFormat"),
			findings.stream().map(finding -> finding.expected() + "/" + finding.actual()).sorted().toList()
		);
	}

	@Test
	void compliantConnectorHasNoFinding() throws IOException {
		assertEquals(List.of(), findings("""
			monitors:
			  thing:
			    simple:
			      sources:
			        table:
			          type: snmpTable
			          oid: 1.3
			          selectColumns: "1,2"
			        token:
			          type: awk
			          script: "BEGIN { print \\"t\\" }"
			        request:
			          type: http
			          method: get
			          path: /x
			          header: "X-Token: ${source::token}"
			          authenticationToken: ${source::token}
			          computes:
			          - type: json2Table
			          - type: append
			            column: 1
			            value: ${source::token}
			        a:
			          type: commandLine
			          commandLine: echo a
			        b:
			          type: commandLine
			          commandLine: echo b
			        texts:
			          type: textUnion
			          texts: [ "${source::a}", "${source::b}" ]
			          computes:
			          - type: awk
			            script: "{ print $1 }"
			            variables:
			              rows: ${source::table}
			            output: table
			        union:
			          type: tableUnion
			          tables:
			          - [ [ x, y ] ]
			          - ${source::table}
			          - ${source::texts}
			          - ${source::request}
			        joined:
			          type: tableJoin
			          leftTable: ${source::union}
			          rightTable: [ [ x, z ] ]
			          leftKeyColumn: 1
			          rightKeyColumn: 1
			        single:
			          type: awk
			          script: "{ print $1 }"
			          input: ${source::joined}
			          output: table
			      mapping:
			        source: ${source::single}
			        attributes:
			          id: $1
			          name: ${source::token}
			tools:
			  run:
			    description: Runs
			    sources:
			      out:
			        type: commandLine
			        commandLine: echo
			    result:
			      source: ${source::out}
			"""));
	}
}
