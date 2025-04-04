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

import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import java.io.FileInputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.opentelemetry.LogContextSetter;

/**
 * OpenTelemetry client for sending metrics to an HTTP endpoint using Protobuf.
 * This client uses the Java HttpClient introduced since Java 11 to send metrics to the OpenTelemetry receiver.
 */
@Slf4j
public class HttpProtobufClient extends AbstractOtelClient {

	private HttpClient client;

	/**
	 * Constructor for an HTTP client using Protobuf.
	 *
	 * @param endpoint    The URL of the OpenTelemetry receiver.
	 * @param headers     The headers to be sent on OpenTelemetry requests.
	 * @param certificate The path to the file containing trusted certificates to use when verifying a server's TLS credentials.
	 * @param timeout     The timeout for the OpenTelemetry requests.
	 * @param poolSize    The size of the thread pool used by the client.
	 */
	@Builder(setterPrefix = "with")
	public HttpProtobufClient(
		final String endpoint,
		final Map<String, String> headers,
		final String certificate,
		final long timeout,
		final int poolSize
	) {
		super(endpoint, headers, certificate, timeout, poolSize);
		resolveEndpoint();
		this.client = createHttpClient();
	}

	/**
	 * Sends an ExportMetricsServiceRequest to the OpenTelemetry receiver.
	 * The request is serialized into a Protobuf byte array and sent as the request body.
	 * The response is logged to the console in case of an error.
	 * @param request          The {@link ExportMetricsServiceRequest} instance to send.
	 * @param logContextSetter The {@link LogContextSetter} to use for asynchronous logging.
	 */
	@Override
	public void send(final ExportMetricsServiceRequest request, final LogContextSetter logContextSetter) {
		try {
			final byte[] requestBody = request.toByteArray(); // Serialize Protobuf request

			final HttpRequest.Builder requestBuilder = HttpRequest
				.newBuilder()
				.uri(url.toURI())
				.timeout(Duration.ofSeconds(timeout))
				.header("Content-Type", "application/x-protobuf");

			// Inject headers
			headers.forEach(requestBuilder::header);

			// Build the request with the Protobuf payload
			final HttpRequest httpRequest = requestBuilder.POST(HttpRequest.BodyPublishers.ofByteArray(requestBody)).build();

			// Send request and handle response
			final long startTime = System.currentTimeMillis();

			client
				.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
				.thenAccept(response -> {
					// Set the log context
					logContextSetter.setContext();

					if (response.statusCode() == 200) {
						log.debug("Metrics sent successfully. Duration: {} ms", System.currentTimeMillis() - startTime);
					} else {
						log.error(
							"Failed to send metrics. HTTP Response Code: {}. Server response: {}",
							response.statusCode(),
							response.body()
						);
					}
				})
				.exceptionally(e -> {
					// Set the log context
					logContextSetter.setContext();

					log.error("Failed to send metrics: {}", e.getMessage());
					log.debug("Failed to send metrics:", e);
					return null;
				});
		} catch (Exception e) {
			throw new IllegalStateException("Failed to send metrics: " + e.getMessage(), e);
		}
	}

	/**
	 * Get the default port for HTTP/Protobuf.
	 * @return the default port number. (4318)
	 */
	@Override
	protected int defaultPort() {
		return 4318;
	}

	/**
	 * Creates an HttpClient instance with proper SSL/TLS handling.
	 * - Uses custom certificate if provided.
	 * - Falls back to system CA trust store if no certificate.
	 * - Uses plaintext if the connection is HTTP.
	 * @return the {@link HttpClient} instance.
	 */
	private HttpClient createHttpClient() {
		try {
			if (!isSecure()) {
				// Use plain HTTP (no TLS)
				return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeout)).executor(executorService).build();
			}

			final SSLContext sslContext = certificate != null && !certificate.isBlank()
				? createSslContext(certificate) // Load custom certificate
				: SSLContext.getDefault(); // Use system CA trust store

			return HttpClient
				.newBuilder()
				.sslContext(sslContext)
				.connectTimeout(Duration.ofSeconds(timeout))
				.executor(executorService)
				.build();
		} catch (Exception e) {
			throw new IllegalStateException("Failed to initialize HttpClient: " + e.getMessage(), e);
		}
	}

	/**
	 * Loads a custom certificate into an SSLContext.
	 * The certificate is loaded from the provided path.
	 * @param certPath the path to the certificate
	 *
	 * @return the {@link SSLContext} instance.
	 */
	private SSLContext createSslContext(final String certPath) throws Exception {
		final CertificateFactory cf = CertificateFactory.getInstance("X.509");

		try (FileInputStream certInputStream = new FileInputStream(certPath)) {
			final X509Certificate caCert = (X509Certificate) cf.generateCertificate(certInputStream);

			// Create a KeyStore and add the certificate
			final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
			keyStore.load(null, null);
			keyStore.setCertificateEntry("otel_cert", caCert);

			// Create TrustManagerFactory using the KeyStore
			final TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			tmf.init(keyStore);

			// Initialize SSLContext
			final SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(null, tmf.getTrustManagers(), new java.security.SecureRandom());

			return sslContext;
		}
	}

	@Override
	public void shutdown() {
		try {
			super.shutdownExecutor();
		} catch (Exception e) {
			log.error("Failed to shutdown the HTTP client. Error message: {}", e.getMessage());
			log.debug("Failed to shutdown the HTTP client:", e);
		}
	}
}
