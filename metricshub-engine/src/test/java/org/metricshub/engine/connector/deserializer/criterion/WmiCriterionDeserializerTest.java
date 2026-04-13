package org.metricshub.engine.connector.deserializer.criterion;

import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.InvalidNullException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.deserializer.DeserializerTest;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.identity.criterion.Criterion;
import org.metricshub.engine.connector.model.identity.criterion.WmiCriterion;

class WmiCriterionDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/connector/detection/criteria/wmi/";
	}

	@Test
	/**
	 * Checks that the criteria type is wmi and that the attributes match
	 *
	 * @throws IOException
	 */
	void testDeserializeWmiCriterion() throws IOException {
		final Connector connector = getConnector("wmiCriterion");

		final List<Criterion> expected = new ArrayList<>();

		final WmiCriterion wmi = WmiCriterion
			.builder()
			.type("wmi")
			.query("testQuery")
			.namespace("testNamespace")
			.expectedResult("testExpectedResult")
			.errorMessage("testErrorMessage")
			.forceSerialization(true)
			.build();

		expected.add(wmi);

		compareCriterion(connector, expected);
	}

	@Test
	/**
	 * Checks that the namespace field gets assigned the proper default value
	 *
	 * @throws IOException
	 */
	void testWmiDefaultNamespace() throws IOException { // NOSONAR compareCriterion performs assertion
		final Connector connector = getConnector("wmiCriterionDefaultNamespace");

		final List<Criterion> expected = new ArrayList<>();

		final WmiCriterion wmi = WmiCriterion.builder().type("wmi").query("testQuery").build();

		expected.add(wmi);

		compareCriterion(connector, expected);
	}

	@Test
	/**
	 * Checks that the query field throws an error when it is null or missing
	 *
	 * @throws IOException
	 */
	void testWmiMissingOrNullQueryNotAccepted() throws IOException {
		{
			try {
				getConnector("wmiCriterionMissingQuery");
				fail(MISMATCHED_EXCEPTION_MSG);
			} catch (MismatchedInputException e) {
				final String message = "Missing required creator property 'query' (index 2)";
				checkMessage(e, message);
			}
		}

		{
			try {
				getConnector("wmiCriterionNullQuery");
				fail(INVALID_NULL_EXCEPTION_MSG);
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
	void testWmiBlankQueryNotAccepted() throws IOException {
		try {
			getConnector("wmiCriterionBlankQuery");
			fail(INVALID_FORMAT_EXCEPTION_MSG);
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
	void testWmiBlankNamespaceNotAccepted() throws IOException {
		try {
			getConnector("wmiCriterionBlankNamespace");
			fail(INVALID_FORMAT_EXCEPTION_MSG);
		} catch (InvalidFormatException e) {
			String message = "Invalid blank value encountered for property 'namespace'.";
			checkMessage(e, message);
		}
	}
}
