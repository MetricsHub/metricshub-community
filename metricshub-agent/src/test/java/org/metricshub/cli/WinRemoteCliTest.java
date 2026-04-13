package org.metricshub.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;
import org.metricshub.cli.service.protocol.WinRmConfigCli;
import org.metricshub.cli.service.protocol.WmiConfigCli;
import picocli.CommandLine;
import picocli.CommandLine.ParameterException;

public class WinRemoteCliTest {

	WinRemoteCli winRemoteCli;
	CommandLine commandLine;

	public static final String WINREMOTE_TEST_COMMAND = "ipconfig /all";

	void initCli() {
		winRemoteCli = new WinRemoteCli();
		commandLine = new CommandLine(winRemoteCli);
		winRemoteCli.setSpec(commandLine.getCommandSpec());
	}

	@Test
	void testGetQuery() {
		initCli();
		winRemoteCli.setCommand(WINREMOTE_TEST_COMMAND);
		final ObjectNode commandNode = JsonNodeFactory.instance.objectNode();
		commandNode.set("query", new TextNode(WINREMOTE_TEST_COMMAND));
		commandNode.set("queryType", new TextNode("winremote"));
		assertEquals(commandNode, winRemoteCli.getQuery(), "Query node should match expected command and queryType");
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

		// testing protocol validation - no protocol configured
		winRemoteCli.setWmiConfigCli(null);
		winRemoteCli.setWinRmConfigCli(null);
		parameterException = assertThrows(ParameterException.class, () -> winRemoteCli.validate());
		assertEquals(
			"At least one protocol must be specified: --winrm, --wmi.",
			parameterException.getMessage(),
			"No protocol configured should throw exception"
		);

		// testing protocol validation - WMI only (valid)
		final WmiConfigCli wmiConfigCli = new WmiConfigCli();
		wmiConfigCli.setUseWmi(true);
		winRemoteCli.setWmiConfigCli(wmiConfigCli);
		winRemoteCli.setWinRmConfigCli(null);
		assertDoesNotThrow(() -> winRemoteCli.validate(), "WMI only should be valid");

		// testing protocol validation - WinRM only (valid)
		final WinRmConfigCli winRmConfigCli = new WinRmConfigCli();
		winRmConfigCli.setUseWinRM(true);
		winRemoteCli.setWmiConfigCli(null);
		winRemoteCli.setWinRmConfigCli(winRmConfigCli);
		assertDoesNotThrow(() -> winRemoteCli.validate(), "WinRM only should be valid");

		// testing protocol validation - both protocols configured (invalid)
		winRemoteCli.setWmiConfigCli(wmiConfigCli);
		winRemoteCli.setWinRmConfigCli(winRmConfigCli);
		parameterException = assertThrows(ParameterException.class, () -> winRemoteCli.validate());
		assertEquals(
			"Only one protocol should be specified: --winrm or --wmi.",
			parameterException.getMessage(),
			"Both protocols configured should throw exception"
		);
	}
}
