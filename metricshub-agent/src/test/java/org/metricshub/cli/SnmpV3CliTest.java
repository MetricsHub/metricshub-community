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

class SnmpV3CliTest {

	SnmpV3Cli snmpV3Cli;
	CommandLine commandLine;

	static String SNMP_OID = "1.3.6.1.4.1.64.58.5.5.1.20.10.4";
	static String SNMP_VERSION = "v3";
	static String SNMP_COMMUNITY = "public";
	static String[] COLUMNS = { "1", "3", "6", "1" };

	void initCli() {
		snmpV3Cli = new SnmpV3Cli();
		commandLine = new CommandLine(snmpV3Cli);
	}

	@Test
	void testGetQuery() {
		initCli();
		snmpV3Cli.setGet(SNMP_OID);
		JsonNode snmpQuery = snmpV3Cli.getQuery();
		assertEquals("get", snmpQuery.get("action").asText());
		assertEquals(SNMP_OID, snmpQuery.get("oid").asText());
		snmpV3Cli.setGet(null);

		snmpV3Cli.setGetNext(SNMP_OID);
		snmpQuery = snmpV3Cli.getQuery();
		assertEquals("getNext", snmpQuery.get("action").asText());
		assertEquals(SNMP_OID, snmpQuery.get("oid").asText());
		snmpV3Cli.setGetNext(null);

		snmpV3Cli.setWalk(SNMP_OID);
		snmpQuery = snmpV3Cli.getQuery();
		assertEquals("walk", snmpQuery.get("action").asText());
		assertEquals(SNMP_OID, snmpQuery.get("oid").asText());
		snmpV3Cli.setWalk(null);

		snmpV3Cli.setTable(SNMP_OID);
		snmpV3Cli.setColumns(COLUMNS);
		snmpQuery = snmpV3Cli.getQuery();
		assertEquals("table", snmpQuery.get("action").asText());
		final String[] resultedColumns = new ObjectMapper().convertValue(snmpQuery.get("columns"), String[].class);
		assertArrayEquals(COLUMNS, resultedColumns);
	}

	@Test
	void testValidate() {
		initCli();
		ParameterException noQueriesException = assertThrows(ParameterException.class, () -> snmpV3Cli.validate());
		assertEquals(
			"At least one SNMP V3 query must be specified: --get, --get-next, --walk, --table.",
			noQueriesException.getMessage()
		);
		snmpV3Cli.setGet(SNMP_OID);
		assertDoesNotThrow(() -> snmpV3Cli.validate());
		snmpV3Cli.setGetNext(SNMP_OID);
		ParameterException manyQueriesException = assertThrows(ParameterException.class, () -> snmpV3Cli.validate());
		assertEquals(
			"Only one SNMP V3 query can be specified at a time: --get, --get-next, --walk, --table.",
			manyQueriesException.getMessage()
		);
	}
}
