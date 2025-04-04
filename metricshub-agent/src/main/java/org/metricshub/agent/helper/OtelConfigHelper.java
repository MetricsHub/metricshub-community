package org.metricshub.agent.helper;

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

import static org.metricshub.agent.helper.AgentConstants.DEFAULT_OTEL_CRT_FILENAME;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.config.AgentConfig;
import org.metricshub.agent.opentelemetry.OtelConfigConstants;

/**
 * Helper class providing methods related to OpenTelemetry (OTEL) configuration.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class OtelConfigHelper {

	/**
	 * Get security file path. E.g certificate or key file path
	 *
	 * @param securityFileDir         The directory of the security file
	 * @param defaultSecurityFilename The default security filename
	 * @param securityFile            Defines the security file
	 * @param otlpEndpoint            OpenTelemetry gRPC OTLP receiver endpoint
	 * @return Optional of {@link Path}
	 */
	static Optional<String> getSecurityFilePath(
		@NonNull final String securityFileDir,
		@NonNull final String defaultSecurityFilename,
		final String securityFile,
		@NonNull final String otlpEndpoint
	) {
		final Path securityFilePath;
		// No security file path? we will use the default one
		if (securityFile == null || securityFile.isBlank()) {
			securityFilePath =
				ConfigHelper.getSubPath(
					String.format(AgentConstants.FILE_PATH_FORMAT, securityFileDir, defaultSecurityFilename)
				);
		} else {
			securityFilePath = Path.of(securityFile);
		}

		// No security for HTTP
		if (otlpEndpoint.toLowerCase().startsWith("http://")) {
			log.debug(
				"There is no Otel security file to load for the OTLP exporter[endpoint: {}]. The security file {} is loaded only for https connections.",
				otlpEndpoint,
				securityFilePath
			);
			return Optional.empty();
		}

		// No file? we cannot proceed any more...
		if (!Files.exists(securityFilePath)) {
			log.debug("There is no Otel security file to load. Expected path: {}.", securityFilePath);
			return Optional.empty();
		}

		return Optional.of(securityFilePath.toAbsolutePath().toString());
	}

	/**
	 * Build OpenTelemetry configuration properties from the given agent configuration
	 *
	 * @param agentConfig The agent's configuration where the exporter's configuration can be overridden
	 * @return Map of key-value pair used to configure the OpenTelemetry Java exporter
	 */
	public static Map<String, String> buildOtelConfiguration(final AgentConfig agentConfig) {
		final Map<String, String> properties = new HashMap<>();

		properties.putAll(agentConfig.getOtelConfig());

		// Set the exporter pool size to the job pool size to prevent delays caused by
		// network latency and waiting for responses.
		// By doing this we ensure that if the user has increased a job pool size, the exporter
		// will automatically increase its pool size to handle the increased simultaneous jobs.
		if (properties.get(OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_POOL_SIZE) == null) {
			properties.put(
				OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_POOL_SIZE,
				String.valueOf(agentConfig.getJobPoolSize())
			);
		}

		// Certificate file path for metrics
		populateCertificate(
			properties,
			OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_ENDPOINT,
			OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_CERTIFICATE
		);

		return properties;
	}

	/**
	 * Populates the OTLP certificate property in the given properties map based on the OTLP endpoint property.
	 *
	 * This method checks if the OTLP endpoint is specified in the properties map. If the endpoint is provided,
	 * it attempts to obtain the trusted certificates file path using the getSecurityFilePath method and updates
	 * the OTLP certificate property in the properties map accordingly.
	 *
	 * @param properties                 The map containing key-value pairs of properties.
	 * @param otlpEndpointPropertyKey    The key representing the OTLP endpoint property in the properties map.
	 * @param otlpCertificatePropertyKey The key representing the OTLP certificate property in the properties map.
	 */
	private static void populateCertificate(
		final Map<String, String> properties,
		final String otlpEndpointPropertyKey,
		final String otlpCertificatePropertyKey
	) {
		final String otlpEndpoint = properties.get(otlpEndpointPropertyKey);
		if (otlpEndpoint != null) {
			getSecurityFilePath(
				AgentConstants.SECURITY_DIRECTORY_NAME,
				DEFAULT_OTEL_CRT_FILENAME,
				properties.get(otlpCertificatePropertyKey),
				otlpEndpoint
			)
				.ifPresent(trustedCertificatesFile -> properties.put(otlpCertificatePropertyKey, trustedCertificatesFile));
		}
	}
}
