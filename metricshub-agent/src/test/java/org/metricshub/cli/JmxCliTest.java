package org.metricshub.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.extension.IProtocolExtension;
import picocli.CommandLine;
import picocli.CommandLine.ParameterException;

class JmxCliTest {

	private JmxCli jmxCli;

	@BeforeEach
	void setUp() {
		jmxCli = new JmxCli();
		jmxCli.setObjectName("org.metricshub:type=ExampleMBean,scope=*");
		jmxCli.setAttributes(List.of("Count", "Type"));
		jmxCli.setKeyProperties(List.of("scope"));
		jmxCli.setHostname("localhost");
		jmxCli.setSpec(new CommandLine(jmxCli).getCommandSpec());
	}

	@Test
	void testShouldBuildCorrectQueryJson() {
		final JsonNode query = jmxCli.getQuery();

		assertEquals(
			"org.metricshub:type=ExampleMBean,scope=*",
			query.get("objectName").asText(),
			"ObjectName should match"
		);
		assertTrue(query.has("attributes"), "Query should contain attributes");
		assertEquals("Count", query.get("attributes").get(0).asText(), "First attribute should be 'Count'");
	}

	@Test
	void testShouldThrowExceptionWhenObjectNameMissing() {
		jmxCli.setObjectName(null);
		final ParameterException ex = assertThrows(
			ParameterException.class,
			jmxCli::validate,
			"--object-name input must be provided"
		);
		assertTrue(
			ex.getMessage().contains("--object-name input must be provided"),
			"ObjectName must be provided in the query"
		);
	}

	@Test
	void testShouldThrowExceptionWhenObjectNameBlank() {
		jmxCli.setObjectName("   ");
		final ParameterException ex = assertThrows(
			ParameterException.class,
			jmxCli::validate,
			"--object-name input must not be empty"
		);
		assertTrue(
			ex.getMessage().contains("--object-name input must not be empty"),
			"ObjectName must not be blank in the query"
		);
	}

	@Test
	void testShouldThrowExceptionWhenNoAttributesAndKeyProperties() {
		jmxCli.setAttributes(null);
		jmxCli.setKeyProperties(null);
		final ParameterException ex = assertThrows(
			ParameterException.class,
			jmxCli::validate,
			"At least one attribute or key-property must be specified"
		);
		assertTrue(
			ex.getMessage().contains("At least one attribute or key-property must be specified"),
			"At least one attribute or key-property must be specified in the query"
		);
	}

	@Test
	void testShouldRequestInteractivePassword() {
		jmxCli.setUsername("admin");
		jmxCli.setPassword(null);

		jmxCli.tryInteractivePassword((fmt, args) -> "secret".toCharArray());

		assertArrayEquals("secret".toCharArray(), jmxCli.getPassword(), "Password should be set interactively when null");
	}

	@Test
	void testShouldRunQuerySuccessfully() throws Exception {
		// Mocks
		final IProtocolExtension mockExtension = mock(IProtocolExtension.class);
		final IConfiguration mockConfig = mock(IConfiguration.class);
		final StringWriter out = new StringWriter();

		jmxCli.setPrintWriter(new PrintWriter(out));
		jmxCli.setPort(9999);
		jmxCli.setTimeout("30");
		jmxCli.setUsername("admin");
		jmxCli.setPassword("admin".toCharArray());

		// Mock expected behavior
		when(mockExtension.buildConfiguration(any(), any(), any())).thenReturn(mockConfig);
		when(mockExtension.executeQuery(any(), any())).thenReturn("mocked result");

		// Call the private method through reflection or by temporarily making it package-private
		jmxCli.runQuery(mockExtension);

		final String output = out.toString();
		assertTrue(output.contains("mocked result"), "Output should contain the mocked result from the query execution");
	}
}
