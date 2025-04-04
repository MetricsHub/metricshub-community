package org.metricshub.engine.connector.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.monitor.SimpleMonitorJob;
import org.metricshub.engine.connector.model.monitor.StandardMonitorJob;
import org.metricshub.engine.connector.model.monitor.task.source.TableJoinSource;
import org.metricshub.engine.connector.model.monitor.task.source.TableUnionSource;
import org.metricshub.engine.connector.model.monitor.task.source.WbemSource;
import org.metricshub.engine.connector.model.monitor.task.source.WmiSource;

class ConnectorParserTest {

	private static final Path RESOURCES_TEST_FILES_PATH = Path.of("src/test/resources/test-files");

	@Test
	void testExtendsManagementArrayObjectsDepthExtends() throws IOException {
		new ConnectorParserExtendsManagement("arrayObjectsDepthExtends").test();
	}

	@Test
	void testExtendsManagementArrayObjectsMerge() throws IOException {
		new ConnectorParserExtendsManagement("arrayObjectsMerge").test();
	}

	@Test
	void testExtendsManagementArrayObjectsMergeOneExtends() throws IOException {
		new ConnectorParserExtendsManagement("arrayObjectsMergeOneExtends").test();
	}

	@Test
	void testExtendsManagementMergeObjects() throws IOException {
		new ConnectorParserExtendsManagement("mergeObjects").test();
	}

	@Test
	void testExtendsManagementMergeObjectsOneExtends() throws IOException {
		new ConnectorParserExtendsManagement("mergeObjectsOneExtends").test();
	}

	@Test
	void testExtendsManagementOverwriteArraysSimpleValues() throws IOException {
		new ConnectorParserExtendsManagement("overwriteArraysSimpleValues").test();
	}

	@Test
	void testExtendsManagementOverwriteArraysSimpleValuesOneExtends() throws IOException {
		new ConnectorParserExtendsManagement("overwriteArraysSimpleValuesOneExtends").test();
	}

	@Test
	void testConstantsManagement() throws IOException {
		new ConnectorParserConstantsManagement("management").test();
	}

	@Test
	void testCompiledFilenameUpdate() throws IOException {
		final Connector connector = new ConnectorParserUpdateManagement("connector").parse("connector");
		assertEquals("connector", connector.getConnectorIdentity().getCompiledFilename());
	}

	@Test
	void testAvailableSourceUpdate() throws IOException {
		final Connector connector = new ConnectorParserUpdateManagement("connector/management/availableUpdate")
			.parse("availableSources");
		assertEquals(
			Set.of(WmiSource.class, WbemSource.class, TableJoinSource.class, TableUnionSource.class),
			connector.getSourceTypes()
		);
	}

	@Test
	void testMonitorTaskSourceDepUpdateUseCase1() throws IOException {
		final Connector connector = new ConnectorParserUpdateManagement(
			"connector/management/monitorTaskSourceDep/useCase1"
		)
			.parse("sourceDep");

		final StandardMonitorJob monitorJob = (StandardMonitorJob) connector.getMonitors().get("enclosure");

		final List<Set<String>> expected = buildUseCase1Dependency();

		assertEquals(expected, monitorJob.getDiscovery().getSourceDep());

		assertEquals(List.of(Set.of("source(1)")), monitorJob.getCollect().getSourceDep());
	}

	@Test
	void testMonitorTaskSourceDepUpdateUseCase2() throws IOException {
		final Connector connector = new ConnectorParserUpdateManagement(
			"connector/management/monitorTaskSourceDep/useCase2"
		)
			.parse("sourceDep");

		final StandardMonitorJob monitorJob = (StandardMonitorJob) connector.getMonitors().get("enclosure");

		final List<Set<String>> expected = buildUseCase2Dependency();

		assertEquals(expected, monitorJob.getDiscovery().getSourceDep());
	}

	@Test
	void testMonitorTaskSourceDepUpdateUseCase3() throws IOException {
		final Connector connector = new ConnectorParserUpdateManagement(
			"connector/management/monitorTaskSourceDep/useCase3"
		)
			.parse("sourceDep");

		final StandardMonitorJob monitorJob = (StandardMonitorJob) connector.getMonitors().get("enclosure");

		final List<Set<String>> expected = buildUseCase3Dependency();

		assertEquals(expected, monitorJob.getDiscovery().getSourceDep());
	}

