package org.metricshub.engine.connector.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.constants.Constants.LOCALHOST;

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
import org.metricshub.engine.connector.parser.ConnectorParser;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.strategy.source.compute.ComputeProcessor;

/**
 * An AWK script is compiled the first time it runs, and its program is kept in the connector (#1576).
 */
class AwkScriptCheckTest {

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

	/**
	 * Runs a copy of the compute on a text, as every run does.
	 */
	private static SourceTable runCopy(final Awk awk, final String text) {
		final SourceTable sourceTable = SourceTable.ofText(text);
		ComputeProcessor.builder().hostname(LOCALHOST).sourceTable(sourceTable).build().process(awk.copy());
		return sourceTable;
	}

	@Test
	void testNothingIsCompiledWhenTheConnectorLoads() {
		final Awk awk = Awk.builder().type("awk").script("{ print }").build();
		final JawkSource jawkSource = JawkSource.builder().type("awk").script("BEGIN { print 1 }").build();
		jawkSource.setComputes(new ArrayList<>(List.of(awk)));

		ConnectorParser.createUpdateChain().update(connectorWith(jawkSource));

		assertNull(awk.getProgram());
		assertNull(jawkSource.getProgram());
	}

	/**
	 * A run works on a copy of the compute. The program it compiles is kept in the compute of the connector, and the
	 * next runs take it.
	 */
	@Test
	void testTheFirstRunLeavesTheProgramInTheConnector() {
		final Awk awk = Awk.builder().type("awk").script("{ print toupper($1) }").build();

		assertEquals(SourceTable.ofText("A;"), runCopy(awk, "a b\n"));

		final var program = awk.getProgram();
		assertNotNull(program, "The compute of the connector holds the program its copy compiled");
		assertEquals(SourceTable.ofText("C;"), runCopy(awk, "c d\n"));
		assertSame(program, awk.getProgram(), "The second run did not compile again");
		assertSame(program, awk.copy().getProgram());

		final JawkSource jawkSource = JawkSource.builder().type("awk").script("BEGIN { print 1 }").build();
		final JawkSource copy = (JawkSource) jawkSource.copy();
		copy.setProgram(program);
		assertSame(program, jawkSource.getProgram(), "A source and its copies share the program too");
	}

	@Test
	void testScriptThatDoesNotCompileHasNoProgram() {
		// A syntax error, a construct the sandbox rejects, a MetricsHub function in a compute
		for (final String script : List.of(
			"{ print $1",
			"BEGIN { system(\"echo x\") }",
			"BEGIN { print executeHttpRequest(args) }"
		)) {
			final Awk awk = Awk.builder().type("awk").script(script).build();
			assertEquals(SourceTable.ofText("unchanged"), runCopy(awk, "unchanged"), script);
			assertNull(awk.getProgram(), script);
		}
	}

	/**
	 * No reference is replaced in a script: the script and its program are what the connector holds, whatever the
	 * run replaces in the other properties. A value reaches the script through the variables.
	 */
	@Test
	void testUpdateNeverRewritesTheScript() throws Exception {
		final Awk awk = Awk.builder()
			.type("awk")
			.script("$1 == id { print \"${attribute::id}\" }")
			.keep("${attribute::id}")
			.variables(Map.of("id", "${attribute::id}"))
			.build();
		awk.setProgram(AwkExecutor.compile(awk.getScript(), AwkExecutor.getUtilityEngine()));

		final Awk copy = awk.copy();
		copy.update(value -> value == null ? null : value.replace("${attribute::id}", "disk1"));

		assertEquals(awk.getScript(), copy.getScript());
		assertSame(awk.getProgram(), copy.getProgram());
		assertEquals("disk1", copy.getKeep());
	}

	/**
	 * A script that is replaced is compiled again, and the compute it was copied from keeps its own.
	 */
	@Test
	void testSetScriptDropsTheProgram() {
		final Awk awk = Awk.builder().type("awk").script("{ print 1 }").build();
		assertEquals(SourceTable.ofText("1;"), runCopy(awk, "x\n"));
		final var program = awk.getProgram();

		final Awk copy = awk.copy();
		copy.setScript("{ print 2 }");
		assertNull(copy.getProgram());
		assertSame(program, awk.getProgram());
		assertEquals(SourceTable.ofText("2;"), runCopy(copy, "x\n"));

		awk.setScript("{ print 3 }");
		assertNull(awk.getProgram());
		assertEquals(SourceTable.ofText("3;"), runCopy(awk, "x\n"));
	}

	@Test
	void testHoldsReference() {
		assertTrue(AwkScriptCheck.holdsReference("BEGIN { days = ${param::days} }"));
		assertTrue(AwkScriptCheck.holdsReference("$1 == \"${attribute::id}\" { print }"));
		assertTrue(AwkScriptCheck.holdsReference("{ print \"${source::monitors.a.discovery.sources.b}\" }"));
		// A file reference names the script, or an embedded file given to a function
		assertFalse(AwkScriptCheck.holdsReference("${file::script.awk}"));
		assertFalse(AwkScriptCheck.holdsReference("BEGIN { args[\"header\"] = \"${file::header.txt}\" }"));
		assertFalse(AwkScriptCheck.holdsReference("{ print $1 }"));
		assertFalse(AwkScriptCheck.holdsReference("# Not ${attribute::id}\n  # nor ${param::days}\n{ print $1 }"));
		assertTrue(AwkScriptCheck.holdsReference("# A comment\n{ print \"${attribute::id}\" }"));
		assertFalse(AwkScriptCheck.holdsReference(null));
	}

	/**
	 * The code that is checked is the one that runs: the embedded file the <code>script</code> property names.
	 */
	@Test
	void testHoldsReferenceInTheEmbeddedFile() {
		final Connector connector = new Connector();
		connector.setEmbeddedFiles(
			Map.of(1, EmbeddedFile.fromString("BEGIN { days = ${param::days} }"), 2, EmbeddedFile.fromString("{ print $1 }"))
		);
		assertTrue(AwkScriptCheck.holdsReference("${file::1}", connector));
		assertFalse(AwkScriptCheck.holdsReference("${file::2}", connector));
		assertTrue(AwkScriptCheck.holdsReference("{ print \"${attribute::id}\" }", connector));
		// A file the connector does not have is reported when the script runs
		assertFalse(AwkScriptCheck.holdsReference("${file::3}", connector));
		assertFalse(AwkScriptCheck.holdsReference(null, connector));
	}

	/**
	 * A connector is {@link java.io.Serializable}: so are the programs it holds once its scripts have run. A program
	 * that went through Java serialization still runs.
	 */
	@Test
	void testConnectorWithProgramsIsSerializable() throws Exception {
		final Awk awk = Awk.builder().type("awk").script("{ print toupper($1) }").build();
		final Source source = HttpSource.builder().type("http").url("/x").computes(new ArrayList<>(List.of(awk))).build();
		final Connector connector = connectorWith(source);
		runCopy(awk, "x\n");
		assertNotNull(awk.getProgram());

		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
			output.writeObject(connector);
		}
		final Connector copy;
		try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
			copy = (Connector) input.readObject();
		}

		final Awk awkCopy = (Awk) ((StandardMonitorJob) copy.getMonitors().get("enclosure")).getDiscovery()
			.getSources()
			.get("source(1)")
			.getComputes()
			.get(0);
		assertEquals("A\n", AwkExecutor.executeAwk(awkCopy.getProgram(), "a b\n", AwkExecutor.getUtilityEngine(), null));
	}
}
