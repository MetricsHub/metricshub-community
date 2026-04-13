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

class SshCliTest {

	private static final String COMMAND_LINE_VALIDATION_ERROR_MESSAGE = "SSH command line must not be empty nor blank.";
	public static final String SSH_COMMAND_LINE_TEST = "echo test";
	public static final String EMPTY = "";
	public static final String BLANK = "     ";

	SshCli sshCli;
	CommandLine commandLine;

	void initCli() {
		sshCli = new SshCli();
		commandLine = new CommandLine(sshCli);
	}

	@Test
	void testGetQuery() {
		initCli();
		final ObjectNode queryNode = JsonNodeFactory.instance.objectNode();
		queryNode.set("commandLine", new TextNode(SSH_COMMAND_LINE_TEST));
		sshCli.setCommandLine(SSH_COMMAND_LINE_TEST);
		assertEquals(queryNode, sshCli.getQuery());
	}

	@Test
	void testValidate() {
		initCli();

		// Empty command line
		sshCli.setCommandLine(EMPTY);
		ParameterException parameterException = assertThrows(ParameterException.class, () -> sshCli.validate());
		assertEquals(COMMAND_LINE_VALIDATION_ERROR_MESSAGE, parameterException.getMessage());

		// blank command line
		sshCli.setCommandLine(BLANK);
		parameterException = assertThrows(ParameterException.class, () -> sshCli.validate());
		assertEquals(COMMAND_LINE_VALIDATION_ERROR_MESSAGE, parameterException.getMessage());

		// valid command line
		sshCli.setCommandLine(SSH_COMMAND_LINE_TEST);
		assertDoesNotThrow(() -> sshCli.validate());
	}
}
