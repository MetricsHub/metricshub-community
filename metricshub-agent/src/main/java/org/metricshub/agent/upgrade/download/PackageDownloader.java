package org.metricshub.agent.upgrade.download;

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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Locale;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.config.UpgradeConfig;
import org.metricshub.agent.upgrade.UpgradeException;
import org.metricshub.agent.upgrade.api.PackageOffer;

/**
 * Downloads an offered package from the repository over HTTPS, computing its SHA-256 while
 * streaming and enforcing the configured source and size restrictions. The file is written to a
 * {@code .part} file and atomically renamed once the hash matches the offer.
 */
@Slf4j
public class PackageDownloader {

	private static final int BUFFER_SIZE = 64 * 1024;
	private static final long PROGRESS_EMIT_INTERVAL_MS = 1000;
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
	private static final int MAX_REDIRECTS = 5;

	/**
	 * Downloads the offered package into the given directory, retrying up to the configured
	 * number of attempts.
	 *
	 * @param offer            the package offer
	 * @param config           the upgrade configuration (limits, allowlist, trust material)
	 * @param targetDirectory  the directory receiving the staged package
	 * @param progressListener the listener receiving download progress; never {@code null}
	 * @return the path of the staged, hash-verified package file
	 * @throws UpgradeException     when the download definitively fails
	 * @throws InterruptedException when the downloading thread is interrupted
	 */
	public Path download(
		final PackageOffer offer,
		final UpgradeConfig config,
		final Path targetDirectory,
		final DownloadProgressListener progressListener
	) throws UpgradeException, InterruptedException {
		final URI uri = validateSource(offer, config);
		final Path targetFile = targetDirectory.resolve(sanitizedFileName(uri, offer));

		UpgradeException lastFailure = null;
		final int attempts = Math.max(1, config.getDownloadRetries());
		for (int attempt = 1; attempt <= attempts; attempt++) {
			try {
				downloadOnce(offer, config, uri, targetFile, progressListener);
				return targetFile;
			} catch (UpgradeException e) {
				lastFailure = e;
				log.warn("Package download attempt {}/{} failed: {}", attempt, attempts, e.getMessage());
			}
		}
		throw lastFailure;
	}

	/**
	 * Validates the download source: HTTPS only (plain HTTP is tolerated for loopback addresses,
	 * for development and testing) and an optional host allowlist.
	 *
	 * @param offer  the package offer
	 * @param config the upgrade configuration
	 * @return the validated download URI
	 * @throws UpgradeException when the source is not acceptable
	 */
	static URI validateSource(final PackageOffer offer, final UpgradeConfig config) throws UpgradeException {
		final URI uri;
		try {
			uri = URI.create(offer.downloadUrl().trim());
		} catch (Exception e) {
			throw new UpgradeException("Invalid package download URL: " + offer.downloadUrl());
		}
		final String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		final String host = uri.getHost() == null ? "" : uri.getHost();
		if (!"https".equals(scheme) && !("http".equals(scheme) && isLoopback(host))) {
			throw new UpgradeException("Package downloads require HTTPS: " + offer.downloadUrl());
		}
		if (!config.getHostAllowlist().isEmpty() && config.getHostAllowlist().stream().noneMatch(host::equalsIgnoreCase)) {
			throw new UpgradeException("Package download host is not in the configured allowlist: " + host);
		}
		return uri;
	}

