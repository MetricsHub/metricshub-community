package org.metricshub.agent.m8b;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2026 MetricsHub
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import static org.metricshub.agent.helper.AgentConstants.AGENT_INFO_BUILD_NUMBER_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_HOST_NAME_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_OS_TYPE_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_SERVICE_NAME_ATTRIBUTE_KEY;

import java.util.Map;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.m8b.protocol.AgentDescriptor;
import org.metricshub.agent.opamp.OpAmpAgentDescriptionMapper;
import org.metricshub.web.service.ApplicationStatusService;

/**
 * Builds the {@link AgentDescriptor} sent to the M8B Governor at registration. Reuses the OpAMP
 * attribute resolution so both fleet channels describe the agent identically, and adds the edition,
 * which OpAMP does not report.
 */
public final class AgentDescriptorMapper {

	private AgentDescriptorMapper() {}

	/**
	 * @param agentContext the running agent context
	 * @return the agent identity
	 */
	public static AgentDescriptor map(final AgentContext agentContext) {
		final Map<String, String> attributes = OpAmpAgentDescriptionMapper.resolveAttributes(
			agentContext.getAgentInfo(),
			agentContext.getAgentConfig()
		);
		return new AgentDescriptor(
			attributes.get(AGENT_RESOURCE_SERVICE_NAME_ATTRIBUTE_KEY),
			OpAmpAgentDescriptionMapper.resolveServiceVersion(attributes),
			ApplicationStatusService.determineLicenseType(agentContext),
			attributes.get(AGENT_RESOURCE_HOST_NAME_ATTRIBUTE_KEY),
			attributes.get(AGENT_RESOURCE_OS_TYPE_ATTRIBUTE_KEY),
			System.getProperty("os.arch"),
			attributes.get(AGENT_INFO_BUILD_NUMBER_ATTRIBUTE_KEY),
			attributes
		);
	}
}
