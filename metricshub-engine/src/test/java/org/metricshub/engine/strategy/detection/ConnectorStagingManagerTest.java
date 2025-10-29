package org.metricshub.engine.strategy.detection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.constants.Constants.LOCALHOST;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.identity.ConnectorIdentity;
import org.metricshub.engine.connector.model.identity.Detection;
import org.metricshub.engine.strategy.detection.ConnectorStagingManager.StagedConnectorIdentifiers;

class ConnectorStagingManagerTest {

	@Test
	void testStagingUseCase1() {
		// We force connector1
		final Set<String> connectorsConfig = Set.of("+connector1");

		final Connector connector1 = Connector.builder().connectorIdentity(ConnectorIdentity.builder().build()).build();

		final Connector connector2 = Connector.builder().connectorIdentity(ConnectorIdentity.builder().build()).build();

		final ConnectorStore connectorStore = new ConnectorStore();
		connectorStore.setStore(Map.of("connector1", connector1, "connector2", connector2));

		final ConnectorStagingManager connectorStagingManager = new ConnectorStagingManager(LOCALHOST);

		final StagedConnectorIdentifiers stagedConnectorIdentifiers = connectorStagingManager.stage(
			connectorStore,
			connectorsConfig
		);
		assertEquals(Collections.emptySet(), stagedConnectorIdentifiers.getAutoDetectionConnectorIds());
		assertEquals(Set.of("connector1"), stagedConnectorIdentifiers.getForcedConnectorIds());
		assertTrue(stagedConnectorIdentifiers.isForcedStaging());
		assertFalse(stagedConnectorIdentifiers.isAutoDetectionStaged());
	}

	@Test
	void testStagingUseCase2() {
		// We force connector1
		// We stage hardware connectors for the automatic detection
		final Set<String> connectorsConfig = Set.of("+connector1", "#hardware");

		final Connector connector1 = Connector.builder().connectorIdentity(ConnectorIdentity.builder().build()).build();

		final Connector hardwareConnector = Connector
			.builder()
			.connectorIdentity(
				ConnectorIdentity
					.builder()
					.detection(Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).tags(Set.of("hardware")).build())
					.build()
			)
			.build();

