package org.metricshub.engine.awk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.jawk.AwkProgram;
import io.jawk.jrt.AssocArray;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.monitor.task.source.JawkSource;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Awk;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.HostProperties;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * Pins where an Awk variable is resolved.
 * <p>
 * <code>update(...)</code> never touches the variables: a variable holding a source reference must become an array, not
 * the CSV text a {@code UnaryOperator<String>} could return, so resolving it there would be impossible. Every reference
 * kind is instead resolved together by {@link AwkVariableHelper}, in one pass, immediately before the script runs.
 * Every other field, including the script and the filter properties, keeps receiving the substitutions it always had.
 */
class AwkVariablesUpdateTest {

	private static final String SOURCE_REF = "${source::monitors.disk.discovery.sources.raw}";
	private static final String CONNECTOR_ID = "connectorId";

	private static Map<String, String> variables() {
		final Map<String, String> variables = new LinkedHashMap<>();
		variables.put("resourceAttr", "${resource.attribute::host.name}");
		variables.put("monoInstance", "${attribute::id}");
		variables.put("scalar", "365.25");
		variables.put("fromSource", SOURCE_REF);
		return variables;
	}

	@Test
	void testUpdateNeverTouchesTheVariables() {
		final JawkSource source = JawkSource.builder()
			.script("BEGIN { print resourceAttr }")
			.input(SOURCE_REF)
			.variables(variables())
			.build();

		source.update(value -> value == null ? null : value.replace(SOURCE_REF, "RESOLVED").replace("${", "CLOBBERED{"));

		assertEquals(variables(), source.getVariables(), "update() must leave every variable untouched");
		assertEquals("RESOLVED", source.getInput(), "The input must still receive the replacement");
		assertEquals("BEGIN { print resourceAttr }", source.getScript(), "The script is code and must be left alone");
	}

	/**
	 * The filter properties receive the source replacement. The script does not: it is code, compiled when the
	 * connector loads (#1576), and a value reaches it through the variables.
	 */
	@Test
	void testEveryComputeFieldExceptScriptAndVariablesReceivesTheReplacement() {
		final Awk compute = Awk.builder()
			.script("BEGIN { print \"" + SOURCE_REF + "\" }")
			.keep(SOURCE_REF)
			.exclude(SOURCE_REF)
			.separators(SOURCE_REF)
			.selectColumns(SOURCE_REF)
			.variables(variables())
			.build();

		compute.update(value -> value == null ? null : value.replace(SOURCE_REF, "RESOLVED"));

		assertEquals("BEGIN { print \"" + SOURCE_REF + "\" }", compute.getScript(), "The script is never rewritten");
		assertEquals("RESOLVED", compute.getKeep(), "keep must receive the replacement");
		assertEquals("RESOLVED", compute.getExclude(), "exclude must receive the replacement");
		assertEquals("RESOLVED", compute.getSeparators(), "separators must receive the replacement");
		assertEquals("RESOLVED", compute.getSelectColumns(), "selectColumns must receive the replacement");
		assertEquals(variables(), compute.getVariables(), "The variables are left for AwkVariableHelper");
	}

	/**
	 * The single resolution pass covers every reference kind, including the mono-instance monitor attribute, and turns
	 * only the source reference into a table.
	 */
	@Test
	void testHelperResolvesEveryReferenceKindInOnePass() {
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostConfiguration(
				HostConfiguration.builder()
					.hostname("test-host")
					.hostId("test-host")
					.attributes(Map.of("host.name", "my-host"))
					.build()
			)
			.build();

		final List<List<String>> table = List.of(List.of("FOO", "1"), List.of("BAR", "2"));
		final HostProperties hostProperties = HostProperties.builder().build();
		hostProperties.getConnectorNamespace(CONNECTOR_ID).addSourceTable(SOURCE_REF, SourceTable.ofTable(table));
		telemetryManager.setHostProperties(hostProperties);

		final Map<String, Object> resolved = AwkVariableHelper.resolveVariables(
			variables(),
			telemetryManager,
			CONNECTOR_ID,
			Map.of("id", "monitor-42"),
			"someOperation"
		);

		assertEquals("my-host", resolved.get("resourceAttr"), "The resource attribute must resolve");
		assertEquals("monitor-42", resolved.get("monoInstance"), "The mono-instance attribute must resolve");
		assertEquals("365.25", resolved.get("scalar"), "A plain value stays a scalar");
		assertEquals(table, resolved.get("fromSource"), "The source reference must become the table itself");
	}

	@Test
	void testUnresolvableSourceReferenceBecomesAnEmptyArray() {
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostConfiguration(HostConfiguration.builder().hostname("test-host").hostId("test-host").build())
			.build();
		telemetryManager.setHostProperties(HostProperties.builder().build());

		final Map<String, Object> resolved = AwkVariableHelper.resolveVariables(
			Map.of("missing", SOURCE_REF),
			telemetryManager,
			CONNECTOR_ID,
			Map.of(),
			"someOperation"
		);

		assertEquals(List.of(), resolved.get("missing"), "An unresolvable reference must not fail the whole source");
	}

