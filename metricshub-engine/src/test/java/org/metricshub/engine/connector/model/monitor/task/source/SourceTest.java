package org.metricshub.engine.connector.model.monitor.task.source;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jawk.jrt.AssocArray;
import io.jawk.jrt.StrNum;
import java.lang.reflect.Constructor;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.connector.deserializer.PostDeserializeHelper;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Awk;

class SourceTest {

	private static final String SOURCE_REF0 = "${source::beforeAll.source1}";
	private static final String SOURCE_REF1 = "${source::monitors.cpu.discovery.sources.source1}";
	private static final String SOURCE_REF2 = "${source::monitors.cpu.discovery.sources.source2}";

	private static final ObjectMapper MAPPER = PostDeserializeHelper.addPostDeserializeSupport(
		JsonHelper.buildYamlMapper()
	);

	@Test
	void testReferencesCopy() throws IOException {
		final String copyYaml = """
			type: copy
			from: ${source::monitors.cpu.discovery.sources.source1}
			forceSerialization: false
			""";
		final Source source = MAPPER.readValue(copyYaml, Source.class);
		assertEquals(Set.of(SOURCE_REF1), source.getReferences());
	}

	@Test
	void testReferencesHttp() throws IOException {
		final String httpYaml = """
			type: http
			url: url/$entry.column(1)$
			header: ${source::beforeAll.source1}
			body: body1
			authenticationToken: authToken
			resultContent: body
			forceSerialization: false
			executeForEachEntryOf:
			  source: ${source::monitors.cpu.discovery.sources.source1}
			  concatMethod: list
			""";

		final Source source = MAPPER.readValue(httpYaml, Source.class);
		assertEquals(Set.of(SOURCE_REF0, SOURCE_REF1), source.getReferences());
	}

	@Test
	void testReferencesIpmi() throws IOException {
		final String ipmiYaml = """
			type: ipmi
			forceSerialization: false
			""";

		final Source source = MAPPER.readValue(ipmiYaml, Source.class);
		assertEquals(Collections.emptySet(), source.getReferences());
	}

	@Test
	void testReferencesCommandLine() throws IOException {
		final String commandLineYaml = """
			type: commandLine
			forceSerialization: false
			commandLine: ${source::monitors.cpu.discovery.sources.source1}
			exclude: exclude
			keep: keep
			beginAtLineNumber: 1
			endAtLineNumber: 10
			separators: "\t"
			selectColumns: 1,2,3
			""";

		final Source source = MAPPER.readValue(commandLineYaml, Source.class);
		assertEquals(Set.of(SOURCE_REF1), source.getReferences());
	}

	@Test
	void testReferencesSnmpGet() throws IOException {
		final String snmpGetYaml = """
			type: snmpGet
			forceSerialization: false
			oid: ${source::monitors.cpu.discovery.sources.source1}
			""";

		final Source source = MAPPER.readValue(snmpGetYaml, Source.class);
		assertEquals(Set.of(SOURCE_REF1), source.getReferences());
	}

	@Test
	void testReferencesSnmpTable() throws IOException {
		final String snmpTableYaml = """
			type: snmpTable
			forceSerialization: false
			oid: ${source::monitors.cpu.discovery.sources.source1}
			selectColumns: ID,1,2,3
			""";

		final Source source = MAPPER.readValue(snmpTableYaml, Source.class);
		assertEquals(Set.of(SOURCE_REF1), source.getReferences());
	}

	@Test
	void testReferencesStatic() throws IOException {
		final String staticSourceYaml = """
			type: static
			value: ${source::monitors.cpu.discovery.sources.source1}
			""";
		final Source source = MAPPER.readValue(staticSourceYaml, Source.class);
		assertEquals(Set.of(SOURCE_REF1), source.getReferences());
	}

	@Test
	void testReferencesTableJoin() throws IOException {
		final String tableJoinYaml = """
			type: tableJoin
			forceSerialization: false
			leftTable: ${source::monitors.cpu.discovery.sources.source1}
			rightTable: ${source::monitors.cpu.discovery.sources.source2}
			defaultRightLine: ;;;;;;
			keyType: WBEM
			leftKeyColumn: 1
			rightKeyColumn: 2
			""";

		final Source source = MAPPER.readValue(tableJoinYaml, Source.class);
		assertEquals(Set.of(SOURCE_REF1, SOURCE_REF2), source.getReferences());
	}

	@Test
	void testReferencesTableUnion() throws IOException {
		final String tableUnionYaml = """
			type: tableUnion
			forceSerialization: false
			tables:
			- ${source::monitors.cpu.discovery.sources.source1}
			- ${source::monitors.cpu.discovery.sources.source2}
			""";

		final Source source = MAPPER.readValue(tableUnionYaml, Source.class);
		assertEquals(Set.of(SOURCE_REF1, SOURCE_REF2), source.getReferences());
	}

