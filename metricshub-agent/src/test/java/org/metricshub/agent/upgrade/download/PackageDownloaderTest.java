package org.metricshub.agent.upgrade.download;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
	private final AtomicReference<Headers> capturedHeaders = new AtomicReference<>();

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
		server.createContext("/headers/", exchange -> {
			capturedHeaders.set(exchange.getRequestHeaders());
			exchange.sendResponseHeaders(200, packageContent.length);
			try (OutputStream output = exchange.getResponseBody()) {
				output.write(packageContent);
			}
		});
		server.createContext("/missing/", exchange -> {
			exchange.sendResponseHeaders(404, -1);
			exchange.close();
		});
		server.createContext("/redirect-same-host/", exchange -> {
			exchange.getResponseHeaders().set("Location", baseUrl() + "/repo/metricshub-redirected.deb");
			exchange.sendResponseHeaders(302, -1);
			exchange.close();
		});
		server.createContext("/redirect-other-host/", exchange -> {
			exchange.getResponseHeaders().set("Location", "https://evil.example.com/metricshub.deb");
			exchange.sendResponseHeaders(302, -1);
			exchange.close();
		});
		server.createContext("/stall/", exchange -> {
			// Send the headers and a partial body, then stall without closing (long enough for
			// the 2-second download deadline of the test to fire first)
			exchange.sendResponseHeaders(200, packageContent.length);
			exchange.getResponseBody().write(packageContent, 0, 1024);
			exchange.getResponseBody().flush();
			try {
				Thread.sleep(8_000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
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
		return offer(url, sha256, Map.of());
	}

	private PackageOffer offer(final String url, final byte[] sha256, final Map<String, String> headers) {
		return new PackageOffer("metricshub", "3.10.00", url, sha256, new byte[] { 7 }, headers);
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
			(percent, _) -> lastPercent[0] = percent
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
				(_, _) -> {}
			)
		);

		assertTrue(failure.getMessage().contains("SHA-256"));
		assertEquals(2, requestCount.get(), "The download must be retried");
	}

	@Test
	void httpErrorShouldFail() {
		assertThrows(UpgradeException.class, () ->
			downloader.download(offer(baseUrl() + "/missing/metricshub.deb", packageSha256), config(), tempDir, (_, _) -> {})
		);
	}

	@Test
	void oversizedPackageShouldBeRejected() {
		final UpgradeConfig tinyCap = UpgradeConfig.builder().maxPackageSizeBytes(1024).downloadRetries(1).build();

		final UpgradeException failure = assertThrows(UpgradeException.class, () ->
			downloader.download(offer(baseUrl() + "/repo/metricshub.deb", packageSha256), tinyCap, tempDir, (_, _) -> {})
		);

		assertTrue(failure.getMessage().contains("maximum"));
	}

	@Test
	void stalledBodyShouldBeAbortedAtTheDownloadDeadline() {
		final UpgradeConfig shortTimeout = UpgradeConfig.builder().downloadTimeout(2).downloadRetries(1).build();

		final long startedMs = System.currentTimeMillis();
		assertThrows(UpgradeException.class, () ->
			downloader.download(
				offer(baseUrl() + "/stall/metricshub.deb", packageSha256),
				shortTimeout,
				tempDir,
				(_, _) -> {}
			)
		);

		final long elapsedMs = System.currentTimeMillis() - startedMs;
		assertTrue(
			elapsedMs < 30_000,
			"The stalled download must be aborted at the deadline, but took " + elapsedMs + " ms"
		);
	}

	@Test
	void redirectWithinTheAllowedHostShouldBeFollowed() throws Exception {
		final Path staged = downloader.download(
			offer(baseUrl() + "/redirect-same-host/metricshub.deb", packageSha256),
			config(),
			tempDir,
			(_, _) -> {}
		);

		assertArrayEquals(packageContent, Files.readAllBytes(staged));
	}

	@Test
	void redirectOutsideTheAllowlistShouldBeRejected() {
		final UpgradeConfig allowlisted = UpgradeConfig.builder()
			.hostAllowlist(java.util.List.of("127.0.0.1"))
			.downloadRetries(1)
			.build();

		final UpgradeException failure = assertThrows(UpgradeException.class, () ->
			downloader.download(
				offer(baseUrl() + "/redirect-other-host/metricshub.deb", packageSha256),
				allowlisted,
				tempDir,
				(_, _) -> {}
			)
		);

		assertTrue(failure.getMessage().contains("redirected"));
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
	void configuredDownloadHeadersShouldBeSent() throws Exception {
		final UpgradeConfig withHeaders = UpgradeConfig.builder()
			.downloadHeaders(Map.of("127.0.0.1", Map.of("Authorization", "Basic cmVhZGVyOnNlY3JldA==")))
			.downloadRetries(1)
			.build();

		downloader.download(
			offer(baseUrl() + "/headers/metricshub.deb", packageSha256),
			withHeaders,
			tempDir,
			(_, _) -> {}
		);

		assertEquals(List.of("Basic cmVhZGVyOnNlY3JldA=="), capturedHeaders.get().get("Authorization"));
	}

	@Test
	void configuredHeadersShouldNeverReachAHostTheOperatorDidNotName() throws Exception {
		// The offer URL is chosen by the OpAMP server. If the configured credentials followed
		// whatever host an offer names, a compromised server could offer
		// https://attacker.example/... and harvest them on the very first request — the exact
		// exfiltration this feature must not enable. Here the credentials are bound to
		// 'localhost' and the offer points at '127.0.0.1': same loopback server, different host
		// name, so the download must succeed WITHOUT the credentials.
		final UpgradeConfig boundElsewhere = UpgradeConfig.builder()
			.downloadHeaders(Map.of("localhost", Map.of("Authorization", "Basic cmVhZGVyOnNlY3JldA==")))
			.downloadRetries(1)
			.build();

		final Path staged = downloader.download(
			offer(baseUrl() + "/headers/metricshub.deb", packageSha256),
			boundElsewhere,
			tempDir,
			(_, _) -> {}
		);

		assertArrayEquals(packageContent, Files.readAllBytes(staged));
		assertTrue(
			capturedHeaders.get().get("Authorization") == null,
			"credentials bound to another host must not be attached to the offered one"
		);
	}

	@Test
	void configuredHostShouldMatchCaseInsensitively() {
		final UpgradeConfig config = UpgradeConfig.builder()
			.downloadHeaders(Map.of("Nexus.Example.COM", Map.of("Authorization", "local")))
			.build();
		final PackageOffer offered = offer("https://nexus.example.com/metricshub.deb", packageSha256);

		final Map<String, String> merged = PackageDownloader.resolveRequestHeaders(offered, config, "nexus.example.com");

		assertEquals("local", merged.get("Authorization"));
	}

	@Test
	void configuredHeadersShouldOverrideOfferHeaders() throws Exception {
		final UpgradeConfig withHeaders = UpgradeConfig.builder()
			.downloadHeaders(Map.of("127.0.0.1", Map.of("X-Repo-Token", "from-config")))
			.downloadRetries(1)
			.build();

		// The offer spells the name in a different case: HTTP header names are
		// case-insensitive, so this is the same header, not two.
		downloader.download(
			offer(baseUrl() + "/headers/metricshub.deb", packageSha256, Map.of("x-repo-token", "from-offer")),
			withHeaders,
			tempDir,
			(_, _) -> {}
		);

		// Exactly one line: two sources of the same name must be merged before the request is
		// built (HttpRequest.Builder::header appends), and the local configuration wins.
		assertEquals(List.of("from-config"), capturedHeaders.get().get("X-Repo-Token"));
	}

	@Test
	void noConfiguredHeadersMeansNoneAreSent() throws Exception {
		downloader.download(offer(baseUrl() + "/headers/metricshub.deb", packageSha256), config(), tempDir, (_, _) -> {});

		assertTrue(capturedHeaders.get().get("Authorization") == null, "no Authorization header may be invented");
	}

	@Test
	void headersShouldNotFollowARedirectToAnotherHost() throws Exception {
		// A second loopback identity: the server binds the wildcard address so both 127.0.0.1
		// (the offered host) and localhost (the redirect target) reach it, while the downloader
		// sees two different host names — the credentials must stay on the first.
		final HttpServer wildcard = HttpServer.create(new InetSocketAddress(0), 0);
		final AtomicReference<Headers> secondHopHeaders = new AtomicReference<>();
		try {
			final int port = wildcard.getAddress().getPort();
			wildcard.createContext("/hop/", exchange -> {
				exchange.getResponseHeaders().set("Location", "http://localhost:" + port + "/target/metricshub.deb");
				exchange.sendResponseHeaders(302, -1);
				exchange.close();
			});
			wildcard.createContext("/target/", exchange -> {
				secondHopHeaders.set(exchange.getRequestHeaders());
				exchange.sendResponseHeaders(200, packageContent.length);
				try (OutputStream output = exchange.getResponseBody()) {
					output.write(packageContent);
				}
			});
			wildcard.start();

			final UpgradeConfig withHeaders = UpgradeConfig.builder()
				.downloadHeaders(Map.of("127.0.0.1", Map.of("Authorization", "Basic cmVhZGVyOnNlY3JldA==")))
				.downloadRetries(1)
				.build();
			final Path staged = downloader.download(
				offer("http://127.0.0.1:" + port + "/hop/metricshub.deb", packageSha256),
				withHeaders,
				tempDir,
				(_, _) -> {}
			);

			assertArrayEquals(packageContent, Files.readAllBytes(staged));
			assertTrue(
				secondHopHeaders.get().get("Authorization") == null,
				"the configured credentials must not follow a redirect to another host"
			);
		} finally {
			wildcard.stop(0);
		}
	}

	@Test
	void requestHeadersShouldMergeWithLocalConfigurationWinning() {
		final UpgradeConfig config = UpgradeConfig.builder()
			.downloadHeaders(Map.of("repo.example.com", Map.of("X-Repo-Token", "local", "X-Extra", "kept")))
			.build();
		final PackageOffer offered = offer(
			"https://repo.example.com/metricshub.deb",
			packageSha256,
			Map.of("X-Repo-Token", "offered", "X-Offer-Only", "kept-too")
		);

		final Map<String, String> merged = PackageDownloader.resolveRequestHeaders(offered, config, "repo.example.com");

		assertEquals(3, merged.size());
		assertEquals("local", merged.get("X-Repo-Token"));
		assertEquals("kept", merged.get("X-Extra"));
		assertEquals("kept-too", merged.get("X-Offer-Only"));
	}

	@Test
	void headersWithoutAValueShouldBeIgnoredNotFatal() throws Exception {
		// downloadHeaders entries without a value (Authorization:) deserialize to nulls the
		// whole-map @JsonSetter(nulls = SKIP) does not catch; HttpRequest.Builder.header
		// rejects nulls, so an unguarded merge would fail every download before a request is
		// made — and a host entry without a block (a bare host name) is null too.
		final java.util.Map<String, String> withNullValue = new java.util.HashMap<>();
		withNullValue.put("Authorization", null);
		withNullValue.put("X-Repo-Token", "kept");
		final java.util.Map<String, Map<String, String>> byHost = new java.util.HashMap<>();
		byHost.put("127.0.0.1", withNullValue);
		byHost.put("empty-block.example.com", null);
		final UpgradeConfig config = UpgradeConfig.builder().downloadHeaders(byHost).downloadRetries(1).build();

		final Path staged = downloader.download(
			offer(baseUrl() + "/headers/metricshub.deb", packageSha256),
			config,
			tempDir,
			(_, _) -> {}
		);

		assertArrayEquals(packageContent, Files.readAllBytes(staged));
		assertTrue(capturedHeaders.get().get("Authorization") == null, "the valueless header must be dropped");
		assertEquals(List.of("kept"), capturedHeaders.get().get("X-Repo-Token"));
	}

	@Test
	void headersShouldNotFollowARedirectToAnotherPort() throws Exception {
		// Same host name, different port: a different origin, likely a different service on
		// the machine — it must not receive the credentials, though the download proceeds.
		final HttpServer otherPort = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		final AtomicReference<Headers> otherPortHeaders = new AtomicReference<>();
		try {
			otherPort.createContext("/target/", exchange -> {
				otherPortHeaders.set(exchange.getRequestHeaders());
				exchange.sendResponseHeaders(200, packageContent.length);
				try (OutputStream output = exchange.getResponseBody()) {
					output.write(packageContent);
				}
			});
			otherPort.start();

			final String crossPortTarget = "http://127.0.0.1:" + otherPort.getAddress().getPort() + "/target/metricshub.deb";
			server.createContext("/redirect-other-port/", exchange -> {
				exchange.getResponseHeaders().set("Location", crossPortTarget);
				exchange.sendResponseHeaders(302, -1);
				exchange.close();
			});

			final UpgradeConfig withHeaders = UpgradeConfig.builder()
				.downloadHeaders(Map.of("127.0.0.1", Map.of("Authorization", "Basic cmVhZGVyOnNlY3JldA==")))
				.downloadRetries(1)
				.build();
			final Path staged = downloader.download(
				offer(baseUrl() + "/redirect-other-port/metricshub.deb", packageSha256),
				withHeaders,
				tempDir,
				(_, _) -> {}
			);

			assertArrayEquals(packageContent, Files.readAllBytes(staged));
			assertTrue(
				otherPortHeaders.get().get("Authorization") == null,
				"a different port is a different origin: the credentials must not follow"
			);
		} finally {
			otherPort.stop(0);
		}
	}

	@Test
	void headersShouldFollowARedirectWithinTheSameOrigin() throws Exception {
		// A same-origin redirect (same scheme, host and port) legitimately keeps the
		// credentials: repositories redirect within themselves and still require auth.
		server.createContext("/redirect-to-headers/", exchange -> {
			exchange.getResponseHeaders().set("Location", baseUrl() + "/headers/metricshub.deb");
			exchange.sendResponseHeaders(302, -1);
			exchange.close();
		});
		final UpgradeConfig withHeaders = UpgradeConfig.builder()
			.downloadHeaders(Map.of("127.0.0.1", Map.of("Authorization", "Basic cmVhZGVyOnNlY3JldA==")))
			.downloadRetries(1)
			.build();

		downloader.download(
			offer(baseUrl() + "/redirect-to-headers/metricshub.deb", packageSha256),
			withHeaders,
			tempDir,
			(_, _) -> {}
		);

		assertEquals(List.of("Basic cmVhZGVyOnNlY3JldA=="), capturedHeaders.get().get("Authorization"));
	}

	@Test
	void sameOriginShouldCompareSchemeHostAndEffectivePort() {
		assertTrue(
			PackageDownloader.sameOrigin(
				URI.create("https://repo.example.com/pkg"),
				URI.create("https://REPO.example.com:443/other")
			),
			"the scheme's default port counts as its explicit spelling"
		);
		assertTrue(
			!PackageDownloader.sameOrigin(
				URI.create("https://repo.example.com/pkg"),
				URI.create("https://repo.example.com:8443/capture")
			),
			"a different port is a different origin"
		);
		assertTrue(
			!PackageDownloader.sameOrigin(
				URI.create("https://repo.example.com/pkg"),
				URI.create("http://repo.example.com/pkg")
			),
			"a different scheme is a different origin"
		);
	}

	@Test
	void requestHeadersShouldMergeNamesCaseInsensitively() {
		// HTTP header names are case-insensitive: an offered 'authorization' and a configured
		// 'Authorization' are the same header. A case-sensitive merge would keep both entries
		// and send two Authorization lines, which repositories may reject — and the configured
		// credential would no longer reliably override the offered one.
		final UpgradeConfig config = UpgradeConfig.builder()
			.downloadHeaders(Map.of("repo.example.com", Map.of("Authorization", "local")))
			.build();
		final PackageOffer offered = offer(
			"https://repo.example.com/metricshub.deb",
			packageSha256,
			Map.of("authorization", "offered")
		);

		final Map<String, String> merged = PackageDownloader.resolveRequestHeaders(offered, config, "repo.example.com");

		assertEquals(1, merged.size());
		assertEquals("local", merged.get("AUTHORIZATION"));
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