	@Test
	void testMonitorTaskSourceDepUpdateUseCase4() throws IOException {
		final Connector connector = new ConnectorParserUpdateManagement(
			"connector/management/monitorTaskSourceDep/useCase4"
		)
			.parse("sourceDep");

		final StandardMonitorJob monitorJob = (StandardMonitorJob) connector.getMonitors().get("enclosure");

		final List<Set<String>> expected = buildUseCase4Dependency();

		assertEquals(expected, monitorJob.getDiscovery().getSourceDep());
	}

	@Test
	void testMonitorTaskSourceDepUpdateUseCase5() throws IOException {
		final Connector connector = new ConnectorParserUpdateManagement(
			"connector/management/monitorTaskSourceDep/useCase5"
		)
			.parse("sourceDep");

		final StandardMonitorJob monitorJob = (StandardMonitorJob) connector.getMonitors().get("enclosure");

		final List<Set<String>> expected = buildUseCase5MultiCollectDependency();

		assertEquals(expected, monitorJob.getCollect().getSourceDep());
	}

	@Test
	void testMonitorTaskSourceDepUpdateUseCase6() throws IOException {
		final Connector connector = new ConnectorParserUpdateManagement(
			"connector/management/monitorTaskSourceDep/useCase6"
		)
			.parse("sourceDep");

		final SimpleMonitorJob monitorJob = (SimpleMonitorJob) connector.getMonitors().get("enclosure");

		final List<Set<String>> expected = buildUseCase6Dependency();

		assertEquals(expected, monitorJob.getSimple().getSourceDep());
	}

	@Test
	void testMonitorTaskSourceDepUpdateUseCase7() throws IOException {
		final Connector connector = new ConnectorParserUpdateManagement(
			"connector/management/monitorTaskSourceDep/useCase7"
		)
			.parse("sourceDep");

		final StandardMonitorJob monitorJob = (StandardMonitorJob) connector.getMonitors().get("enclosure");

		final List<Set<String>> expected = buildUseCase7MultiCollectDependency();

		assertEquals(expected, monitorJob.getCollect().getSourceDep());
	}

	@Test
	void testMonitorTaskSourceDepUpdateUseCase8() throws IOException {
		final Connector connector = new ConnectorParserUpdateManagement(
			"connector/management/monitorTaskSourceDep/useCase8"
		)
			.parse("sourceDep");

		final StandardMonitorJob monitorJob = (StandardMonitorJob) connector.getMonitors().get("enclosure");

		final List<Set<String>> expected = buildUseCase8MultiCollectDependency();

		assertEquals(expected, monitorJob.getCollect().getSourceDep());
	}

	@Test
	void testMonitorTaskSourceDepUpdateUseCase9() throws IOException {
		final Connector connector = new ConnectorParserUpdateManagement(
			"connector/management/monitorTaskSourceDep/useCase9"
		)
			.parse("sourceDep");

		final StandardMonitorJob monitorJob = (StandardMonitorJob) connector.getMonitors().get("enclosure");

		final List<Set<String>> expected = buildUseCase9MultiCollectDependency();

		assertEquals(expected, monitorJob.getCollect().getSourceDep());
	}

	@Test
	void testMonitorTaskSourceDepUpdateUseCase10() throws IOException {
		final Connector connector = new ConnectorParserUpdateManagement(
			"connector/management/monitorTaskSourceDep/useCase10"
		)
			.parse("sourceDep");

		final StandardMonitorJob monitorJob = (StandardMonitorJob) connector.getMonitors().get("enclosure");

		final List<Set<String>> expected = buildUseCase10MultiCollectDependency();

		assertEquals(expected, monitorJob.getCollect().getSourceDep());
	}

	@Test
	void testMonitorTaskSourceDepUpdateUseCase11() throws IOException {
		final Connector connector = new ConnectorParserUpdateManagement(
			"connector/management/monitorTaskSourceDep/useCase11"
		)
			.parse("sourceDep");

		final StandardMonitorJob monitorJob = (StandardMonitorJob) connector.getMonitors().get("enclosure");

		final List<Set<String>> expected = buildUseCase11MultiCollectDependency();

		assertEquals(expected, monitorJob.getCollect().getSourceDep());
	}

	@Test
	void testMonitorTaskSourceDepUpdateUseCase12() throws IOException {
		final Connector connector = new ConnectorParserUpdateManagement(
			"connector/management/monitorTaskSourceDep/useCase12"
		)
			.parse("sourceDep");

		final StandardMonitorJob monitorJob = (StandardMonitorJob) connector.getMonitors().get("enclosure");

		final List<Set<String>> expected = buildUseCase12Dependency();

		assertEquals(expected, monitorJob.getDiscovery().getSourceDep());
	}

