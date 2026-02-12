package org.metricshub.web.config;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import javax.net.ssl.SSLException;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.service.EphemeralApiKeyService;
import org.metricshub.web.service.M8bControlPlaneClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * Configuration class for M8B Control Plane integration.
 * <p>
 * This configuration enables the M8B WebSocket client when the M8B
 * configuration properties are set in the application configuration.
 * </p>
 * <p>
 * To enable M8B integration, configure the following properties in metricshub.yaml:
 * </p>
 * <pre>
 * web:
 *   m8b.enabled: true
 *   m8b.url: ws://localhost:8080/ws/agent
 *   m8b.api-key: your-api-key
 *   m8b.agent-id: optional-custom-agent-id
 * </pre>
 */
@Configuration
@EnableConfigurationProperties(M8bConfigurationProperties.class)
@EnableScheduling
public class M8bConfiguration {

	/**
	 * Creates a WebClient configured to trust all SSL certificates.
	 * <p>
	 * This is necessary for proxy requests to localhost which uses a self-signed certificate.
	 * </p>
	 *
	 * @return the configured WebClient
	 * @throws SSLException if SSL configuration fails
	 */
	@Bean
	public WebClient m8bWebClient() throws SSLException {
		final var sslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();

		final var httpClient = HttpClient.create().secure(spec -> spec.sslContext(sslContext));

		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient)).build();
	}

	/**
	 * Creates the M8B Control Plane client bean.
	 * <p>
	 * The client will automatically connect to the M8B server on startup
	 * if the configuration is valid (enabled, url, and api-key are set).
	 * </p>
	 *
	 * @param config                 the M8B configuration properties
	 * @param agentContextHolder     the agent context holder
	 * @param objectMapper           the JSON object mapper
	 * @param ephemeralApiKeyService the ephemeral API key service
	 * @param m8bWebClient           the WebClient for proxy requests
	 * @param serverPort             the local server port
	 * @return the M8B control plane client
	 */
	@Bean
	public M8bControlPlaneClient m8bControlPlaneClient(
		M8bConfigurationProperties config,
		AgentContextHolder agentContextHolder,
		ObjectMapper objectMapper,
		EphemeralApiKeyService ephemeralApiKeyService,
		WebClient m8bWebClient,
		@Value("${server.port:31888}") int serverPort
	) {
		return new M8bControlPlaneClient(
			config,
			agentContextHolder,
			objectMapper,
			ephemeralApiKeyService,
			m8bWebClient,
			serverPort
		);
	}
}
