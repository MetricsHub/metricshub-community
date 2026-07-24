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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.RawConnector;
import org.metricshub.engine.connector.model.RawConnectorStore;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.identity.ConnectionType;
import org.metricshub.engine.connector.model.identity.ConnectorIdentity;
import org.metricshub.engine.connector.model.identity.Detection;
import org.metricshub.engine.connector.model.monitor.task.source.CommandLineSource;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.extension.oscommand.OsCommandExtension;
import org.metricshub.web.dto.uiconfig.UiConnectorSummaryDto;

class UiConnectorCompatibilityServiceTest {

	private static final String LINUX_CONNECTOR_ID = "Linux";
	private static final String WINDOWS_CONNECTOR_ID = "Windows";
	private static final String TEMPLATE_CONNECTOR_ID = "Template";

	private UiConnectorCompatibilityService service;
	private ExtensionManager extensionManager;
	private ConnectorStore connectorStore;

	@BeforeEach
	void setUp() {
		service = new UiConnectorCompatibilityService();
		extensionManager = ExtensionManager.builder().withProtocolExtensions(List.of(new OsCommandExtension())).build();

		final Connector linuxConnector = Connector.builder()
			.connectorIdentity(
				ConnectorIdentity.builder()
					.displayName("Linux Monitoring")
					.information("Monitor Linux servers and hardware")
					.detection(
						Detection.builder()
							.appliesTo(Set.of(DeviceKind.LINUX))
							.connectionTypes(Set.of(ConnectionType.REMOTE))
							.tags(Set.of("os"))
							.build()
					)
					.build()
			)
			.sourceTypes(Set.of(CommandLineSource.class))
			.build();

		final Connector windowsConnector = Connector.builder()
			.connectorIdentity(
				ConnectorIdentity.builder()
					.displayName("Windows Monitoring")
					.detection(
						Detection.builder()
							.appliesTo(Set.of(DeviceKind.WINDOWS))
							.connectionTypes(Set.of(ConnectionType.REMOTE))
							.build()
					)
					.build()
			)
			.sourceTypes(Set.of(CommandLineSource.class))
			.build();

		final Connector templateConnector = Connector.builder()
			.connectorIdentity(
				ConnectorIdentity.builder()
					.displayName("Template Connector")
					.detection(
						Detection.builder()
							.appliesTo(Set.of(DeviceKind.LINUX))
							.connectionTypes(Set.of(ConnectionType.REMOTE))
							.build()
					)
					.build()
			)
			.sourceTypes(Set.of(CommandLineSource.class))
			.build();

		final RawConnector rawTemplate = new RawConnector();
		rawTemplate.setByteConnector(
			"""
			connector:
			  information: Template connector description from raw YAML
			  variables:
			    instance:
			      description: Instance name
			      defaultValue: default
			""".stripIndent()
				.getBytes(StandardCharsets.UTF_8)
		);

		final RawConnectorStore rawConnectorStore = new RawConnectorStore();
		rawConnectorStore.setStore(Map.of(TEMPLATE_CONNECTOR_ID, rawTemplate));

		connectorStore = new ConnectorStore();
		connectorStore.setStore(
			Map.of(
				LINUX_CONNECTOR_ID,
				linuxConnector,
				WINDOWS_CONNECTOR_ID,
				windowsConnector,
				TEMPLATE_CONNECTOR_ID,
				templateConnector
			)
		);
		connectorStore.setRawConnectorStore(rawConnectorStore);
		connectorStore.addConnectorsWithVariables(List.of(TEMPLATE_CONNECTOR_ID));
	}

