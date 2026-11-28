package org.metricshub.engine.connector.update;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.connector.deserializer.ConnectorDeserializer;
import org.metricshub.engine.connector.deserializer.PostDeserializeHelper;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ContentTypeFinding;
import org.metricshub.engine.connector.model.IntermediateConnector;
import org.metricshub.engine.connector.model.RawConnector;
import org.metricshub.engine.connector.model.common.ContentType;
import org.metricshub.engine.connector.model.identity.ConnectorIdentity;
import org.metricshub.engine.connector.model.monitor.MonitorJob;
import org.metricshub.engine.connector.model.monitor.SimpleMonitorJob;
import org.metricshub.engine.connector.model.monitor.StandardMonitorJob;
import org.metricshub.engine.connector.model.monitor.task.Discovery;
import org.metricshub.engine.connector.model.monitor.task.Mapping;
import org.metricshub.engine.connector.model.monitor.task.Simple;
import org.metricshub.engine.connector.model.monitor.task.source.JawkSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.tool.ConnectorTool;
import org.metricshub.engine.connector.parser.ConnectorParser;

/**
 * The load-time AWK checks (#1684): mapping expressions, connector value names and scripts. The connectors are parsed
 * and updated as the agent does, then checked directly; constants are set after the parse.
 */
class AwkCheckTest {

	private static final String HEADER = """
		connector:
		  displayName: Test
		  detection:
		    appliesTo: [ linux ]
		    criteria:
		    - type: deviceType
		      keep: [ linux ]
		""";

	private static final String VARIABLES = """
		  variables:
		    scale:
		      description: Multiplier
		      defaultValue: 2
		""";

	private static final String MAPPING = "monitors.disk.discovery.mapping.";

	private static final String DISKS = "${source::monitors.disk.discovery.sources.disks}";

	@TempDir
	Path directory;

	/**
	 * A connector made of the header, the identity lines and the given body, parsed and updated as the agent does, not
	 * checked.
	 */
	private Connector parse(final String identity, final String body) throws IOException {
		final Path file = directory.resolve("Test.yaml");
		Files.writeString(file, HEADER + identity + body);
		final RawConnector raw = ConnectorParser.withNodeProcessorAndUpdateChain(directory).parseRaw(file.toFile());
		final ConnectorDeserializer deserializer = new ConnectorDeserializer(JsonHelper.buildYamlMapper());
		PostDeserializeHelper.addPostDeserializeSupport(deserializer.getMapper());
		final Connector connector = deserializer.deserialize(new IntermediateConnector("Test", raw).getConnectorNode());
		ConnectorParser.createUpdateChain().update(connector);
		return connector;
	}

	private Connector parse(final String body) throws IOException {
		return parse("", body);
	}

	/**
	 * The findings of the connector, after setting its constants.
	 */
	private static List<ContentTypeFinding> check(final Connector connector, final Map<String, String> constants) {
		connector.setConstants(new HashMap<>(constants));
		return ContentTypeCheck.check(connector, "Test");
	}

	private static List<ContentTypeFinding> check(final Connector connector) {
		return check(connector, Map.of());
	}

	/**
	 * The findings as "RULE location actual" lines.
	 */
	private static Set<String> lines(final List<ContentTypeFinding> findings) {
		return findings.stream()
			.map(finding -> finding.rule() + " " + finding.location() + " " + finding.actual())
			.collect(Collectors.toSet());
	}

	/**
	 * A disk discovery: a text source <code>model</code>, a table source <code>disks</code> read by the mapping, the
	 * given mapping lines (the sections).
	 */
	private static String discovery(final String mapping) {
		return """
			monitors:
			  disk:
			    discovery:
			      sources:
			        model:
			          type: commandLine
			          commandLine: model
			        disks:
			          type: snmpTable
			          oid: 1.3
			          selectColumns: "ID,1,2"
			      mapping:
			        source: ${source::disks}
			""" + mapping.indent(8);
	}

