package org.metricshub.agent.opentelemetry;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
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

import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.config.CentralConfig;
import org.metricshub.agent.fleet.CentralTokenProvider;
import org.metricshub.agent.fleet.EnrollmentCoordinator;
import org.metricshub.agent.opentelemetry.client.GrpcClient;
import org.metricshub.agent.opentelemetry.client.HttpProtobufClient;
import org.metricshub.agent.opentelemetry.client.IOtelClient;
import org.metricshub.agent.opentelemetry.client.NoopClient;
import org.metricshub.engine.deserialization.TimeDeserializer;

/**
 * MetricsExporter is responsible for exporting metrics to the OpenTelemetry Collector backend.
 * It uses an internal {@link IOtelClient} to send the metrics to the OpenTelemetry Collector. Basically, it is a wrapper
 * around the client to provide a simple interface to export metrics through gRPC or HTTP/Protobuf.
 */
@Slf4j
@Builder(setterPrefix = "with")
public class MetricsExporter {

	@Getter
	private IOtelClient client;

	@Getter
	private boolean isAppendResourceAttributes;

	/**
	 * Exports the metrics to the OptenTelemetry Collector.
	 *
	 * @param resourceMetrics  A list of the ScopeMetrics collection from a Resource.
	 * @param logContextSetter The log context setter to use for asynchronous logging.
	 */
	public void export(final List<ResourceMetrics> resourceMetrics, final LogContextSetter logContextSetter) {
		try {
			// Simply send the metrics using the client
			client.send(
				ExportMetricsServiceRequest.newBuilder().addAllResourceMetrics(resourceMetrics).build(),
				logContextSetter
			);
		} catch (Exception e) {
			log.error("Failed to export metrics. Message: {}", e.getMessage());
			log.debug("Failed to export metrics", e);
		}
	}

	/**
	 * Builder for the MetricsExporter. It is encouraged to call <code>withConfiguration</code> to configure the
	 * exporter and then build the MetricsExporter.
	 */
	public static class MetricsExporterBuilder {

		/**
		 * Configures the MetricsExporter with the provided configuration.
		 *
		 * @param configuration The configuration as key-value pairs.
		 * @return The builder itself.
		 */
		public MetricsExporterBuilder withConfiguration(final Map<String, String> configuration) {
			return withConfiguration(configuration, null);
		}

		/**
		 * Configures the MetricsExporter with the provided configuration.
		 *
		 * @param configuration The configuration as key-value pairs.
		 * @param central       The agent's {@code central} configuration, which
		 *                      {@value OtelConfigConstants#OTEL_EXPORTER_OTLP_METRICS_AUTH}{@code : central} requires.
		 * @return The builder itself.
		 */
		public MetricsExporterBuilder withConfiguration(
			final Map<String, String> configuration,
			final CentralConfig central
		) {
			String protocol = configuration.get(OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_PROTOCOL);

			// If the protocol is not defined, fallback to gRPC
			if (protocol == null) {
				log.debug("The protocol is not defined. Fallback to gRPC protocol");
				protocol = OtelConfigConstants.GRPC;
			}

			final String endpoint = configuration.get(OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_ENDPOINT);
			final String headers = configuration.get(OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_HEADERS);
			final String certificate = configuration.get(OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_CERTIFICATE);
			final long timeout = parseTimeout(configuration.get(OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_TIMEOUT));
			final int poolSize = parsePoolSize(configuration.get(OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_POOL_SIZE));

			if (usesCentralAuth(configuration)) {
				newCentralAuthClient(central, protocol, endpoint, headers, certificate, timeout, poolSize);
				isAppendResourceAttributes = Boolean.parseBoolean(
					configuration.get(OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_APPEND_RESOURCE_ATTRIBUTES)
				);
				return this;
			}

			switch (protocol.toLowerCase().trim()) {
				case OtelConfigConstants.GRPC:
					newGrpcClient(endpoint, headers, certificate, timeout, poolSize);
					break;
				case OtelConfigConstants.HTTP_PROTOBUF:
					newHttpProtobufClient(endpoint, headers, certificate, timeout, poolSize);
					break;
				default:
					log.debug("Creating no-op client. Protocol: {}", protocol);
					newNoopClient();
					break;
			}

			isAppendResourceAttributes = Boolean.parseBoolean(
				configuration.get(OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_APPEND_RESOURCE_ATTRIBUTES)
			);
			return this;
		}

		/**
		 * Parses the timeout from the configuration. If the timeout is not defined or cannot be parsed, it falls back to
		 * the default timeout value {@value OtelConfigConstants#DEFAULT_OTLP_TIMEOUT} seconds.
		 *
		 * @param timeout The timeout as a string.
		 * @return The timeout in seconds.
		 */
		private long parseTimeout(final String timeout) {
			if (timeout != null) {
				try {
					return TimeDeserializer.interpretValueOf(timeout);
				} catch (Exception e) {
					log.error(
						"Failed to parse timeout. Error Message: {}. Fall back to default. Value {}",
						e.getMessage(),
						OtelConfigConstants.DEFAULT_OTLP_TIMEOUT
					);
					log.debug("Failed to parse timeout. Exception:", e);
				}
			}

			return Integer.parseInt(OtelConfigConstants.DEFAULT_OTLP_TIMEOUT);
		}

