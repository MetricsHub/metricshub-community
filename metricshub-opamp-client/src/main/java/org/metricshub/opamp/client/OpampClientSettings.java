package org.metricshub.opamp.client;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub OpAMP Client
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

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

/**
 * Immutable settings of the OpAMP client.
 */
@Value
@Builder(setterPrefix = "with")
public class OpampClientSettings {

	/**
	 * Default interval between two OpAMP polls.
	 */
	public static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(30);

	/**
	 * Default timeout of one OpAMP HTTP exchange.
	 */
	public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(10);

	/**
	 * Default maximum backoff delay between reconnection attempts.
	 */
	public static final Duration DEFAULT_MAX_BACKOFF = Duration.ofMinutes(10);

	/**
	 * The OpAMP server endpoint (e.g. {@code https://opamp.example.com/v1/opamp}).
	 */
	@NonNull
	URI endpoint;

	/**
	 * Headers sent with every OpAMP request (e.g. {@code Authorization}).
	 */
	@Builder.Default
	Map<String, String> headers = Map.of();

	/**
	 * Path to a PEM file containing the trusted certificate used to verify the server's TLS
	 * credentials; {@code null} to use the system trust store.
	 */
	String certificateFile;

	/**
	 * Interval between two OpAMP polls.
	 */
	@Builder.Default
	Duration pollInterval = DEFAULT_POLL_INTERVAL;

	/**
	 * Timeout of one OpAMP HTTP exchange.
	 */
	@Builder.Default
	Duration requestTimeout = DEFAULT_REQUEST_TIMEOUT;

	/**
	 * Maximum backoff delay between reconnection attempts.
	 */
	@Builder.Default
	Duration maxBackoff = DEFAULT_MAX_BACKOFF;

	/**
	 * File in which the agent {@code instance_uid} is persisted.
	 */
	@NonNull
	Path instanceUidFile;

	/**
	 * Whether the client reports the agent health ({@code ReportsHealth} capability).
	 */
	@Builder.Default
	boolean reportHealth = true;

	/**
	 * Creates the settings, storing an immutable copy of the headers so a caller-supplied mutable
	 * map can neither be mutated after construction (silently dropping authentication from later
	 * requests) nor be mutated concurrently while a request iterates it.
	 *
	 * @param endpoint        the OpAMP server endpoint
	 * @param headers         the headers sent with every request; {@code null} means none
	 * @param certificateFile the trusted certificate (PEM) path, or {@code null}
	 * @param pollInterval    the interval between two polls
	 * @param requestTimeout  the timeout of one HTTP exchange
	 * @param maxBackoff      the maximum backoff delay
	 * @param instanceUidFile the file persisting the agent instance UID
	 * @param reportHealth    whether the agent health is reported
	 */
	OpampClientSettings(
		@NonNull final URI endpoint,
		final Map<String, String> headers,
		final String certificateFile,
		final Duration pollInterval,
		final Duration requestTimeout,
		final Duration maxBackoff,
		@NonNull final Path instanceUidFile,
		final boolean reportHealth
	) {
		this.endpoint = endpoint;
		this.headers = headers == null ? Map.of() : Map.copyOf(headers);
		this.certificateFile = certificateFile;
		this.pollInterval = pollInterval;
		this.requestTimeout = requestTimeout;
		this.maxBackoff = maxBackoff;
		this.instanceUidFile = instanceUidFile;
		this.reportHealth = reportHealth;
	}
}