	/**
	 * A simple job <code>thing</code> made of the given sources lines, a simple mapping.
	 */
	private static String simpleJob(final String jobLines) {
		return """
			monitors:
			  thing:
			    simple:
			""" + jobLines.indent(6) + """
			      mapping:
			        type: simple
			        attributes:
			          id: x
			""";
	}

	@Test
	void nullValue() throws IOException {
		final List<ContentTypeFinding> findings = check(parse(discovery("""
			attributes:
			  id: $1
			  name:
			""")));
		assertEquals(Set.of("NULL_MAPPING_VALUE " + MAPPING + "attributes.name nothing"), lines(findings));
		assertEquals("none", findings.get(0).sourceKey());
		assertEquals("Test", findings.get(0).connectorId());
	}

	@Test
	void expressionDoesNotCompile() throws IOException {
		final List<ContentTypeFinding> findings = check(parse(discovery("""
			attributes:
			  id: $1
			  a: = $1 +
			  b: = foo($1)
			  c: = megaBit2Bit($1, 2)
			  d: = system("ls")
			""")));
		assertEquals(
			Set.of(
				"EXPRESSION_SYNTAX " + MAPPING + "attributes.a a syntax error",
				"EXPRESSION_SYNTAX " + MAPPING + "attributes.b an unknown function or a semantic error",
				"EXPRESSION_SYNTAX " + MAPPING + "attributes.c a wrong number of arguments",
				"EXPRESSION_SYNTAX " + MAPPING + "attributes.d a command or a file"
			),
			lines(findings)
		);
		findings.forEach(finding -> assertEquals("one AWK expression", finding.expected()));
	}

	@Test
	void monitorFunctionOutsideMetrics() throws IOException {
		final List<ContentTypeFinding> findings = check(parse(discovery("""
			attributes:
			  id: $1
			  a: = rate($2)
			metrics:
			  m: = rate($2) * 8
			conditionalCollection:
			  m: = legacyLedStatus($2)
			""")));
		assertEquals(
			Set.of(
				"MONITOR_FUNCTION_OUTSIDE_METRICS " + MAPPING + "attributes.a a function of the metrics section",
				"MONITOR_FUNCTION_OUTSIDE_METRICS " + MAPPING + "conditionalCollection.m a function of the metrics section"
			),
			lines(findings)
		);
	}

	@Test
	void stateFunctionNotWholeValue() throws IOException {
		final List<ContentTypeFinding> findings = check(parse(discovery("""
			attributes:
			  id: $1
			metrics:
			  a: = fakeCounter($2) / 1000
			  b: = computePowerShareRatio($2) * 2
			  c: = fakeCounter($2 / 1000)
			  d: '= accumulate($2 > 0 ? $2 : 0)'
			""")));
		assertEquals(
			Set.of(
				"STATE_FUNCTION_NOT_WHOLE_VALUE " + MAPPING + "metrics.a fakeCounter",
				"STATE_FUNCTION_NOT_WHOLE_VALUE " + MAPPING + "metrics.b computePowerShareRatio"
			),
			lines(findings)
		);
		assertEquals("= fakeCounter(argument)", findings.get(0).expected());
	}

	@Test
	void unknownName() throws IOException {
		final List<ContentTypeFinding> findings = check(parse("""
			beforeAll:
			  early:
			    type: commandLine
			    commandLine: early
			""" + discovery("""
			attributes:
			  id: $1
			  a: = enclosure
			  b: = diskModle
			  c: = disks
			  d: = early
			  e: = other
			  f: = var["x"]
			  g: = sources["source(1)"]
			  h: = a[1]
			  i: '= split($1, parts, ".") ? parts[1] : ""'
			""") + """
			  fan:
			    simple:
			      sources:
			        other:
			          type: commandLine
			          commandLine: other
			      mapping:
			        type: simple
			        attributes:
			          id: fan
			"""));
		final String unknown = "EXPRESSION_UNKNOWN_NAME " + MAPPING + "attributes.";
		assertEquals(
			Set.of(
				unknown + "a 'enclosure'",
				unknown + "b 'diskModle'",
				unknown + "c 'disks', a table source",
				unknown + "d 'early'",
				unknown + "e 'other'",
				unknown + "f 'var'",
				unknown + "g 'sources'",
				unknown + "h 'a'"
			),
			lines(findings)
		);
		findings.forEach(finding -> assertEquals("none", finding.sourceKey()));
	}