	@Test
	void testReferencesTableLiterals() throws IOException {
		// A table literal written as a list of rows sits next to the references, which are found as before
		final String tableUnionYaml = """
			type: tableUnion
			tables:
			- [ [ a, "b;c" ] ]
			- ${source::monitors.cpu.discovery.sources.source1}
			""";
		assertEquals(Set.of(SOURCE_REF1), MAPPER.readValue(tableUnionYaml, Source.class).getReferences());

		final String tableJoinYaml = """
			type: tableJoin
			leftTable: [ [ a, b ], [ c, d ] ]
			rightTable: ${source::monitors.cpu.discovery.sources.source2}
			leftKeyColumn: 1
			rightKeyColumn: 1
			""";
		assertEquals(Set.of(SOURCE_REF2), MAPPER.readValue(tableJoinYaml, Source.class).getReferences());
	}

	@Test
	void testReferencesTextUnion() throws IOException {
		// The members of a textUnion are dependencies, as the tables of a tableUnion
		final String textUnionYaml = """
			type: textUnion
			texts:
			- ${source::monitors.cpu.discovery.sources.source1}
			- ${source::beforeAll.source1}
			""";
		assertEquals(Set.of(SOURCE_REF1, SOURCE_REF0), MAPPER.readValue(textUnionYaml, Source.class).getReferences());
	}

	@Test
	void testReferencesWbem() throws IOException {
		final String wbemYaml = """
			type: wbem
			forceSerialization: false
			query: ${source::monitors.cpu.discovery.sources.source1}
			namespace: ${source::monitors.cpu.discovery.sources.source2}
			""";

		final Source source = MAPPER.readValue(wbemYaml, Source.class);
		assertEquals(Set.of(SOURCE_REF1, SOURCE_REF2), source.getReferences());
	}

	@Test
	void testReferencesWmi() throws IOException {
		final String wmiYaml = """
			type: wmi
			forceSerialization: false
			query: ${source::monitors.cpu.discovery.sources.source1}
			namespace: ${source::monitors.cpu.discovery.sources.source2}
			""";

		final Source source = MAPPER.readValue(wmiYaml, Source.class);
		assertEquals(Set.of(SOURCE_REF1, SOURCE_REF2), source.getReferences());
	}

	@Test
	void testReferencesRequireSourcePrefix() throws IOException {
		final String wmiYaml = """
			type: wmi
			forceSerialization: false
			query: SELECT afterAll.Name FROM tools.x.sources.y} WHERE 1
			namespace: ${source::monitors.cpu.discovery.sources.source1}
			""";

		final Source source = MAPPER.readValue(wmiYaml, Source.class);
		assertEquals(Set.of(SOURCE_REF1), source.getReferences());
	}

	/**
	 * Builds a Jawk numeric string, as Jawk hands over a field or a value of an awk variable list, map or table.
	 *
	 * @param value The text
	 * @return the numeric string
	 */
	private static StrNum strNum(final String value) throws ReflectiveOperationException {
		final Constructor<StrNum> constructor = StrNum.class.getDeclaredConstructor(String.class);
		constructor.setAccessible(true);
		return constructor.newInstance(value);
	}

	/**
	 * An awk script that copies a cell of a variable table into the arguments of <code>executeHttpRequest</code>
	 * hands over a Jawk numeric string: the source reads its text, at any depth (#1579).
	 */
	@Test
	void testFromMapReadsNumericStringsAsText() throws ReflectiveOperationException {
		final AssocArray arguments = AssocArray.createHash();
		arguments.put("method", "get");
		arguments.put("path", strNum("/redfish/v1/Systems/1"));
		arguments.put("authenticationToken", strNum("abc123"));
		arguments.put("header", strNum("42"));
		arguments.put("resultContent", "body");

		final HttpSource source = Source.fromMap(HttpSource.class, arguments);

		assertEquals("/redfish/v1/Systems/1", source.getPath());
		assertEquals("abc123", source.getAuthenticationToken());
		assertEquals("42", source.getHeader());
		assertEquals("get", source.getMethod().name().toLowerCase());
	}

	/**
	 * Numeric strings nested in a list of maps are read as their text too.
	 */
	@Test
	void testFromMapReadsNestedNumericStringsAsText() throws ReflectiveOperationException {
		final AssocArray compute = AssocArray.createHash();
		compute.put("type", "awk");
		compute.put("script", strNum("{ print $1 }"));
		final AssocArray arguments = AssocArray.createHash();
		arguments.put("path", "/api");
		arguments.put("computes", List.of(compute));

		final HttpSource source = Source.fromMap(HttpSource.class, arguments);

		assertEquals("{ print $1 }", ((Awk) source.getComputes().get(0)).getScript());
	}
}
