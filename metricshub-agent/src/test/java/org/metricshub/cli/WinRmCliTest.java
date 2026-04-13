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

public class WinRmCliTest {

	WinRmCli winRmCli;
	CommandLine commandLine;

	public static final String WINRM_TEST_QUERY = "SELECT ObjectId FROM MSFT_Volume";
	public static final String WINRM_TEST_NAMESPACE = "root\\Microsoft\\Windows\\Storage";

	void initCli() {
		winRmCli = new WinRmCli();
		commandLine = new CommandLine(winRmCli);
	}

	@Test
	void testGetQuery() {
		initCli();
		winRmCli.setQuery(WINRM_TEST_QUERY);
		final ObjectNode queryNode = JsonNodeFactory.instance.objectNode();
		queryNode.set("query", new TextNode(WINRM_TEST_QUERY));
		queryNode.set("queryType", new TextNode("wmi"));
		assertEquals(queryNode, winRmCli.getQuery());
	}

	@Test
	void testValidate() {
		initCli();
		// testing query validation
		winRmCli.setQuery("");
		ParameterException parameterException = assertThrows(ParameterException.class, () -> winRmCli.validate());
		assertEquals("WinRm query must not be empty nor blank.", parameterException.getMessage());
		winRmCli.setQuery(" ");
		parameterException = assertThrows(ParameterException.class, () -> winRmCli.validate());
		assertEquals("WinRm query must not be empty nor blank.", parameterException.getMessage());
		winRmCli.setQuery(WINRM_TEST_QUERY);

		// testing namespace validation
		winRmCli.setNamespace("");
		parameterException = assertThrows(ParameterException.class, () -> winRmCli.validate());
		assertEquals("WinRm namespace must not be empty nor blank.", parameterException.getMessage());
		winRmCli.setNamespace(" ");
		parameterException = assertThrows(ParameterException.class, () -> winRmCli.validate());
		assertEquals("WinRm namespace must not be empty nor blank.", parameterException.getMessage());
		winRmCli.setNamespace(WINRM_TEST_NAMESPACE);

		winRmCli.setAuthentications(List.of("NTLM", "HTTP"));
		parameterException = assertThrows(ParameterException.class, () -> winRmCli.validate());
		assertTrue(parameterException.getMessage().contains("Invalid authentication schema"));
		winRmCli.setAuthentications(List.of("NTLM", "KERBEROS"));
		assertDoesNotThrow(() -> winRmCli.validate());
	}

	@Test
	void testGetOrDeducePortNumber() {
		initCli();
		// Port specified, thus port value returned.
		winRmCli.setPort(456);
		assertEquals(456, winRmCli.getOrDeducePortNumber());
		// Port null, but "https" protocol specified, default HTTPS port returned
		winRmCli.setPort(null);
		winRmCli.setProtocol("https");
		assertEquals(WinRmCli.DEFAULT_HTTPS_PORT, winRmCli.getOrDeducePortNumber());
		// Protocol null & whatever protocol value is, default HTTP port returned.
		winRmCli.setProtocol(null);
		assertEquals(WinRmCli.DEFAULT_HTTP_PORT, winRmCli.getOrDeducePortNumber());
	}
}
