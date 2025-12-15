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

public class WinRemoteCliTest {

	WinRemoteCli winRemoteCli;
	CommandLine commandLine;

	public static final String WINREMOTE_TEST_COMMAND = "ipconfig /all";
	public static final String WINREMOTE_TEST_PROTOCOL_WMI = "wmi";
	public static final String WINREMOTE_TEST_PROTOCOL_WINRM = "winrm";

	void initCli() {
		winRemoteCli = new WinRemoteCli();
		commandLine = new CommandLine(winRemoteCli);
		winRemoteCli.setSpec(commandLine.getCommandSpec());
	}

	@Test
	void testGetQuery() {
		initCli();
		winRemoteCli.setCommand(WINREMOTE_TEST_COMMAND);
		winRemoteCli.setProtocol(WINREMOTE_TEST_PROTOCOL_WMI);
		final ObjectNode commandNode = JsonNodeFactory.instance.objectNode();
		commandNode.set("query", new TextNode(WINREMOTE_TEST_COMMAND));
		commandNode.set("queryType", new TextNode("winremote"));
		assertEquals(commandNode, winRemoteCli.getQuery(), "Query node should match expected command and protocol");
	}

	@Test
	void testGetQueryWithWinRmProtocol() {
		initCli();
		winRemoteCli.setCommand(WINREMOTE_TEST_COMMAND);
		winRemoteCli.setProtocol(WINREMOTE_TEST_PROTOCOL_WINRM);
		final ObjectNode commandNode = JsonNodeFactory.instance.objectNode();
		commandNode.set("query", new TextNode(WINREMOTE_TEST_COMMAND));
		commandNode.set("queryType", new TextNode("winremote"));
		assertEquals(commandNode, winRemoteCli.getQuery(), "Query node should match expected command and WinRM protocol");
	}

	@Test
	void testValidate() {
		initCli();
		// testing command validation
		winRemoteCli.setCommand("");
		ParameterException parameterException = assertThrows(ParameterException.class, () -> winRemoteCli.validate());
		assertEquals(
			"Windows OS command must not be empty nor blank.",
			parameterException.getMessage(),
			"Empty command should throw exception"
		);
		winRemoteCli.setCommand(" ");
		parameterException = assertThrows(ParameterException.class, () -> winRemoteCli.validate());
		assertEquals(
			"Windows OS command must not be empty nor blank.",
			parameterException.getMessage(),
			"Blank command should throw exception"
		);
		winRemoteCli.setCommand(WINREMOTE_TEST_COMMAND);

		// testing protocol validation - valid WMI
		winRemoteCli.setProtocol("wmi");
		assertDoesNotThrow(() -> winRemoteCli.validate(), "WMI protocol should be valid");
		winRemoteCli.setProtocol("WMI");
		assertDoesNotThrow(() -> winRemoteCli.validate(), "WMI protocol (uppercase) should be valid");

		// testing protocol validation - valid WINRM
		winRemoteCli.setProtocol("winrm");
		assertDoesNotThrow(() -> winRemoteCli.validate(), "WinRM protocol should be valid");
		winRemoteCli.setProtocol("WINRM");
		assertDoesNotThrow(() -> winRemoteCli.validate(), "WinRM protocol (uppercase) should be valid");

		// testing protocol validation - invalid protocol
		winRemoteCli.setProtocol("invalid");
		parameterException = assertThrows(ParameterException.class, () -> winRemoteCli.validate());
		assertEquals(
			"Protocol must be either WMI or WINRM.",
			parameterException.getMessage(),
			"Invalid protocol should throw exception"
		);
		winRemoteCli.setProtocol("http");
		parameterException = assertThrows(ParameterException.class, () -> winRemoteCli.validate());
		assertEquals(
			"Protocol must be either WMI or WINRM.",
			parameterException.getMessage(),
			"Invalid protocol 'http' should throw exception"
		);
	}
}