	@Test
	void testMonitorTaskSourceDepUpdateUseCase13() throws IOException {
		final Connector connector = new ConnectorParserUpdateManagement(
			"connector/management/monitorTaskSourceDep/useCase13"
		)
			.parse("sourceDep");

		final StandardMonitorJob monitorJob = (StandardMonitorJob) connector.getMonitors().get("enclosure");

		final List<Set<String>> expected = buildUseCase13Dependency();

		assertEquals(expected, monitorJob.getDiscovery().getSourceDep());
	}

	@Test
	void testBeforeAllSourceDepUpdateUseCase1() throws IOException {
		final Connector connector = new ConnectorParserUpdateManagement("connector/management/beforeAllSourceDep/useCase1")
			.parse("sourceDep");

		final List<Set<String>> expected = buildUseCase1Dependency();

		assertEquals(expected, connector.getBeforeAllSourceDep());
	}

	@Test
	void testBeforeAllSourceDepUpdateUseCase2() throws IOException {
		final Connector connector = new ConnectorParserUpdateManagement("connector/management/beforeAllSourceDep/useCase2")
			.parse("sourceDep");

		final List<Set<String>> expected = buildUseCase2Dependency();

		assertEquals(expected, connector.getBeforeAllSourceDep());
	}

	@Test
	void testBeforeAllSourceDepUpdateUseCase3() throws IOException {
		final Connector connector = new ConnectorParserUpdateManagement("connector/management/beforeAllSourceDep/useCase3")
			.parse("sourceDep");

		final List<Set<String>> expected = buildUseCase3Dependency();
		assertEquals(expected, connector.getBeforeAllSourceDep());
	}

	@Test
	void testBeforeAllSourceDepUpdateUseCase4() throws IOException {
		final Connector connector = new ConnectorParserUpdateManagement("connector/management/beforeAllSourceDep/useCase4")
			.parse("sourceDep");

		final List<Set<String>> expected = new ArrayList<>();
		final Set<String> level1 = new HashSet<>();
		// WMI query
		level1.add("source(1)");

		final Set<String> level2 = new HashSet<>();
		// WMI query with executeForEachEntryOf source(1)
		level2.add("source(2)");

		expected.add(level1);
		expected.add(level2);

		assertEquals(expected, connector.getBeforeAllSourceDep());
	}

	@Test
	void testAfterAllSourceDepUpdateUseCase1() throws IOException {
		final Connector connector = new ConnectorParserUpdateManagement("connector/management/afterAllSourceDep/useCase1")
			.parse("sourceDep");

		final List<Set<String>> expected = buildUseCase1Dependency();

		assertEquals(expected, connector.getAfterAllSourceDep());
	}

	@Test
	void testAfterAllSourceDepUpdateUseCase2() throws IOException {
		final Connector connector = new ConnectorParserUpdateManagement("connector/management/afterAllSourceDep/useCase2")
			.parse("sourceDep");

		final List<Set<String>> expected = buildUseCase2Dependency();

		assertEquals(expected, connector.getAfterAllSourceDep());
	}

	@Test
	void testAfterAllSourceDepUpdateUseCase3() throws IOException {
		final Connector connector = new ConnectorParserUpdateManagement("connector/management/afterAllSourceDep/useCase3")
			.parse("sourceDep");

		final List<Set<String>> expected = buildUseCase3Dependency();
		assertEquals(expected, connector.getAfterAllSourceDep());
	}

	@Test
	void testAfterAllSourceDepUpdateUseCase4() throws IOException {
		final Connector connector = new ConnectorParserUpdateManagement("connector/management/afterAllSourceDep/useCase4")
			.parse("sourceDep");

		final List<Set<String>> expected = new ArrayList<>();
		final Set<String> level1 = new HashSet<>();
		// WMI query
		level1.add("source(1)");

		final Set<String> level2 = new HashSet<>();
		// WMI query with executeForEachEntryOf source(1)
		level2.add("source(2)");

		expected.add(level1);
		expected.add(level2);

		assertEquals(expected, connector.getAfterAllSourceDep());
	}

