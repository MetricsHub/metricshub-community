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

import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Configuration class defining properties used to configure OpenTelemetry exporter.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OtelConfigConstants {

	/**
	 * Property used to configure the transport protocol to use on OTLP metric requests.
	 * Options include grpc and http/protobuf. Default is grpc.
	 */
	public static final String OTEL_EXPORTER_OTLP_METRICS_PROTOCOL = "otel.exporter.otlp.metrics.protocol";

	/**
	 * Property used to configure the OTLP metrics endpoint to connect to. Must be a URL with a scheme of either http or https based on the use of TLS.
	 * Default is http://localhost:4317 when protocol is grpc, and http://localhost:4318/v1/metrics when protocol is http/protobuf.
	 */
	public static final String OTEL_EXPORTER_OTLP_METRICS_ENDPOINT = "otel.exporter.otlp.metrics.endpoint";

	/**
	 * Property used to configure the path to the file containing trusted certificates to use when verifying an OTLP metric server's TLS credentials.
	 * The file should contain one or more X.509 certificates in PEM format.
	 * By default the host platform's trusted root certificates are used.
	 */
	public static final String OTEL_EXPORTER_OTLP_METRICS_CERTIFICATE = "otel.exporter.otlp.metrics.certificate";

	/**
	 * Property used to configure the headers to be sent on OTLP metric requests.
	 */
	public static final String OTEL_EXPORTER_OTLP_METRICS_HEADERS = "otel.exporter.otlp.metrics.headers";

	/**
	 * Property used to configure the timeout for the OTLP metric requests.
	 */
	public static final String OTEL_EXPORTER_OTLP_METRICS_TIMEOUT = "otel.exporter.otlp.metrics.timeout";

	/**
	 * Property used to configure the exporter pool size.
	 */
	public static final String OTEL_EXPORTER_OTLP_METRICS_POOL_SIZE = "otel.exporter.otlp.metrics.pool.size";

	/**
	 * Property used to append resource attributes to each metric.
	 */
	public static final String OTEL_EXPORTER_OTLP_METRICS_APPEND_RESOURCE_ATTRIBUTES =
		"otel.exporter.otlp.metrics.append_resource_attributes";

	/**
	 * OTLP protocol: gRPC
	 */
	public static final String GRPC = "grpc";

	/**
	 * OTLP protocol: HTTP/Protobuf
	 */
	public static final String HTTP_PROTOBUF = "http/protobuf";

	/**
	 * Default OTLP Endpoint: http://localhost:4317
	 */
	public static final String DEFAULT_OTLP_GRPC_ENDPOINT = "http://localhost:4317";

	/**
	 * Default OTLP HTTP Endpoint: http://localhost:4318/v1/metrics
	 */
	public static final String DEFAULT_OTLP_HTTP_ENDPOINT = "http://localhost:4318/v1/metrics";

	/**
	 * Default OTLP Timeout: 10 seconds
	 */
	public static final String DEFAULT_OTLP_TIMEOUT = "10";

	/**
	 * Default OTLP Pool Size: 20
	 */
	public static final String DEFAULT_OTLP_POOL_SIZE = "20";

	/**
	 * Default configuration
	 */
	// @formatter:off
	public static final Map<String, String> DEFAULT_CONFIGURATION = Map.of(
		OTEL_EXPORTER_OTLP_METRICS_ENDPOINT, DEFAULT_OTLP_GRPC_ENDPOINT,
		OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, GRPC,
		OTEL_EXPORTER_OTLP_METRICS_TIMEOUT, DEFAULT_OTLP_TIMEOUT
	);
	// @formatter:on

}
