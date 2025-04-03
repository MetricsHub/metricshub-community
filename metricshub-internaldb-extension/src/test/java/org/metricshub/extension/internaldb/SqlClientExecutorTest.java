package org.metricshub.extension.internaldb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sentrysoftware.metricshub.engine.configuration.HostConfiguration;
import org.sentrysoftware.metricshub.engine.connector.model.common.DeviceKind;
import org.sentrysoftware.metricshub.engine.connector.model.common.SqlColumn;
import org.sentrysoftware.metricshub.engine.connector.model.common.SqlTable;
import org.sentrysoftware.metricshub.engine.strategy.source.SourceTable;
import org.sentrysoftware.metricshub.engine.telemetry.ConnectorNamespace;
import org.sentrysoftware.metricshub.engine.telemetry.HostProperties;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;

class SqlClientExecutorTest {

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
	private static final String CONNECTOR_ID = "myConnector";
	private static final String TIME = "14:30:00";
	private static final String DATE = "2025-01-30";
	private static final String TIMESTAMP = "2025-01-30 14:30:00";

	@Test
	void testSqlQuery() {
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname("localhost")
			.hostId("localhost")
			.hostType(DeviceKind.LINUX)
			.build();

		final Map<String, SourceTable> mapSources = new HashMap<>();
		SourceTable tabl1 = SourceTable
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
		connectorNamespaces.put(CONNECTOR_ID, connectorNamespace);

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
		final SqlClientExecutor sqlClientExecutor = SqlClientExecutor
			.builder()
			.telemetryManager(telemetryManager)
			.connectorId(CONNECTOR_ID)
			.build();

		// Sql tables null
		assertEquals(new ArrayList<>(), sqlClientExecutor.executeQuery(null, "SQL QUERY;"));
		assertEquals(new ArrayList<>(), sqlClientExecutor.executeQuery(new ArrayList<>(), null));

		// SqlSource with empty tables
		List<SqlColumn> columnsTable1 = new ArrayList<>();
		List<SqlColumn> columnsTable2 = new ArrayList<>();
		final SqlTable sqlTable1 = SqlTable.builder().alias("T1").columns(columnsTable1).source(TAB1_REF).build();
		final SqlTable sqlTable2 = SqlTable.builder().alias("T2").columns(columnsTable2).source(TAB2_REF).build();

		final List<SqlTable> sqlTables = Arrays.asList(sqlTable1, sqlTable2);

		assertEquals(new ArrayList<>(), sqlClientExecutor.executeQuery(sqlTables, "SQL QUERY;"));

		// SqlSource well formed
		SqlColumn sqlColumn1Table1 = SqlColumn.builder().name("COL1_1").number(1).type("VARCHAR(255)").build();
		SqlColumn sqlColumn2Table1 = SqlColumn.builder().name("COL2_1").number(3).type("BOOLEAN").build();
		columnsTable1.add(sqlColumn1Table1);
		columnsTable1.add(sqlColumn2Table1);

		SqlColumn sqlColumn1Table2 = SqlColumn.builder().name("COL1_2").number(2).type("VARCHAR(255)").build();
		SqlColumn sqlColumn2Table2 = SqlColumn.builder().name("COL2_2").number(4).type("BOOLEAN").build();
		columnsTable2.add(sqlColumn1Table2);
		columnsTable2.add(sqlColumn2Table2);

		List<List<String>> result = sqlClientExecutor.executeQuery(
			sqlTables,
			"SELECT COL1_1, COL2_1, COL1_2, COL2_2 FROM T1 JOIN T2 ON COL1_1 = COL1_2;"
		);

		List<List<String>> expectedResult = Arrays.asList(
			Arrays.asList(LOWERCASE_A, TRUE, LOWERCASE_A, TRUE),
			Arrays.asList(LOWERCASE_B, TRUE, LOWERCASE_B, FALSE),
			Arrays.asList(LOWERCASE_C, FALSE, LOWERCASE_C, TRUE),
			Arrays.asList(LOWERCASE_D, FALSE, LOWERCASE_D, FALSE)
		);

		assertEquals(expectedResult, result);

		sqlColumn1Table1 = SqlColumn.builder().name("COL1_1").number(1).type("VARCHAR(255)").build();
		sqlColumn2Table1 = SqlColumn.builder().name("COL2_1").number(4).type("VARCHAR(255)").build();
		columnsTable1 = Arrays.asList(sqlColumn1Table1, sqlColumn2Table1);
		sqlTable1.setColumns(columnsTable1);

		sqlColumn1Table2 = SqlColumn.builder().name("COL1_2").number(1).type("VARCHAR(255)").build();
		sqlColumn2Table2 = SqlColumn.builder().name("COL2_2").number(3).type("VARCHAR(255)").build();
		columnsTable2 = Arrays.asList(sqlColumn1Table2, sqlColumn2Table2);
		sqlTable2.setColumns(columnsTable2);

		expectedResult =
			Arrays.asList(
				Arrays.asList(LOWERCASE_A, ONE, ONE, UPPERCASE_V1),
				Arrays.asList(LOWERCASE_A, ONE, ONE, UPPERCASE_V2),
				Arrays.asList(LOWERCASE_A, ONE, ONE, UPPERCASE_V3),
				Arrays.asList(LOWERCASE_B, TWO, TWO, UPPERCASE_V4)
			);
		result =
			sqlClientExecutor.executeQuery(
				sqlTables,
				"SELECT COL1_1, COL2_1, COL1_2, COL2_2 FROM T1 JOIN T2 ON COL2_1 = COL1_2;"
			);

		assertEquals(expectedResult, result);

		expectedResult =
			Arrays.asList(
				Arrays.asList(LOWERCASE_A, ONE, ONE, UPPERCASE_V1),
				Arrays.asList(LOWERCASE_A, ONE, ONE, UPPERCASE_V2),
				Arrays.asList(LOWERCASE_A, ONE, ONE, UPPERCASE_V3),
				Arrays.asList(LOWERCASE_B, TWO, TWO, UPPERCASE_V4),
				Arrays.asList(LOWERCASE_C, THREE, "", ""),
				Arrays.asList(LOWERCASE_D, FOUR, "", "")
			);
		result =
			sqlClientExecutor.executeQuery(
				sqlTables,
				"SELECT COL1_1, COL2_1, COL1_2, COL2_2 FROM T1 LEFT OUTER JOIN T2 ON COL2_1 = COL1_2;"
			);

		assertEquals(expectedResult, result);

		expectedResult =
			Arrays.asList(
				Arrays.asList(LOWERCASE_B, TWO, ONE, UPPERCASE_V1),
				Arrays.asList(LOWERCASE_B, TWO, ONE, UPPERCASE_V2),
				Arrays.asList(LOWERCASE_B, TWO, ONE, UPPERCASE_V3),
				Arrays.asList(LOWERCASE_C, THREE, ONE, UPPERCASE_V1),
				Arrays.asList(LOWERCASE_C, THREE, ONE, UPPERCASE_V2),
				Arrays.asList(LOWERCASE_C, THREE, ONE, UPPERCASE_V3),
				Arrays.asList(LOWERCASE_C, THREE, TWO, UPPERCASE_V4),
				Arrays.asList(LOWERCASE_D, FOUR, ONE, UPPERCASE_V1),
				Arrays.asList(LOWERCASE_D, FOUR, ONE, UPPERCASE_V2),
				Arrays.asList(LOWERCASE_D, FOUR, ONE, UPPERCASE_V3),
				Arrays.asList(LOWERCASE_D, FOUR, TWO, UPPERCASE_V4)
			);
		result =
			sqlClientExecutor.executeQuery(
				sqlTables,
				"SELECT COL1_1, COL2_1, COL1_2, COL2_2 FROM T1 JOIN T2 ON COL2_1 > COL1_2;"
			);

		assertEquals(expectedResult, result);

		expectedResult =
			Arrays.asList(
				Arrays.asList(LOWERCASE_A, ONE, "a1"),
				Arrays.asList(LOWERCASE_B, TWO, "b2"),
				Arrays.asList(LOWERCASE_C, THREE, "c3"),
				Arrays.asList(LOWERCASE_D, FOUR, "d4")
			);
		result = sqlClientExecutor.executeQuery(sqlTables, "SELECT COL1_1, COL2_1, CONCAT(COL1_1, COL2_1) FROM T1;");

		assertEquals(expectedResult, result);

		expectedResult =
			Arrays.asList(
				Arrays.asList(ONE, UPPERCASE_V1, "1g1"),
				Arrays.asList(ONE, UPPERCASE_V2, "1g2"),
				Arrays.asList(ONE, UPPERCASE_V3, "1g3"),
				Arrays.asList(TWO, UPPERCASE_V4, "2g4")
			);
		result =
			sqlClientExecutor.executeQuery(
				sqlTables,
				"SELECT COL1_2, COL2_2, REPLACE(CONCAT(COL1_2, COL2_2), 'V', LOWER('G')) FROM T2;"
			);

		assertEquals(expectedResult, result);

		expectedResult =
			Arrays.asList(
				Arrays.asList(ONE, UPPERCASE_V1, "1g1"),
				Arrays.asList(ONE, UPPERCASE_V2, "1g2"),
				Arrays.asList(ONE, UPPERCASE_V3, "1g3"),
				Arrays.asList(TWO, UPPERCASE_V4, "2G4")
			);
		result =
			sqlClientExecutor.executeQuery(
				sqlTables,
				"SELECT COL1_2, COL2_2, REPLACE(CONCAT(COL1_2, COL2_2), 'V', CASE WHEN COL1_2 = '1' THEN LOWER('G') ELSE 'G' END) FROM T2;"
			);

		assertEquals(expectedResult, result);

		sqlColumn1Table1 = SqlColumn.builder().name("COL1_1").number(1).type("VARCHAR(255)").build();
		sqlColumn2Table1 = SqlColumn.builder().name("COL2_1").number(3).type("BOOLEAN").build();
		columnsTable1 =
			Arrays.asList(
				sqlColumn1Table1,
				sqlColumn2Table1,
				SqlColumn.builder().name("COL3_1").number(4).type("INTEGER").build()
			);
		sqlTable1.setColumns(columnsTable1);

		expectedResult =
			Arrays.asList(
				Arrays.asList(LOWERCASE_A, TRUE, LOWERCASE_A),
				Arrays.asList(LOWERCASE_B, TRUE, LOWERCASE_B),
				Arrays.asList(LOWERCASE_C, FALSE, THREE),
				Arrays.asList(LOWERCASE_D, FALSE, FOUR)
			);
		result =
			sqlClientExecutor.executeQuery(
				sqlTables,
				"SELECT COL1_1, COL2_1, CASE WHEN COL2_1 THEN COL1_1 ELSE CAST(COL3_1 AS VARCHAR(255)) END FROM T1;"
			);

		assertEquals(expectedResult, result);

		expectedResult =
			Arrays.asList(
				Arrays.asList(ONE, UPPERCASE_V1, "2"),
				Arrays.asList(ONE, UPPERCASE_V2, "2"),
				Arrays.asList(ONE, UPPERCASE_V3, "2"),
				Arrays.asList(TWO, UPPERCASE_V4, "2")
			);
		result =
			sqlClientExecutor.executeQuery(sqlTables, "SELECT COL1_2, COL2_2, LOCATE('V', CONCAT(COL1_2, COL2_2)) FROM T2;");

		assertEquals(expectedResult, result);

		expectedResult = Arrays.asList(Arrays.asList(LOWERCASE_B, ""), Arrays.asList(LOWERCASE_D, ""));

		tabl1 =
			SourceTable
				.builder()
				.table(
					Arrays.asList(
						Arrays.asList(LOWERCASE_A, LOWERCASE_V1, TRUE, ONE),
						Arrays.asList(LOWERCASE_B, "", TRUE, TWO),
						Arrays.asList(LOWERCASE_C, LOWERCASE_V3, FALSE, THREE),
						Arrays.asList(LOWERCASE_D, null, FALSE, FOUR)
					)
				)
				.build();
		mapSources.put(TAB1_REF, tabl1);
		mapSources.put(TAB2_REF, tabl2);
		connectorNamespace.setSourceTables(mapSources);

		sqlColumn1Table1 = SqlColumn.builder().name("COL1_1").number(1).type("VARCHAR(255)").build();
		sqlColumn2Table1 = SqlColumn.builder().name("COL2_1").number(2).type("VARCHAR(255)").build();
		columnsTable1 = Arrays.asList(sqlColumn1Table1, sqlColumn2Table1);
		sqlTable1.setColumns(columnsTable1);
		result =
			sqlClientExecutor.executeQuery(Arrays.asList(sqlTable1), "SELECT COL1_1, COL2_1 FROM T1 WHERE COL2_1 IS NULL;");
		assertEquals(expectedResult, result);
	}