	@Test
	void testSourceDepUpdateWithBeforeAllAndAfterAll() throws IOException {
		final Connector connector = new ConnectorParserUpdateManagement("connector/management/surroundingSourceDep")
			.parse("sourceDep");

		final List<Set<String>> expectedAfterAllSourceDeps = buildUseCase1Dependency();

		assertEquals(expectedAfterAllSourceDeps, connector.getAfterAllSourceDep());

		final List<Set<String>> expectedBeforeAllSourceDeps = new ArrayList<>();
		final Set<String> levelOne = new HashSet<>();
		levelOne.add("source(10)");
		expectedBeforeAllSourceDeps.add(levelOne);
		assertEquals(expectedBeforeAllSourceDeps, connector.getBeforeAllSourceDep());
	}

	private List<Set<String>> buildUseCase1Dependency() {
		final List<Set<String>> expected = new ArrayList<>();
		final Set<String> level1 = new HashSet<>();
		// WBEM queries
		level1.add("source(1)");
		level1.add("source(2)");
		level1.add("source(3)");
		level1.add("source(5)");

		final Set<String> level2 = new HashSet<>();
		// TableUnion of source(2) and source(3)
		level2.add("source(4)");

		final Set<String> level3 = new HashSet<>();
		// tableJoin of source(1) and source(4)
		level3.add("source(6)");

		final Set<String> level4 = new HashSet<>();
		// tableJoin of source(6) and source(7)
		level4.add("source(7)");

		expected.add(level1);
		expected.add(level2);
		expected.add(level3);
		expected.add(level4);

		return expected;
	}

	private List<Set<String>> buildUseCase2Dependency() {
		final List<Set<String>> expected = new ArrayList<>();
		final Set<String> level1 = new HashSet<>();
		// WBEM queries
		level1.add("source(1)");
		level1.add("source(2)");
		level1.add("source(3)");
		level1.add("source(6)");

		final Set<String> level2 = new HashSet<>();
		// Copy of  source(3)
		level2.add("source(4)");

		final Set<String> level3 = new HashSet<>();
		// tableUnion of source(2) and source(4)
		level3.add("source(5)");

		final Set<String> level4 = new HashSet<>();
		// tableJoin of source(1) and source(5)
		level4.add("source(7)");

		final Set<String> level5 = new HashSet<>();
		// tableJoin of source(6) and source(7)
		level5.add("source(8)");

		expected.add(level1);
		expected.add(level2);
		expected.add(level3);
		expected.add(level4);
		expected.add(level5);

		return expected;
	}

	private List<Set<String>> buildUseCase3Dependency() {
		final List<Set<String>> expected = new ArrayList<>();
		final Set<String> level1 = new HashSet<>();
		// WBEM queries
		level1.add("source(1)");
		level1.add("source(2)");
		level1.add("source(3)");
		level1.add("source(6)");
		level1.add("source(9)");

		final Set<String> level2 = new HashSet<>();
		// Copy of source(3)
		level2.add("source(4)");
		// TableUnion of source(2) and source(3)
		level2.add("source(5)");

		final Set<String> level3 = new HashSet<>();
		// TableJoin of source(1) and source(5)
		level3.add("source(7)");
		// TableJoin of source(1) and source(4)
		level3.add("source(10)");

		final Set<String> level4 = new HashSet<>();
		// TableJoin of source(7) and source(6)
		level4.add("source(8)");
		// TableJoin of source(10) and source(9)
		level4.add("source(11)");

		final Set<String> level5 = new HashSet<>();
		// TableUnion of source(11) and source(8)
		level5.add("source(12)");

		expected.add(level1);
		expected.add(level2);
		expected.add(level3);
		expected.add(level4);
		expected.add(level5);

		return expected;
	}

	private List<Set<String>> buildUseCase4Dependency() {
		final List<Set<String>> expected = new ArrayList<>();
		final Set<String> level1 = new HashSet<>();
		// WMI query
		level1.add("source(1)");

		final Set<String> level2 = new HashSet<>();
		// WMI query with executeForEachEntryOf source(1)
		level2.add("source(2)");

		expected.add(level1);
		expected.add(level2);

		return expected;
	}

	private List<Set<String>> buildUseCase5MultiCollectDependency() {
		final List<Set<String>> expected = new ArrayList<>();
		final Set<String> level1 = new HashSet<>();
		// WBEM query
		level1.add("source(1)");
		// Copy of discovery source(6)
		level1.add("source(2)");

		final Set<String> level2 = new HashSet<>();
		// TableJoin of source(1) and source(2)
		level2.add("source(3)");

		expected.add(level1);
		expected.add(level2);

		return expected;
	}

