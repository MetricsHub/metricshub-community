package org.metricshub.engine.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.constants.Constants.HTTP;
import static org.metricshub.engine.constants.Constants.HTTPS;
import static org.metricshub.engine.constants.Constants.INVALID_PROTOCOL;
import static org.metricshub.engine.constants.Constants.INVALID_PROTOCOL_EXCEPTION_MESSAGE;

import org.junit.jupiter.api.Test;

/**
 * Test of {@link TransportProtocols}
 */
class TransportProtocolsTest {

	@Test
	void testToString() {
		final TransportProtocols transportProtocols = TransportProtocols.HTTP;
		assertEquals("http", transportProtocols.toString());
	}

	@Test
	void testInterpretValueOf() {
		// Valid protocols are HTTP and HTTPS
		assertEquals(TransportProtocols.HTTP, TransportProtocols.interpretValueOf(HTTP));
		assertEquals(TransportProtocols.HTTPS, TransportProtocols.interpretValueOf(HTTPS));

		// Invalid protocol: throw an exception
		final Exception exception = assertThrows(
			IllegalArgumentException.class,
			() -> {
				TransportProtocols.interpretValueOf(INVALID_PROTOCOL);
			}
		);
		final String actualMessage = exception.getMessage();
		assertTrue(actualMessage.contains(INVALID_PROTOCOL_EXCEPTION_MESSAGE));
	}
}
