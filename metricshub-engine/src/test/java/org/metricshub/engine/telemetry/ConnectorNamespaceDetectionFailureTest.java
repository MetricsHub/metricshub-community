package org.metricshub.engine.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Tests for the consecutive detection failure tracking in {@link ConnectorNamespace}.
 */
class ConnectorNamespaceDetectionFailureTest {

	@Test
	void testIncrementDetectionFailures() {
		final ConnectorNamespace namespace = new ConnectorNamespace();
		assertEquals(0, namespace.getConsecutiveDetectionFailures());

		assertEquals(1, namespace.incrementDetectionFailures());
		assertEquals(2, namespace.incrementDetectionFailures());
		assertEquals(3, namespace.incrementDetectionFailures());
		assertEquals(3, namespace.getConsecutiveDetectionFailures());
	}

	@Test
	void testResetDetectionFailures() {
		final ConnectorNamespace namespace = new ConnectorNamespace();

		namespace.incrementDetectionFailures();
		namespace.incrementDetectionFailures();
		assertEquals(2, namespace.getConsecutiveDetectionFailures());

		namespace.resetDetectionFailures();
		assertEquals(0, namespace.getConsecutiveDetectionFailures());
	}

	@Test
	void testIncrementAfterReset() {
		final ConnectorNamespace namespace = new ConnectorNamespace();

		namespace.incrementDetectionFailures();
		namespace.incrementDetectionFailures();
		namespace.resetDetectionFailures();

		assertEquals(1, namespace.incrementDetectionFailures());
		assertEquals(1, namespace.getConsecutiveDetectionFailures());
	}
}
