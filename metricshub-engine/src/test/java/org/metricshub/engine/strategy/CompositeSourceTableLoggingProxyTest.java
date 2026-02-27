package org.metricshub.engine.strategy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.strategy.source.SourceTable;

class CompositeSourceTableLoggingProxyTest {

	private static final String OPERATION_TAG = "collect";
	private static final String CONNECTOR_ID = "conn-1";
	private static final String HOSTNAME = "host-1";

	private final CustomSourceTableLoggingProxy proxy = new CustomSourceTableLoggingProxy();

	@Test
	void formatForLogWhenNotFileSourceReturnsFullMessage() {
		SourceTable sourceTable = SourceTable.builder().rawData("full raw content").build();

		String result = proxy.formatForLog(OPERATION_TAG, "OtherSource", "key1", CONNECTOR_ID, sourceTable, HOSTNAME);

		assertNotNull(result);
		assertTrue(result.contains("full raw content"));
		assertTrue(result.contains("Raw result:"));
	}

	@Test
	void formatForLogWhenFileSourceReturnsHeaderOnly() {
		SourceTable sourceTable = SourceTable.builder().rawData("any content").table(List.of(List.of("cell"))).build();

		String result = proxy.formatForLog(OPERATION_TAG, "FileSource", "key1", CONNECTOR_ID, sourceTable, HOSTNAME);

		assertNotNull(result);
		assertTrue(result.contains("Hostname " + HOSTNAME));
		assertTrue(result.contains("FileSource"));
		assertTrue(result.contains("key1"));
		assertFalse(result.contains("Raw result:"));
		assertFalse(result.contains("Table result:"));
		assertFalse(result.contains("any content"));
		assertFalse(result.contains("cell"));
	}
}
