package org.metricshub.extension.internaldb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.common.SqlColumn;
import org.metricshub.engine.connector.model.common.SqlTable;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.InternalDbQuerySource;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiSource;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.ConnectorNamespace;
import org.metricshub.engine.telemetry.HostProperties;
import org.metricshub.engine.telemetry.TelemetryManager;

class InternalDbExtensionTest {

	private static final String LOWERCASE_A = "a";
	private static final String LOWERCASE_B = "b";
	private static final String LOWERCASE_C = "c";
	private static final String LOWERCASE_D = "d";
	private static final String LOWERCASE_V1 = "v1";
	private static final String LOWERCASE_V2 = "v2";
	private static final String LOWERCASE_V3 = "v3";
	private static final String LOWERCASE_V4 = "v4";
	private static final String UPPERCASE_V1 = "V1";
	private static final String UPPERCASE_V2 = "V2";
	private static final String UPPERCASE_V3 = "V3";
	private static final String UPPERCASE_V4 = "V4";
	private static final String TAB1_REF = "${source::monitors.cpu.discovery.sources.tab1}";
	private static final String TAB2_REF = "${source::monitors.cpu.discovery.sources.tab2}";
	private static final String ONE = "1";
	private static final String TWO = "2";
	private static final String THREE = "3";
	private static final String FOUR = "4";
	private static final String TRUE = "TRUE";
	private static final String FALSE = "FALSE";

	@Test
	void testIsValidSource() {
		final InternalDbExtension internalDbExtension = new InternalDbExtension();
		assertFalse(internalDbExtension.isValidSource(new IpmiSource()));
		assertTrue(internalDbExtension.isValidSource(new InternalDbQuerySource()));
	}

	@Test
	void testProcessSource() {
		final String connectorId = "myConnector";

		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname("localhost")
			.hostId("localhost")
			.hostType(DeviceKind.LINUX)
			.build();

		final Map<String, SourceTable> mapSources = new HashMap<>();
		final SourceTable tabl1 = SourceTable
			.builder()
			.table(
				Arrays.asList(
					Arrays.asList(LOWERCASE_A, LOWERCASE_V1, TRUE, ONE),
					Arrays.asList(LOWERCASE_B, LOWERCASE_V2, TRUE, TWO),
					Arrays.asList(LOWERCASE_C, LOWERCASE_V3, FALSE, THREE),
					Arrays.asList(LOWERCASE_D, LOWERCASE_V4, FALSE, FOUR)
				)
			)
			.build();
		final SourceTable tabl2 = SourceTable
			.builder()
			.table(
				Arrays.asList(
					Arrays.asList(ONE, LOWERCASE_A, UPPERCASE_V1, TRUE),
					Arrays.asList(ONE, LOWERCASE_B, UPPERCASE_V2, FALSE),
					Arrays.asList(ONE, LOWERCASE_C, UPPERCASE_V3, TRUE),
					Arrays.asList(TWO, LOWERCASE_D, UPPERCASE_V4, FALSE)
				)
			)
			.build();
		mapSources.put(TAB1_REF, tabl1);
		mapSources.put(TAB2_REF, tabl2);
		final ConnectorNamespace connectorNamespace = ConnectorNamespace.builder().sourceTables(mapSources).build();
		final Map<String, ConnectorNamespace> connectorNamespaces = new HashMap<>();
		connectorNamespaces.put(connectorId, connectorNamespace);

		final HostProperties hostProperties = HostProperties
			.builder()
			.connectorNamespaces(connectorNamespaces)
			.isLocalhost(true)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(hostConfiguration)
			.hostProperties(hostProperties)
			.build();

		final InternalDbExtension internalDbExtension = new InternalDbExtension();

		// Source null
		SourceTable sourceTableResult = internalDbExtension.processSource(null, connectorId, telemetryManager);
		assertEquals(new ArrayList<>(), sourceTableResult.getTable());

		// Source is not a SQL Source
		sourceTableResult = internalDbExtension.processSource(new HttpSource(), connectorId, telemetryManager);
		assertEquals(new ArrayList<>(), sourceTableResult.getTable());

		// Empty SQL Source
		sourceTableResult = internalDbExtension.processSource(new InternalDbQuerySource(), connectorId, telemetryManager);
		assertEquals(new ArrayList<>(), sourceTableResult.getTable());

		final List<SqlColumn> columnsTable1 = new ArrayList<>();
		final List<SqlColumn> columnsTable2 = new ArrayList<>();

		columnsTable1.add(SqlColumn.builder().name("COL1_1").number(1).type("VARCHAR(255)").build());
		columnsTable1.add(SqlColumn.builder().name("COL2_1").number(3).type("BOOLEAN").build());

		columnsTable2.add(SqlColumn.builder().name("COL1_2").number(2).type("VARCHAR(255)").build());
		columnsTable2.add(SqlColumn.builder().name("COL2_2").number(4).type("BOOLEAN").build());

		final List<SqlTable> sqlTables = Arrays.asList(
			SqlTable.builder().alias("T1").columns(columnsTable1).source(TAB1_REF).build(),
			SqlTable.builder().alias("T2").columns(columnsTable2).source(TAB2_REF).build()
		);

		final InternalDbQuerySource internalDbQuery = InternalDbQuerySource
			.builder()
			.query("SELECT COL1_1, COL2_1, COL1_2, COL2_2 FROM T1 JOIN T2 ON COL1_1 = COL1_2;")
			.tables(sqlTables)
			.build();

		final List<List<String>> expectedResult = Arrays.asList(
			Arrays.asList(LOWERCASE_A, TRUE, LOWERCASE_A, TRUE),
			Arrays.asList(LOWERCASE_B, TRUE, LOWERCASE_B, FALSE),
			Arrays.asList(LOWERCASE_C, FALSE, LOWERCASE_C, TRUE),
			Arrays.asList(LOWERCASE_D, FALSE, LOWERCASE_D, FALSE)
		);

		sourceTableResult = internalDbExtension.processSource(internalDbQuery, connectorId, telemetryManager);
		assertEquals(expectedResult, sourceTableResult.getTable());
	}
}
