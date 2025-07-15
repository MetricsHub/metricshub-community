package org.metricshub.programmable.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Paths;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.http.HttpClient;
import org.metricshub.http.HttpResponse;
import org.mockito.MockedStatic;

class ProgrammableConfigurationProviderTest {

	@Test
	void testLoad() {
		var pcp = new ProgrammableConfigurationProvider();
		var nodes = pcp.load(Paths.get("src/test/resources/config"));
		assertEquals(1, nodes.size(), "Should load one configuration fragment");
		final JsonNode node = nodes.iterator().next();
		final JsonNode resources = node.get("resources");
		assertNotNull(resources, "Resources node should not be null");
		assertTrue(resources.has("host-01-system"), "Resources should contain host-01-system");
		assertTrue(resources.has("host-02-system"), "Resources should contain host-02-system");
		assertTrue(resources.has("host-03-system"), "Resources should contain host-03-system");
		assertResourceConfiguration(resources, "host-01-system");
		assertResourceConfiguration(resources, "host-02-system");
		assertResourceConfiguration(resources, "host-03-system");
	}

	/**
	 * Asserts that the resource configuration for host-01-system is correct.
	 * @param resources The resources JsonNode containing the configuration.
	 */
	private void assertResourceConfiguration(final JsonNode resources, String resourceName) {
		final JsonNode resource = resources.get(resourceName);
		assertTrue(resource.has("attributes"), resourceName + " should have attributes");
		final JsonNode attributes = resource.get("attributes");
		assertTrue(attributes.has("host.name"), resourceName + " should have host.name attribute");
		assertTrue(attributes.has("host.type"), resourceName + " should have host.type attribute");
		assertTrue(resource.has("protocols"), resourceName + " should have protocols");
		final JsonNode protocols = resource.get("protocols");
		assertTrue(protocols.has("ssh"), resourceName + " should have ssh protocol");
		assertTrue(protocols.get("ssh").has("username"), resourceName + " ssh should have username");
		assertTrue(protocols.get("ssh").has("password"), resourceName + " ssh should have password");
		assertTrue(resource.has("connectors"), resourceName + " should have connectors");
		assertTrue(resource.get("connectors").isArray(), resourceName + " connectors should be an array");
	}

	@Test
	void testLoadUsingHttpTool() {
		try (MockedStatic<HttpClient> httpClientMock = mockStatic(HttpClient.class)) {
			final HttpResponse httpResponseList = new HttpResponse();
			httpResponseList.setStatusCode(200);
			httpResponseList.appendBody(
				"""
				  [
				    {"hostname":"winhost1","OSType":"Windows","adminUsername":"admin1"}
				  ]
				"""
			);
			httpClientMock
				.when(() ->
					HttpClient.sendRequest(
						"https://cmdb/servers",
						"GET",
						null,
						null,
						null,
						null,
						0,
						null,
						null,
						null,
						Map.of(),
						null,
						HttpTool.DEFAULT_HTTP_TIMEOUT,
						null
					)
				)
				.thenReturn(httpResponseList);

			httpClientMock.clearInvocations();

			final HttpResponse passwordResponse = new HttpResponse();
			passwordResponse.setStatusCode(200);
			passwordResponse.appendBody("password");
			httpClientMock
				.when(() ->
					HttpClient.sendRequest(
						"https://passwords/servers/winhost1/password",
						"GET",
						null,
						null,
						null,
						null,
						0,
						null,
						null,
						null,
						Map.of(),
						null,
						HttpTool.DEFAULT_HTTP_TIMEOUT,
						null
					)
				)
				.thenReturn(passwordResponse);

			var pcp = new ProgrammableConfigurationProvider();
			var nodes = pcp.load(Paths.get("src/test/resources/http"));

			assertEquals(
				"""
				[{"resources":{"winhost1":{"attributes":{"host.name":"winhost1","host.type":"windows"},"protocols":{"ping":null,"wmi":{"timeout":120,"username":"admin1","password":"password"}}}}}]""",
				nodes.toString(),
				"Should load one configuration fragment with HTTP tool"
			);
		}
	}

	@Test
	void testLoadUsingFileTool() {
		var pcp = new ProgrammableConfigurationProvider();
		var nodes = pcp.load(Paths.get("src/test/resources/file"));
		assertEquals(
			"""
			[{"resources":{"host1":{"attributes":{"host.name":"host1","host.type":"win"},"protocols":{"wmi":{"username":"user","password":"pass"}}},"host2":{"attributes":{"host.name":"host2","host.type":"linux"},"protocols":{"ssh":{"username":"user","password":"pass"}}}}}]""",
			nodes.toString(),
			"Should load correct configuration fragment using file tool"
		);
	}
}
