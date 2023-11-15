package com.sentrysoftware.metricshub.engine.strategy.utils;

import static com.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.DEFAULT_LOCK_TIMEOUT;
import static com.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.HOSTNAME_EXCEPTION_MESSAGE;

import com.sentrysoftware.metricshub.engine.strategy.detection.CriterionTestResult;
import com.sentrysoftware.metricshub.engine.strategy.source.SourceTable;
import com.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ForceSerializationHelper {

	/**
	 * Force the serialization when processing the given object, this method tries
	 * to acquire the connector namespace <em>lock</em> before running the
	 * executable, if the lock cannot be acquired or there is an exception or an
	 * interruption then the defaultValue is returned
	 *
	 * @param <T>          for example {@link CriterionTestResult} or a
	 *                     {@link SourceTable}
	 *
	 * @param executable       the supplier executable function, e.g. visiting a criterion or a source
	 * @param telemetryManager wraps the host's properties where the connector namespace holds the lock
	 * @param connectorId      the identifier of the connector (criterion, source or compute) belongs to
	 * @param objToProcess     the object to process used for debug purpose
	 * @param description      the object to process description used in the debug messages
	 * @param defaultValue     the default value to return in case of any glitch
	 * @return T instance
	 */
	public static <T> T forceSerialization(
		@NonNull Supplier<T> executable,
		@NonNull TelemetryManager telemetryManager,
		@NonNull final String connectorId,
		final Object objToProcess,
		@NonNull final String description,
		@NonNull final T defaultValue
	) {
		final ReentrantLock forceSerializationLock = getForceSerializationLock(telemetryManager, connectorId);
		final String hostname = telemetryManager.getHostConfiguration().getHostname();

		final boolean isLockAcquired;
		try {
			// Try to get the lock
			isLockAcquired = forceSerializationLock.tryLock(DEFAULT_LOCK_TIMEOUT, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			log.error(
				"Hostname {} - Interrupted exception detected when trying to acquire the force serialization lock to process {} {}. Connector: {}.",
				hostname,
				description,
				objToProcess,
				connectorId
			);

			log.debug(HOSTNAME_EXCEPTION_MESSAGE, hostname, e);

			Thread.currentThread().interrupt();

			return defaultValue;
		}

		if (isLockAcquired) {
			try {
				return executable.get();
			} finally {
				// Release the lock when the executable is terminated
				forceSerializationLock.unlock();
			}
		} else {
			log.error(
				"Hostname {} - Could not acquire the force serialization lock to process {} {}. Connector: {}.",
				hostname,
				description,
				objToProcess,
				connectorId
			);

			return defaultValue;
		}
	}

	/**
	 * Get the Connector Namespace force serialization lock
	 *
	 * @param telemetryManager Wraps the host's properties where the connector namespace holds the lock
	 * @param connectorId       the connector identifier we currently process its criteria/sources/computes
	 * @return {@link ReentrantLock} instance. never null.
	 */
	static ReentrantLock getForceSerializationLock(final TelemetryManager telemetryManager, final String connectorId) {
		return telemetryManager.getHostProperties().getConnectorNamespace(connectorId).getForceSerializationLock();
	}
}
