package org.sentrysoftware.metricshub.agent.deserialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.extension.ping.PingConfiguration;
import org.metricshub.extension.ping.PingExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sentrysoftware.metricshub.engine.configuration.IConfiguration;
import org.sentrysoftware.metricshub.engine.extension.ExtensionManager;

@ExtendWith(MockitoExtension.class)
class ExtensionProtocolsDeserializerTest {

	@Mock
	private YAMLParser yamlParserMock;

	@Mock
	private DeserializationContext deserializeContext;

	@Test
	void testDeserializeNullParserNullContext() throws IOException {
		assertEquals(Collections.emptyMap(), new ExtensionProtocolsDeserializer().deserialize(null, deserializeContext));
		assertEquals(Collections.emptyMap(), new ExtensionProtocolsDeserializer().deserialize(yamlParserMock, null));
	}

	@Test
	void testDeserialize() throws IOException {
		final ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(Collections.singletonList(new PingExtension()))
			.build();

		final ExtensionProtocolsDeserializer deserializer = new ExtensionProtocolsDeserializer();

		// Assert that the returned value is an empty map when the parser returns null node
		{
			doReturn(null).when(yamlParserMock).readValueAsTree();
			assertEquals(Collections.emptyMap(), deserializer.deserialize(yamlParserMock, deserializeContext));
		}

		// Assert that the returned value is a map containing the default configuration when the user doesn't configure
		// the protocol's properties
		// Example:
		// protocols:
		//   ping:
		{
			final ObjectNode protocolsNode = JsonNodeFactory.instance.objectNode();
			protocolsNode.set("ping", JsonNodeFactory.instance.nullNode());
			doReturn(protocolsNode).when(yamlParserMock).readValueAsTree();

			doReturn(extensionManager)
				.when(deserializeContext)
				.findInjectableValue(ExtensionManager.class.getName(), null, null);
			Map<String, IConfiguration> protocols = deserializer.deserialize(yamlParserMock, deserializeContext);
			assertEquals(PingConfiguration.builder().build(), protocols.get("ping"));

			protocolsNode.set("ping", null);
			protocols = deserializer.deserialize(yamlParserMock, deserializeContext);
			assertEquals(PingConfiguration.builder().build(), protocols.get("ping"));
		}

		// Assert ordinary deserialization
		{
			final ObjectNode protocolsNode = JsonNodeFactory.instance.objectNode();
			final ObjectNode pingNode = JsonNodeFactory.instance.objectNode();
			pingNode.set("timeout", new TextNode("10s"));
			protocolsNode.set("ping", pingNode);
			doReturn(protocolsNode).when(yamlParserMock).readValueAsTree();

			doReturn(extensionManager)
				.when(deserializeContext)
				.findInjectableValue(ExtensionManager.class.getName(), null, null);
			final Map<String, IConfiguration> protocols = deserializer.deserialize(yamlParserMock, deserializeContext);
			assertEquals(PingConfiguration.builder().timeout(10L).build(), protocols.get("ping"));
		}
	}
}
