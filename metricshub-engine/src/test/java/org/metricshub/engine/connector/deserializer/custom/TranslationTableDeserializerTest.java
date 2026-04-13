package org.metricshub.engine.connector.deserializer.custom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.connector.model.common.ITranslationTable;
import org.metricshub.engine.connector.model.common.ReferenceTranslationTable;
import org.metricshub.engine.connector.model.common.TranslationTable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TranslationTableDeserializerTest {

	@Mock
	private YAMLParser yamlParser;

	@Test
	void testDeserializeNull() throws Exception {
		{
			assertNull(new TranslationTableDeserializer().deserialize(null, null));
		}
		{
			doReturn("translationTable").when(yamlParser).currentName();
			doReturn(null).when(yamlParser).readValueAsTree();
			assertThrows(
				InvalidFormatException.class,
				() -> new TranslationTableDeserializer().deserialize(yamlParser, null)
			);
		}
	}

	@Test
	void testDeserializeReferenceTranslationTableOK() throws Exception {
		doReturn("translationTable").when(yamlParser).currentName();
		doReturn(new TextNode("${translation::DiskControllerStatusTranslationTable}")).when(yamlParser).readValueAsTree();

		ITranslationTable translationTable = new TranslationTableDeserializer().deserialize(yamlParser, null);

		assertEquals(
			new ReferenceTranslationTable("${translation::DiskControllerStatusTranslationTable}"),
			translationTable
		);
	}

	@Test
	void testDeserializeTranslationTableOK() throws Exception {
		doReturn("translationTable").when(yamlParser).currentName();

		ObjectNode objectNode = JsonNodeFactory.instance.objectNode();
		objectNode.set("optimal", new TextNode("ok"));
		objectNode.set("warning", new TextNode("degraded"));
		objectNode.set("failed", new TextNode("failed"));
		doReturn(objectNode).when(yamlParser).readValueAsTree();

		ITranslationTable translationTable = new TranslationTableDeserializer().deserialize(yamlParser, null);

		Map<String, String> translations = new LinkedHashMap<>();
		translations.put("optimal", "ok");
		translations.put("warning", "degraded");
		translations.put("failed", "failed");
		assertEquals(TranslationTable.builder().translations(translations).build(), translationTable);
	}
}