		/**
		 * Parses the pool size from the configuration. If the pool size is not defined or cannot be parsed, it falls back
		 * to the default pool size value {@value OtelConfigConstants#DEFAULT_OTLP_POOL_SIZE}.
		 * @param poolSize The pool size as a string.
		 * @return The pool size that represents the number of threads in parallel.
		 */
		private int parsePoolSize(final String poolSize) {
			if (poolSize != null) {
				try {
					return Integer.parseInt(poolSize);
				} catch (Exception e) {
					log.error(
						"Failed to parse pool size. Error Message: {}. Fall back to default. Value {}",
						e.getMessage(),
						OtelConfigConstants.DEFAULT_OTLP_POOL_SIZE
					);
					log.debug("Failed to parse pool size. Exception:", e);
				}
			}

			return Integer.parseInt(OtelConfigConstants.DEFAULT_OTLP_POOL_SIZE);
		}

		/**
		 * Creates the HTTP client that authenticates each send with the agent's MetricsHub Central token. A setting it
		 * cannot run with refuses the startup, naming the key to fix.
		 *
		 * @param central     The agent's {@code central} configuration, with {@code central.auth}.
		 * @param protocol    The configured protocol: {@value OtelConfigConstants#HTTP_PROTOBUF} only.
		 * @param endpoint    The endpoint, required: {@code https://}, or {@code http://} on a loopback address.
		 * @param headers     The static headers, without {@code Authorization}.
		 * @param certificate The certificate used by the client.
		 * @param timeout     The timeout in seconds.
		 * @param poolSize    The pool size of the client.
		 */
		private void newCentralAuthClient(
			final CentralConfig central,
			final String protocol,
			final String endpoint,
			final String headers,
			final String certificate,
			final long timeout,
			final int poolSize
		) {
			if (!EnrollmentCoordinator.isConfigured(central)) {
				throw new IllegalStateException(
					OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_AUTH + ": central requires central.auth (and central.enabled)."
				);
			}
			if (!OtelConfigConstants.HTTP_PROTOBUF.equals(protocol.toLowerCase().trim())) {
				throw new IllegalStateException(
					OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_AUTH +
						": central requires " +
						OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_PROTOCOL +
						": http/protobuf."
				);
			}
			if (endpoint == null || endpoint.isBlank()) {
				throw new IllegalStateException(
					OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_ENDPOINT +
						" is required with " +
						OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_AUTH +
						": central."
				);
			}
			final Map<String, String> staticHeaders = parseHeaders(headers);
			if (staticHeaders.keySet().stream().anyMatch("Authorization"::equalsIgnoreCase)) {
				throw new IllegalStateException(
					OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_AUTH +
						": central and an Authorization header in " +
						OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_HEADERS +
						" cannot both be configured: remove the Authorization header."
				);
			}
			final EnrollmentCoordinator coordinator = EnrollmentCoordinator.global();
			// Not built in a try: an endpoint it cannot use refuses the startup instead of a no-op client
			final HttpProtobufClient centralClient = HttpProtobufClient.builder()
				.withEndpoint(endpoint)
				.withHeaders(staticHeaders)
				.withCertificate(certificate)
				.withTimeout(timeout)
				.withPoolSize(poolSize)
				.withCredentialHeaders(centralCredential(coordinator, central, staticHeaders))
				.withInvalidateCredential(coordinator::invalidateToken)
				.build();
			// The token never travels in cleartext, except to this host
			if (!CentralTokenProvider.isAcceptable(centralClient.getUri())) {
				centralClient.shutdown();
				throw new IllegalStateException(
					OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_ENDPOINT +
						" must use https:// with " +
						OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_AUTH +
						": central (http:// is accepted for a loopback address only)."
				);
			}
			client = centralClient;
		}

		/**
		 * The headers of each send: the current token, never a copy, once the coordinator has applied this
		 * configuration's {@code central} settings. Until a reload is reconciled, the token is the former issuer's and
		 * nothing is sent with it.
		 *
		 * @param coordinator   the process's enrollment coordinator
		 * @param central       the {@code central} configuration the exporter was built with
		 * @param staticHeaders the static headers
		 * @return the headers supplier, which throws when no token of this configuration is available
		 */
		static Supplier<Map<String, String>> centralCredential(
			final EnrollmentCoordinator coordinator,
			final CentralConfig central,
			final Map<String, String> staticHeaders
		) {
			return () -> {
				// Read first: a reconciliation after the check moves it, and headers() then refuses
				final long generation = coordinator.tokenGeneration();
				if (!coordinator.isReadyFor(central)) {
					throw new UncheckedIOException(
						new IOException("The MetricsHub Central settings of this configuration are not applied yet.")
					);
				}
				return coordinator.headers(staticHeaders, generation);
			};
		}

