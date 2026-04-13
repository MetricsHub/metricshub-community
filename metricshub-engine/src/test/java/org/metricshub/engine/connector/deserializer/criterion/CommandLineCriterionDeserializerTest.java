package org.metricshub.engine.connector.deserializer.criterion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.InvalidNullException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.deserializer.DeserializerTest;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.identity.criterion.CommandLineCriterion;
import org.metricshub.engine.connector.model.identity.criterion.Criterion;

class CommandLineCriterionDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/connector/detection/criteria/commandLine/";
	}

	@Test
	/**
	 * Checks input properties for commandLine detection criteria
	 *
	 * @throws IOException
	 */
	void testDeserializeCommandLine() throws IOException {
		final String testResource = "commandLineCriterion";
		final Connector commandLineConnector = getConnector(testResource);

		List<Criterion> expected = new ArrayList<>();

		final String commandLine = "naviseccli -help";
		final String errorMessage = "Not a Navisphere system";
		final String expectedResult = "Navisphere";
		final boolean executeLocally = true;
		final Long timeout = 1234L;

		expected.add(
			new CommandLineCriterion("commandLine", true, commandLine, errorMessage, expectedResult, executeLocally, timeout)
		);

		assertNotNull(commandLineConnector);

		assertNotNull(commandLineConnector.getConnectorIdentity().getDetection());
		assertEquals(expected, commandLineConnector.getConnectorIdentity().getDetection().getCriteria());
	}

	@Test
	/**
	 * Checks that if commandLine is declared it is not null
	 *
	 * @throws IOException
	 */
	void testDeserializeCommandLineNoCommand() throws IOException {
		// commandLine is null
		try {
			getConnector("commandLineCriterionCommandLineNo");
			Assertions.fail(MISMATCHED_EXCEPTION_MSG);
		} catch (MismatchedInputException e) {
			final String message = "Missing required creator property 'commandLine' (index 2)";
			checkMessage(e, message);
		}
	}

	@Test
	/**
	 * Checks that if commandLine is not whitespace
	 *
	 * @throws IOException
	 */
	void testDeserializeCommandLineBlankCommand() throws IOException {
		try {
			getConnector("commandLineCriterionCommandLineBlank");
			Assertions.fail(IO_EXCEPTION_MSG);
		} catch (IOException e) {
			String message = "Invalid blank value encountered for property 'commandLine'.";
			checkMessage(e, message);
		}
	}

	@Test
	/**
	 * Checks that if commandLine is not declared we throw an exception
	 *
	 * @throws IOException
	 */
	void testDeserializeCommandLineNullCommand() throws IOException {
		// commandLine is null
		try {
			getConnector("commandLineCriterionCommandLineNull");
			Assertions.fail(INVALID_NULL_EXCEPTION_MSG);
		} catch (InvalidNullException e) {
			final String message = "Invalid `null` value encountered for property \"commandLine\"";
			checkMessage(e, message);
		}
	}

	@Test
	/**
	 * Checks that negative timeout throws exception
	 */
	void testDeserializeCommandLineNegativeTimeout() throws IOException {
		try {
			getConnector("commandLineCriterionNegativeTimeout");
			Assertions.fail(INVALID_FORMAT_EXCEPTION_MSG);
		} catch (InvalidFormatException e) {
			final String message = "Invalid negative or zero value encountered for property 'timeout'";
			checkMessage(e, message);
		}
	}

	@Test
	/**
	 * Checks that zero timeout throws exception
	 */
	void testDeserializeCommandLineZeroTimeout() throws IOException {
		try {
			getConnector("commandLineCriterionZeroTimeout");
			Assertions.fail(INVALID_FORMAT_EXCEPTION_MSG);
		} catch (InvalidFormatException e) {
			final String message = "Invalid negative or zero value encountered for property 'timeout'";
			checkMessage(e, message);
		}
	}

	@Test
	/**
	 * Checks that string timeout throws exception
	 */
	void testDeserializeCommandLineStringTimeout() throws IOException {
		try {
			getConnector("commandLineCriterionStringTimeout");
			Assertions.fail(INVALID_FORMAT_EXCEPTION_MSG);
		} catch (InvalidFormatException e) {
			final String message = "Invalid value encountered for property 'timeout'";
			checkMessage(e, message);
		}
	}
}