	@Test
	void visibleNamesAccepted() throws IOException {
		final Connector connector = parse(VARIABLES, discovery("""
			attributes:
			  id: $1
			  a: = $2 * BLOCK_SIZE
			  b: = $2 * scale
			  c: = model " " $1
			  d: = NF
			  e: '= match($1, /a/) ? RSTART : 0'
			  f: '= split($1, parts, ".") ? parts[1] : ""'
			  g: = "costs " $2
			"""));
		assertEquals(List.of(), check(connector, Map.of("BLOCK_SIZE", "512", "model", "x", "scale", "3")));
	}

	@Test
	void referenceInExpression() throws IOException {
		final List<ContentTypeFinding> findings = check(parse(discovery("""
			attributes:
			  id: $1
			  a: = ${source::model} " x"
			  b: = ${source::disks}
			  c: = ${source::missing}
			""")));
		assertEquals(
			Set.of(
				"REFERENCE_IN_EXPRESSION " + MAPPING + "attributes.a a reference",
				"REFERENCE_IN_EXPRESSION " + MAPPING + "attributes.b a reference",
				"REFERENCE_IN_EXPRESSION " + MAPPING + "attributes.c a reference"
			),
			lines(findings)
		);
	}

	@Test
	void environAndSymtab() throws IOException {
		final List<ContentTypeFinding> findings = check(parse(discovery("""
			attributes:
			  id: $1
			  a: = ENVIRON["PATH"]
			  b: = length(SYMTAB["ENV" "IRON"])
			""")));
		assertEquals(
			Set.of(
				"ENVIRON_IN_EXPRESSION " + MAPPING + "attributes.a ENVIRON",
				"ENVIRON_IN_EXPRESSION " + MAPPING + "attributes.b SYMTAB"
			),
			lines(findings)
		);
	}

	@Test
	void constantHoldingReference() throws IOException {
		final Connector connector = parse("""
			monitors:
			  disk:
			    discovery:
			      sources:
			        disks:
			          type: snmpTable
			          oid: 1.3
			          selectColumns: "ID,1,2"
			          computes:
			          - type: awk
			            script: "{ print REF }"
			            output: table
			      mapping:
			        source: ${source::disks}
			        attributes:
			          id: $1
			          a: = REF " x"
			""");
		final List<ContentTypeFinding> findings = check(connector, Map.of("REF", "${file::query.sql}"));
		assertEquals(
			Set.of(
				"CONSTANT_HOLDS_REFERENCE " + MAPPING + "attributes.a 'REF'",
				"CONSTANT_HOLDS_REFERENCE awk #0 script 'REF'"
			),
			lines(findings)
		);
		assertEquals(
			DISKS,
			findings.stream().filter(f -> f.location().endsWith("script")).findFirst().orElseThrow().sourceKey()
		);
	}

	@Test
	void nameUsedAsArrayInExpression() throws IOException {
		final List<ContentTypeFinding> findings = check(parse(discovery("""
			attributes:
			  id: $1
			  a: = BLOCK_SIZE[1]
			  b: = model["x"]
			""")), Map.of("BLOCK_SIZE", "512"));
		assertEquals(
			Set.of(
				"NAME_USED_AS_ARRAY " + MAPPING + "attributes.a 'BLOCK_SIZE' (constant)",
				"NAME_USED_AS_ARRAY " + MAPPING + "attributes.b 'model' (text source)"
			),
			lines(findings)
		);
	}

	@Test
	void columnInSimpleMapping() throws IOException {
		final List<ContentTypeFinding> findings = check(parse("""
			monitors:
			  cpu:
			    simple:
			      sources:
			        vendor:
			          type: commandLine
			          commandLine: vendor
			      mapping:
			        type: simple
			        attributes:
			          a: = $1 " x"
			          b: = "costs $5"
			          c: = vendor
			"""));
		assertEquals(Set.of("COLUMN_IN_SIMPLE_MAPPING monitors.cpu.simple.mapping.attributes.a a column"), lines(findings));
	}

