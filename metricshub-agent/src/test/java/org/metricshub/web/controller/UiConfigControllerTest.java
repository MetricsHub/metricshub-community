package org.metricshub.web.controller;

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.extension.oscommand.OsCommandExtension;
import org.metricshub.extension.oscommand.SshConfiguration;
import org.metricshub.web.dto.uiconfig.CreateResourceGroupRequestDto;
import org.metricshub.web.dto.uiconfig.HostUpCheckResponseDto;
import org.metricshub.web.dto.uiconfig.ProtocolCheckRequestDto;
import org.metricshub.web.dto.uiconfig.UiConfigSnapshotDto;
import org.metricshub.web.dto.uiconfig.UiConnectorSummaryDto;
import org.metricshub.web.dto.uiconfig.UpdateResourceGroupRequestDto;
import org.metricshub.web.service.UiConfigService;
import org.metricshub.web.service.UiProtocolCheckService;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UiConfigControllerTest {

	private MockMvc mockMvc;
	private UiConfigService uiConfigService;
	private UiProtocolCheckService uiProtocolCheckService;
	private final ExtensionManager extensionManager = ExtensionManager
		.builder()
		.withProtocolExtensions(List.of(new OsCommandExtension()))
		.build();
	private ObjectMapper objectMapper;

	@BeforeEach
	void setup() {
		uiConfigService = Mockito.mock(UiConfigService.class);
		uiProtocolCheckService = Mockito.mock(UiProtocolCheckService.class);
		final UiConfigController controller = new UiConfigController(uiConfigService, uiProtocolCheckService);
		objectMapper = new ObjectMapper();
		final InjectableValues.Std injectableValues = new InjectableValues.Std();
		injectableValues.addValue(ExtensionManager.class, extensionManager);
		injectableValues.addValue(ExtensionManager.class.getName(), extensionManager);
		objectMapper.setInjectableValues(injectableValues);
		mockMvc =
			MockMvcBuilders
				.standaloneSetup(controller)
				.setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
				.build();
	}

	// -------------------------------------------------------------------------
	// POST /api/ui-config/protocol-check
	// -------------------------------------------------------------------------

	@Test
	void testCheckProtocolReturnsHostUp() throws Exception {
		final ProtocolCheckRequestDto request = new ProtocolCheckRequestDto();
		request.setHostname("server1");
		request.setProtocol("ssh");
		request.setProtocolConfig(
			Map.of("ssh", SshConfiguration.sshConfigurationBuilder().username("admin").port(22).build())
		);

		final HostUpCheckResponseDto response = HostUpCheckResponseDto.builder().hostUp(1).responseTimeMs(120.0).build();
		when(uiProtocolCheckService.checkHostUp(any(ProtocolCheckRequestDto.class))).thenReturn(response);

		mockMvc
			.perform(
				post("/api/ui-config/protocol-check")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request))
			)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.hostUp").value(1))
			.andExpect(jsonPath("$.responseTimeMs").value(120.0));
	}

	// -------------------------------------------------------------------------
	// GET /api/ui-config/hosts
	// -------------------------------------------------------------------------

	@Test
	void testGetSnapshotReturnsResourcesAndGroups() throws Exception {
		final UiConfigSnapshotDto snapshot = UiConfigSnapshotDto
			.builder()
			.resources(Map.of("host1", Map.of("attributes", Map.of("host.name", "server1"))))
			.resourceGroups(Map.of("GroupA", Map.of("attributes", Map.of("site", "Paris"))))
			.build();
		when(uiConfigService.getSnapshot()).thenReturn(snapshot);

		mockMvc
			.perform(get("/api/ui-config/hosts"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.resourceGroups.GroupA").exists())
			.andExpect(jsonPath("$.resources.host1").exists());
	}

	@Test
	void testGetSnapshotWhenEmptyReturnsEmptyCollections() throws Exception {
		when(uiConfigService.getSnapshot()).thenReturn(UiConfigSnapshotDto.builder().build());

		mockMvc
			.perform(get("/api/ui-config/hosts"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.resourceGroups").isEmpty())
			.andExpect(jsonPath("$.resources").isEmpty());
	}

	// -------------------------------------------------------------------------
	// POST /api/ui-config/resource-groups
	// -------------------------------------------------------------------------

	@Test
	void testCreateResourceGroupWithMetricsReturns200AndSnapshot() throws Exception {
		final CreateResourceGroupRequestDto request = new CreateResourceGroupRequestDto();
		request.setName("Paris");
		request.setAttributes(Map.of("site", "Paris"));
		request.setMetrics(Map.of("hw.site.carbon_intensity", 230, "hw.site.electricity_cost", 0.12, "hw.site.pue", 1.8));

		final UiConfigSnapshotDto snapshot = UiConfigSnapshotDto
			.builder()
			.resourceGroups(
				Map.of(
					"Paris",
					Map.of(
						"attributes",
						Map.of("site", "Paris"),
						"metrics",
						Map.of("hw.site.carbon_intensity", 230, "hw.site.electricity_cost", 0.12, "hw.site.pue", 1.8),
						"resources",
						Map.of()
					)
				)
			)
			.build();
		when(uiConfigService.createResourceGroup(any(CreateResourceGroupRequestDto.class))).thenReturn(snapshot);

		mockMvc
			.perform(
				post("/api/ui-config/resource-groups")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request))
			)
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.resourceGroups.Paris").exists())
			.andExpect(jsonPath("$.resourceGroups.Paris.metrics['hw.site.carbon_intensity']").value(230))
			.andExpect(jsonPath("$.resourceGroups.Paris.metrics['hw.site.electricity_cost']").value(0.12))
			.andExpect(jsonPath("$.resourceGroups.Paris.metrics['hw.site.pue']").value(1.8))
			.andExpect(jsonPath("$.resourceGroups.Paris.attributes.site").value("Paris"));
	}

	@Test
	void testCreateResourceGroupDelegatesToServiceWithCorrectName() throws Exception {
		when(uiConfigService.createResourceGroup(any())).thenReturn(UiConfigSnapshotDto.builder().build());

		final CreateResourceGroupRequestDto request = new CreateResourceGroupRequestDto();
		request.setName("NewGroup");

		mockMvc
			.perform(
				post("/api/ui-config/resource-groups")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request))
			)
			.andExpect(status().isOk());

		verify(uiConfigService).createResourceGroup(argThat(r -> "NewGroup".equals(r.getName())));
	}

	@Test
	void testCreateResourceGroupDelegatesToServiceWithMetrics() throws Exception {
		when(uiConfigService.createResourceGroup(any())).thenReturn(UiConfigSnapshotDto.builder().build());

		final CreateResourceGroupRequestDto request = new CreateResourceGroupRequestDto();
		request.setName("Group");
		request.setMetrics(Map.of("hw.site.pue", 1.5));

		mockMvc
			.perform(
				post("/api/ui-config/resource-groups")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request))
			)
			.andExpect(status().isOk());

		verify(uiConfigService).createResourceGroup(argThat(r -> r.getMetrics() != null && !r.getMetrics().isEmpty()));
	}

	// -------------------------------------------------------------------------
	// PUT /api/ui-config/resource-groups/{groupName}
	// -------------------------------------------------------------------------

	@Test
	void testUpdateResourceGroupWithMetricsReturns200() throws Exception {
		final UpdateResourceGroupRequestDto request = new UpdateResourceGroupRequestDto();
		request.setName("Paris");
		request.setAttributes(Map.of("site", "Paris"));
		request.setMetrics(Map.of("hw.site.electricity_cost", 0.15));

		final UiConfigSnapshotDto snapshot = UiConfigSnapshotDto
			.builder()
			.resourceGroups(
				Map.of(
					"Paris",
					Map.of(
						"attributes",
						Map.of("site", "Paris"),
						"metrics",
						Map.of("hw.site.electricity_cost", 0.15),
						"resources",
						Map.of()
					)
				)
			)
			.build();
		when(uiConfigService.updateResourceGroup(eq("Paris"), any(UpdateResourceGroupRequestDto.class)))
			.thenReturn(snapshot);

		mockMvc
			.perform(
				put("/api/ui-config/resource-groups/Paris")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request))
			)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.resourceGroups.Paris.metrics['hw.site.electricity_cost']").value(0.15));
	}

	@Test
	void testUpdateResourceGroupDelegatesToServiceWithGroupNameAndMetrics() throws Exception {
		when(uiConfigService.updateResourceGroup(any(), any())).thenReturn(UiConfigSnapshotDto.builder().build());

		final UpdateResourceGroupRequestDto request = new UpdateResourceGroupRequestDto();
		request.setName("Renamed");
		request.setMetrics(Map.of("hw.site.pue", 2.0));

		mockMvc
			.perform(
				put("/api/ui-config/resource-groups/Original")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request))
			)
			.andExpect(status().isOk());

		verify(uiConfigService)
			.updateResourceGroup(
				eq("Original"),
				argThat(r -> "Renamed".equals(r.getName()) && r.getMetrics() != null && !r.getMetrics().isEmpty())
			);
	}

	// -------------------------------------------------------------------------
	// DELETE /api/ui-config/resource-groups/{groupName}
	// -------------------------------------------------------------------------

	@Test
	void testDeleteResourceGroupReturns200() throws Exception {
		when(uiConfigService.deleteResourceGroup("OldGroup")).thenReturn(UiConfigSnapshotDto.builder().build());

		mockMvc
			.perform(delete("/api/ui-config/resource-groups/OldGroup"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

		verify(uiConfigService).deleteResourceGroup("OldGroup");
	}

	@Test
	void testDeleteGroupedHostReturns200() throws Exception {
		when(uiConfigService.deleteGroupedHost("GroupA", "host1")).thenReturn(UiConfigSnapshotDto.builder().build());

		mockMvc.perform(delete("/api/ui-config/resource-groups/GroupA/hosts/host1")).andExpect(status().isOk());

		verify(uiConfigService).deleteGroupedHost("GroupA", "host1");
	}

	@Test
	void testGetConnectorCatalogReturnsStaticCatalog() throws Exception {
		when(uiConfigService.getConnectorCatalog())
			.thenReturn(
				List.of(
					UiConnectorSummaryDto
						.builder()
						.id("Linux")
						.displayName("Linux")
						.appliesToHostTypes(List.of("linux"))
						.requiredProtocols(List.of("ssh"))
						.build()
				)
			);

		mockMvc
			.perform(get("/api/ui-config/connectors/catalog"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value("Linux"))
			.andExpect(jsonPath("$[0].requiredProtocols[0]").value("ssh"));

		verify(uiConfigService).getConnectorCatalog();
	}
}
