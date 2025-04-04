package org.metricshub.agent.opentelemetry.client;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2025 Sentry Software
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

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Abstract class defining the common properties and methods for OpenTelemetry clients.
 */
@Data
@Slf4j
public abstract class AbstractOtelClient implements IOtelClient {

	protected static final String HTTP = "http";
	protected static final String HTTPS = "https";

	protected String endpoint;
	protected Map<String, String> headers = new HashMap<>();
	protected String certificate;
	protected long timeout = 10;
	protected URL url;
	protected URI uri;
	protected ExecutorService executorService;

	/**
	 * Constructor for an OpenTelemetry client.
	 *
	 * @param endpoint    The URL of the OpenTelemetry receiver.
	 * @param headers     The headers to be sent on OpenTelemetry requests.
	 * @param certificate The path to the file containing trusted certificates to use when verifying a server's TLS credentials.
	 * @param timeout     The timeout for the OpenTelemetry requests.
	 * @param poolSize    The number of threads in the client pool.
	 */
	protected AbstractOtelClient(
		final String endpoint,
		final Map<String, String> headers,
		final String certificate,
		final long timeout,
		final int poolSize
	) {
		this.endpoint = endpoint;
		this.headers = headers;
		this.certificate = certificate;
		this.timeout = timeout;
		this.executorService = Executors.newFixedThreadPool(poolSize);
	}

	/**
	 * Validates the Endpoint's syntax using Java's URL and URI classes.
	 */
	void resolveEndpoint() {
		// if the protocol isn't included in the URL, we add https://
		if (endpoint != null && !endpoint.startsWith(HTTP)) {
			endpoint = "https://" + endpoint;
		}

		try {
			// Performing a basic validation of the URL format
			url = new URL(endpoint);
			uri = url.toURI();
		} catch (MalformedURLException e) {
			throw new IllegalStateException("Malformed endpoint: %s. Message: %s".formatted(endpoint, e.getMessage()), e);
		} catch (URISyntaxException e) {
			throw new IllegalStateException(
				"Syntax error in endpoint: %s. Message: %s".formatted(endpoint, e.getMessage()),
				e
			);
		} catch (Exception e) {
			throw new IllegalStateException(
				"Failed to resolve endpoint: %s. Message: %s".formatted(endpoint, e.getMessage()),
				e
			);
		}
	}

	/**
	 * Resolves the port number from the URL or returns a default value.
	 *
	 * @return the resolved port or default port number
	 */
	protected int resolvePort() {
		// Check if the port is explicitly specified
		final int port = url.getPort();
		if (port != -1) {
			return port; // Port found in the URL
		}

		// Default port based on the protocol
		return defaultPort();
	}

	/**
	 * Returns the default port number based on the protocol.
	 *
	 * @return the default port
	 */
	protected abstract int defaultPort();

	/**
	 * Checks if the URL is secure.
	 *
	 * @return true if the URL is secure or false otherwise.
	 */
	protected boolean isSecure() {
		return HTTPS.equals(url.getProtocol());
	}

	/**
	 * Shutdown the executor service.
	 *
	 * @throws InterruptedException if the executor service is interrupted while waiting to shutdown.
	 */
	protected void shutdownExecutor() throws InterruptedException {
		executorService.shutdown();
		// Give some time for the executor to shutdown before forcing it
		if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
			log.warn("Forcing shutdown of executor...");
			executorService.shutdownNow();
		}
	}
}
