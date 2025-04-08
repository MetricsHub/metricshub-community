package org.metricshub.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.ParameterException;

public class WbemCliTest {

	WbemCli wbemCli;
	CommandLine commandLine;

	public static final List<String> WBEM_UP_TEST_NAMESPACES = List.of(
		"root/Interop",
		"interop",
		"root/PG_Interop",
		"PG_Interop"
	);
	public static final String WBEM_TEST_QUERY = "SELECT Name FROM CIM_NameSpace";
	public static final String WBEM_TEST_NAMESPACE = "interop";

	void initCli() {
		wbemCli = new WbemCli();
		commandLine = new CommandLine(wbemCli);
	}

	@Test
	void testGetQuery() {
		initCli();
		wbemCli.setQuery(WBEM_TEST_QUERY);
		final ObjectNode queryNode = JsonNodeFactory.instance.objectNode();
		queryNode.set("query", new TextNode(WBEM_TEST_QUERY));
		assertEquals(queryNode, wbemCli.getQuery());
	}

	@Test
	void testValidate() {
		initCli();
		// testing query validation
		wbemCli.setQuery("");
		ParameterException parameterException = assertThrows(ParameterException.class, () -> wbemCli.validate());
		assertEquals("WBEM query must not be empty nor blank.", parameterException.getMessage());
		wbemCli.setQuery(" ");
		parameterException = assertThrows(ParameterException.class, () -> wbemCli.validate());
		assertEquals("WBEM query must not be empty nor blank.", parameterException.getMessage());
		wbemCli.setQuery(WBEM_TEST_QUERY);

		// testing namespace validation
		wbemCli.setNamespace("");
		parameterException = assertThrows(ParameterException.class, () -> wbemCli.validate());
		assertEquals("WBEM namespace must not be empty nor blank.", parameterException.getMessage());
		wbemCli.setNamespace(" ");
		parameterException = assertThrows(ParameterException.class, () -> wbemCli.validate());
		assertEquals("WBEM namespace must not be empty nor blank.", parameterException.getMessage());
		wbemCli.setNamespace(WBEM_TEST_NAMESPACE);

		// testing transport protocol validation
		wbemCli.setProtocol("IPMI");
		parameterException = assertThrows(ParameterException.class, () -> wbemCli.validate());
		assertTrue(parameterException.getMessage().contains("Invalid WBEM transport protocol"));
		wbemCli.setProtocol("HTTP");
		assertDoesNotThrow(() -> wbemCli.validate());
	}

	@Test
	void testGetOrDeducePortNumber() {
		initCli();
		wbemCli.setPort(956);
		assertEquals(956, wbemCli.getOrDeducePortNumber());
		wbemCli.setPort(null);
		wbemCli.setProtocol("https");
		assertEquals(5989, wbemCli.getOrDeducePortNumber());
		wbemCli.setProtocol("http");
		assertEquals(5988, wbemCli.getOrDeducePortNumber());
		wbemCli.setProtocol("test");
		assertEquals(5988, wbemCli.getOrDeducePortNumber());
	}
}