	private List<Set<String>> buildUseCase6Dependency() {
		return buildUseCase1Dependency();
	}

	private List<Set<String>> buildUseCase7MultiCollectDependency() {
		return buildUseCase5MultiCollectDependency();
	}

	private List<Set<String>> buildUseCase8MultiCollectDependency() {
		final List<Set<String>> expected = new ArrayList<>();
		final Set<String> level1 = new HashSet<>();
		// Copy from a host job source(6)
		level1.add("myExternalSource");
		// Copy of discovery source(6)
		level1.add("source(2)");
		// WBEM query
		level1.add("source(1)");

		final Set<String> level2 = new HashSet<>();
		// TableJoin of source(1) and source(2)
		level2.add("source(3)");

		final Set<String> level3 = new HashSet<>();
		// TableUnion of myExternalSource and source(2) and source(3)
		level3.add("myUnionSource");

		expected.add(level1);
		expected.add(level2);
		expected.add(level3);

		return expected;
	}

	private List<Set<String>> buildUseCase9MultiCollectDependency() {
		final List<Set<String>> expected = new ArrayList<>();
		final Set<String> level1 = new HashSet<>();
		// TableUnion from foreign discovery sources
		level1.add("myUnionSource2");
		// Copy from a host job source(6)
		level1.add("myExternalSource");
		// Copy of discovery source(6)
		level1.add("source(2)");
		// WBEM query
		level1.add("source(1)");

		final Set<String> level2 = new HashSet<>();
		// TableJoin of source(1) and source(2)
		level2.add("source(3)");

		final Set<String> level3 = new HashSet<>();
		// TableUnion of myExternalSource and source(2) and source(3)
		level3.add("myUnionSource1");

		expected.add(level1);
		expected.add(level2);
		expected.add(level3);

		return expected;
	}

	private List<Set<String>> buildUseCase10MultiCollectDependency() {
		final List<Set<String>> expected = new ArrayList<>();
		final Set<String> level1 = new HashSet<>();
		// TableJoin from foreign discovery sources
		level1.add("tableJoin");
		// Copy from a host job source(6)
		level1.add("myExternalSource");
		// Copy of discovery source(6)
		level1.add("source(2)");
		// WBEM query
		level1.add("source(1)");

		final Set<String> level2 = new HashSet<>();
		// TableJoin of source(1) and source(2)
		level2.add("source(3)");

		final Set<String> level3 = new HashSet<>();
		// TableUnion of myExternalSource and source(2) and source(3)
		level3.add("myUnionSource");

		expected.add(level1);
		expected.add(level2);
		expected.add(level3);

		return expected;
	}

	private List<Set<String>> buildUseCase11MultiCollectDependency() {
		final List<Set<String>> expected = new ArrayList<>();
		final Set<String> level1 = new HashSet<>();
		// Copy from a host job source(6)
		level1.add("myExternalSource");
		// Copy of discovery source(6)
		level1.add("source(2)");
		// WBEM query
		level1.add("source(1)");

		final Set<String> level2 = new HashSet<>();
		// TableJoin of source(1) and source(2)
		level2.add("source(3)");

		final Set<String> level3 = new HashSet<>();
		// TableJoin on foreign discovery source and internal  source(3)
		level3.add("tableJoin");
		// TableUnion of myExternalSource and source(2) and source(3)
		level3.add("myUnionSource");

		expected.add(level1);
		expected.add(level2);
		expected.add(level3);

		return expected;
	}

	private List<Set<String>> buildUseCase12Dependency() {
		final List<Set<String>> expected = new ArrayList<>();
		final Set<String> level1 = new HashSet<>();
		// WBEM queries
		level1.add("source(1)");
		level1.add("source(2)");
		level1.add("source(3)");
		level1.add("source(6)");
		// Copy of a bad source
		level1.add("source(4)");

		final Set<String> level2 = new HashSet<>();
		// tableUnion of source(2) and source(4)
		level2.add("source(5)");

		final Set<String> level3 = new HashSet<>();
		// tableJoin of source(1) and source(5)
		level3.add("source(7)");

		final Set<String> level4 = new HashSet<>();
		// tableJoin of source(6) and source(7)
		level4.add("source(8)");

		expected.add(level1);
		expected.add(level2);
		expected.add(level3);
		expected.add(level4);

		return expected;
	}

