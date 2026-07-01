package org.metricshub.web.service;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.dto.uiconfig.CreateResourceGroupRequestDto;
import org.metricshub.web.dto.uiconfig.UiConfigSnapshotDto;
import org.metricshub.web.dto.uiconfig.UpdateResourceGroupRequestDto;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

class UiConfigServiceTest {

	@TempDir
	Path tempDir;

	private UiConfigService service;

	@BeforeEach
	void setup() {
		final AgentContext agentContext = Mockito.mock(AgentContext.class, Mockito.RETURNS_DEEP_STUBS);
		when(agentContext.getConfigDirectory()).thenReturn(tempDir);
		when(agentContext.getExtensionManager()).thenReturn(ExtensionManager.builder().build());

		final AgentContextHolder agentContextHolder = mock(AgentContextHolder.class);
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);

		service =
			new UiConfigService(agentContextHolder, mock(UiConnectorCompatibilityService.class), new ConnectorStore());
	}

	// -------------------------------------------------------------------------
	// getSnapshot
	// -------------------------------------------------------------------------

	@Test
	void testGetSnapshotWhenNoFileReturnsEmptyCollections() {
		final UiConfigSnapshotDto snapshot = service.getSnapshot();

		assertNotNull(snapshot, "Snapshot should never be null");
		assertTrue(snapshot.getResources().isEmpty(), "Resources should be empty when no config file exists");
		assertTrue(snapshot.getResourceGroups().isEmpty(), "Resource groups should be empty when no config file exists");
	}

	// -------------------------------------------------------------------------
	// createResourceGroup
	// -------------------------------------------------------------------------

	@Test
	@SuppressWarnings("unchecked")
	void testCreateResourceGroupPersistsAttributesAndMetrics() {
		final CreateResourceGroupRequestDto request = new CreateResourceGroupRequestDto();
		request.setName("Paris");
		request.setAttributes(Map.of("site", "Paris"));
		request.setMetrics(Map.of("hw.site.carbon_intensity", 230, "hw.site.electricity_cost", 0.12, "hw.site.pue", 1.8));

		final UiConfigSnapshotDto snapshot = service.createResourceGroup(request);

		assertTrue(snapshot.getResourceGroups().containsKey("Paris"), "Snapshot should contain the created group");
		final Map<String, Object> group = (Map<String, Object>) snapshot.getResourceGroups().get("Paris");

		final Map<String, Object> attributes = (Map<String, Object>) group.get("attributes");
		assertNotNull(attributes, "Attributes section should be present");
		assertEquals("Paris", attributes.get("site"), "Attribute 'site' should match");

		final Map<String, Object> metrics = (Map<String, Object>) group.get("metrics");
		assertNotNull(metrics, "Metrics section should be present");
		assertEquals(230, ((Number) metrics.get("hw.site.carbon_intensity")).intValue(), "Carbon intensity should be 230");
		assertEquals(
			0.12,
			((Number) metrics.get("hw.site.electricity_cost")).doubleValue(),
			0.001,
			"Electricity cost should be 0.12"
		);
		assertEquals(1.8, ((Number) metrics.get("hw.site.pue")).doubleValue(), 0.001, "PUE should be 1.8");
	}

	@Test
	@SuppressWarnings("unchecked")
	void testCreateResourceGroupWithDefaultEmptyMetrics() {
		final CreateResourceGroupRequestDto request = new CreateResourceGroupRequestDto();
		request.setName("London");
		request.setAttributes(Map.of("site", "London"));
		// metrics left as default empty map

		final UiConfigSnapshotDto snapshot = service.createResourceGroup(request);

		assertTrue(snapshot.getResourceGroups().containsKey("London"), "Snapshot should contain the created group");
		final Map<String, Object> group = (Map<String, Object>) snapshot.getResourceGroups().get("London");
		final Map<String, Object> metrics = (Map<String, Object>) group.get("metrics");
		assertTrue(metrics == null || metrics.isEmpty(), "Metrics should be absent or empty when none were provided");
	}

	@Test
	@SuppressWarnings("unchecked")
	void testCreateMultipleResourceGroupsAreAllPresent() {
		final CreateResourceGroupRequestDto paris = new CreateResourceGroupRequestDto();
		paris.setName("Paris");
		paris.setMetrics(Map.of("hw.site.carbon_intensity", 230));
		service.createResourceGroup(paris);

		final CreateResourceGroupRequestDto london = new CreateResourceGroupRequestDto();
		london.setName("London");
		london.setMetrics(Map.of("hw.site.carbon_intensity", 215));
		final UiConfigSnapshotDto snapshot = service.createResourceGroup(london);

		assertEquals(2, snapshot.getResourceGroups().size(), "Both groups should be in the snapshot");
		assertTrue(snapshot.getResourceGroups().containsKey("Paris"));
		assertTrue(snapshot.getResourceGroups().containsKey("London"));

		final Map<String, Object> londonGroup = (Map<String, Object>) snapshot.getResourceGroups().get("London");
		final Map<String, Object> londonMetrics = (Map<String, Object>) londonGroup.get("metrics");
		assertEquals(215, ((Number) londonMetrics.get("hw.site.carbon_intensity")).intValue());
	}

	// -------------------------------------------------------------------------
	// updateResourceGroup
	// -------------------------------------------------------------------------

	@Test
	@SuppressWarnings("unchecked")
	void testUpdateResourceGroupReplacesMetrics() {
		final CreateResourceGroupRequestDto create = new CreateResourceGroupRequestDto();
		create.setName("Berlin");
		create.setAttributes(Map.of("site", "Berlin"));
		create.setMetrics(Map.of("hw.site.carbon_intensity", 400));
		service.createResourceGroup(create);

		final UpdateResourceGroupRequestDto update = new UpdateResourceGroupRequestDto();
		update.setName("Berlin");
		update.setAttributes(Map.of("site", "Berlin-Updated"));
		update.setMetrics(Map.of("hw.site.carbon_intensity", 350, "hw.site.pue", 1.5));

		final UiConfigSnapshotDto snapshot = service.updateResourceGroup("Berlin", update);

		final Map<String, Object> group = (Map<String, Object>) snapshot.getResourceGroups().get("Berlin");
		final Map<String, Object> attributes = (Map<String, Object>) group.get("attributes");
		assertEquals("Berlin-Updated", attributes.get("site"), "Updated attribute should be persisted");

		final Map<String, Object> metrics = (Map<String, Object>) group.get("metrics");
		assertNotNull(metrics, "Metrics should be present after update");
		assertEquals(
			350,
			((Number) metrics.get("hw.site.carbon_intensity")).intValue(),
			"Carbon intensity should be updated to 350"
		);
		assertEquals(1.5, ((Number) metrics.get("hw.site.pue")).doubleValue(), 0.001, "PUE should be added by the update");
	}

	@Test
	@SuppressWarnings("unchecked")
	void testUpdateResourceGroupRenamePreservesMetrics() {
		final CreateResourceGroupRequestDto create = new CreateResourceGroupRequestDto();
		create.setName("OldName");
		create.setMetrics(Map.of("hw.site.pue", 2.0));
		service.createResourceGroup(create);

		final UpdateResourceGroupRequestDto update = new UpdateResourceGroupRequestDto();
		update.setName("NewName");
		update.setMetrics(Map.of("hw.site.pue", 2.0));

		final UiConfigSnapshotDto snapshot = service.updateResourceGroup("OldName", update);

		assertFalse(snapshot.getResourceGroups().containsKey("OldName"), "Old name should be gone after rename");
		assertTrue(snapshot.getResourceGroups().containsKey("NewName"), "New name should appear after rename");

		final Map<String, Object> group = (Map<String, Object>) snapshot.getResourceGroups().get("NewName");
		final Map<String, Object> metrics = (Map<String, Object>) group.get("metrics");
		assertEquals(
			2.0,
			((Number) metrics.get("hw.site.pue")).doubleValue(),
			0.001,
			"PUE should be preserved after rename"
		);
	}

	@Test
	void testUpdateResourceGroupNotFoundThrows404() {
		final UpdateResourceGroupRequestDto update = new UpdateResourceGroupRequestDto();
		update.setName("DoesNotExist");

		final ResponseStatusException ex = assertThrows(
			ResponseStatusException.class,
			() -> service.updateResourceGroup("DoesNotExist", update),
			"Updating a missing group should throw ResponseStatusException"
		);
		assertEquals(404, ex.getStatusCode().value(), "HTTP status should be 404 Not Found");
	}

	@Test
	void testUpdateResourceGroupNameConflictThrows409() {
		final CreateResourceGroupRequestDto alpha = new CreateResourceGroupRequestDto();
		alpha.setName("Alpha");
		service.createResourceGroup(alpha);

		final CreateResourceGroupRequestDto beta = new CreateResourceGroupRequestDto();
		beta.setName("Beta");
		service.createResourceGroup(beta);

		// Try to rename Beta → Alpha (conflict)
		final UpdateResourceGroupRequestDto update = new UpdateResourceGroupRequestDto();
		update.setName("Alpha");

		final ResponseStatusException ex = assertThrows(
			ResponseStatusException.class,
			() -> service.updateResourceGroup("Beta", update),
			"Renaming to an existing group name should throw ResponseStatusException"
		);
		assertEquals(409, ex.getStatusCode().value(), "HTTP status should be 409 Conflict");
	}

	// -------------------------------------------------------------------------
	// deleteResourceGroup
	// -------------------------------------------------------------------------

	@Test
	void testDeleteResourceGroupRemovesItFromSnapshot() {
		final CreateResourceGroupRequestDto create = new CreateResourceGroupRequestDto();
		create.setName("ToDelete");
		create.setMetrics(Map.of("hw.site.pue", 1.8));
		service.createResourceGroup(create);

		final UiConfigSnapshotDto snapshot = service.deleteResourceGroup("ToDelete");

		assertFalse(
			snapshot.getResourceGroups().containsKey("ToDelete"),
			"Deleted group should not appear in the snapshot"
		);
	}

	@Test
	void testDeleteResourceGroupDoesNotAffectOtherGroups() {
		final CreateResourceGroupRequestDto keep = new CreateResourceGroupRequestDto();
		keep.setName("Keep");
		service.createResourceGroup(keep);

		final CreateResourceGroupRequestDto remove = new CreateResourceGroupRequestDto();
		remove.setName("Remove");
		service.createResourceGroup(remove);

		final UiConfigSnapshotDto snapshot = service.deleteResourceGroup("Remove");

		assertTrue(snapshot.getResourceGroups().containsKey("Keep"), "Unrelated group should survive deletion");
		assertFalse(snapshot.getResourceGroups().containsKey("Remove"), "Deleted group should be gone");
	}
}
