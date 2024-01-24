package org.sentrysoftware.metricshub.engine.common.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

class JsonHelperTest {

	@Test
	void testSerialize() throws JsonProcessingException {
		assertEquals("true", JsonHelper.serialize(true));
		assertEquals("[ 0 ]", JsonHelper.serialize(new int[1]));
		assertEquals("null", JsonHelper.serialize(null));
	}
}