	private List<Set<String>> buildUseCase13Dependency() {
		// Sources are concatenated manually in source(4) tableUnion
		return buildUseCase1Dependency();
	}

	@Test
	void withNodeProcessorExtendsAndConstantsProcessorTest() {
		final AbstractNodeProcessor processor = ConnectorParser.withNodeProcessor(RESOURCES_TEST_FILES_PATH).getProcessor();
		assertNotNull(
			processor,
			() ->
				"Context: Extends, reference resolver and constants node processors creation. First processor shouldn't be null"
		);
		assertTrue(
			processor instanceof ExtendsProcessor,
			() ->
				"Context: Extends, reference resolver and constants node processors creation. First processor should be: " +
				ExtendsProcessor.class.getSimpleName()
		);

		AbstractNodeProcessor next = processor.getNext();
		assertNotNull(
			next,
			() ->
				"Context: Extends, reference resolver and constants node processors creation. Second processor shouldn't be null"
		);
		assertTrue(
			next instanceof ReferenceResolverProcessor,
			() ->
				"Context: Extends, reference resolver and constants node processors creation. Second processor should be: " +
				ReferenceResolverProcessor.class.getSimpleName()
		);
		next = next.next;
		assertNotNull(
			next,
			() ->
				"Context: Extends, reference resolver and constants node processors creation. Third processor shouldn't be null"
		);
		assertTrue(
			next instanceof ConstantsProcessor,
			() ->
				"Context: Extends, reference resolver and constants node processors creation. Third processor should be: " +
				ConstantsProcessor.class.getSimpleName()
		);
	}

	@Test
	void withExtendsAndTemplateVariableProcessorTest() {
		final AbstractNodeProcessor processor = ConnectorParser
			.withNodeProcessor(RESOURCES_TEST_FILES_PATH, Collections.emptyMap())
			.getProcessor();
		assertNotNull(
			processor,
			() ->
				"Context: Extends, template variable, reference resolver and constants node processors creation. First processor shouldn't be null"
		);
		assertTrue(
			processor instanceof ExtendsProcessor,
			() ->
				"Context: Extends, template variable, reference resolver and constants node processors creation. First processor should be: " +
				ExtendsProcessor.class.getSimpleName()
		);

		AbstractNodeProcessor next = processor.getNext();
		assertNotNull(
			next,
			() ->
				"Context: Extends, template variable, reference resolver and constants node processors creation. Second processor shouldn't be null"
		);
		assertTrue(
			next instanceof TemplateVariableProcessor,
			() ->
				"Context: Extends, template variable, reference resolver and constants node processors creation. Second processor should be: " +
				TemplateVariableProcessor.class.getSimpleName()
		);

		next = next.getNext();
		assertNotNull(
			next,
			() ->
				"Context: Extends, template variable, reference resolver and constants node processors creation. Third processor shouldn't be null"
		);
		assertTrue(
			next instanceof ReferenceResolverProcessor,
			() ->
				"Context: Extends, template variable, reference resolver and constants node processors creation. Third processor should be: " +
				ReferenceResolverProcessor.class.getSimpleName()
		);
		next = next.getNext();
		assertNotNull(
			next,
			() ->
				"Context: Extends, template variable, reference resolver and constants node processors creation. Fourth processor shouldn't be null"
		);
		assertTrue(
			next instanceof ConstantsProcessor,
			() ->
				"Context: Extends, template variable, reference resolver and constants node processors creation. Fourth processor should be: " +
				ConstantsProcessor.class.getSimpleName()
		);
	}

	@Test
	void testMonitorKeys() throws IOException {
		final Connector connector = new ConnectorParserUpdateManagement("connector/management/keys").parse("monitorKeys");

		// Check enclosure keys
		final StandardMonitorJob enclosureJob = (StandardMonitorJob) connector.getMonitors().get("enclosure");
		assertEquals(List.of("id", "model"), new ArrayList<>(enclosureJob.getKeys()));

		// Check disk controller keys
		final StandardMonitorJob diskControllerJob = (StandardMonitorJob) connector.getMonitors().get("disk_controller");
		assertEquals(List.of("id", "model", "controller_number"), new ArrayList<>(diskControllerJob.getKeys()));

		// Check physical disk keys
		final StandardMonitorJob physicalDiskJob = (StandardMonitorJob) connector.getMonitors().get("physical_disk");
		assertEquals(List.of("id"), new ArrayList<>(physicalDiskJob.getKeys()));
	}
}
