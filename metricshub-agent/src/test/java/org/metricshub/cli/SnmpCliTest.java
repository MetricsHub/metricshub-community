package org.metricshub.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.ParameterException;

class SnmpCliTest {

	SnmpCli snmpCli;
	CommandLine commandLine;

	static String SNMP_OID = "1.3.6.1.4.1.64.58.5.5.1.20.10.4";
	static String SNMP_VERSION = "v2c";
	static String SNMP_COMMUNITY = "public";
	static String[] COLUMNS = { "1", "3", "6", "1" };

	void initCli() {
		snmpCli = new SnmpCli();
		commandLine = new CommandLine(snmpCli);
	}

	@Test
	void testGetQuery() {
		initCli();
		snmpCli.setGet(SNMP_OID);
		JsonNode snmpQuery = snmpCli.getQuery();
		assertEquals("get", snmpQuery.get("action").asText());
		assertEquals(SNMP_OID, snmpQuery.get("oid").asText());
		snmpCli.setGet(null);

		snmpCli.setGetNext(SNMP_OID);
		snmpQuery = snmpCli.getQuery();
		assertEquals("getNext", snmpQuery.get("action").asText());
		assertEquals(SNMP_OID, snmpQuery.get("oid").asText());
		snmpCli.setGetNext(null);

		snmpCli.setWalk(SNMP_OID);
		snmpQuery = snmpCli.getQuery();
		assertEquals("walk", snmpQuery.get("action").asText());
		assertEquals(SNMP_OID, snmpQuery.get("oid").asText());
		snmpCli.setWalk(null);

		snmpCli.setTable(SNMP_OID);
		snmpCli.setColumns(COLUMNS);
		snmpQuery = snmpCli.getQuery();
		assertEquals("table", snmpQuery.get("action").asText());
		final String[] resultedColumns = new ObjectMapper().convertValue(snmpQuery.get("columns"), String[].class);
		assertArrayEquals(COLUMNS, resultedColumns);
	}

	@Test
	void testValidate() {
		initCli();
		// Test 1: No SNMP queries
		ParameterException parameterException = assertThrows(ParameterException.class, () -> snmpCli.validate());
		assertEquals(
			"At least one SNMP query must be specified: --get, --get-next, --walk, --table.",
			parameterException.getMessage()
		);

		// Test 2: One SNMP query (valid)
		snmpCli.setGet(SNMP_OID);
		assertDoesNotThrow(() -> snmpCli.validate());

		// Test 3: Two SNMP queries
		snmpCli.setGetNext(SNMP_OID);
		parameterException = assertThrows(ParameterException.class, () -> snmpCli.validate());
		assertEquals(
			"Only one SNMP query can be specified at a time: --get, --get-next, --walk, --table.",
			parameterException.getMessage()
		);

		// Test 4: Table query with missing columns
		initCli();
		snmpCli.setTable(SNMP_OID);
		parameterException = assertThrows(ParameterException.class, () -> snmpCli.validate());
		assertEquals(
			"SNMP Table query requires columns to select: both --table and --columns must be specified.",
			parameterException.getMessage()
		);
	}
}