	/**
	 * Indicates whether the given host is a loopback address.
	 *
	 * @param host the host name or address
	 * @return {@code true} for loopback hosts
	 */
	private static boolean isLoopback(final String host) {
		try {
			return InetAddress.getByName(host).isLoopbackAddress();
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Builds a safe file name for the staged package from the URL path.
	 *
	 * @param uri   the download URI
	 * @param offer the package offer
	 * @return the sanitized file name
	 */
	static String sanitizedFileName(final URI uri, final PackageOffer offer) {
		final String path = uri.getPath() == null ? "" : uri.getPath();
		final int lastSlash = path.lastIndexOf('/');
		String name = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
		name = name.replaceAll("[^A-Za-z0-9._+-]", "_");
		if (name.isBlank() || name.startsWith(".")) {
			name = offer.packageName() + "-" + offer.version().replaceAll("[^A-Za-z0-9._+-]", "_");
		}
		return name;
	}

	/**
	 * Performs one download attempt: streams the body through a SHA-256 digest into a
	 * {@code .part} file, enforcing the size cap, then atomically renames it once the hash
	 * matches.
	 *
	 * @param offer            the package offer
	 * @param config           the upgrade configuration
	 * @param uri              the validated download URI
	 * @param targetFile       the final staged file path
	 * @param progressListener the progress listener
	 * @throws UpgradeException     when the attempt fails
	 * @throws InterruptedException when the downloading thread is interrupted
	 */
	private void downloadOnce(
		final PackageOffer offer,
		final UpgradeConfig config,
		final URI uri,
		final Path targetFile,
		final DownloadProgressListener progressListener
	) throws UpgradeException, InterruptedException {
		final Path partFile = targetFile.resolveSibling(targetFile.getFileName() + ".part");
		try {
			Files.createDirectories(targetFile.getParent());

			final HttpClient httpClient = createHttpClient(config);
			final HttpResponse<InputStream> response = sendFollowingRedirects(httpClient, offer, config, uri);
			if (response.statusCode() != 200) {
				throw new UpgradeException("The package repository answered with HTTP status " + response.statusCode());
			}

			final long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
			if (contentLength > config.getMaxPackageSizeBytes()) {
				throw new UpgradeException(
					"The package size (" +
						contentLength +
						" bytes) exceeds the configured maximum of " +
						config.getMaxPackageSizeBytes() +
						" bytes"
				);
			}

			final byte[] actualSha256 = streamToFile(response.body(), partFile, contentLength, config, progressListener);

			if (offer.sha256() == null || offer.sha256().length == 0) {
				throw new UpgradeException("The package offer does not carry the mandatory SHA-256 content hash");
			}
			if (!MessageDigest.isEqual(offer.sha256(), actualSha256)) {
				throw new UpgradeException("The downloaded package SHA-256 does not match the offered content hash");
			}

			try {
				Files.move(partFile, targetFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(partFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			throw new UpgradeException("Package download failed: " + e.getMessage(), e);
		} finally {
			try {
				Files.deleteIfExists(partFile);
			} catch (IOException cleanupError) {
				log.debug("Cannot delete the partial download {}: {}", partFile, cleanupError.getMessage());
			}
		}
	}

	/**
	 * Sends the download request, following redirects manually so every hop is re-validated
	 * against the HTTPS requirement and the configured host allowlist (automatic redirects would
	 * allow an approved host to bounce the client to an arbitrary destination). The offer headers
	 * (e.g. repository credentials) are only sent to the originally offered host.
	 *
	 * @param httpClient the HTTP client (configured with {@code Redirect.NEVER})
	 * @param offer      the package offer
	 * @param config     the upgrade configuration
	 * @param initialUri the validated initial download URI
	 * @return the final, non-redirect response
	 * @throws UpgradeException     when a redirect hop is not acceptable or too many hops occur
	 * @throws IOException          on I/O failure
	 * @throws InterruptedException when the downloading thread is interrupted
	 */
	private static HttpResponse<InputStream> sendFollowingRedirects(
		final HttpClient httpClient,
		final PackageOffer offer,
		final UpgradeConfig config,
		final URI initialUri
	) throws UpgradeException, IOException, InterruptedException {
		final String offeredHost = initialUri.getHost();
		URI currentUri = initialUri;
		for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
			final HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
				.uri(currentUri)
				.timeout(Duration.ofSeconds(config.getDownloadTimeout()))
				.GET();
			if (currentUri.getHost() != null && currentUri.getHost().equalsIgnoreCase(offeredHost)) {
				offer.headers().forEach(requestBuilder::header);
			}

			final HttpResponse<InputStream> response = httpClient.send(
				requestBuilder.build(),
				HttpResponse.BodyHandlers.ofInputStream()
			);
			if (!isRedirect(response.statusCode())) {
				return response;
			}

			try (InputStream discarded = response.body()) {
				// Drop the redirect body
			}
			final String location = response
				.headers()
				.firstValue("Location")
				.orElseThrow(() ->
					new UpgradeException("The package repository answered a redirect without a Location header")
				);
			currentUri = validateRedirectTarget(currentUri.resolve(location), config);
			log.debug("Package download redirected to {}.", currentUri);
		}
		throw new UpgradeException("The package download exceeded " + MAX_REDIRECTS + " redirects");
	}

	/**
	 * Indicates whether the given HTTP status code is a redirect.
	 *
	 * @param statusCode the HTTP status code
	 * @return {@code true} for 301, 302, 303, 307 and 308
	 */
	private static boolean isRedirect(final int statusCode) {
		return statusCode == 301 || statusCode == 302 || statusCode == 303 || statusCode == 307 || statusCode == 308;
	}

	/**
	 * Validates a redirect target against the same restrictions as the initial download source.
	 *
	 * @param target the redirect target URI
	 * @param config the upgrade configuration
	 * @return the validated target
	 * @throws UpgradeException when the target is not acceptable
	 */
	private static URI validateRedirectTarget(final URI target, final UpgradeConfig config) throws UpgradeException {
		final String scheme = target.getScheme() == null ? "" : target.getScheme().toLowerCase(Locale.ROOT);
		final String host = target.getHost() == null ? "" : target.getHost();
		if (!"https".equals(scheme) && !("http".equals(scheme) && isLoopback(host))) {
			throw new UpgradeException("The package download was redirected to a non-HTTPS location: " + target);
		}
		if (!config.getHostAllowlist().isEmpty() && config.getHostAllowlist().stream().noneMatch(host::equalsIgnoreCase)) {
			throw new UpgradeException("The package download was redirected to a host outside the allowlist: " + host);
		}
		return target;
	}

	/**
	 * Streams the response body into the given file, updating the digest, enforcing the size cap
	 * and emitting throttled progress updates.
	 *
	 * @param body             the response body stream
	 * @param partFile         the partial download file
	 * @param contentLength    the announced content length, or -1 when unknown
	 * @param config           the upgrade configuration
	 * @param progressListener the progress listener
	 * @return the SHA-256 of the streamed content
	 * @throws IOException      on I/O failure
	 * @throws UpgradeException when the size cap is exceeded
	 */
	private static byte[] streamToFile(
		final InputStream body,
		final Path partFile,
		final long contentLength,
		final UpgradeConfig config,
		final DownloadProgressListener progressListener
	) throws IOException, UpgradeException {
		final MessageDigest digest = newSha256Digest();
		long totalBytes = 0;
		long lastEmitMs = System.currentTimeMillis();
		long lastEmitBytes = 0;
		double smoothedRate = 0;

		try (InputStream input = body; OutputStream output = Files.newOutputStream(partFile)) {
			final byte[] buffer = new byte[BUFFER_SIZE];
			int read;
			while ((read = input.read(buffer)) >= 0) {
				totalBytes += read;
				if (totalBytes > config.getMaxPackageSizeBytes()) {
					throw new UpgradeException(
						"The package exceeds the configured maximum size of " + config.getMaxPackageSizeBytes() + " bytes"
					);
				}
				digest.update(buffer, 0, read);
				output.write(buffer, 0, read);

				final long nowMs = System.currentTimeMillis();
				if (nowMs - lastEmitMs >= PROGRESS_EMIT_INTERVAL_MS) {
					final double instantRate = ((totalBytes - lastEmitBytes) * 1000.0) / (nowMs - lastEmitMs);
					smoothedRate = smoothedRate == 0 ? instantRate : 0.7 * smoothedRate + 0.3 * instantRate;
					final double percent = contentLength > 0 ? (totalBytes * 100.0) / contentLength : 0;
					progressListener.onProgress(percent, smoothedRate);
					lastEmitMs = nowMs;
					lastEmitBytes = totalBytes;
				}
			}
		}
		if (totalBytes == 0) {
			throw new UpgradeException("The downloaded package is empty");
		}
		progressListener.onProgress(100, smoothedRate);
		return digest.digest();
	}

	/**
	 * Creates the SHA-256 digest.
	 *
	 * @return the digest instance
	 */
	private static MessageDigest newSha256Digest() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (Exception e) {
			throw new IllegalStateException("SHA-256 is not available", e);
		}
	}

	/**
	 * Creates the HTTP client used for the download, trusting the configured PEM certificate when
	 * one is provided.
	 *
	 * @param config the upgrade configuration
	 * @return the HTTP client
	 * @throws UpgradeException when the trust material cannot be loaded
	 */
	private static HttpClient createHttpClient(final UpgradeConfig config) throws UpgradeException {
		try {
			// Redirects are followed manually so every hop is re-validated against the HTTPS
			// requirement and the host allowlist
			final HttpClient.Builder builder = HttpClient.newBuilder()
				.connectTimeout(CONNECT_TIMEOUT)
				.followRedirects(HttpClient.Redirect.NEVER);
			final String certificateFile = config.getTrustedCertificateFile();
			if (certificateFile != null && !certificateFile.isBlank()) {
				builder.sslContext(createSslContext(certificateFile));
			}
			return builder.build();
		} catch (Exception e) {
			throw new UpgradeException("Failed to initialize the package download HTTP client: " + e.getMessage(), e);
		}
	}

	/**
	 * Loads a custom trusted certificate (PEM) into an {@link SSLContext}.
	 *
	 * @param certificateFile the path to the PEM file
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
			keyStore.setCertificateEntry("upgrade_cert", caCertificate);
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
