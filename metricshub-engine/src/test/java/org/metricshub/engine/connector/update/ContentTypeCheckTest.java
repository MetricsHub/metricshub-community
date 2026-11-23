package org.metricshub.engine.connector.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.connector.deserializer.ConnectorDeserializer;
import org.metricshub.engine.connector.deserializer.PostDeserializeHelper;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.ContentTypeFinding;
import org.metricshub.engine.connector.model.ContentTypeFinding.Rule;
import org.metricshub.engine.connector.model.IntermediateConnector;
import org.metricshub.engine.connector.model.RawConnector;
import org.metricshub.engine.connector.parser.ConnectorParser;

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
	 * The findings of a connector made of the header and the given body, parsed and updated as the agent does. The
	 * agent loads the connector only when there is none.
	 */
	private List<ContentTypeFinding> findings(final String body) throws IOException {
		final List<ContentTypeFinding> findings = ContentTypeCheck.check(parse(body), "Test");
		findings.forEach(finding -> assertEquals("Test", finding.connectorId()));
		assertEquals(
			findings.isEmpty(),
			new ConnectorStore(directory).getStore().containsKey("Test"),
			"the connector loads only without finding: " + findings
		);
		return findings;
	}

	/**
	 * A connector made of the header and the given body, parsed and updated as the agent does, not checked.
	 */
	private Connector parse(final String body) throws IOException {
		final Path file = directory.resolve("Test.yaml");
		Files.writeString(file, HEADER + body);
		final RawConnector raw = ConnectorParser.withNodeProcessorAndUpdateChain(directory).parseRaw(file.toFile());
		final ConnectorDeserializer deserializer = new ConnectorDeserializer(JsonHelper.buildYamlMapper());
		PostDeserializeHelper.addPostDeserializeSupport(deserializer.getMapper());
		final Connector connector = deserializer.deserialize(new IntermediateConnector("Test", raw).getConnectorNode());
		ConnectorParser.createUpdateChain().update(connector);
		return connector;
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
		assertEquals("write a toTable", compute.hint());
	}

	/**
	 * An <code>snmpGet</code> source returns a text (#1615): read as the source of a mapping, it is a finding whose hint
	 * gives both ways out; after a <code>toTable</code>, or referenced in a <code>static</code> cell, it is not.
	 */
	@Test
	void snmpGetIsAText() throws IOException {
		final List<ContentTypeFinding> findings = findings("""
			monitors:
			  thing:
			    simple:
			      sources:
			        value:
			          type: snmpGet
			          oid: 1.3.6.1.4.1.99999.1.0
			      mapping:
			        source: ${source::value}
			        attributes:
			          id: $1
			""");
		assertEquals(Set.of("TEXT_FEEDS_TABLE monitors.thing.simple.mapping"), lines(findings));
		assertEquals(
			"write a toTable, or remove the mapping source and read the text as ${source::<key>} in a value",
			findings.get(0).hint()
		);

		assertEquals(List.of(), findings("""
			monitors:
			  thing:
			    simple:
			      sources:
			        code:
			          type: snmpGet
			          oid: 1.3.6.1.4.1.99999.1.0
			          computes:
			          - type: toTable
			          - type: translate
			            column: 1
			            translationTable: ${translation::codes}
			        nameValue:
			          type: snmpGet
			          oid: 1.3.6.1.4.1.99999.2.0
			        name:
			          type: static
			          value: [ "${source::nameValue}" ]
			          computes:
			          - type: excludeMatchingLines
			            column: 1
			            regExp: ^$
			        rows:
			          type: tableJoin
			          leftTable: ${source::name}
			          rightTable: ${source::code}
			          leftKeyColumn: 1
			          rightKeyColumn: 1
			      mapping:
			        source: ${source::rows}
			        attributes:
			          id: $1
			          info: ${source::nameValue}
			translations:
			  codes:
			    "1": ok
			"""));
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

	/**
	 * <code>append</code>, <code>prepend</code>, <code>replace</code> and <code>substring</code> work on what they are
	 * fed: a <code>column</code> on a text and a missing <code>column</code> on a table are reported (#1577).
	 */
	@Test
	void computeWithOrWithoutColumn() throws IOException {
		assertEquals(
			// The four computes of "text" give no finding, and leave a text, which the mapping cannot read
			Set.of(
				"NO_COLUMN_ON_TABLE prepend #0",
				"COLUMN_ON_TEXT append #0",
				"TEXT_FEEDS_TABLE monitors.thing.simple.mapping"
			),
			lines(findings("""
				monitors:
				  thing:
				    simple:
				      sources:
				        text:
				          type: http
				          url: /
				          computes:
				          - type: prepend
				            value: "Bearer "
				          - type: append
				            value: x
				          - type: replace
				            existingValue: a
				            newValue: b
				          - type: substring
				            start: 1
				            length: 2
				        noColumnOnTable:
				          type: snmpTable
				          oid: 1.3
				          selectColumns: "1"
				          computes:
				          - type: prepend
				            value: x
				        columnOnText:
				          type: http
				          url: /
				          computes:
				          - type: append
				            column: 1
				            value: x
				      mapping:
				        source: ${source::text}
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
	void notAReference() throws IOException {
		final List<ContentTypeFinding> findings = findings("""
			monitors:
			  thing:
			    simple:
			      sources:
			        copied:
			          type: copy
			          from: a;b;c
			        each:
			          type: http
			          method: get
			          path: /$1
			          executeForEachEntryOf:
			            source: a;b;c
			            concatMethod: list
			        query:
			          type: internalDbQuery
			          tables:
			          - source: a;b;c
			            alias: T1
			            columns:
			            - name: COL1
			              number: 1
			              type: VARCHAR(255)
			          query: SELECT COL1 FROM T1
			      mapping:
			        source: a;b;c
			        attributes:
			          id: $1
			tools:
			  show:
			    description: Shows a row.
			    sources:
			      row:
			        type: static
			        value: [ a, b, c ]
			    result:
			      source: a;b;c
			""");
		assertEquals(
			Set.of(
				"NOT_A_REFERENCE from",
				"NOT_A_REFERENCE executeForEachEntryOf",
				"NOT_A_REFERENCE tables[0]",
				"NOT_A_REFERENCE monitors.thing.simple.mapping.source",
				"NOT_A_REFERENCE tools.show.result.source"
			),
			lines(findings)
		);
		// A finding never holds a value of the connector
		findings.forEach(finding -> assertFalse(finding.toString().contains("a;b;c"), finding.toString()));
	}

	/**
	 * A field that must be a source reference holds exactly one, the whole value: a reference among other text is a
	 * literal (#1639).
	 */
	@Test
	void aValueThatOnlyHoldsAReferenceIsNotAReference() throws IOException {
		assertEquals(
			Set.of(
				"STRING_LITERAL_ROWS tables[0]",
				"STRING_LITERAL_ROWS tables[1]",
				"STRING_LITERAL_ROWS leftTable",
				"STRING_LITERAL_ROWS rightTable",
				"NOT_A_REFERENCE from",
				"NOT_A_REFERENCE executeForEachEntryOf",
				"NOT_A_REFERENCE tables[0]",
				"NOT_A_REFERENCE monitors.thing.simple.mapping.source",
				"NOT_A_REFERENCE tools.show.result.source"
			),
			lines(findings("""
				monitors:
				  thing:
				    simple:
				      sources:
				        raw:
				          type: snmpTable
				          oid: 1.3
				          selectColumns: "1,2"
				        union:
				          type: tableUnion
				          tables:
				          - "a;b;${source::raw}"
				          - "${source::raw}${source::raw}"
				          - ${source::raw}
				        joined:
				          type: tableJoin
				          leftTable: "x;${source::raw}"
				          rightTable: "${source::raw};x"
				          leftKeyColumn: 1
				          rightKeyColumn: 1
				        copied:
				          type: copy
				          from: "a;b;${source::raw}"
				        each:
				          type: http
				          method: get
				          path: /$1
				          executeForEachEntryOf:
				            source: "${source::raw} ${source::raw}"
				            concatMethod: list
				        query:
				          type: internalDbQuery
				          tables:
				          - source: "a;${source::raw}"
				            alias: T1
				            columns:
				            - name: COL1
				              number: 1
				              type: VARCHAR(255)
				          query: SELECT COL1 FROM T1
				      mapping:
				        source: "a;${source::raw}"
				        attributes:
				          id: $1
				tools:
				  show:
				    description: Shows a row.
				    sources:
				      row:
				        type: static
				        value: [ a, b, c ]
				    result:
				      source: "a;${source::row}"
				"""))
		);
	}

	@Test
	void staticCellInterpolatesATextOnly() throws IOException {
		// A static source does not read a reference as a source, a copy does: its cells are interpolated
		assertEquals(Set.of("TABLE_INTERPOLATED value[0][1] -> " + THING + "table}"), lines(findings("""
			monitors:
			  thing:
			    simple:
			      sources:
			        table:
			          type: snmpTable
			          oid: 1.3
			          selectColumns: "1,2"
			        text:
			          type: http
			          method: get
			          path: /x
			        row:
			          type: static
			          value: [ "${source::text}", "${source::table}" ]
			      mapping:
			        source: ${source::row}
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
	void tableInterpolatedInTwoFieldsWithTheSameValue() throws IOException {
		final String table = " -> " + THING + "table}";
		assertEquals(List.of("TABLE_INTERPOLATED body" + table, "TABLE_INTERPOLATED header" + table), findings("""
			monitors:
			  thing:
			    simple:
			      sources:
			        table:
			          type: snmpTable
			          oid: 1.3
			          selectColumns: "1,2"
			        request:
			          type: http
			          method: post
			          path: /x
			          header: ${source::table}
			          body: ${source::table}
			""").stream().map(finding -> finding.rule() + " " + finding.location()).sorted().toList());
	}

	@Test
	void referencesInAVariableListOrMap() throws IOException {
		final String missing = " -> " + THING + "missing}";
		final String text = " -> " + THING + "text}";
		assertEquals(
			Set.of(
				"UNRESOLVED_REFERENCE computes[0].variables.list[1]" + missing,
				"UNRESOLVED_REFERENCE computes[0].variables.map.nested[0].key" + missing,
				// A table read of a text, however deep in the variable
				"TEXT_FEEDS_TABLE awk #0 variables.list" + text,
				"TEXT_FEEDS_TABLE awk #0 variables.map" + text
			),
			lines(findings("""
				monitors:
				  thing:
				    simple:
				      sources:
				        text:
				          type: commandLine
				          commandLine: echo a
				        request:
				          type: http
				          method: get
				          url: http://host/
				          computes:
				          - type: awk
				            script: "{ print $1 }"
				            variables:
				              list:
				              - plain
				              - ${source::missing}
				              - ${source::text}
				              map:
				                nested:
				                - key: ${source::missing}
				                - other: ${source::text}
				            output: table
				      mapping:
				        source: ${source::request}
				        attributes:
				          id: $1
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
				"UNRESOLVED_REFERENCE monitors.thing.simple.mapping.source" + missing,
				"UNRESOLVED_REFERENCE texts[1]" + missing,
				// A short reference the parser cannot make absolute stays short, and the engine finds no such key
				"UNRESOLVED_REFERENCE commandLine -> ${source::raw+x}"
			),
			lines(findings("""
				monitors:
				  thing:
				    simple:
				      sources:
				        raw+x:
				          type: commandLine
				          commandLine: echo
				        other:
				          type: commandLine
				          commandLine: "echo ${source::raw+x}"
				        texts:
				          type: textUnion
				          texts: [ "${source::other}", "${source::missing}" ]
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
			          - type: toText
			          - type: json2Table
			            parsingMode: events
			          - type: toText
			          - type: json2Table
			            properties: [ id, name ]
			            separator: ;
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
			Set.of(
				"DEPRECATED_SYNTAX json2Csv #0",
				"DEPRECATED_SYNTAX awk #1 input",
				"DEPRECATED_SYNTAX json2Table #3 parsingMode",
				"DEPRECATED_SYNTAX json2Table #5 separator",
				"DEPRECATED_SYNTAX inputFormat"
			),
			lines(findings)
		);
		assertEquals(
			List.of(
				"json2Table/json2Csv",
				"no input/input",
				"no inputFormat/inputFormat",
				"no parsingMode/parsingMode",
				"no separator/separator"
			),
			findings.stream().map(finding -> finding.expected() + "/" + finding.actual()).sorted().toList()
		);
	}

	@Test
	void removedAwkOptions() throws IOException {
		final List<ContentTypeFinding> findings = findings("""
			monitors:
			  thing:
			    simple:
			      sources:
			        command:
			          type: commandLine
			          commandLine: x
			          keep: ^a
			          separators: ;
			          selectColumns: "1,2"
			          computes:
			          - type: awk
			            script: "{ print $1 }"
			            keep: ^a
			            exclude: ^b
			            separators: ;
			            selectColumns: "1,2"
			          - type: toTable
			            keep: ^a
			            exclude: ^b
			            selectColumns: "1,2"
			        script:
			          type: awk
			          script: "BEGIN { print 1 }"
			          separators: ;
			          output: table
			      mapping:
			        source: ${source::command}
			        attributes:
			          id: $1
			""");
		// One finding per removed property, which names it. The same options of the other sources and computes stay
		assertEquals(
			List.of(
				"DEPRECATED_SYNTAX awk #0 exclude: lines and columns selected by the script/exclude",
				"DEPRECATED_SYNTAX awk #0 keep: lines and columns selected by the script/keep",
				"DEPRECATED_SYNTAX awk #0 selectColumns: lines and columns selected by the script/selectColumns",
				"DEPRECATED_SYNTAX awk #0 separators: lines and columns selected by the script/separators",
				"DEPRECATED_SYNTAX separators: no separators/separators"
			),
			findings.stream()
				.map(finding -> finding.rule() + " " + finding.location() + ": " + finding.expected() + "/" + finding.actual())
				.toList()
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

	@Test
	void simpleMappingReadsConstantsAndTexts() throws IOException {
		assertEquals(List.of(), findings("""
			monitors:
			  thing:
			    simple:
			      sources:
			        vendor:
			          type: commandLine
			          commandLine: echo
			      mapping:
			        type: simple
			        attributes:
			          id: ${source::vendor}
			          vendor: ${source::vendor}
			        metrics:
			          thing.count: 1
			          thing.limit: ${source::vendor}
			  bare:
			    simple:
			      mapping:
			        type: simple
			        metrics:
			          bare.count: 1
			  both:
			    discovery:
			      mapping:
			        type: simple
			        attributes:
			          hw.id: both
			    collect:
			      type: multiInstance
			      mapping:
			        type: simple
			        metrics:
			          both.count: 1
			"""));
	}

	@Test
	void simpleMappingReadsNoColumnAndNoTable() throws IOException {
		final List<ContentTypeFinding> findings = findings("""
			monitors:
			  thing:
			    simple:
			      sources:
			        table:
			          type: snmpTable
			          oid: 1.3
			          selectColumns: "1,2"
			      mapping:
			        type: simple
			        attributes:
			          id: $1
			          name: thing $2
			          serial: $0
			          vendor: ${source::table}
			        metrics:
			          thing.limit: ${awk::$3 * 2}
			  other:
			    discovery:
			      mapping:
			        type: simple
			        attributes:
			          id: $1
			    collect:
			      type: multiInstance
			      mapping:
			        type: simple
			        attributes:
			          id: other
			        metrics:
			          other.count: rate($1)
			          other.rate: rate($0)
			          other.size: ${awk::$0 * 2}
			          other.price: $$0
			""");
		assertEquals(
			Set.of(
				"COLUMN_IN_SIMPLE_MAPPING monitors.thing.simple.mapping.attributes.id",
				"COLUMN_IN_SIMPLE_MAPPING monitors.thing.simple.mapping.attributes.name",
				"COLUMN_IN_SIMPLE_MAPPING monitors.thing.simple.mapping.attributes.serial",
				"COLUMN_IN_SIMPLE_MAPPING monitors.thing.simple.mapping.metrics.thing.limit",
				"TABLE_INTERPOLATED monitors.thing.simple.mapping.attributes.vendor -> " + THING + "table}",
				"COLUMN_IN_SIMPLE_MAPPING monitors.other.discovery.mapping.attributes.id",
				"COLUMN_IN_SIMPLE_MAPPING monitors.other.collect.mapping.metrics.other.count",
				"COLUMN_IN_SIMPLE_MAPPING monitors.other.collect.mapping.metrics.other.rate",
				"COLUMN_IN_SIMPLE_MAPPING monitors.other.collect.mapping.metrics.other.size"
			),
			lines(findings)
		);
		final ContentTypeFinding column = findings.get(0);
		assertEquals("a constant or a text source", column.expected());
		assertEquals("a column", column.actual());
	}

	@Test
	void mappingTypeDecidesTheSourceTheTypesAndTheKeys() throws IOException {
		assertEquals(
			Set.of(
				"TABLE_MAPPING_WITHOUT_SOURCE monitors.nosource.simple.mapping: a source, none",
				"SOURCE_IN_SIMPLE_MAPPING monitors.sourced.simple.mapping.source: none, a source",
				"MIXED_MAPPING_TYPES monitors.mixed: one mapping type, [TABLE, SIMPLE]",
				"KEYS_WITH_SIMPLE_MAPPING monitors.keyed.keys: none, [id]"
			),
			findings("""
				monitors:
				  nosource:
				    simple:
				      mapping:
				        attributes:
				          id: one
				  sourced:
				    simple:
				      sources:
				        table:
				          type: snmpTable
				          oid: 1.3
				          selectColumns: "1,2"
				      mapping:
				        type: simple
				        source: ${source::table}
				  mixed:
				    discovery:
				      sources:
				        table:
				          type: snmpTable
				          oid: 1.3
				          selectColumns: "1,2"
				      mapping:
				        source: ${source::table}
				        attributes:
				          id: $1
				    collect:
				      type: monoInstance
				      mapping:
				        type: simple
				        metrics:
				          mixed.count: 1
				  keyed:
				    keys: [ id ]
				    simple:
				      mapping:
				        type: simple
				        attributes:
				          id: one
				""").stream()
				.map(f -> f.rule() + " " + f.location() + ": " + f.expected() + ", " + f.actual())
				.collect(Collectors.toSet())
		);
	}

	@Test
	void unknownMappingTypeFailsToLoad() throws IOException {
		final String body = """
			monitors:
			  thing:
			    simple:
			      mapping:
			        type: keyValue
			""";
		assertThrows(IOException.class, () -> parse(body));
		assertFalse(new ConnectorStore(directory).getStore().containsKey("Test"));
	}

	@Test
	void findingsAreALoadError() throws IOException {
		final Connector connector = parse("""
			tools:
			  run:
			    description: Runs
			    sources:
			      out:
			        type: commandLine
			        commandLine: echo
			    result:
			      source: ${source::missing}
			""");
		final IOException error = assertThrows(IOException.class, () -> ContentTypeCheck.validate(connector, "Test"));
		assertEquals(
			"Connector Test breaks the strict content types: UNRESOLVED_REFERENCE ${source::tools.run.sources.missing} at tools.run.result: " +
				"expected an existing source, actual none (reference an existing source)",
			error.getMessage()
		);
	}

	@Test
	void aFailingCheckIsALoadError() {
		final Connector connector = mock(Connector.class);
		when(connector.getMonitors()).thenThrow(new IllegalStateException());
		assertThrows(IOException.class, () -> ContentTypeCheck.validate(connector, "broken"));
	}
}
