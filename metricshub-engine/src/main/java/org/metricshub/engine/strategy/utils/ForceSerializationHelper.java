package org.metricshub.engine.strategy.utils;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2025 MetricsHub
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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.DEFAULT_LOCK_TIMEOUT;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.HOSTNAME_EXCEPTION_MESSAGE;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * The {@code ForceSerializationHelper} class provides utility methods for enforcing serialization of operations on
 * connector namespaces. It aims to acquire a lock before running an executable function, ensuring that the
 * serialization is maintained. The class is designed to have a private no-argument constructor to prevent instantiation.
 */
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
		final String hostname = telemetryManager.getHostname();

		// Clear any pending interrupt to avoid an immediate InterruptedException from tryLock(),
		// but remember it so we can restore the original state at the end of the call.
		boolean interrupted = Thread.currentThread().isInterrupted();
		if (interrupted) {
			Thread.interrupted();
		}

		boolean isLockAcquired = false;
		T result = defaultValue;
		try {
			try {
				// Try to get the lock
				isLockAcquired = forceSerializationLock.tryLock(DEFAULT_LOCK_TIMEOUT, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				interrupted = true;
				log.error(
					"Hostname {} - Interrupted exception detected when trying to acquire the force serialization lock to process {} {}. Connector: {}.",
					hostname,
					description,
					objToProcess,
					connectorId
				);

				log.debug(HOSTNAME_EXCEPTION_MESSAGE, hostname, e);

				return defaultValue;
			}

			if (isLockAcquired) {
				result = executable.get();
			} else {
				log.error(
					"Hostname {} - Could not acquire the force serialization lock to process {} {}. Connector: {}.",
					hostname,
					description,
					objToProcess,
					connectorId
				);
			}

			return result;
		} finally {
			if (isLockAcquired && forceSerializationLock.isHeldByCurrentThread()) {
				// Release the lock when the executable is terminated
				forceSerializationLock.unlock();
			}

			if (interrupted) {
				Thread.currentThread().interrupt();
			}
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
