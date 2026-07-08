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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.metricshub.web.dto.uiconfig.AddHostRequestDto;
import org.metricshub.web.dto.uiconfig.CreateResourceGroupRequestDto;
import org.metricshub.web.dto.uiconfig.HostUpCheckResponseDto;
import org.metricshub.web.dto.uiconfig.ProtocolCheckRequestDto;
import org.metricshub.web.dto.uiconfig.UiConfigSnapshotDto;
import org.metricshub.web.dto.uiconfig.UiConnectorSummaryDto;
import org.metricshub.web.dto.uiconfig.UpdateResourceGroupRequestDto;
import org.metricshub.web.service.UiConfigService;
import org.metricshub.web.service.UiProtocolCheckService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Typed endpoints to manage hosts in metricshub-ui.yaml.
 */
@RestController
@RequestMapping(value = "/api/ui-config")
@Tag(name = "UI Configuration", description = "Typed operations for metricshub-ui.yaml")
public class UiConfigController {

	private final UiConfigService uiConfigService;

	private final UiProtocolCheckService uiProtocolCheckService;

	public UiConfigController(
		final UiConfigService uiConfigService,
		final UiProtocolCheckService uiProtocolCheckService
	) {
		this.uiConfigService = uiConfigService;
		this.uiProtocolCheckService = uiProtocolCheckService;
	}

	@Operation(summary = "Get UI configuration snapshot")
	@GetMapping(value = "/hosts", produces = MediaType.APPLICATION_JSON_VALUE)
	public UiConfigSnapshotDto getSnapshot() {
		return uiConfigService.getSnapshot();
	}

	@Operation(summary = "Run an on-demand protocol health check (metricshub.host.up)")
	@PostMapping(
		value = "/protocol-check",
		consumes = MediaType.APPLICATION_JSON_VALUE,
		produces = MediaType.APPLICATION_JSON_VALUE
	)
	public HostUpCheckResponseDto checkProtocol(@Valid @RequestBody final ProtocolCheckRequestDto request) {
		return uiProtocolCheckService.checkHostUp(request);
	}

	@Operation(summary = "Static connector catalog for the resource configuration form (metadata only)")
	@GetMapping(value = "/connectors/catalog", produces = MediaType.APPLICATION_JSON_VALUE)
	public List<UiConnectorSummaryDto> getConnectorCatalog() {
		return uiConfigService.getConnectorCatalog();
	}

	@Operation(summary = "List connectors for the resource configuration")
	@GetMapping(value = "/connectors", produces = MediaType.APPLICATION_JSON_VALUE)
	public List<UiConnectorSummaryDto> listConnectors(
		@RequestParam(value = "hostType", required = false) final String hostType,
		@RequestParam(value = "protocols", required = false) final List<String> protocols,
		@RequestParam(value = "includeAll", defaultValue = "false") final boolean includeAll
	) {
		return uiConfigService.listConnectors(hostType, protocols, includeAll);
	}

	@Operation(summary = "Create a resource group")
	@PostMapping(value = "/resource-groups", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<UiConfigSnapshotDto> createResourceGroup(
		@Valid @RequestBody CreateResourceGroupRequestDto request
	) {
		return ResponseEntity.ok(uiConfigService.createResourceGroup(request));
	}

	@Operation(summary = "Update a resource group")
	@PutMapping(
		value = "/resource-groups/{groupName}",
		consumes = MediaType.APPLICATION_JSON_VALUE,
		produces = MediaType.APPLICATION_JSON_VALUE
	)
	public UiConfigSnapshotDto updateResourceGroup(
		@PathVariable("groupName") final String groupName,
		@Valid @RequestBody final UpdateResourceGroupRequestDto request
	) {
		return uiConfigService.updateResourceGroup(groupName, request);
	}

	@Operation(summary = "Delete a resource group")
	@DeleteMapping(value = "/resource-groups/{groupName}", produces = MediaType.APPLICATION_JSON_VALUE)
	public UiConfigSnapshotDto deleteResourceGroup(@PathVariable("groupName") final String groupName) {
		return uiConfigService.deleteResourceGroup(groupName);
	}

	@Operation(summary = "Add host (standalone or grouped)")
	@PostMapping(
		value = "/hosts",
		consumes = MediaType.APPLICATION_JSON_VALUE,
		produces = MediaType.APPLICATION_JSON_VALUE
	)
	public UiConfigSnapshotDto addHost(@Valid @RequestBody final AddHostRequestDto request) {
		return uiConfigService.addHost(request);
	}

	@Operation(summary = "Delete a standalone host")
	@DeleteMapping(value = "/hosts/{hostId}", produces = MediaType.APPLICATION_JSON_VALUE)
	public UiConfigSnapshotDto deleteStandaloneHost(@PathVariable("hostId") final String hostId) {
		return uiConfigService.deleteTopLevelHost(hostId);
	}

	@Operation(summary = "Delete a grouped host")
	@DeleteMapping(value = "/resource-groups/{groupName}/hosts/{hostId}", produces = MediaType.APPLICATION_JSON_VALUE)
	public UiConfigSnapshotDto deleteGroupedHost(
		@PathVariable("groupName") final String groupName,
		@PathVariable("hostId") final String hostId
	) {
		return uiConfigService.deleteGroupedHost(groupName, hostId);
	}
}
