package org.metricshub.engine.connector.deserializer.criterion;

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
import org.metricshub.engine.connector.model.identity.criterion.Criterion;
import org.metricshub.engine.connector.model.identity.criterion.WbemCriterion;

class WbemCriterionDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/connector/detection/criteria/wbem/";
	}

	@Test
	/**
	 * Checks that the criteria type is wbem and that the attributes match
	 *
	 * @throws IOException
	 */
	void testDeserializeWbemCriterion() throws IOException {
		final Connector connector = getConnector("wbemCriterion");

		final List<Criterion> expected = new ArrayList<>();

		final WbemCriterion wbem = WbemCriterion
			.builder()
			.type("wbem")
			.query("testQuery")
			.namespace("testNamespace")
			.expectedResult("testExpectedResult")
			.errorMessage("testErrorMessage")
			.forceSerialization(true)
			.build();

		expected.add(wbem);

		compareCriterion(connector, expected);
	}

	@Test
	/**
	 * Checks that the namespace field gets assigned the proper default value
	 *
	 * @throws IOException
	 */
	void testWbemDefaultNamespace() throws IOException { // NOSONAR compareCriterion performs assertion
		final Connector connector = getConnector("wbemCriterionDefaultNamespace");

		final List<Criterion> expected = new ArrayList<>();

		final WbemCriterion wbem = WbemCriterion.builder().type("wbem").query("testQuery").build();

		expected.add(wbem);

		compareCriterion(connector, expected);
	}

	@Test
	/**
	 * Checks that the query field throws an error when it is null or missing
	 *
	 * @throws IOException
	 */
	void testWbemMissingOrNullQueryNotAccepted() throws IOException {
		{
			try {
				getConnector("wbemCriterionMissingQuery");
				Assertions.fail(MISMATCHED_EXCEPTION_MSG);
			} catch (MismatchedInputException e) {
				final String message = "Missing required creator property 'query' (index 2)";
				checkMessage(e, message);
			}
		}

		{
			try {
				getConnector("wbemCriterionNullQuery");
				Assertions.fail(INVALID_NULL_EXCEPTION_MSG);
			} catch (InvalidNullException e) {
				final String message = "Invalid `null` value encountered for property \"query\"";
				checkMessage(e, message);
			}
		}
	}

	@Test
	/**
	 * Checks that the query field throws an error when it is blank
	 *
	 * @throws IOException
	 */
	void testWbemBlankQueryNotAccepted() throws IOException {
		try {
			getConnector("wbemCriterionBlankQuery");
			Assertions.fail(INVALID_FORMAT_EXCEPTION_MSG);
		} catch (InvalidFormatException e) {
			String message = "Invalid blank value encountered for property 'query'.";
			checkMessage(e, message);
		}
	}

	@Test
	/**
	 * Checks that the namespace field throws an error when it is blank
	 *
	 * @throws IOException
	 */
	void testWbemBlankNamespaceNotAccepted() throws IOException {
		try {
			getConnector("wbemCriterionBlankNamespace");
			Assertions.fail(INVALID_FORMAT_EXCEPTION_MSG);
		} catch (InvalidFormatException e) {
			String message = "Invalid blank value encountered for property 'namespace'.";
			checkMessage(e, message);
		}
	}
}
