package org.metricshub.engine.connector.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
	 * No reference is replaced in a script: the script and its program are what the connector loaded, whatever the
	 * run replaces in the other properties. A value reaches the script through the variables.
	 */
	@Test
	void testUpdateNeverRewritesTheScript() {
		final Awk awk = Awk.builder()
			.type("awk")
			.script("$1 == id { print \"${attribute::id}\" }")
			.keep("${attribute::id}")
			.variables(Map.of("id", "${attribute::id}"))
			.build();
		new AwkCompilationUpdate().update(connectorWith(httpWith(awk)));
		assertNotNull(awk.getProgram());

		final Awk copy = awk.copy();
		copy.update(value -> value == null ? null : value.replace("${attribute::id}", "disk1"));

		assertEquals(awk.getScript(), copy.getScript());
		assertSame(awk.getProgram(), copy.getProgram());
		assertEquals("disk1", copy.getKeep());
	}

	/**
	 * A connector is {@link java.io.Serializable}: so are the programs it holds. A program that went through Java
	 * serialization still runs, with its MetricsHub function.
	 */
	@Test
	void testConnectorWithProgramsIsSerializable() throws Exception {
		final Awk awk = Awk.builder().type("awk").script("{ print toupper($1) }").build();
		final JawkSource jawkSource = JawkSource.builder()
			.type("awk")
			.script("BEGIN { print 1; if (0) { executeHttpRequest(args) } }")
			.build();
		jawkSource.setComputes(new ArrayList<>(List.of(awk)));
		final Connector connector = connectorWith(jawkSource);
		new AwkCompilationUpdate().update(connector);

		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
			output.writeObject(connector);
		}
		final Connector copy;
		try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
			copy = (Connector) input.readObject();
		}

		final JawkSource sourceCopy = (JawkSource) ((StandardMonitorJob) copy.getMonitors().get("enclosure")).getDiscovery()
			.getSources()
			.get("source(1)");
		assertNotNull(sourceCopy.getProgram());
		final Awk awkCopy = (Awk) sourceCopy.getComputes().get(0);
		assertEquals("A\n", AwkExecutor.executeAwk(awkCopy.getProgram(), "a b\n", AwkExecutor.getUtilityEngine(), null));
	}

	@Test
	void testUpdateChainCompiles() {
		final Awk awk = Awk.builder().type("awk").script("{ print }").build();

		ConnectorParser.createUpdateChain().update(connectorWith(httpWith(awk)));

		assertNotNull(awk.getProgram());
	}
}
