package org.metricshub.engine.connector.deserializer.criterion;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.deserializer.DeserializerTest;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.identity.criterion.Criterion;
import org.metricshub.engine.connector.model.identity.criterion.ProductRequirementsCriterion;

class ProductRequirementsCriterionDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/connector/detection/criteria/productRequirements/";
	}

	@Test
	/**
	 * Checks input properties for product requirements detection criteria
	 *
	 * @throws Exception
	 */
	void testDeserializeProductRequirementsDeserializer() throws Exception { // NOSONAR compareCriterion performs assertion
		final Connector productRequirements = getConnector("productRequirementsCriterion");

		List<Criterion> expected = new ArrayList<>();
		expected.add(new ProductRequirementsCriterion("productRequirements", false, "testengineversion", "testkmversion"));

		compareCriterion(productRequirements, expected);
	}
}