		/**
		 * Creates a new NoopClient.
		 */
		void newNoopClient() {
			client = new NoopClient();
		}

		/**
		 * Creates a new HTTP client. If the client cannot be created, it falls back to a NoopClient.
		 *
		 * @param endpoint    The endpoint to connect to.
		 *                    If the endpoint is not defined, it falls back to the default: {@value OtelConfigConstants#DEFAULT_OTLP_HTTP_ENDPOINT}
		 * @param headers     The headers to send.
		 * @param certificate The certificate use by the client.
		 * @param timeout     The timeout in seconds.
		 * @param poolSize    The pool size of the client.
		 */
		void newHttpProtobufClient(
			String endpoint,
			final String headers,
			final String certificate,
			final long timeout,
			final int poolSize
		) {
			if (endpoint == null) {
				log.debug(
					"The endpoint is not defined. Fallback to default HTTP endpoint {}",
					OtelConfigConstants.DEFAULT_OTLP_HTTP_ENDPOINT
				);
				endpoint = OtelConfigConstants.DEFAULT_OTLP_HTTP_ENDPOINT;
			}

			try {
				client = HttpProtobufClient.builder()
					.withEndpoint(endpoint)
					.withHeaders(parseHeaders(headers))
					.withCertificate(certificate)
					.withTimeout(timeout)
					.withPoolSize(poolSize)
					.build();
			} catch (Exception e) {
				log.error("Failed to create HTTP client. Error Message: {}. Fall back to no-op client", e.getMessage());
				log.debug("Failed to create HTTP client. Exception:", e);
				newNoopClient();
			}
		}

		/**
		 * Creates a new gRPC client. If the client cannot be created, it falls back to a NoopClient.
		 *
		 * @param endpoint    The endpoint to connect to.
		 *                    If the endpoint is not defined, it falls back to the default: {@value OtelConfigConstants#DEFAULT_OTLP_GRPC_ENDPOINT}
		 * @param headers     The headers to send.
		 * @param certificate The certificate use by the client.
		 * @param timeout     The timeout in seconds.
		 * @param poolSize    The pool size of the client.
		 */
		void newGrpcClient(
			String endpoint,
			final String headers,
			final String certificate,
			final long timeout,
			final int poolSize
		) {
			if (endpoint == null) {
				log.debug(
					"The endpoint is not defined. Fallback to default gRPC endpoint {}",
					OtelConfigConstants.DEFAULT_OTLP_GRPC_ENDPOINT
				);
				endpoint = OtelConfigConstants.DEFAULT_OTLP_GRPC_ENDPOINT;
			}

			try {
				client = GrpcClient.builder()
					.withEndpoint(endpoint)
					.withHeaders(parseHeaders(headers))
					.withCertificate(certificate)
					.withTimeout(timeout)
					.withPoolSize(poolSize)
					.build();
			} catch (Exception e) {
				log.error("Failed to create gRPC client. Error Message: {}. Fall back to no-op client", e.getMessage());
				log.debug("Failed to create gRPC client. Exception:", e);
				newNoopClient();
			}
		}

		/**
		 * Parses the headers from the configuration.
		 *
		 * @param headers The headers as a string.
		 * @return The headers as key-value pairs.
		 */
		private Map<String, String> parseHeaders(String headers) {
			if (headers == null || headers.trim().isEmpty()) {
				return new HashMap<>();
			}

			final Map<String, String> headerMap = new HashMap<>();

			String[] pairs = headers.split("\\s*,\\s*");
			for (String pair : pairs) {
				// Trim any extraneous whitespace
				pair = pair.trim();
				if (pair.isEmpty()) {
					// skip empty
					continue;
				}

				// Split on the first '='
				int idx = pair.indexOf('=');
				if (idx == -1) {
					continue;
				}

				final String key = pair.substring(0, idx).trim();
				final String value = pair.substring(idx + 1).trim();

				if (!key.isEmpty()) {
					headerMap.put(key, value);
				}
			}

			return headerMap;
		}
	}

	/**
	 * @param configuration the OpenTelemetry configuration
	 * @return whether {@value OtelConfigConstants#OTEL_EXPORTER_OTLP_METRICS_AUTH} is {@code central}
	 * @throws IllegalStateException when it has another value
	 */
	public static boolean usesCentralAuth(final Map<String, String> configuration) {
		final String auth = configuration.get(OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_AUTH);
		if (auth == null || auth.isBlank()) {
			return false;
		}
		if (OtelConfigConstants.CENTRAL_AUTH.equalsIgnoreCase(auth.trim())) {
			return true;
		}
		throw new IllegalStateException(
			OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_AUTH + " accepts central only, or no value."
		);
	}

	/**
	 * Orders the exporter to shutdown.
	 */
	public void shutdown() {
		client.shutdown();
	}
}
