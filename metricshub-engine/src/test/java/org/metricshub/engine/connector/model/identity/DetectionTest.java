package org.metricshub.engine.connector.model.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.common.DeviceKind;

class DetectionTest {

	@Test
	void testConstructorConnectionTypes() {
		// Default connectionTypes value
		Detection detection = new Detection(
			null,
			false,
			"onLastResort",
			Collections.singleton(DeviceKind.WINDOWS),
			Collections.emptySet(),
			Collections.emptyList(),
			Collections.emptySet()
		);

		assertEquals(Set.of(ConnectionType.LOCAL, ConnectionType.REMOTE), detection.getConnectionTypes());

		// LOCAL connectionType value
		detection =
			new Detection(
				Set.of(ConnectionType.LOCAL),
				false,
				"onLastResort",
				Collections.singleton(DeviceKind.WINDOWS),
				Collections.emptySet(),
				Collections.emptyList(),
				Collections.emptySet()
			);

		assertEquals(Set.of(ConnectionType.LOCAL), detection.getConnectionTypes());

		// REMOTE connectionType value
		detection =
			new Detection(
				Set.of(ConnectionType.REMOTE),
				false,
				"onLastResort",
				Collections.singleton(DeviceKind.WINDOWS),
				Collections.emptySet(),
				Collections.emptyList(),
				Collections.emptySet()
			);

		assertEquals(Set.of(ConnectionType.REMOTE), detection.getConnectionTypes());
	}
}