	@Test
	void testSqlQueryWithDatesAndTimes() {
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname("localhost")
			.hostId("localhost")
			.hostType(DeviceKind.LINUX)
			.build();

		final Map<String, SourceTable> mapSources = new HashMap<>();
		SourceTable tabl1 = SourceTable
			.builder()
			.table(
				Arrays.asList(
					Arrays.asList(LOWERCASE_A, LOWERCASE_V1, TRUE, ONE, DATE, TIME, TIMESTAMP),
					Arrays.asList(LOWERCASE_B, LOWERCASE_V2, TRUE, TWO, DATE, TIME, TIMESTAMP),
					Arrays.asList(LOWERCASE_C, LOWERCASE_V3, FALSE, THREE, DATE, TIME, TIMESTAMP),
					Arrays.asList(LOWERCASE_D, LOWERCASE_V4, FALSE, FOUR, DATE, TIME, TIMESTAMP)
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
		connectorNamespaces.put(CONNECTOR_ID, connectorNamespace);

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
		final SqlClientExecutor sqlClientExecutor = SqlClientExecutor
			.builder()
			.telemetryManager(telemetryManager)
			.connectorId(CONNECTOR_ID)
			.build();

		// SqlSource with empty tables
		List<SqlColumn> columnsTable1 = new ArrayList<>();
		List<SqlColumn> columnsTable2 = new ArrayList<>();

		// SqlSource well formed
		SqlColumn sqlColumn1Table1 = SqlColumn.builder().name("COL1_1").number(1).type("VARCHAR(255)").build();
		SqlColumn sqlColumn2Table1 = SqlColumn.builder().name("COL2_1").number(3).type("BOOLEAN").build();
		SqlColumn sqlColumn3Table1 = SqlColumn.builder().name("COL3_1").number(5).type("DATE").build();
		SqlColumn sqlColumn4Table1 = SqlColumn.builder().name("COL4_1").number(6).type("TIME").build();
		SqlColumn sqlColumn5Table1 = SqlColumn.builder().name("COL5_1").number(7).type("TIMESTAMP").build();
		columnsTable1.add(sqlColumn1Table1);
		columnsTable1.add(sqlColumn2Table1);
		columnsTable1.add(sqlColumn3Table1);
		columnsTable1.add(sqlColumn4Table1);
		columnsTable1.add(sqlColumn5Table1);

		SqlColumn sqlColumn1Table2 = SqlColumn.builder().name("COL1_2").number(2).type("VARCHAR(255)").build();
		SqlColumn sqlColumn2Table2 = SqlColumn.builder().name("COL2_2").number(4).type("BOOLEAN").build();
		columnsTable2.add(sqlColumn1Table2);
		columnsTable2.add(sqlColumn2Table2);

		final SqlTable sqlTable1 = SqlTable.builder().alias("T1").columns(columnsTable1).source(TAB1_REF).build();
		final SqlTable sqlTable2 = SqlTable.builder().alias("T2").columns(columnsTable2).source(TAB2_REF).build();

		final List<SqlTable> sqlTables = Arrays.asList(sqlTable1, sqlTable2);

		List<List<String>> result = sqlClientExecutor.executeQuery(
			sqlTables,
			"SELECT COL1_1, COL2_1, COL1_2, COL2_2, COL3_1, COL4_1, COL5_1 FROM T1 JOIN T2 ON COL1_1 = COL1_2;"
		);

		final List<List<String>> expectedResult = Arrays.asList(
			Arrays.asList(LOWERCASE_A, TRUE, LOWERCASE_A, TRUE, DATE, TIME, TIMESTAMP),
			Arrays.asList(LOWERCASE_B, TRUE, LOWERCASE_B, FALSE, DATE, TIME, TIMESTAMP),
			Arrays.asList(LOWERCASE_C, FALSE, LOWERCASE_C, TRUE, DATE, TIME, TIMESTAMP),
			Arrays.asList(LOWERCASE_D, FALSE, LOWERCASE_D, FALSE, DATE, TIME, TIMESTAMP)
		);

		assertEquals(expectedResult, result);
	}
}
