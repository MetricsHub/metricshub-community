package org.metricshub.engine.connector.deserializer.criterion;

import com.fasterxml.jackson.databind.exc.InvalidNullException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.deserializer.DeserializerTest;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.identity.criterion.Criterion;
import org.metricshub.engine.connector.model.identity.criterion.ServiceCriterion;

class ServiceCriterionDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/connector/detection/criteria/service/";
	}

	@Test
	void testDeserializeService() throws IOException { // NOSONAR compareCriterion performs assertion
		final Connector service = getConnector("serviceCriterion");
		List<Criterion> expected = new ArrayList<>();
		expected.add(new ServiceCriterion("service", false, "TWGIPC"));
		compareCriterion(service, expected);
	}

	@Test
	/**
	 * Checks that null name is rejected
	 *
	 * @throws IOException
	 */
	void testServiceNullName() throws IOException {
		// name is null
		try {
			getConnector("serviceCriterionNullName");
			Assertions.fail(INVALID_NULL_EXCEPTION_MSG);
		} catch (InvalidNullException e) {
			final String message = "Invalid `null` value encountered for property \"name\"";
			checkMessage(e, message);
		}
	}

	@Test
	/**
	 * Checks that blank name is rejected
	 *
	 * @throws IOException
	 */
	void testServiceBlankName() throws IOException {
		// name is " "
		try {
			getConnector("serviceCriterionBlankName");
			Assertions.fail(IO_EXCEPTION_MSG);
		} catch (IOException e) {
			final String message = "Invalid blank value encountered for property 'name'.";
			checkMessage(e, message);
		}
	}

	@Test
	/**
	 * Checks that name is declared
	 *
	 * @throws IOException
	 */
	void testServiceNoName() throws IOException {
		// name is not declared
		try {
			getConnector("serviceCriterionNoName");
			Assertions.fail(IO_EXCEPTION_MSG);
		} catch (IOException e) {
			final String message = "Missing required creator property 'name' (index 2)";
			checkMessage(e, message);
		}
	}
}