	@Test
	void testGetConnectorCatalogBuildsStaticMetadataAndCachesResult() {
		final List<UiConnectorSummaryDto> first = service.getConnectorCatalog(connectorStore, extensionManager);
		final List<UiConnectorSummaryDto> second = service.getConnectorCatalog(connectorStore, extensionManager);

		assertEquals(3, first.size(), "Catalog should contain all connectors");
		assertSame(first, second, "Second call should return cached catalog");

		final UiConnectorSummaryDto linux = first
			.stream()
			.filter(item -> LINUX_CONNECTOR_ID.equals(item.getId()))
			.findFirst()
			.orElseThrow();
		assertEquals("Linux Monitoring", linux.getDisplayName());
		assertEquals("Monitor Linux servers and hardware", linux.getInformation());
		assertEquals(List.of("linux"), linux.getAppliesToHostTypes());
		assertTrue(linux.getRequiredProtocols().contains("ssh"));
		assertFalse(linux.isCompatible(), "Static catalog should not pre-compute compatibility");

		final UiConnectorSummaryDto template = first
			.stream()
			.filter(item -> TEMPLATE_CONNECTOR_ID.equals(item.getId()))
			.findFirst()
			.orElseThrow();
		assertTrue(template.isHasVariables());
		assertEquals(TEMPLATE_CONNECTOR_ID, template.getUsesTemplateId());
		assertEquals("Template connector description from raw YAML", template.getInformation());
		assertEquals(1, template.getVariables().size());
		assertEquals("instance", template.getVariables().get(0).getName());
		assertEquals("Instance name", template.getVariables().get(0).getDescription());
	}

	@Test
	void testGetConnectorCatalogFlagsPatchedConnectors() {
		connectorStore.getStore().get(WINDOWS_CONNECTOR_ID).setPatched(true);

		final List<UiConnectorSummaryDto> catalog = service.getConnectorCatalog(connectorStore, extensionManager);

		final UiConnectorSummaryDto windows = catalog
			.stream()
			.filter(item -> WINDOWS_CONNECTOR_ID.equals(item.getId()))
			.findFirst()
			.orElseThrow();
		assertTrue(windows.isPatched(), "Connector from the patch directory should be flagged as patched");

		final UiConnectorSummaryDto linux = catalog
			.stream()
			.filter(item -> LINUX_CONNECTOR_ID.equals(item.getId()))
			.findFirst()
			.orElseThrow();
		assertFalse(linux.isPatched(), "Built-in connector should not be flagged as patched");
	}

	@Test
	void testListConnectorsReturnsOnlyCompatibleEntriesByDefault() {
		final List<UiConnectorSummaryDto> compatible = service.listConnectors(
			"linux",
			List.of("ssh"),
			false,
			connectorStore,
			extensionManager
		);

		assertEquals(2, compatible.size(), "Linux + template connectors should be compatible with ssh/linux");
		assertTrue(compatible.stream().allMatch(UiConnectorSummaryDto::isCompatible));
		assertTrue(compatible.stream().anyMatch(item -> LINUX_CONNECTOR_ID.equals(item.getId())));
		assertTrue(compatible.stream().anyMatch(item -> TEMPLATE_CONNECTOR_ID.equals(item.getId())));
	}

	@Test
	void testListConnectorsIncludeAllAnnotatesIncompatibleConnectors() {
		final List<UiConnectorSummaryDto> all = service.listConnectors(
			"linux",
			List.of("ssh"),
			true,
			connectorStore,
			extensionManager
		);

		assertEquals(3, all.size(), "Include-all should return every connector");

		final UiConnectorSummaryDto windows = all
			.stream()
			.filter(item -> WINDOWS_CONNECTOR_ID.equals(item.getId()))
			.findFirst()
			.orElseThrow();
		assertFalse(windows.isCompatible());
		assertFalse(windows.getIncompatibilityReasons().isEmpty());
	}

	@Test
	void testListConnectorsRejectsInvalidHostType() {
		final List<UiConnectorSummaryDto> all = service.listConnectors(
			"not-a-host-type",
			List.of("ssh"),
			true,
			connectorStore,
			extensionManager
		);

		assertEquals(3, all.size());
		assertTrue(all.stream().noneMatch(UiConnectorSummaryDto::isCompatible));
		assertTrue(
			all
				.stream()
				.flatMap(item -> item.getIncompatibilityReasons().stream())
				.anyMatch(reason -> reason.contains("Invalid or missing host.type"))
		);
	}

