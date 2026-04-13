package org.metricshub.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.metricshub.agent.helper.TestConstants.TEST_CONFIG_DIRECTORY_PATH;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.service.TestHelper;
import org.metricshub.configuration.YamlConfigurationProvider;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.extension.snmp.SnmpExtension;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.dto.ApplicationStatus;

class ApplicationStatusServiceTest {

	private ApplicationStatusService applicationStatusService;

	@BeforeEach
	void setup() throws IOException {
		TestHelper.configureGlobalLogger();

		// Initialize the extension manager required by the agent context
		final ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(List.of(new SnmpExtension()))
			.withConfigurationProviderExtensions(List.of(new YamlConfigurationProvider()))
			.build();
		final AgentContext agentContext = new AgentContext(TEST_CONFIG_DIRECTORY_PATH, extensionManager);
		final AgentContextHolder agentContextHolder = new AgentContextHolder(agentContext);
		applicationStatusService = new ApplicationStatusService(agentContextHolder);
	}

	@Test
	void testShouldReturnCorrectApplicationStatus() {
		final ApplicationStatus applicationStatus = applicationStatusService.reportApplicationStatus();
		assertNotNull(applicationStatus, "Application status should not be null");
		assertEquals(ApplicationStatus.Status.UP, applicationStatus.getStatus(), "Application status should be UP");
		assertNotNull(applicationStatus.getAgentInfo(), "Agent info should not be null");
		assertEquals(4, applicationStatus.getNumberOfConfiguredResources(), "Number of configured resources should be 4");
		assertEquals(4, applicationStatus.getNumberOfObservedResources(), "Number of observed resources should be 4");
	}
}
