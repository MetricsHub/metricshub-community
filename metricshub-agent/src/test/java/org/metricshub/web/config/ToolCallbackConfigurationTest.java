package org.metricshub.web.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.metricshub.web.mcp.HttpProtocolCheckService;
import org.metricshub.web.mcp.IpmiProtocolCheckService;
import org.metricshub.web.mcp.JdbcProtocolCheckService;
import org.metricshub.web.mcp.JmxProtocolCheckService;
import org.metricshub.web.mcp.ListResourcesService;
import org.metricshub.web.mcp.PingToolService;
import org.metricshub.web.mcp.SnmpProtocolCheckService;
import org.metricshub.web.mcp.SnmpV3ProtocolCheckService;
import org.metricshub.web.mcp.SshProtocolCheckService;
import org.metricshub.web.mcp.TroubleshootHostService;
import org.metricshub.web.mcp.WbemProtocolCheckService;
import org.metricshub.web.mcp.WinrmProtocolCheckService;
import org.metricshub.web.mcp.WmiProtocolCheckService;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = { ToolCallbackConfiguration.class, ToolCallbackConfigurationTest.TestConfig.class })
class ToolCallbackConfigurationTest {

	@Autowired
	private ToolCallbackProvider toolCallbackProvider;

	@Test
	void testShouldCreateToolCallbackProvider() {
		assertNotNull(toolCallbackProvider, "ToolCallbackProvider should not be null");
		final ToolCallback[] toolCallbacks = toolCallbackProvider.getToolCallbacks();
		assertNotNull(toolCallbacks, "Tool callbacks should not be null");
		assertTrue(toolCallbacks.length > 0, "Tool callbacks should not be empty");
	}

	// Define a mock PingToolService for the context
	@Configuration
	static class TestConfig {

		@Bean
		public PingToolService pingToolService() {
			return mock(PingToolService.class);
		}

		@Bean
		public HttpProtocolCheckService httpProtocolCheckService() {
			return mock(HttpProtocolCheckService.class);
		}

		@Bean
		public IpmiProtocolCheckService ipmiProtocolCheckService() {
			return mock(IpmiProtocolCheckService.class);
		}

		@Bean
		public JdbcProtocolCheckService jdbcProtocolCheckService() {
			return mock(JdbcProtocolCheckService.class);
		}

		@Bean
		public JmxProtocolCheckService jmxProtocolCheckService() {
			return mock(JmxProtocolCheckService.class);
		}

		@Bean
		public SnmpProtocolCheckService snmpProtocolCheckService() {
			return mock(SnmpProtocolCheckService.class);
		}

		@Bean
		public SnmpV3ProtocolCheckService snmpV3ProtocolCheckService() {
			return mock(SnmpV3ProtocolCheckService.class);
		}

		@Bean
		public SshProtocolCheckService sshProtocolCheckService() {
			return mock(SshProtocolCheckService.class);
		}

		@Bean
		public WbemProtocolCheckService wbemProtocolCheckService() {
			return mock(WbemProtocolCheckService.class);
		}

		@Bean
		public WinrmProtocolCheckService winrmProtocolCheckService() {
			return mock(WinrmProtocolCheckService.class);
		}

		@Bean
		public WmiProtocolCheckService wmiProtocolCheckService() {
			return mock(WmiProtocolCheckService.class);
		}

		@Bean
		public ListResourcesService listResourcesService() {
			return mock(ListResourcesService.class);
		}

		@Bean
		public TroubleshootHostService troubleshootHostService() {
			return mock(TroubleshootHostService.class);
		}
	}
}
