package org.metricshub.agent.opentelemetry.client;

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
import java.io.FileInputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
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

	private static final String AUTHORIZATION = "Authorization";

	private HttpClient client;

	/** The headers of each send, the current token included, instead of the static ones; {@code null} without. */
	private final Supplier<Map<String, String>> credentialHeaders;

	/** Drops the token a {@code 401} refused, given the {@code Authorization} it was sent with. */
	private final Consumer<String> invalidateCredential;

	/** The {@code Authorization} refused again right after a renewal: a later {@code 401} with it renews nothing. */
	private volatile String refusedAfterRenewal;

	/** The size of the thread pool: how many batches may wait for a token at once. */
	private final int poolSize;

	/** The batches handed to the pool and not posted yet, their token being asked. */
	private final AtomicInteger waitingForToken = new AtomicInteger();

	/**
	 * Constructor for an HTTP client using Protobuf.
	 *
	 * @param endpoint             The URL of the OpenTelemetry receiver.
	 * @param headers              The headers to be sent on OpenTelemetry requests.
	 * @param certificate          The path to the file containing trusted certificates to use when verifying a server's TLS credentials.
	 * @param timeout              The timeout for the OpenTelemetry requests.
	 * @param poolSize             The size of the thread pool used by the client.
	 * @param credentialHeaders    The headers of each send, the current token included, read at each send instead of
	 *                             {@code headers}; throws when no token is available. {@code null} for the static headers.
	 * @param invalidateCredential Drops the token a {@code 401} refused, given the {@code Authorization} it was sent
	 *                             with. Required with {@code credentialHeaders}.
	 */
	@Builder(setterPrefix = "with")
	public HttpProtobufClient(
		final String endpoint,
		final Map<String, String> headers,
		final String certificate,
		final long timeout,
		final int poolSize,
		final Supplier<Map<String, String>> credentialHeaders,
		final Consumer<String> invalidateCredential
	) {
		super(endpoint, headers, certificate, timeout, poolSize);
		this.poolSize = poolSize;
		this.credentialHeaders = credentialHeaders;
		this.invalidateCredential = invalidateCredential;
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
		// Serialize Protobuf request
		final byte[] requestBody = request.toByteArray();
		if (credentialHeaders == null) {
			post(requestBody, logContextSetter, true);
			return;
		}
		// No queue: while Central is slow to give a token, the batches beyond one per thread are dropped, never piled up
		// and flushed late
		if (waitingForToken.incrementAndGet() > poolSize) {
			waitingForToken.decrementAndGet();
			log.warn("Metrics not sent: the batches before it still wait for a token from MetricsHub Central.");
			return;
		}
		// The token may have to be asked from Central first: never on the collection's thread
		try {
			executorService.execute(() -> {
				try {
					post(requestBody, logContextSetter, true);
				} catch (RuntimeException e) {
					logContextSetter.setContext();
					log.error("Failed to send metrics: {}", e.getMessage());
				} finally {
					waitingForToken.decrementAndGet();
				}
			});
		} catch (RejectedExecutionException e) {
			waitingForToken.decrementAndGet();
			log.debug("Metrics not sent: the exporter is shut down.");
		}
	}

	/**
	 * Posts a serialized batch. With a credential, a {@code 401} drops the token it was sent with and posts the batch
	 * once more with a new one, never more.
	 *
	 * @param requestBody      the serialized batch
	 * @param logContextSetter the log context setter
	 * @param mayRenew         whether a {@code 401} may renew the token and post again
	 */
	private void post(final byte[] requestBody, final LogContextSetter logContextSetter, final boolean mayRenew) {
		final Map<String, String> sentHeaders;
		if (credentialHeaders == null) {
			sentHeaders = headers;
		} else {
			try {
				// Read at each send: a renewed token is used by the next batch
				sentHeaders = credentialHeaders.get();
			} catch (RuntimeException e) {
				// Never the token: the token provider's messages name the failure only
				logContextSetter.setContext();
				log.warn("Metrics not sent: no token from MetricsHub Central: {}", e.getMessage());
				return;
			}
		}
		try {
			final HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
				.uri(url.toURI())
				.timeout(Duration.ofSeconds(timeout))
				.header("Content-Type", "application/x-protobuf");

			// Inject headers
			sentHeaders.forEach(requestBuilder::header);

			// Build the request with the Protobuf payload
			final HttpRequest httpRequest = requestBuilder.POST(HttpRequest.BodyPublishers.ofByteArray(requestBody)).build();

			// Send request and handle response
			final long startTime = System.currentTimeMillis();

			client.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
				// Set the log context
				logContextSetter.setContext();

				// Any 2xx: VictoriaMetrics answers 200 or 204
				if (response.statusCode() / 100 == 2) {
					log.debug("Metrics sent successfully. Duration: {} ms", System.currentTimeMillis() - startTime);
				} else if (response.statusCode() == 401 && credentialHeaders != null) {
					final String authorization = sentHeaders.get(AUTHORIZATION);
					if (mayRenew && !authorization.equals(refusedAfterRenewal)) {
						log.debug("Metrics refused with 401: the token is renewed and the batch sent once more.");
						// The very token refused: a newer one, renewed meanwhile, is kept
						invalidateCredential.accept(authorization);
						post(requestBody, logContextSetter, false);
						return;
					}
					// A renewed token refused too: renewing cannot fix it, so the token shared with the Central channels
					// is no longer dropped at each batch, until the agent renews it on its own
					refusedAfterRenewal = authorization;
					// Never the body: a proxy may echo the request's headers, the token included
					log.error("Failed to send metrics. HTTP Response Code: 401, after a token renewal.");
				} else if (credentialHeaders != null) {
					// Never the body: a proxy may echo the request's headers, the token included
					log.error("Failed to send metrics. HTTP Response Code: {}.", response.statusCode());
				} else {
					log.error(
						"Failed to send metrics. HTTP Response Code: {}. Server response: {}",
						response.statusCode(),
						response.body()
					);
				}
			}).exceptionally(e -> {
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

			return HttpClient.newBuilder()
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
