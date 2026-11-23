package org.metricshub.extension.ping;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

/**
 * A {@link PingRequestExecutor} that sends nothing: resolution, reachability and clock are scripted.
 */
class FakePingRequestExecutor extends PingRequestExecutor {

	/**
	 * Thrown by the resolution when set.
	 */
	UnknownHostException resolveError;

	/**
	 * Thrown by the reachability test when set.
	 */
	IOException pingError;

	/**
	 * The answer of the reachability test.
	 */
	boolean reachable;

	/**
	 * The time spent in the reachability test, in milliseconds.
	 */
	long elapsedMillis;

	/**
	 * The last hostname resolved.
	 */
	String resolvedHostname;

	/**
	 * The scripted clock, in nanoseconds.
	 */
	private long clock;

	@Override
	InetAddress resolve(final String hostname) throws UnknownHostException {
		resolvedHostname = hostname;
		if (resolveError != null) {
			throw resolveError;
		}
		return InetAddress.getLoopbackAddress();
	}

	@Override
	boolean isReachable(final InetAddress address, final int timeout) throws IOException {
		clock += TimeUnit.MILLISECONDS.toNanos(elapsedMillis);
		if (pingError != null) {
			throw pingError;
		}
		return reachable;
	}

	@Override
	long nanoTime() {
		return clock;
	}
}
