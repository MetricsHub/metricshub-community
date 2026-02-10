package org.metricshub.web.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.agent.helper.AgentConstants.AGENT_INFO_BUILD_DATE_NUMBER_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_INFO_BUILD_NUMBER_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_INFO_CC_VERSION_NUMBER_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_INFO_NAME_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_INFO_VERSION_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_AGENT_HOST_NAME_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_HOST_NAME_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_HOST_TYPE_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_OS_TYPE_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_SERVICE_NAME_ATTRIBUTE_KEY;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.context.AgentInfo;
import org.metricshub.web.AgentContextHolder;

class AgentInfoServiceTest {

	@Test
	void testGetAgentInfo() {
		// Setup mocks
		final AgentContextHolder agentContextHolder = mock(AgentContextHolder.class);
		final AgentContext agentContext = mock(AgentContext.class);
		final AgentInfo agentInfo = mock(AgentInfo.class);

		// Mock agent attributes
		final Map<String, String> mockAttributes = Map.of(
			AGENT_RESOURCE_SERVICE_NAME_ATTRIBUTE_KEY,
			"metricshub",
			AGENT_RESOURCE_HOST_NAME_ATTRIBUTE_KEY,
			"test-host",
			AGENT_RESOURCE_AGENT_HOST_NAME_ATTRIBUTE_KEY,
			"test-host",
			AGENT_RESOURCE_HOST_TYPE_ATTRIBUTE_KEY,
			"compute",
			AGENT_RESOURCE_OS_TYPE_ATTRIBUTE_KEY,
			"windows",
			AGENT_INFO_NAME_ATTRIBUTE_KEY,
			"metricshub",
			AGENT_INFO_VERSION_ATTRIBUTE_KEY,
			"1.0.0",
			AGENT_INFO_BUILD_NUMBER_ATTRIBUTE_KEY,
			"12345",
			AGENT_INFO_BUILD_DATE_NUMBER_ATTRIBUTE_KEY,
			"2026-02-10",
			AGENT_INFO_CC_VERSION_NUMBER_ATTRIBUTE_KEY,
			"2.0.0"
		);

		// Setup mock behavior
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);
		when(agentContext.getAgentInfo()).thenReturn(agentInfo);
		when(agentContext.getPid()).thenReturn("12345");
		when(agentInfo.getAttributes()).thenReturn(mockAttributes);

		// Create service and call method
		final AgentInfoService agentInfoService = new AgentInfoService(agentContextHolder);
		final AgentInfoResponse response = agentInfoService.getAgentInfo();

		// Verify metadata from metricshub.agent.info attributes
		assertNotNull(response, "Response should not be null");
		assertEquals("metricshub", response.getServiceName(), "Service name should match");
		assertEquals("test-host", response.getHostName(), "Host name should match");
		assertEquals("test-host", response.getAgentHostName(), "Agent host name should match");
		assertEquals("compute", response.getHostType(), "Host type should match");
		assertEquals("windows", response.getOsType(), "OS type should match");
		assertEquals("metricshub", response.getName(), "Name should match");
		assertEquals("1.0.0", response.getVersion(), "Version should match");
		assertEquals("12345", response.getBuildNumber(), "Build number should match");
		assertEquals("2026-02-10", response.getBuildDate(), "Build date should match");
		assertEquals("2.0.0", response.getCcVersion(), "CC version should match");

		// Verify runtime information
		assertEquals("12345", response.getProcessPid(), "Process PID should match");
		assertNotNull(response.getHostArch(), "Host arch should not be null");
		assertNotNull(response.getOsName(), "OS name should not be null");
		assertNotNull(response.getJavaVersion(), "Java version should not be null");
		assertNotNull(response.getJavaHome(), "Java home should not be null");
		assertNotNull(response.getUserDir(), "User dir should not be null");

		// Verify memory information
		assertNotNull(response.getMemoryTotal(), "Memory total should not be null");
		assertNotNull(response.getMemoryUsed(), "Memory used should not be null");
		assertNotNull(response.getMemoryFree(), "Memory free should not be null");
		assertNotNull(response.getMemoryMax(), "Memory max should not be null");
		assertNotNull(response.getMemoryUsagePercent(), "Memory usage percent should not be null");
		assertTrue(response.getMemoryUsed() > 0, "Used memory should be positive");
		assertTrue(response.getMemoryUsagePercent() >= 0, "Memory usage percent should be non-negative");
		assertTrue(response.getMemoryUsagePercent() <= 100, "Memory usage percent should not exceed 100");

		// Verify CPU information
		assertNotNull(response.getCpuUsagePercent(), "CPU usage percent should not be null");
	}

	@Test
	void testGetAgentInfoMemoryCalculations() {
		// Setup mocks
		final AgentContextHolder agentContextHolder = mock(AgentContextHolder.class);
		final AgentContext agentContext = mock(AgentContext.class);
		final AgentInfo agentInfo = mock(AgentInfo.class);

		final Map<String, String> mockAttributes = Map.of(
			AGENT_RESOURCE_SERVICE_NAME_ATTRIBUTE_KEY,
			"metricshub",
			AGENT_RESOURCE_HOST_NAME_ATTRIBUTE_KEY,
			"test-host",
			AGENT_RESOURCE_AGENT_HOST_NAME_ATTRIBUTE_KEY,
			"test-host",
			AGENT_RESOURCE_HOST_TYPE_ATTRIBUTE_KEY,
			"compute",
			AGENT_RESOURCE_OS_TYPE_ATTRIBUTE_KEY,
			"linux",
			AGENT_INFO_NAME_ATTRIBUTE_KEY,
			"metricshub",
			AGENT_INFO_VERSION_ATTRIBUTE_KEY,
			"1.0.0",
			AGENT_INFO_BUILD_NUMBER_ATTRIBUTE_KEY,
			"12345",
			AGENT_INFO_BUILD_DATE_NUMBER_ATTRIBUTE_KEY,
			"2026-02-10",
			AGENT_INFO_CC_VERSION_NUMBER_ATTRIBUTE_KEY,
			"2.0.0"
		);

		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);
		when(agentContext.getAgentInfo()).thenReturn(agentInfo);
		when(agentContext.getPid()).thenReturn("67890");
		when(agentInfo.getAttributes()).thenReturn(mockAttributes);

		final AgentInfoService agentInfoService = new AgentInfoService(agentContextHolder);
		final AgentInfoResponse response = agentInfoService.getAgentInfo();

		// Verify memory calculations are consistent
		final long total = response.getMemoryTotal();
		final long used = response.getMemoryUsed();
		final long free = response.getMemoryFree();

		// Free memory should be total - used
		assertEquals(total - used, free, "Free memory should equal total minus used");
	}
}
