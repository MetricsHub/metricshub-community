package org.metricshub.engine.strategy;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2025 MetricsHub
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.strategy.source.SourceTable;

class FileSourceTableLoggingProxyTest {

	private static final String OPERATION_TAG = "collect";
	private static final String CONNECTOR_ID = "conn-1";
	private static final String HOSTNAME = "host-1";

	private final DefaultSourceTableLoggingProxy delegate = new DefaultSourceTableLoggingProxy();

	@Test
	void formatForLogWhenDetailDisabledReturnsHeaderOnly() {
		FileSourceTableLoggingProxy proxy = new FileSourceTableLoggingProxy(delegate, false);
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

	@Test
	void formatForLogWhenDetailEnabledDelegatesFullMessage() {
		FileSourceTableLoggingProxy proxy = new FileSourceTableLoggingProxy(delegate, true);
		SourceTable sourceTable = SourceTable.builder().rawData("any content").table(List.of(List.of("cell"))).build();

		String result = proxy.formatForLog(OPERATION_TAG, "FileSource", "key1", CONNECTOR_ID, sourceTable, HOSTNAME);

		assertNotNull(result);
		assertTrue(result.contains("any content"));
		assertTrue(result.contains("Raw result:"));
		assertTrue(result.contains("cell"));
	}
}
