package org.metricshub.engine.connector.deserializer.criterion;

import com.fasterxml.jackson.databind.JsonMappingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.deserializer.DeserializerTest;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.identity.criterion.Criterion;
import org.metricshub.engine.connector.model.identity.criterion.DeviceTypeCriterion;

class DeviceTypeCriterionDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/connector/detection/criteria/deviceType/";
	}

	@Test
	/**
	 * Checks input properties for device type detection criteria
	 *
	 * @throws Exception
	 */
	void testDeserializeDeviceType() throws Exception { // NOSONAR compareCriterion performs assertion
		final Connector deviceType = getConnector("deviceTypeCriterion");

		List<Criterion> expected = new ArrayList<>();
		expected.add(
			new DeviceTypeCriterion("deviceType", false, Set.of(DeviceKind.values()), Set.of(DeviceKind.values()))
		);

		compareCriterion(deviceType, expected);
	}

	@Test
	/**
	 * Checks that non enum members are rejected
	 *
	 * @throws Exception
	 */
	void testDeserializeOsTypeEnum() throws Exception {
		// Yaml contains invalid OsType so if deserializer does not throw an invalid
		// exception, test will fail.
		try {
			getConnector("deviceTypeCriterionOsTypeNonEnum");
			Assertions.fail(JSON_MAPPING_EXCEPTION_MSG);
		} catch (JsonMappingException e) {
			checkMessage(e, "'toto' is not a supported device kind.");
		}
	}
}
