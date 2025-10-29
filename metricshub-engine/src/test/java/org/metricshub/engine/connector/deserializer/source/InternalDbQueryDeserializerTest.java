package org.metricshub.engine.connector.deserializer.source;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.deserializer.DeserializerTest;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.common.SqlColumn;
import org.metricshub.engine.connector.model.common.SqlTable;
import org.metricshub.engine.connector.model.monitor.task.source.InternalDbQuerySource;

class InternalDbQueryDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/source/internalDbQuery/";
	}

	@Test
	void testDeserializeStatic() throws IOException {
		final String testResource = "internalDbQuerySource";
		final Connector connector = getConnector(testResource);

		final SqlColumn sqlColumn1 = SqlColumn.builder().name("COL1").type("VARCHAR(255)").number(1).build();
		final SqlColumn sqlColumn2 = SqlColumn.builder().name("COL2").type("BOOLEAN").number(3).build();

		final List<SqlColumn> sqlColumns1 = new ArrayList<>();
		sqlColumns1.add(sqlColumn1);
		sqlColumns1.add(sqlColumn2);

		final SqlColumn sqlColumn3 = SqlColumn.builder().name("COL1").type("VARCHAR(255)").number(2).build();
		final SqlColumn sqlColumn4 = SqlColumn.builder().name("COL2").type("BOOLEAN").number(4).build();

		final List<SqlColumn> sqlColumns2 = new ArrayList<>();
		sqlColumns2.add(sqlColumn3);
		sqlColumns2.add(sqlColumn4);

		final List<SqlTable> tables = new ArrayList<>();
		final SqlTable table1 = SqlTable.builder().source("${source::source_one}").alias("T1").columns(sqlColumns1).build();
		final SqlTable table2 = SqlTable.builder().source("${source::source_two}").alias("T2").columns(sqlColumns2).build();

		tables.add(table1);
		tables.add(table2);

		final InternalDbQuerySource expected = InternalDbQuerySource
			.builder()
			.type("internalDbQuery")
			.tables(tables)
			.query("SELECT T1.COL1, T1.COL2, T2.COL1, T2.COL2 FROM T1 JOIN T2 ON T1.COL1 = T2.COL1;")
			.build();
		expected.setKey("${source::beforeAll.testInternalDbQuerySource}");

		final InternalDbQuerySource internalDbQuerySource = (InternalDbQuerySource) connector
			.getBeforeAll()
			.get("testInternalDbQuerySource");

		assertEquals(expected.toString(), internalDbQuerySource.toString());
	}
}
