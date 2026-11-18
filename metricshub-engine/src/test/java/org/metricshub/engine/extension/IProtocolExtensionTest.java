package org.metricshub.engine.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

		// An extension offers no tool unless it says so, and then runs none
		assertEquals(List.of(), extension.getTools());
		final IConfiguration configuration = TestConfiguration.builder().build();
		assertThrows(
			UnsupportedOperationException.class,
			() -> extension.executeTool("runCommand", configuration, JsonNodeFactory.instance.objectNode())
		);

		// A tool runs with any configuration the extension accepts, unless it says otherwise
		when(extension.isToolConfiguration(configuration)).thenCallRealMethod();
		when(extension.isValidConfiguration(configuration)).thenReturn(true);
		assertEquals(true, extension.isToolConfiguration(configuration));
	}
}
