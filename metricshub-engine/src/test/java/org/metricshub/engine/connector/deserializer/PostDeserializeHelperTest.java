package org.metricshub.engine.connector.deserializer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.DefaultDeserializationContext;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.connector.deserializer.custom.CustomDeserializer;
import org.metricshub.engine.connector.model.monitor.task.source.CopySource;

class PostDeserializeHelperTest {

	/**
	 * Counts the {@link CustomDeserializer} wrappers around the deserializer the mapper uses for {@link CopySource}.
	 *
	 * @param mapper the mapper to inspect
	 * @return the number of nested {@link CustomDeserializer} instances
	 * @throws Exception if the deserializer cannot be resolved
	 */
	private static int countPostDeserializers(final ObjectMapper mapper) throws Exception {
		final DeserializationContext context = ((DefaultDeserializationContext) mapper.getDeserializationContext())
			.createDummyInstance(mapper.getDeserializationConfig());
		JsonDeserializer<?> deserializer = context.findNonContextualValueDeserializer(
			mapper.constructType(CopySource.class)
		);
		int count = 0;
		while (deserializer != null) {
			if (deserializer instanceof CustomDeserializer) {
				count++;
			}
			deserializer = deserializer.getDelegatee();
		}
		return count;
	}

	@Test
	void testAddPostDeserializeSupportIsIdempotent() throws Exception {
		final ObjectMapper mapper = JsonHelper.buildYamlMapper();
		for (int i = 0; i < 5; i++) {
			PostDeserializeHelper.addPostDeserializeSupport(mapper);
		}
		assertEquals(1, countPostDeserializers(mapper));
	}
}