	@Test
	void reservedNames() throws IOException {
		final Connector connector = parse("""
			  variables:
			    log:
			      defaultValue: x
			""", discovery("""
			attributes:
			  id: $1
			"""));
		assertEquals(
			Set.of("RESERVED_NAME constants.FS 'FS'", "RESERVED_NAME connector.variables.log 'log'"),
			lines(check(connector, Map.of("FS", ";")))
		);
	}

	@Test
	void scriptArray() throws IOException {
		final Connector connector = parse(simpleJob("""
			sources:
			  text:
			    type: commandLine
			    commandLine: echo
			    computes:
			    - type: awk
			      script: "{ BLOCK_SIZE[1] = 2; print }"
			"""));
		assertEquals(
			Set.of("NAME_USED_AS_ARRAY awk #0 script 'BLOCK_SIZE' (constant)"),
			lines(check(connector, Map.of("BLOCK_SIZE", "512")))
		);
	}

	@Test
	void scriptFunction() throws IOException {
		final Connector connector = parse(VARIABLES, simpleJob("""
			sources:
			  text:
			    type: commandLine
			    commandLine: echo
			    computes:
			    - type: awk
			      script: "function scale(x) { return x } { print scale($1) }"
			"""));
		assertEquals(
			Set.of("NAME_DEFINED_AS_FUNCTION awk #0 script 'scale' (connector variable)"),
			lines(check(connector))
		);
	}

	@Test
	void scriptVariablesEntry() throws IOException {
		final Connector connector = parse(simpleJob("""
			sources:
			  model:
			    type: commandLine
			    commandLine: model
			  text:
			    type: commandLine
			    commandLine: echo
			    computes:
			    - type: awk
			      script: "{ print model }"
			      variables:
			        model: x
			"""));
		assertEquals(
			Set.of("NAME_DECLARED_IN_VARIABLES awk #0 variables.model 'model' (text source)"),
			lines(check(connector))
		);
	}

	@Test
	void scriptReadsLaterOrOwnTextSource() throws IOException {
		final Connector connector = parse(simpleJob("""
			sources:
			  reader:
			    type: awk
			    script: "BEGIN { print later; print exempt }"
			    variables:
			      exempt: v
			  later:
			    type: commandLine
			    commandLine: echo
			    computes:
			    - type: awk
			      script: "{ print later }"
			  exempt:
			    type: commandLine
			    commandLine: echo
			"""));
		final List<ContentTypeFinding> findings = check(connector);
		assertEquals(
			Set.of("TEXT_SOURCE_NOT_YET_RUN script 'later'", "TEXT_SOURCE_NOT_YET_RUN awk #0 script 'later'"),
			lines(findings)
		);
		assertEquals(
			Set.of("${source::monitors.thing.simple.sources.reader}", "${source::monitors.thing.simple.sources.later}"),
			findings.stream().map(ContentTypeFinding::sourceKey).collect(Collectors.toSet())
		);

		// The check follows the executionOrder: second runs first
		final Connector ordered = parse(simpleJob("""
			executionOrder: [ second, first ]
			sources:
			  first:
			    type: awk
			    script: "BEGIN { print second }"
			  second:
			    type: commandLine
			    commandLine: echo
			    computes:
			    - type: awk
			      script: "{ print first }"
			"""));
		assertEquals(Set.of("TEXT_SOURCE_NOT_YET_RUN awk #0 script 'first'"), lines(check(ordered)));
	}

	@Test
	void badExecutionOrderSkipsOrderChecks() throws IOException {
		final Connector connector = parse(simpleJob("""
			executionOrder: [ first ]
			sources:
			  first:
			    type: awk
			    script: "BEGIN { print second }"
			  second:
			    type: commandLine
			    commandLine: echo
			"""));
		assertEquals(List.of(), assertDoesNotThrow(() -> check(connector)));
	}

