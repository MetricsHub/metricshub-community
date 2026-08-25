package org.metricshub.engine.awk;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
	 * The regression this guards: protecting the whole compute from the source replacement would leave the script and
	 * the filter properties executing and filtering on literal <code>${source::...}</code> text.
	 */
	@Test
	void testEveryComputeFieldExceptVariablesStillReceivesTheReplacement() {
		final Awk compute = Awk.builder()
			.script("BEGIN { print " + SOURCE_REF + " }")
			.keep(SOURCE_REF)
			.exclude(SOURCE_REF)
			.separators(SOURCE_REF)
			.selectColumns(SOURCE_REF)
			.variables(variables())
			.build();

		compute.update(value -> value == null ? null : value.replace(SOURCE_REF, "RESOLVED"));

		assertEquals("BEGIN { print RESOLVED }", compute.getScript(), "The script must receive the replacement");
		assertEquals("RESOLVED", compute.getKeep(), "keep must receive the replacement");
		assertEquals("RESOLVED", compute.getExclude(), "exclude must receive the replacement");
		assertEquals("RESOLVED", compute.getSeparators(), "separators must receive the replacement");
		assertEquals("RESOLVED", compute.getSelectColumns(), "selectColumns must receive the replacement");
		assertEquals(variables(), compute.getVariables(), "Only the variables are left for AwkVariableHelper");
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
		hostProperties
			.getConnectorNamespace(CONNECTOR_ID)
			.addSourceTable(SOURCE_REF, SourceTable.builder().table(table).build());
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

	@Test
	void testCopyDoesNotShareTheVariablesMap() {
		final JawkSource source = JawkSource.builder().script("script").variables(variables()).build();
		final JawkSource copy = (JawkSource) source.copy();

		copy.getVariables().put("fromSource", "MUTATED");

		assertEquals(SOURCE_REF, source.getVariables().get("fromSource"), "Mutating the copy must not affect the original");
	}
}
