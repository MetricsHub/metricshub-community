package org.metricshub.agent.upgrade.download;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.agent.config.UpgradeConfig;
import org.metricshub.agent.upgrade.UpgradeException;
import org.metricshub.agent.upgrade.api.PackageOffer;

class PackageDownloaderTest {

	@TempDir
	Path tempDir;

	private HttpServer server;
	private byte[] packageContent;
	private byte[] packageSha256;
	private final PackageDownloader downloader = new PackageDownloader();
	private final AtomicInteger requestCount = new AtomicInteger();

	@BeforeEach
	void setUp() throws Exception {
		packageContent = new byte[256 * 1024];
		for (int i = 0; i < packageContent.length; i++) {
			packageContent[i] = (byte) (i % 251);
		}
		packageSha256 = MessageDigest.getInstance("SHA-256").digest(packageContent);

		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/repo/", exchange -> {
			requestCount.incrementAndGet();
			exchange.sendResponseHeaders(200, packageContent.length);
			try (OutputStream output = exchange.getResponseBody()) {
				output.write(packageContent);
			}
		});
		server.createContext("/missing/", exchange -> {
			exchange.sendResponseHeaders(404, -1);
			exchange.close();
		});
		server.start();
	}

	@AfterEach
	void tearDown() {
		server.stop(0);
	}

	private String baseUrl() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	private PackageOffer offer(final String url, final byte[] sha256) {
		return new PackageOffer("metricshub", "3.10.00", url, sha256, Map.of());
	}

	private UpgradeConfig config() {
		return UpgradeConfig.builder().downloadRetries(2).build();
	}

	@Test
	void downloadShouldStageAndVerifyThePackage() throws Exception {
		final double[] lastPercent = { -1 };
		final Path staged = downloader.download(
			offer(baseUrl() + "/repo/metricshub-3.10.00.deb", packageSha256),
			config(),
			tempDir,
			(percent, rate) -> lastPercent[0] = percent
		);

		assertEquals("metricshub-3.10.00.deb", staged.getFileName().toString());
		assertArrayEquals(packageContent, Files.readAllBytes(staged));
		assertEquals(100, lastPercent[0]);
		assertTrue(Files.notExists(staged.resolveSibling(staged.getFileName() + ".part")));
	}

	@Test
	void hashMismatchShouldFailAfterRetries() {
		final UpgradeException failure = assertThrows(UpgradeException.class, () ->
			downloader.download(
				offer(baseUrl() + "/repo/metricshub.deb", new byte[] { 9, 9, 9 }),
				config(),
				tempDir,
				(p, r) -> {}
			)
		);

		assertTrue(failure.getMessage().contains("SHA-256"));
		assertEquals(2, requestCount.get(), "The download must be retried");
	}

	@Test
	void httpErrorShouldFail() {
		assertThrows(UpgradeException.class, () ->
			downloader.download(offer(baseUrl() + "/missing/metricshub.deb", packageSha256), config(), tempDir, (p, r) -> {})
		);
	}

	@Test
	void oversizedPackageShouldBeRejected() {
		final UpgradeConfig tinyCap = UpgradeConfig.builder().maxPackageSizeBytes(1024).downloadRetries(1).build();

		final UpgradeException failure = assertThrows(UpgradeException.class, () ->
			downloader.download(offer(baseUrl() + "/repo/metricshub.deb", packageSha256), tinyCap, tempDir, (p, r) -> {})
		);

		assertTrue(failure.getMessage().contains("maximum"));
	}

	@Test
	void plainHttpShouldBeRejectedForNonLoopbackHosts() {
		assertThrows(UpgradeException.class, () ->
			PackageDownloader.validateSource(offer("http://repo.example.com/metricshub.deb", packageSha256), config())
		);
	}

	@Test
	void hostAllowlistShouldBeEnforced() {
		final UpgradeConfig allowlisted = UpgradeConfig.builder()
			.hostAllowlist(java.util.List.of("repo.metricshub.com"))
			.build();

		assertThrows(UpgradeException.class, () ->
			PackageDownloader.validateSource(offer("https://evil.example.com/metricshub.deb", packageSha256), allowlisted)
		);
		org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
			PackageDownloader.validateSource(offer("https://repo.metricshub.com/metricshub.deb", packageSha256), allowlisted)
		);
	}

	@Test
	void fileNamesShouldBeSanitized() {
		final PackageOffer offer = offer("https://repo/a%20b/pack%20age.deb", packageSha256);
		final String name = PackageDownloader.sanitizedFileName(URI.create(offer.downloadUrl()), offer);
		assertEquals("pack_age.deb", name);

		final PackageOffer noName = offer("https://repo.example.com/", packageSha256);
		final String fallback = PackageDownloader.sanitizedFileName(URI.create(noName.downloadUrl()), noName);
		assertEquals("metricshub-3.10.00", fallback);
	}
}