	private static TelemetryManager telemetryManagerWithTable(final List<List<String>> table) {
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostConfiguration(
				HostConfiguration.builder()
					.hostname("test-host")
					.hostId("test-host")
					.attributes(Map.of("host.name", "my-host"))
					.build()
			)
			.build();
		final HostProperties hostProperties = HostProperties.builder().build();
		hostProperties.getConnectorNamespace(CONNECTOR_ID).addSourceTable(SOURCE_REF, SourceTable.ofTable(table));
		telemetryManager.setHostProperties(hostProperties);
		return telemetryManager;
	}

	/**
	 * Every scalar of a list or a map, at any depth, is resolved as a variable is.
	 */
	@Test
	void testHelperResolvesListsAndMaps() {
		final List<List<String>> table = List.of(List.of("FOO", "1"), List.of("BAR", "2"));

		final Map<String, Object> labels = new LinkedHashMap<>();
		labels.put("host", "${resource.attribute::host.name}");
		labels.put("rows", SOURCE_REF);
		labels.put("ids", List.of("${attribute::id}", "plain"));

		final Map<String, Object> resolved = AwkVariableHelper.resolveVariables(
			Map.of("list", Arrays.asList("${attribute::id}", SOURCE_REF, null), "labels", labels),
			telemetryManagerWithTable(table),
			CONNECTOR_ID,
			Map.of("id", "monitor-42"),
			"someOperation"
		);

		assertEquals(List.of("monitor-42", table, ""), resolved.get("list"), "A list stays a list of resolved values");

		final AssocArray array = assertInstanceOf(AssocArray.class, resolved.get("labels"), "A map becomes an AWK array");
		assertEquals("my-host", array.get("host"));
		assertEquals(table, array.get("rows"));
		assertEquals(List.of("monitor-42", "plain"), array.get("ids"));
	}

	/**
	 * What a script sees: a list indexed from 0, a map indexed by its keys (a numeric subscript finds the key written
	 * as a string in YAML), nesting, and a source table inside a list.
	 */
	@Test
	void testScriptReadsListsAndMaps() throws Exception {
		final List<List<String>> table = List.of(List.of("FOO", "1"), List.of("BAR", "2"));

		final Map<String, Object> labels = new LinkedHashMap<>();
		labels.put("vendor", "NetApp");
		labels.put("1", "one");
		labels.put("nested", List.of("x", Map.of("k", "v")));

		final Map<String, Object> variables = AwkVariableHelper.resolveVariables(
			Map.of("properties", List.of("node", "", "model"), "labels", labels, "tables", List.of(SOURCE_REF)),
			telemetryManagerWithTable(table),
			CONNECTOR_ID,
			Map.of(),
			"someOperation"
		);

		final String script = """
			BEGIN {
				print length(properties), properties[0], "[" properties[1] "]", properties[2], (3 in properties)
				print length(labels), labels["vendor"], labels[1], labels["nested"][1]["k"]
				count = 0
				for (key in labels) count++
				print count, tables[0][1][0]
			}
			""";

		assertEquals(
			"3 node [] model 0\n3 NetApp one v\n3 BAR\n",
			AwkExecutor.executeAwk(compile(script), "", AwkExecutor.getUtilityEngine(), variables)
		);
	}

	/**
	 * Pins what the documentation says: a scalar, at the top level or in a list, a map or a table, compares as a number
	 * when it looks like one (<code>"10" &gt; 9</code>), and as text otherwise (<code>"10x" &gt; 9</code> compares
	 * <code>"10x"</code> with <code>"9"</code>).
	 */
	@Test
	void testNestedValuesCompareAsNumbers() throws Exception {
		final Map<String, Object> variables = AwkVariableHelper.resolveVariables(
			Map.of("scalar", "10", "list", List.of("10", "10x"), "map", Map.of("key", "10"), "table", SOURCE_REF),
			telemetryManagerWithTable(List.of(List.of("10"))),
			CONNECTOR_ID,
			Map.of(),
			"someOperation"
		);

		assertEquals(
			"1 1 1 1 0\n",
			AwkExecutor.executeAwk(
				compile("BEGIN { print (scalar > 9), (list[0] > 9), (map[\"key\"] > 9), (table[0][0] > 9), (list[1] > 9) }"),
				"",
				AwkExecutor.getUtilityEngine(),
				variables
			)
		);
	}

	@Test
	void testCopyDoesNotShareTheVariablesMap() {
		final JawkSource source = JawkSource.builder().script("script").variables(variables()).build();
		final JawkSource copy = (JawkSource) source.copy();

		copy.getVariables().put("fromSource", "MUTATED");

		assertEquals(SOURCE_REF, source.getVariables().get("fromSource"), "Mutating the copy must not affect the original");
	}

	/**
	 * Compiles a script for the utility engine.
	 */
	private static AwkProgram compile(final String script) throws AwkException {
		return AwkExecutor.compile(script, AwkExecutor.getUtilityEngine());
	}
}