		final Connector storageConnector = Connector
			.builder()
			.connectorIdentity(
				ConnectorIdentity
					.builder()
					.detection(Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).tags(Set.of("storage")).build())
					.build()
			)
			.build();

		final ConnectorStore connectorStore = new ConnectorStore();
		connectorStore.setStore(
			Map.of("connector1", connector1, "hardwareConnector", hardwareConnector, "storageConnector", storageConnector)
		);

		final ConnectorStagingManager connectorStagingManager = new ConnectorStagingManager(LOCALHOST);

		final StagedConnectorIdentifiers stagedConnectorIdentifiers = connectorStagingManager.stage(
			connectorStore,
			connectorsConfig
		);
		assertEquals(Set.of("hardwareConnector"), stagedConnectorIdentifiers.getAutoDetectionConnectorIds());
		assertEquals(Set.of("connector1"), stagedConnectorIdentifiers.getForcedConnectorIds());
		assertTrue(stagedConnectorIdentifiers.isForcedStaging());
		assertTrue(stagedConnectorIdentifiers.isAutoDetectionStaged());
	}

	@Test
	void testStagingUseCase3() {
		// We force connector1
		// We exclude storageConnector1 from the automatic detection
		// We stage storage connectors for the automatic detection
		final Set<String> connectorsConfig = Set.of("+connector1", "!storageConnector1", "#storage");

		final Connector connector1 = Connector
			.builder()
			.connectorIdentity(ConnectorIdentity.builder().compiledFilename("connector1").build())
			.build();

		final Connector storageConnector1 = Connector
			.builder()
			.connectorIdentity(
				ConnectorIdentity
					.builder()
					.detection(Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).tags(Set.of("storage")).build())
					.build()
			)
			.build();

		final Connector storageConnector2 = Connector
			.builder()
			.connectorIdentity(
				ConnectorIdentity
					.builder()
					.detection(Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).tags(Set.of("storage")).build())
					.build()
			)
			.build();

		final ConnectorStore connectorStore = new ConnectorStore();
		connectorStore.setStore(
			Map.of("connector1", connector1, "storageConnector1", storageConnector1, "storageConnector2", storageConnector2)
		);

		final ConnectorStagingManager connectorStagingManager = new ConnectorStagingManager(LOCALHOST);

		final StagedConnectorIdentifiers stagedConnectorIdentifiers = connectorStagingManager.stage(
			connectorStore,
			connectorsConfig
		);
		assertEquals(Set.of("storageConnector2"), stagedConnectorIdentifiers.getAutoDetectionConnectorIds());
		assertEquals(Set.of("connector1"), stagedConnectorIdentifiers.getForcedConnectorIds());
		assertTrue(stagedConnectorIdentifiers.isForcedStaging());
		assertTrue(stagedConnectorIdentifiers.isAutoDetectionStaged());
	}

	@Test
	void testStagingUseCase4() {
		// We select connector1 to be executed by the automatic detection
		// We exclude storage connectors from the automatic detection
		// We stage hardware connectors for the automatic detection
		final Set<String> connectorsConfig = Set.of("connector1", "!#storage", "#hardware");

		final Connector connector1 = Connector
			.builder()
			.connectorIdentity(ConnectorIdentity.builder().compiledFilename("connector1").build())
			.build();

		final Connector hardwareConnector = Connector
			.builder()
			.connectorIdentity(
				ConnectorIdentity
					.builder()
					.detection(Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).tags(Set.of("hardware")).build())
					.build()
			)
			.build();

		final Connector storageConnector = Connector
			.builder()
			.connectorIdentity(
				ConnectorIdentity
					.builder()
					.detection(Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).tags(Set.of("storage")).build())
					.build()
			)
			.build();

		final ConnectorStore connectorStore = new ConnectorStore();
		connectorStore.setStore(
			Map.of("connector1", connector1, "hardwareConnector", hardwareConnector, "storageConnector", storageConnector)
		);

		final ConnectorStagingManager connectorStagingManager = new ConnectorStagingManager(LOCALHOST);

		final StagedConnectorIdentifiers stagedConnectorIdentifiers = connectorStagingManager.stage(
			connectorStore,
			connectorsConfig
		);
		assertEquals(Set.of("connector1", "hardwareConnector"), stagedConnectorIdentifiers.getAutoDetectionConnectorIds());
		assertEquals(Collections.emptySet(), stagedConnectorIdentifiers.getForcedConnectorIds());
		assertFalse(stagedConnectorIdentifiers.isForcedStaging());
		assertTrue(stagedConnectorIdentifiers.isAutoDetectionStaged());
	}

	@Test
	void testStagingUseCase5() {
		// We select connector1 to be executed by the automatic detection
		// We exclude storage connectors from the automatic detection
		// We stage hardware connectors for the automatic detection
		// The hardwareAndStorageConnector will be excluded
		final Set<String> connectorsConfig = Set.of("connector1", "#hardware", "!#storage");

		final Connector connector1 = Connector
			.builder()
			.connectorIdentity(ConnectorIdentity.builder().compiledFilename("connector1").build())
			.build();

		final Connector hardwareConnector = Connector
			.builder()
			.connectorIdentity(
				ConnectorIdentity
					.builder()
					.detection(Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).tags(Set.of("hardware")).build())
					.build()
			)
			.build();

		final Connector hardwareAndStorageConnector = Connector
			.builder()
			.connectorIdentity(
				ConnectorIdentity
					.builder()
					.detection(
						Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).tags(Set.of("hardware", "storage")).build()
					)
					.build()
			)
			.build();

		final Connector storageConnector = Connector
			.builder()
			.connectorIdentity(
				ConnectorIdentity
					.builder()
					.detection(Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).tags(Set.of("storage")).build())
					.build()
			)
			.build();

		final ConnectorStore connectorStore = new ConnectorStore();
		connectorStore.setStore(
			Map.of(
				"connector1",
				connector1,
				"hardwareConnector",
				hardwareConnector,
				"hardwareAndStorageConnector",
				hardwareAndStorageConnector,
				"storageConnector",
				storageConnector
			)
		);

		final ConnectorStagingManager connectorStagingManager = new ConnectorStagingManager();

		final StagedConnectorIdentifiers stagedConnectorIdentifiers = connectorStagingManager.stage(
			connectorStore,
			connectorsConfig
		);
		assertEquals(Set.of("connector1", "hardwareConnector"), stagedConnectorIdentifiers.getAutoDetectionConnectorIds());
		assertEquals(Collections.emptySet(), stagedConnectorIdentifiers.getForcedConnectorIds());
		assertFalse(stagedConnectorIdentifiers.isForcedStaging());
		assertTrue(stagedConnectorIdentifiers.isAutoDetectionStaged());
	}

	@Test
	void testStagingUseCase6() {
		// We select connector1 to be executed by the automatic detection
		// We stage storage connectors for the automatic detection
		// We stage hardware connectors for the automatic detection
		final Set<String> connectorsConfig = Set.of("connector1", "#hardware", "#storage");

		final Connector connector1 = Connector
			.builder()
			.connectorIdentity(ConnectorIdentity.builder().compiledFilename("connector1").build())
			.build();

		final Connector hardwareConnector = Connector
			.builder()
			.connectorIdentity(
				ConnectorIdentity
					.builder()
					.detection(Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).tags(Set.of("hardware")).build())
					.build()
			)
			.build();

		final Connector hardwareAndStorageConnector = Connector
			.builder()
			.connectorIdentity(
				ConnectorIdentity
					.builder()
					.detection(
						Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).tags(Set.of("hardware", "storage")).build()
					)
					.build()
			)
			.build();

		final Connector storageConnector = Connector
			.builder()
			.connectorIdentity(
				ConnectorIdentity
					.builder()
					.detection(Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).tags(Set.of("storage")).build())
					.build()
			)
			.build();

		final ConnectorStore connectorStore = new ConnectorStore();
		connectorStore.setStore(
			Map.of(
				"connector1",
				connector1,
				"hardwareConnector",
				hardwareConnector,
				"hardwareAndStorageConnector",
				hardwareAndStorageConnector,
				"storageConnector",
				storageConnector
			)
		);

		final ConnectorStagingManager connectorStagingManager = new ConnectorStagingManager(LOCALHOST);

		final StagedConnectorIdentifiers stagedConnectorIdentifiers = connectorStagingManager.stage(
			connectorStore,
			connectorsConfig
		);
		assertEquals(
			Set.of("connector1", "hardwareConnector", "hardwareAndStorageConnector", "storageConnector"),
			stagedConnectorIdentifiers.getAutoDetectionConnectorIds()
		);
		assertEquals(Collections.emptySet(), stagedConnectorIdentifiers.getForcedConnectorIds());
		assertFalse(stagedConnectorIdentifiers.isForcedStaging());
		assertTrue(stagedConnectorIdentifiers.isAutoDetectionStaged());
	}

	@Test
	void testStagingUseCase7() {
		// We stage all the connectors except the storage ones
		final Set<String> connectorsConfig = Set.of("!#storage");

		final Connector connector1 = Connector
			.builder()
			.connectorIdentity(ConnectorIdentity.builder().compiledFilename("connector1").build())
			.build();

		final Connector hardwareConnector = Connector
			.builder()
			.connectorIdentity(
				ConnectorIdentity
					.builder()
					.detection(Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).tags(Set.of("hardware")).build())
					.build()
			)
			.build();

		final Connector hardwareAndStorageConnector = Connector
			.builder()
			.connectorIdentity(
				ConnectorIdentity
					.builder()
					.detection(
						Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).tags(Set.of("hardware", "storage")).build()
					)
					.build()
			)
			.build();

		final Connector storageConnector = Connector
			.builder()
			.connectorIdentity(
				ConnectorIdentity
					.builder()
					.detection(Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).tags(Set.of("storage")).build())
					.build()
			)
			.build();

		final ConnectorStore connectorStore = new ConnectorStore();
		connectorStore.setStore(
			Map.of(
				"connector1",
				connector1,
				"hardwareConnector",
				hardwareConnector,
				"hardwareAndStorageConnector",
				hardwareAndStorageConnector,
				"storageConnector",
				storageConnector
			)
		);

		final ConnectorStagingManager connectorStagingManager = new ConnectorStagingManager(LOCALHOST);

		final StagedConnectorIdentifiers stagedConnectorIdentifiers = connectorStagingManager.stage(
			connectorStore,
			connectorsConfig
		);
		assertEquals(Set.of("connector1", "hardwareConnector"), stagedConnectorIdentifiers.getAutoDetectionConnectorIds());
		assertEquals(Collections.emptySet(), stagedConnectorIdentifiers.getForcedConnectorIds());
		assertFalse(stagedConnectorIdentifiers.isForcedStaging());
		assertTrue(stagedConnectorIdentifiers.isAutoDetectionStaged());
	}

	@Test
	void testStagingUseCase8() {
		// We select connector1 to be executed by the automatic detection
		// We select unknownConnector to be executed by the automatic detection but
		// it will be discarded as it doesn't belong to the connector store
		final Set<String> connectorsConfig = Set.of("connector1", "unknownConnector");

		final Connector connector1 = Connector
			.builder()
			.connectorIdentity(ConnectorIdentity.builder().compiledFilename("connector1").build())
			.build();

		final Connector hardwareConnector = Connector
			.builder()
			.connectorIdentity(
				ConnectorIdentity
					.builder()
					.detection(Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).tags(Set.of("hardware")).build())
					.build()
			)
			.build();

		final Connector hardwareAndStorageConnector = Connector
			.builder()
			.connectorIdentity(
				ConnectorIdentity
					.builder()
					.detection(
						Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).tags(Set.of("hardware", "storage")).build()
					)
					.build()
			)
			.build();

		final Connector storageConnector = Connector
			.builder()
			.connectorIdentity(
				ConnectorIdentity
					.builder()
					.detection(Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).tags(Set.of("storage")).build())
					.build()
			)
			.build();

		final ConnectorStore connectorStore = new ConnectorStore();
		connectorStore.setStore(
			Map.of(
				"connector1",
				connector1,
				"hardwareConnector",
				hardwareConnector,
				"hardwareAndStorageConnector",
				hardwareAndStorageConnector,
				"storageConnector",
				storageConnector
			)
		);

		{
			final ConnectorStagingManager connectorStagingManager = new ConnectorStagingManager(LOCALHOST);

			final StagedConnectorIdentifiers stagedConnectorIdentifiers = connectorStagingManager.stage(
				connectorStore,
				connectorsConfig
			);
			assertEquals(Set.of("connector1"), stagedConnectorIdentifiers.getAutoDetectionConnectorIds());
			assertEquals(Collections.emptySet(), stagedConnectorIdentifiers.getForcedConnectorIds());
			assertFalse(stagedConnectorIdentifiers.isForcedStaging());
			assertTrue(stagedConnectorIdentifiers.isAutoDetectionStaged());
		}
		{
			// Make sure the same result is produced using the default constructor
			final ConnectorStagingManager connectorStagingManager = new ConnectorStagingManager();

			final StagedConnectorIdentifiers stagedConnectorIdentifiers = connectorStagingManager.stage(
				connectorStore,
				connectorsConfig
			);
			assertEquals(Set.of("connector1"), stagedConnectorIdentifiers.getAutoDetectionConnectorIds());
			assertEquals(Collections.emptySet(), stagedConnectorIdentifiers.getForcedConnectorIds());
			assertFalse(stagedConnectorIdentifiers.isForcedStaging());
			assertTrue(stagedConnectorIdentifiers.isAutoDetectionStaged());
		}
	}

	@Test
	void testStagingUseCase9() {
		// We don't configure connector directives
		// The staging manager will stage all the connectors
		final Set<String> connectorsConfig = Collections.emptySet();

		final Connector connector1 = Connector
			.builder()
			.connectorIdentity(ConnectorIdentity.builder().compiledFilename("connector1").build())
			.build();

		final Connector hardwareConnector = Connector
			.builder()
			.connectorIdentity(
				ConnectorIdentity
					.builder()
					.detection(Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).tags(Set.of("hardware")).build())
					.build()
			)
			.build();

		final Connector hardwareAndStorageConnector = Connector
			.builder()
			.connectorIdentity(
				ConnectorIdentity
					.builder()
					.detection(
						Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).tags(Set.of("hardware", "storage")).build()
					)
					.build()
			)
			.build();

		final Connector storageConnector = Connector
			.builder()
			.connectorIdentity(
				ConnectorIdentity
					.builder()
					.detection(Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).tags(Set.of("storage")).build())
					.build()
			)
			.build();

		final ConnectorStore connectorStore = new ConnectorStore();
		connectorStore.setStore(
			Map.of(
				"connector1",
				connector1,
				"hardwareConnector",
				hardwareConnector,
				"hardwareAndStorageConnector",
				hardwareAndStorageConnector,
				"storageConnector",
				storageConnector
			)
		);

		final ConnectorStagingManager connectorStagingManager = new ConnectorStagingManager(LOCALHOST);

		final StagedConnectorIdentifiers stagedConnectorIdentifiers = connectorStagingManager.stage(
			connectorStore,
			connectorsConfig
		);
		assertEquals(
			Set.of("connector1", "hardwareAndStorageConnector", "hardwareConnector", "storageConnector"),
			stagedConnectorIdentifiers.getAutoDetectionConnectorIds()
		);
		assertEquals(Collections.emptySet(), stagedConnectorIdentifiers.getForcedConnectorIds());
		assertFalse(stagedConnectorIdentifiers.isForcedStaging());
		assertTrue(stagedConnectorIdentifiers.isAutoDetectionStaged());
	}

	@Test
	void testStagingUseCase10() {
		// We don't configure connector directives
		// The staging manager will stage all the connectors
		final Set<String> connectorsConfig = null;

		final Connector connector1 = Connector
			.builder()
			.connectorIdentity(ConnectorIdentity.builder().compiledFilename("connector1").build())
			.build();

		final Connector hardwareConnector = Connector
			.builder()
			.connectorIdentity(
				ConnectorIdentity
					.builder()
					.detection(Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).tags(Set.of("hardware")).build())
					.build()
			)
			.build();

		final Connector hardwareAndStorageConnector = Connector
			.builder()
			.connectorIdentity(
				ConnectorIdentity
					.builder()
					.detection(
						Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).tags(Set.of("hardware", "storage")).build()
					)
					.build()
			)
			.build();

		final Connector storageConnector = Connector
			.builder()
			.connectorIdentity(
				ConnectorIdentity
					.builder()
					.detection(Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).tags(Set.of("storage")).build())
					.build()
			)
			.build();

		final ConnectorStore connectorStore = new ConnectorStore();
		connectorStore.setStore(
			Map.of(
				"connector1",
				connector1,
				"hardwareConnector",
				hardwareConnector,
				"hardwareAndStorageConnector",
				hardwareAndStorageConnector,
				"storageConnector",
				storageConnector
			)
		);

		{
			final ConnectorStagingManager connectorStagingManager = new ConnectorStagingManager(LOCALHOST);

			final StagedConnectorIdentifiers stagedConnectorIdentifiers = connectorStagingManager.stage(
				connectorStore,
				connectorsConfig
			);
			assertEquals(
				Set.of("connector1", "hardwareAndStorageConnector", "hardwareConnector", "storageConnector"),
				stagedConnectorIdentifiers.getAutoDetectionConnectorIds()
			);
			assertEquals(Collections.emptySet(), stagedConnectorIdentifiers.getForcedConnectorIds());
			assertFalse(stagedConnectorIdentifiers.isForcedStaging());
			assertTrue(stagedConnectorIdentifiers.isAutoDetectionStaged());
		}
		{
			// Make sure the same result is reported using the default constructor
			final ConnectorStagingManager connectorStagingManager = new ConnectorStagingManager();

			final StagedConnectorIdentifiers stagedConnectorIdentifiers = connectorStagingManager.stage(
				connectorStore,
				connectorsConfig
			);
			assertEquals(
				Set.of("connector1", "hardwareAndStorageConnector", "hardwareConnector", "storageConnector"),
				stagedConnectorIdentifiers.getAutoDetectionConnectorIds()
			);
			assertEquals(Collections.emptySet(), stagedConnectorIdentifiers.getForcedConnectorIds());
			assertFalse(stagedConnectorIdentifiers.isForcedStaging());
			assertTrue(stagedConnectorIdentifiers.isAutoDetectionStaged());
		}
	}

	@Test
	void testStagingUseCase11() {
		// We stage all the connectors except hardwareAndStorageConnector
		final Set<String> connectorsConfig = Set.of("!hardwareAndStorageConnector");

		final Connector connector1 = Connector
			.builder()
			.connectorIdentity(ConnectorIdentity.builder().compiledFilename("connector1").build())
			.build();

		final Connector hardwareConnector = Connector
			.builder()
			.connectorIdentity(
				ConnectorIdentity
					.builder()
					.detection(Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).tags(Set.of("hardware")).build())
					.build()
			)
			.build();

		final Connector hardwareAndStorageConnector = Connector
			.builder()
			.connectorIdentity(
				ConnectorIdentity
					.builder()
					.detection(
						Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).tags(Set.of("hardware", "storage")).build()
					)
					.build()
			)
			.build();

		final Connector storageConnector = Connector
			.builder()
			.connectorIdentity(
				ConnectorIdentity
					.builder()
					.detection(Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).tags(Set.of("storage")).build())
					.build()
			)
			.build();

		final ConnectorStore connectorStore = new ConnectorStore();
		connectorStore.setStore(
			Map.of(
				"connector1",
				connector1,
				"hardwareConnector",
				hardwareConnector,
				"hardwareAndStorageConnector",
				hardwareAndStorageConnector,
				"storageConnector",
				storageConnector
			)
		);

		final ConnectorStagingManager connectorStagingManager = new ConnectorStagingManager(LOCALHOST);

		final StagedConnectorIdentifiers stagedConnectorIdentifiers = connectorStagingManager.stage(
			connectorStore,
			connectorsConfig
		);
		assertEquals(
			Set.of("connector1", "hardwareConnector", "storageConnector"),
			stagedConnectorIdentifiers.getAutoDetectionConnectorIds()
		);
		assertEquals(Collections.emptySet(), stagedConnectorIdentifiers.getForcedConnectorIds());
		assertFalse(stagedConnectorIdentifiers.isForcedStaging());
		assertTrue(stagedConnectorIdentifiers.isAutoDetectionStaged());
	}

	@Test
	void testStagingEdgeCases() {
		final Set<String> connectorsConfig = Set.of("+connector1");
		{
			// No connector store at all
			final ConnectorStore connectorStore = null;
			ConnectorStagingManager connectorStagingManager = new ConnectorStagingManager();

			assertThrows(
				IllegalArgumentException.class,
				() -> {
					connectorStagingManager.stage(connectorStore, connectorsConfig);
				}
			);
		}
		{
			// Null connector store
			final ConnectorStore connectorStore = new ConnectorStore();
			connectorStore.setStore(null);
			final ConnectorStagingManager connectorStagingManager = new ConnectorStagingManager(LOCALHOST);

			final StagedConnectorIdentifiers stagedConnectorIdentifiers = connectorStagingManager.stage(
				connectorStore,
				connectorsConfig
			);

			assertEquals(Collections.emptySet(), stagedConnectorIdentifiers.getAutoDetectionConnectorIds());
			assertEquals(Collections.emptySet(), stagedConnectorIdentifiers.getForcedConnectorIds());
			assertFalse(stagedConnectorIdentifiers.isForcedStaging());
			assertFalse(stagedConnectorIdentifiers.isAutoDetectionStaged());
		}
		{
			// Empty connector store
			final ConnectorStore connectorStore = new ConnectorStore();
			connectorStore.setStore(Collections.emptyMap());
			final ConnectorStagingManager connectorStagingManager = new ConnectorStagingManager();

			final StagedConnectorIdentifiers stagedConnectorIdentifiers = connectorStagingManager.stage(
				connectorStore,
				connectorsConfig
			);

			assertEquals(Collections.emptySet(), stagedConnectorIdentifiers.getAutoDetectionConnectorIds());
			assertEquals(Collections.emptySet(), stagedConnectorIdentifiers.getForcedConnectorIds());
			assertFalse(stagedConnectorIdentifiers.isForcedStaging());
			assertFalse(stagedConnectorIdentifiers.isAutoDetectionStaged());
		}
	}
}
