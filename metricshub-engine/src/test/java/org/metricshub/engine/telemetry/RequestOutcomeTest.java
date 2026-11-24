package org.metricshub.engine.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.ClosedByInterruptException;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class RequestOutcomeTest {

	@Test
	void testLabels() {
		assertEquals("completed", RequestOutcome.COMPLETED.getLabel());
		assertEquals("timeout", RequestOutcome.TIMEOUT.getLabel());
		assertEquals("failed", RequestOutcome.FAILED.getLabel());
	}

	@Test
	void testTimeouts() {
		assertEquals(RequestOutcome.TIMEOUT, RequestOutcome.of(new TimeoutException()));
		assertEquals(RequestOutcome.TIMEOUT, RequestOutcome.of(new SocketTimeoutException("Read timed out")));
		assertEquals(RequestOutcome.TIMEOUT, RequestOutcome.of(new SQLTimeoutException()));
		// The engine interrupts a request when a deadline of the strategy expires
		assertEquals(RequestOutcome.TIMEOUT, RequestOutcome.of(new InterruptedException()));
		assertEquals(RequestOutcome.TIMEOUT, RequestOutcome.of(new ClosedByInterruptException()));
	}

	@Test
	void testWrappedTimeouts() {
		assertEquals(
			RequestOutcome.TIMEOUT,
			RequestOutcome.of(new ExecutionException(new IOException("wrapper", new SocketTimeoutException())))
		);
		assertEquals(
			RequestOutcome.TIMEOUT,
			RequestOutcome.of(new SQLException("Error executing query", new SQLTimeoutException()))
		);
		assertEquals(RequestOutcome.TIMEOUT, RequestOutcome.of(new RuntimeException(new TimeoutException())));
	}

	@Test
	void testFailures() {
		assertEquals(RequestOutcome.FAILED, RequestOutcome.of(new ConnectException("Connection refused")));
		assertEquals(RequestOutcome.FAILED, RequestOutcome.of(new UnknownHostException("nohost")));
		assertEquals(RequestOutcome.FAILED, RequestOutcome.of(new SQLException("Authentication failed", "28000")));
		assertEquals(RequestOutcome.FAILED, RequestOutcome.of(new ExecutionException(new IOException("reset"))));
		// The message does not matter, only the types of the exception and its causes
		assertEquals(RequestOutcome.FAILED, RequestOutcome.of(new IOException("Timed out")));
	}

	@Test
	void testCauseLoopTerminates() {
		final Exception first = new Exception("first");
		final Exception second = new Exception("second", first);
		first.initCause(second);
		assertEquals(RequestOutcome.FAILED, RequestOutcome.of(first));
	}
}
