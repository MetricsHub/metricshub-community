package org.metricshub.engine.connector.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.awk.AwkExecutor;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.common.EmbeddedFile;
import org.metricshub.engine.connector.model.monitor.StandardMonitorJob;
import org.metricshub.engine.connector.model.monitor.task.Discovery;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.JawkSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Awk;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.connector.parser.ConnectorParser;

class AwkCompilationUpdateTest {

	private static final String KEY = "${source::monitors.enclosure.discovery.sources.source(1)}";

	/**
	 * A connector holding one discovery source under {@link #KEY}.
	 */
	private static Connector connectorWith(final Source source) {
		source.setKey(KEY);
		final Map<String, Source> sources = new LinkedHashMap<>();
		sources.put("source(1)", source);
		final Connector connector = Connector.builder().build();
		connector.getMonitors()
			.put(
				"enclosure",
				StandardMonitorJob.standardBuilder().discovery(Discovery.builder().sources(sources).build()).build()
			);
		return connector;
	}

	private static Source httpWith(final Compute compute) {
		return HttpSource.builder().type("http").url("/x").computes(new ArrayList<>(List.of(compute))).build();
	}

	@Test
	void testCompilesInlineComputeScript() throws Exception {
		final Awk awk = Awk.builder().type("awk").script("{ print toupper($1) }").build();

		new AwkCompilationUpdate().update(connectorWith(httpWith(awk)));

		assertNotNull(awk.getProgram());
		assertEquals(
			"A\n",
			AwkExecutor.executeAwk(awk.getProgram(), "a b\n", AwkExecutor.getUtilityEngine(), null),
			"The program the compute holds is its script"
		);
	}

	@Test
	void testCompilesEmbeddedFileScripts() {
		final Awk awk = Awk.builder().type("awk").script("${file::1}").build();
		// A source script may call the MetricsHub functions, which a compute script does not know
		final JawkSource jawkSource = JawkSource.builder().type("awk").script("${file::2}").build();
		jawkSource.setComputes(new ArrayList<>(List.of(awk)));
		final Connector connector = connectorWith(jawkSource);
		connector.setEmbeddedFiles(
			Map.of(
				1,
				EmbeddedFile.builder().id(1).filename("compute.awk").content("{ print $2 }".getBytes()).build(),
				2,
				EmbeddedFile.builder()
					.id(2)
					.filename("source.awk")
					.content("BEGIN { args[\"path\"] = \"/x\"; print executeHttpRequest(args) }".getBytes())
					.build()
			)
		);

		new AwkCompilationUpdate().update(connector);

		assertNotNull(awk.getProgram());
		assertNotNull(jawkSource.getProgram());
	}

	/**
	 * A script that does not compile is reported and leaves no program: a syntax error, a construct the sandbox
	 * rejects, a MetricsHub function in a compute, a file that is not in the connector.
	 */
	@Test
	void testScriptThatDoesNotCompileHasNoProgram() {
		for (final String script : List.of(
			"{ print $1",
			"BEGIN { system(\"echo x\") }",
			"BEGIN { print executeHttpRequest(args) }",
			"${file::7}"
		)) {
			final Awk awk = Awk.builder().type("awk").script(script).build();
			new AwkCompilationUpdate().update(connectorWith(httpWith(awk)));
			assertNull(awk.getProgram(), script);
		}

		final JawkSource jawkSource = JawkSource.builder().type("awk").script("BEGIN { getline line < \"x\" }").build();
		new AwkCompilationUpdate().update(connectorWith(jawkSource));
		assertNull(jawkSource.getProgram());
	}

	@Test
	void testCopyKeepsTheProgram() {
		final Awk awk = Awk.builder().type("awk").script("{ print }").build();
		final JawkSource jawkSource = JawkSource.builder().type("awk").script("BEGIN { print 1 }").build();
		jawkSource.setComputes(new ArrayList<>(List.of(awk)));
		new AwkCompilationUpdate().update(connectorWith(jawkSource));

		assertSame(awk.getProgram(), awk.copy().getProgram());
		assertSame(jawkSource.getProgram(), ((JawkSource) jawkSource.copy()).getProgram());
	}

	/**
	 * An inline compute script that holds a reference is another script once the reference is replaced: the program
	 * compiled at load is dropped. A script without a reference keeps it.
	 */
	@Test
	void testUpdateDropsTheProgramOfARewrittenScript() {
		final Awk withReference = Awk.builder().type("awk").script("$1 == \"${attribute::id}\" { print }").build();
		final Awk plain = Awk.builder().type("awk").script("{ print }").keep("${attribute::id}").build();
		final Source source = httpWith(withReference);
		source.getComputes().add(plain);
		new AwkCompilationUpdate().update(connectorWith(source));
		// The script with a reference is not AWK yet (${param::days} in an expression is a syntax error): it is not
		// compiled at load. Should it hold a program all the same, replacing the reference drops it
		assertNull(withReference.getProgram());
		assertNotNull(plain.getProgram());
		withReference.setProgram(plain.getProgram());

		final Awk rewritten = withReference.copy();
		rewritten.update(value -> value == null ? null : value.replace("${attribute::id}", "disk1"));
		final Awk untouched = plain.copy();
		untouched.update(value -> value == null ? null : value.replace("${attribute::id}", "disk1"));

		assertEquals("$1 == \"disk1\" { print }", rewritten.getScript());
		assertNull(rewritten.getProgram());
		assertSame(plain.getProgram(), untouched.getProgram());
		assertNotNull(withReference.getProgram(), "The compute of the connector keeps its program");
	}

	@Test
	void testUpdateChainCompiles() {
		final Awk awk = Awk.builder().type("awk").script("{ print }").build();

		ConnectorParser.createUpdateChain().update(connectorWith(httpWith(awk)));

		assertNotNull(awk.getProgram());
	}
}
