package org.metricshub.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.ParameterException;

public class WmiCliTest {

	WmiCli wmiCli;
	CommandLine commandLine;

	public static final String WMI_TEST_QUERY = "SELECT * FROM Win32_OperatingSystem";
	public static final String WMI_TEST_NAMESPACE = "root\\CIMv2";

	void initCli() {
		wmiCli = new WmiCli();
		commandLine = new CommandLine(wmiCli);
	}

	@Test
	void testGetQuery() {
		initCli();
		wmiCli.setQuery(WMI_TEST_QUERY);
		final ObjectNode queryNode = JsonNodeFactory.instance.objectNode();
		queryNode.set("query", new TextNode(WMI_TEST_QUERY));
		assertEquals(queryNode, wmiCli.getQuery());
	}

	@Test
	void testValidate() {
		initCli();
		// testing query validation
		wmiCli.setQuery("");
		ParameterException parameterException = assertThrows(ParameterException.class, () -> wmiCli.validate());
		assertEquals("WMI query must not be empty nor blank.", parameterException.getMessage());
		wmiCli.setQuery(" ");
		parameterException = assertThrows(ParameterException.class, () -> wmiCli.validate());
		assertEquals("WMI query must not be empty nor blank.", parameterException.getMessage());
		wmiCli.setQuery(WMI_TEST_QUERY);

		// testing namespace validation
		wmiCli.setNamespace("");
		parameterException = assertThrows(ParameterException.class, () -> wmiCli.validate());
		assertEquals("WMI namespace must not be empty nor blank.", parameterException.getMessage());
		wmiCli.setNamespace(" ");
		parameterException = assertThrows(ParameterException.class, () -> wmiCli.validate());
		assertEquals("WMI namespace must not be empty nor blank.", parameterException.getMessage());
		wmiCli.setNamespace(WMI_TEST_NAMESPACE);
		assertDoesNotThrow(() -> wmiCli.validate());
	}
}
