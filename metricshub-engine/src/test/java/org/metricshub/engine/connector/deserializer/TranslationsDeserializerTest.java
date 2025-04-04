package org.metricshub.engine.connector.deserializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.common.TranslationTable;

class TranslationsDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/translations/";
	}

	@Test
	void testDeserializeTranslations() throws IOException {
		final Connector connector = getConnector("translationTable");
		assertNotNull(connector);

		final Map<String, TranslationTable> translationsConnector = connector.getTranslations();

		assertTrue(translationsConnector instanceof HashMap, "translations are expected to be a HashMap.");

		final Map<String, String> expectedTranslationTable = new HashMap<>();
		expectedTranslationTable.put("0", "ok");
		expectedTranslationTable.put("1", "degraded");
		expectedTranslationTable.put("2", "failed");
		expectedTranslationTable.put("default", "unknown");

		final Map<String, TranslationTable> expected = new HashMap<String, TranslationTable>(
			Map.of("statusToTranslate", TranslationTable.builder().translations(expectedTranslationTable).build())
		);

		assertEquals(expected, translationsConnector);
	}
}
