package org.metricshub.opamp.client.http;

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

import java.io.FileInputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.opamp.client.OpampClientSettings;

/**
 * OpAMP plain-HTTP transport: POSTs the serialized {@code AgentToServer} message to the
 * configured endpoint with {@code Content-Type: application/x-protobuf} using the JDK
 * {@link HttpClient}, and returns the raw {@code ServerToAgent} response. Supports custom trusted
 * certificates (PEM) and arbitrary request headers (e.g. {@code Authorization}).
 */
@Slf4j
public class OpampHttpTransport implements OpampTransport {

	private static final String CONTENT_TYPE_PROTOBUF = "application/x-protobuf";

	private final OpampClientSettings settings;
	private final HttpClient httpClient;

	/**
	 * Creates the transport from the client settings.
	 *
	 * @param settings the OpAMP client settings
	 */
	public OpampHttpTransport(final OpampClientSettings settings) {
		this.settings = settings;
		this.httpClient = createHttpClient(settings);
	}

	@Override
	public TransportResponse send(final byte[] agentToServerBytes) throws IOException, InterruptedException {
		final HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
			.uri(settings.getEndpoint())
			.timeout(settings.getRequestTimeout())
			.header("Content-Type", CONTENT_TYPE_PROTOBUF);

		settings.getHeaders().forEach(requestBuilder::header);

		final HttpRequest httpRequest = requestBuilder
			.POST(HttpRequest.BodyPublishers.ofByteArray(agentToServerBytes))
			.build();

		final HttpResponse<byte[]> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());

		return new TransportResponse(
			response.statusCode(),
			response.body(),
			response.headers().firstValue("Retry-After").flatMap(OpampHttpTransport::parseRetryAfter)
		);
	}

	@Override
	public void close() {
		httpClient.close();
	}

	/**
	 * Parses a {@code Retry-After} header value, which is either a number of seconds or an
	 * HTTP-date (RFC 1123).
	 *
	 * @param value the raw header value
	 * @return the corresponding delay, or an empty optional when the value cannot be parsed
	 */
	static Optional<Duration> parseRetryAfter(final String value) {
		final String trimmed = value.trim();
		try {
			return Optional.of(Duration.ofSeconds(Long.parseLong(trimmed)));
		} catch (NumberFormatException e) {
			// Not a number of seconds; try HTTP-date
		}
		try {
			final ZonedDateTime dateTime = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME);
			final Duration delay = Duration.between(ZonedDateTime.now(dateTime.getZone()), dateTime);
			return delay.isNegative() ? Optional.empty() : Optional.of(delay);
		} catch (DateTimeParseException e) {
			log.debug("Unparseable Retry-After header value: {}", value);
			return Optional.empty();
		}
	}

	/**
	 * Creates the underlying JDK {@link HttpClient}, trusting the configured PEM certificate when
	 * one is provided, and the system trust store otherwise.
	 *
	 * @param settings the OpAMP client settings
	 * @return the HTTP client
	 */
	private static HttpClient createHttpClient(final OpampClientSettings settings) {
		try {
			final HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(settings.getRequestTimeout());
			final String certificateFile = settings.getCertificateFile();
			if (certificateFile != null && !certificateFile.isBlank()) {
				builder.sslContext(createSslContext(certificateFile));
			}
			return builder.build();
		} catch (Exception e) {
			throw new IllegalStateException("Failed to initialize the OpAMP HTTP client: " + e.getMessage(), e);
		}
	}

	/**
	 * Loads a custom trusted certificate (PEM) into an {@link SSLContext}, following the same
	 * pattern as the MetricsHub OTLP HTTP client.
	 *
	 * @param certificateFile the path to the PEM file containing the trusted certificate
	 * @return the SSL context trusting the given certificate
	 * @throws Exception when the certificate cannot be loaded
	 */
	private static SSLContext createSslContext(final String certificateFile) throws Exception {
		final CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");

		try (FileInputStream certificateInputStream = new FileInputStream(certificateFile)) {
			final X509Certificate caCertificate = (X509Certificate) certificateFactory.generateCertificate(
				certificateInputStream
			);

			final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
			keyStore.load(null, null);
			keyStore.setCertificateEntry("opamp_cert", caCertificate);

			final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
				TrustManagerFactory.getDefaultAlgorithm()
			);
			trustManagerFactory.init(keyStore);

			final SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());

			return sslContext;
		}
	}
}