	@Test
	void scriptThatDoesNotCompileHasNoFinding() throws IOException {
		final Connector connector = parse(simpleJob("""
			sources:
			  text:
			    type: commandLine
			    commandLine: echo
			    computes:
			    - type: awk
			      script: "{ BLOCK_SIZE[1] = text "
			"""));
		assertEquals(List.of(), check(connector, Map.of("BLOCK_SIZE", "512")));
	}

	@Test
	void beforeAllAndToolScopes() throws IOException {
		final Connector connector = parse("""
			beforeAll:
			  first:
			    type: awk
			    script: "BEGIN { print first }"
			tools:
			  t:
			    description: A tool
			    sources:
			      x:
			        type: commandLine
			        commandLine: echo
			        computes:
			        - type: awk
			          script: "{ print x }"
			    result:
			      source: ${source::x}
			""");
		final List<ContentTypeFinding> findings = check(connector);
		assertEquals(
			Set.of("TEXT_SOURCE_NOT_YET_RUN script 'first'", "TEXT_SOURCE_NOT_YET_RUN awk #0 script 'x'"),
			lines(findings)
		);
		assertEquals(
			Set.of("${source::beforeAll.first}", "${source::tools.t.sources.x}"),
			findings.stream().map(ContentTypeFinding::sourceKey).collect(Collectors.toSet())
		);
	}

	@Test
	void legacyMappingHasNoNewFinding() throws IOException {
		final Connector connector = parse(VARIABLES, discovery("""
			attributes:
			  id: $1
			  name: ${awk::sprintf("%s (%s)", $1, $2)}
			  model: ${source::model}
			  constant: BLOCK_SIZE
			metrics:
			  a: megaBit2Byte($2)
			  b: fakeCounter($2)
			  c: rate($2)
			conditionalCollection:
			  a: $2
			legacyTextParameters:
			  a: x = $1
			"""));
		assertEquals(List.of(), check(connector, Map.of("BLOCK_SIZE", "512", "FOO", "${source::model}")));
	}

	@Test
	void unusualShapesDoNotThrow() {
		final Connector empty = new Connector();
		empty.setConstants(null);
		empty.setConnectorIdentity(null);
		empty.setMonitors(null);
		empty.setTools(null);
		empty.setBeforeAll(null);
		empty.setAfterAll(null);
		empty.setBeforeAllSourceDep(null);
		empty.setAfterAllSourceDep(null);
		empty.setEmbeddedFiles(null);
		assertEquals(List.of(), AwkCheck.check(empty, "Test", source -> ContentType.TEXT));

		final Mapping mapping = new Mapping();
		mapping.setAttributes(null);
		mapping.setMetrics(null);
		final Map<String, String> metrics = new LinkedHashMap<>();
		metrics.put("m", null);
		mapping.setConditionalCollection(metrics);
		final Map<String, Source> sources = new LinkedHashMap<>();
		sources.put("s", JawkSource.builder().key("${source::monitors.m.simple.sources.s}").build());
		sources.put("n", null);
		final Simple simple = new Simple();
		simple.setSources(sources);
		simple.setMapping(mapping);
		simple.setExecutionOrder(null);
		simple.setSourceDep(null);
		final Map<String, MonitorJob> monitors = new LinkedHashMap<>();
		monitors.put("m", SimpleMonitorJob.simpleBuilder().simple(simple).build());
		monitors.put("d", StandardMonitorJob.standardBuilder().discovery(new Discovery()).build());
		monitors.put("null", null);
		final Map<String, ConnectorTool> tools = new LinkedHashMap<>();
		tools.put("t", null);
		final ConnectorIdentity identity = new ConnectorIdentity();
		identity.setVariables(null);
		final Connector odd = new Connector();
		odd.setConnectorIdentity(identity);
		odd.setMonitors(monitors);
		odd.setTools(tools);
		odd.setEmbeddedFiles(null);
		final List<ContentTypeFinding> findings = assertDoesNotThrow(
			() -> AwkCheck.check(odd, "Test", source -> ContentType.TEXT)
		);
		assertTrue(
			lines(findings).contains("NULL_MAPPING_VALUE monitors.m.simple.mapping.conditionalCollection.m nothing")
		);
	}
}
