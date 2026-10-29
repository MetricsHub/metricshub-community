package org.metricshub.engine.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.configuration.IConfiguration;

class IProtocolExtensionTest {

	@Test
	void testToolDefaults() throws Exception {
		final IProtocolExtension extension = mock(IProtocolExtension.class);
		when(extension.getTools()).thenCallRealMethod();
		when(extension.executeTool(anyString(), any(), any())).thenCallRealMethod();

		// An extension offers no tool unless it says so
		assertEquals(List.of(), extension.getTools());

		// A tool runs through executeQuery with its arguments as they are
		final IConfiguration configuration = TestConfiguration.builder().build();
		final JsonNode arguments = JsonNodeFactory.instance.objectNode().put("command", "uname");
		when(extension.executeQuery(configuration, arguments)).thenReturn("Linux");

		assertEquals("Linux", extension.executeTool("runCommand", configuration, arguments));

		// A tool runs with any configuration the extension accepts, unless it says otherwise
		when(extension.isToolConfiguration(configuration)).thenCallRealMethod();
		when(extension.isValidConfiguration(configuration)).thenReturn(true);
		assertEquals(true, extension.isToolConfiguration(configuration));
	}
}
