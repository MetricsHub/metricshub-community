package org.metricshub.engine.connector.deserializer.criterion;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.deserializer.DeserializerTest;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.identity.criterion.Criterion;
import org.metricshub.engine.connector.model.identity.criterion.JmxCriterion;

class JmxCriterionDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/connector/detection/criteria/jmx/";
	}

	@Test
	/**
	 * Checks that the criteria type is jmx
	 *
	 * @throws IOException if the deserialization fails
	 */
	void testDeserializeDoesntThrow() throws IOException { // NOSONAR compareCriterion performs assertion
		final Connector connector = getConnector("jmxCriterion");

		final List<Criterion> expected = new ArrayList<>();
		expected.add(
			JmxCriterion
				.builder()
				.type("jmx")
				.forceSerialization(true)
				.objectName("org.metricshub.extension.jmx:type=TestJmx")
				.attributes(List.of("Name"))
				.expectedResult("MetricsHub")
				.build()
		);

		compareCriterion(connector, expected);
	}

	@Test
	/**
	 * Checks that the criteria deserialization fails
	 *
	 * @throws IOException if the deserialization fails
	 */
	void testDeserializeThrow() throws IOException { // NOSONAR compareCriterion performs assertion
		{
			try {
				getConnector("jmxCriterionNullObjectName");
				fail(INVALID_NULL_EXCEPTION_MSG);
			} catch (final IOException e) {
				final String message = "Invalid `null` value encountered for property \"objectName\"";
				checkMessage(e, message);
			}
		}

		{
			try {
				getConnector("jmxCriterionNullAttributes");
				fail(INVALID_NULL_EXCEPTION_MSG);
			} catch (final IOException e) {
				final String message = "Invalid `null` value encountered for property \"attributes\"";
				checkMessage(e, message);
			}
		}
	}
}
