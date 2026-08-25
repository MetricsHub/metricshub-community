package org.metricshub.engine.awk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.monitor.task.source.JawkSource;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Awk;

/**
 * Pins the contract of the <code>variables</code> substitution: variables go through the regular
 * <code>update(...)</code> chain like any other field, and the source references they may hold survive it because the
 * Awk source and compute are declared as holding protected references. {@link AwkVariableHelper} then turns those
 * references into arrays.
 */
class AwkVariablesUpdateTest {

	private static final String SOURCE_REF = "${source::monitors.disk.discovery.sources.raw}";

	private static Map<String, String> variables() {
		final Map<String, String> variables = new LinkedHashMap<>();
		variables.put("hostname", "${resource.attribute::host.name}");
		variables.put("fromSource", SOURCE_REF);
		return variables;
	}

	@Test
	void testJawkSourceUpdatesVariables() {
		final JawkSource source = JawkSource.builder()
			.script("BEGIN { print hostname }")
			.input("inputValue")
			.variables(variables())
			.build();

		source.update(value -> value == null ? null : value.replace("${resource.attribute::host.name}", "my-host"));

		final Map<String, String> expected = new LinkedHashMap<>();
		expected.put("hostname", "my-host");
		expected.put("fromSource", SOURCE_REF);

		assertEquals(expected, source.getVariables(), "The variables must be updated like any other field");
		assertEquals("inputValue", source.getInput(), "The input must be updated too");
		assertEquals("BEGIN { print hostname }", source.getScript(), "The script is code and must be left alone");
	}

	@Test
	void testAwkComputeUpdatesVariables() {
		final Awk compute = Awk.builder().script("BEGIN { print hostname }").variables(variables()).build();

		compute.update(value -> value == null ? null : value.replace("${resource.attribute::host.name}", "my-host"));

		final Map<String, String> expected = new LinkedHashMap<>();
		expected.put("hostname", "my-host");
		expected.put("fromSource", SOURCE_REF);

		assertEquals(expected, compute.getVariables(), "The variables must be updated like any other field");
	}

	@Test
	void testCopyDoesNotShareTheVariablesMap() {
		final JawkSource source = JawkSource.builder().script("script").variables(variables()).build();
		final JawkSource copy = (JawkSource) source.copy();

		copy.update(value -> "MUTATED");

		assertEquals(variables(), source.getVariables(), "Mutating the copy must not affect the original");
	}
}