	@Test
	void testListConnectorsRequiresConfiguredProtocols() {
		final List<UiConnectorSummaryDto> all = service.listConnectors(
			"linux",
			List.of(),
			true,
			connectorStore,
			extensionManager
		);

		assertEquals(3, all.size());
		assertTrue(all.stream().noneMatch(UiConnectorSummaryDto::isCompatible));
		assertTrue(
			all
				.stream()
				.flatMap(item -> item.getIncompatibilityReasons().stream())
				.anyMatch(reason -> reason.contains("Configure at least one protocol"))
		);
	}

	@Test
	void testListConnectorsReportsMissingDetectionDefinition() {
		final Connector noDetection = Connector.builder()
			.connectorIdentity(ConnectorIdentity.builder().displayName("No Detection").build())
			.build();

		final ConnectorStore store = new ConnectorStore();
		store.setStore(Map.of("NoDetection", noDetection));

		final List<UiConnectorSummaryDto> all = service.listConnectors(
			"linux",
			List.of("ssh"),
			true,
			store,
			extensionManager
		);

		assertEquals(1, all.size());
		assertFalse(all.get(0).isCompatible());
		assertTrue(
			all
				.get(0)
				.getIncompatibilityReasons()
				.stream()
				.anyMatch(r -> r.contains("no detection definition"))
		);
	}

	@Test
	void testListConnectorsReportsConnectionTypeMismatch() {
		final Connector localOnly = Connector.builder()
			.connectorIdentity(
				ConnectorIdentity.builder()
					.displayName("Local Only")
					.detection(
						Detection.builder()
							.appliesTo(Set.of(DeviceKind.LINUX))
							.connectionTypes(Set.of(ConnectionType.LOCAL))
							.build()
					)
					.build()
			)
			.sourceTypes(Set.of(CommandLineSource.class))
			.build();

		final ConnectorStore store = new ConnectorStore();
		store.setStore(Map.of("LocalOnly", localOnly));

		final List<UiConnectorSummaryDto> all = service.listConnectors(
			"linux",
			List.of("ssh"),
			true,
			store,
			extensionManager
		);

		assertEquals(1, all.size());
		assertFalse(all.get(0).isCompatible());
		assertTrue(
			all
				.get(0)
				.getIncompatibilityReasons()
				.stream()
				.anyMatch(r -> r.contains("remote connection"))
		);
	}

	@Test
	void testGetConnectorCatalogIgnoresInvalidVariableYaml() {
		final RawConnector invalidTemplate = new RawConnector();
		invalidTemplate.setByteConnector("not: valid: yaml: [[[".getBytes(StandardCharsets.UTF_8));

		final RawConnectorStore rawConnectorStore = new RawConnectorStore();
		rawConnectorStore.setStore(Map.of("BrokenTemplate", invalidTemplate));

		final Connector templateConnector = Connector.builder()
			.connectorIdentity(
				ConnectorIdentity.builder()
					.displayName("Broken Template")
					.detection(
						Detection.builder()
							.appliesTo(Set.of(DeviceKind.LINUX))
							.connectionTypes(Set.of(ConnectionType.REMOTE))
							.build()
					)
					.build()
			)
			.build();

		final ConnectorStore store = new ConnectorStore();
		store.setStore(Map.of("BrokenTemplate", templateConnector));
		store.setRawConnectorStore(rawConnectorStore);
		store.addConnectorsWithVariables(List.of("BrokenTemplate"));

		final UiConnectorSummaryDto summary = service
			.getConnectorCatalog(store, extensionManager)
			.stream()
			.filter(item -> "BrokenTemplate".equals(item.getId()))
			.findFirst()
			.orElseThrow();

		assertTrue(summary.isHasVariables());
		assertTrue(summary.getVariables().isEmpty(), "Invalid YAML should yield no variable definitions");
	}
}
