package org.metricshub.agent.fleet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.WriterAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.fleet.CentralTokenProvider.Credential;
import org.metricshub.agent.fleet.CentralTokenProvider.InvalidClientException;

class CentralTokenProviderTest {

	private static final String AGENT_UID = "0192a6f4-7c1e-7b3a-9f20-4d5e6a7b8c91";
	private static final String SECRET = "c2VjcmV0c2VjcmV0c2VjcmV0c2VjcmV0c2VjcmV0c2U";
	private static final Instant START = Instant.parse("2026-11-09T10:00:00Z");

	/**
	 * A clock the test moves.
	 */
	private static final class MovableClock extends Clock {

		private volatile Instant now = START;

		void advance(final Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public java.time.ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(final java.time.ZoneId zone) {
			return this;
		}
	}

	/**
	 * A scripted answer of the token endpoint.
	 */
	private record Answer(int status, String body, String retryAfter) {}

	private HttpServer server;
	private String issuer;
	private String announcedIssuer;
	private final MovableClock clock = new MovableClock();
	private final Deque<Answer> script = new ConcurrentLinkedDeque<>();
	private final AtomicInteger tokenRequests = new AtomicInteger();
	private final AtomicInteger enrollRequests = new AtomicInteger();
	private final List<String> authorizations = Collections.synchronizedList(new ArrayList<>());
	private volatile long lifetime = 900;
	private volatile long answerDelayMs;
	private volatile Duration clockAdvanceBeforeAnswer = Duration.ZERO;

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.setExecutor(Executors.newCachedThreadPool());
		issuer = "http://127.0.0.1:" + server.getAddress().getPort();
		announcedIssuer = issuer;
		server.createContext(
			"/.well-known/oauth-authorization-server",
			exchange -> respond(
				exchange,
				200,
				"{\"issuer\":\"" + announcedIssuer + "\",\"token_endpoint\":\"" + issuer + "/oauth2/token\"}",
				null
			)
		);
		server.createContext("/v1/agents/enroll", exchange -> {
			enrollRequests.incrementAndGet();
			respond(exchange, 500, "{}", null);
		});
		server.createContext("/oauth2/token", exchange -> {
			final int request = tokenRequests.incrementAndGet();
			authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
			exchange.getRequestBody().readAllBytes();
			try {
				Thread.sleep(answerDelayMs);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			clock.advance(clockAdvanceBeforeAnswer);
			final Answer answer = script.poll();
			if (answer != null) {
				respond(exchange, answer.status(), answer.body(), answer.retryAfter());
			} else {
				respond(exchange, 200, tokenAnswer(request), null);
			}
		});
		server.start();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	private String tokenAnswer(final int request) {
		final long iat = clock.instant().getEpochSecond();
		final String payload = "{\"agent_uid\":\"" +
			AGENT_UID +
			"\",\"jti\":\"jti-" +
			request +
			"\",\"iat\":" +
			iat +
			",\"exp\":" +
			(iat + lifetime) +
			"}";
		final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
		final String jwt = encoder.encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8)) +
			"." +
			encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) +
			".c2lnbmF0dXJl";
		return "{\"access_token\":\"" + jwt + "\",\"token_type\":\"Bearer\",\"expires_in\":" + lifetime + "}";
	}

	private static void respond(final HttpExchange exchange, final int status, final String body, final String retryAfter)
		throws IOException {
		final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		if (retryAfter != null) {
			exchange.getResponseHeaders().add("Retry-After", retryAfter);
		}
		exchange.sendResponseHeaders(status, bytes.length);
		exchange.getResponseBody().write(bytes);
		exchange.close();
	}

	private CentralTokenProvider provider() throws IOException {
		final Random noJitter = new Random() {
			private static final long serialVersionUID = 1L;

			@Override
			public double nextDouble() {
				return 0;
			}
		};
		return new CentralTokenProvider(
			issuer,
			() -> new Credential(AGENT_UID, SECRET.toCharArray()),
			CentralTokenProvider.httpClient(null),
			clock,
			noJitter,
			CentralTokenProvider.DEFAULT_MARGIN
		);
	}

	@Test
	void shouldDiscoverTheEndpointAndAuthenticateWithClientSecretBasic() throws Exception {
		final String token = provider().currentToken();

		assertEquals(AGENT_UID, CentralTokenProvider.claims(token).path("agent_uid").asText());
		assertEquals(
			"Basic " + Base64.getEncoder().encodeToString((AGENT_UID + ":" + SECRET).getBytes(StandardCharsets.UTF_8)),
			authorizations.get(0)
		);
	}

	@Test
	void anInvalidClientAnsweredWith400ShouldBeTerminalToo() throws Exception {
		script.add(new Answer(400, "{\"error\":\"invalid_client\"}", null));
		final CentralTokenProvider provider = provider();

		assertThrows(InvalidClientException.class, provider::currentToken);
		clock.advance(Duration.ofHours(1));
		assertThrows(InvalidClientException.class, provider::currentToken);
		assertEquals(1, tokenRequests.get(), "never asked again");
	}

	@Test
	void anErrorOutsideOAuthShouldNeverBeQuoted() throws Exception {
		script.add(new Answer(400, "{\"error\":\"echoed-" + SECRET + "\"}", null));

		final IOException failure = assertThrows(IOException.class, () -> provider().currentToken());

		assertTrue(failure.getMessage().contains("400 unknown"), failure.getMessage());
		assertFalse(failure.getMessage().contains(SECRET), failure.getMessage());
	}

	@Test
	void anotherIssuerInTheMetadataShouldBeRefused() throws Exception {
		announcedIssuer = "https://issuer.example.com";

		assertThrows(IOException.class, () -> provider().currentToken());
		assertEquals(0, tokenRequests.get());
	}

	@Test
	void a15MinuteTokenShouldBeRenewedEightMinutesAfterItArrived() throws Exception {
		final CentralTokenProvider provider = provider();
		final String first = provider.currentToken();

		clock.advance(Duration.ofSeconds(479));
		assertEquals(first, provider.currentToken());
		assertEquals(1, tokenRequests.get());

		clock.advance(Duration.ofSeconds(1));
		assertNotEquals(first, provider.currentToken());
		assertEquals(2, tokenRequests.get());
	}

	@Test
	void a2MinuteTokenShouldBeRenewedAtHalfItsLifetime() throws Exception {
		lifetime = 120;
		final CentralTokenProvider provider = provider();
		final String first = provider.currentToken();

		clock.advance(Duration.ofSeconds(59));
		assertEquals(first, provider.currentToken());

		clock.advance(Duration.ofSeconds(1));
		assertNotEquals(first, provider.currentToken());
		assertEquals(2, tokenRequests.get());
	}

	@Test
	void concurrentCallersShouldShareOneRequest() throws Exception {
		answerDelayMs = 300;
		final CentralTokenProvider provider = provider();
		final ExecutorService executor = Executors.newFixedThreadPool(20);
		try {
			final CountDownLatch start = new CountDownLatch(1);
			final List<Future<String>> results = new ArrayList<>();
			for (int i = 0; i < 20; i++) {
				results.add(executor.submit(() -> {
					start.await();
					return provider.currentToken();
				}));
			}
			start.countDown();
			for (final Future<String> result : results) {
				assertEquals(results.get(0).get(), result.get());
			}
		} finally {
			executor.shutdownNow();
		}
		assertEquals(1, tokenRequests.get());
	}

	@Test
	void invalidateShouldMakeTheNextCallFetchANewToken() throws Exception {
		final CentralTokenProvider provider = provider();
		final String first = provider.currentToken();

		provider.invalidate();

		assertNotEquals(first, provider.currentToken());
		assertEquals(2, tokenRequests.get());
	}

	@Test
	void anOutageShouldBeRetriedAfterTheBackoffWithTheSameCredential() throws Exception {
		script.add(new Answer(503, "{}", "10"));
		final CentralTokenProvider provider = provider();

		assertThrows(IOException.class, provider::currentToken);
		clock.advance(Duration.ofSeconds(9));
		assertThrows(IOException.class, provider::currentToken);
		assertEquals(1, tokenRequests.get());

		clock.advance(Duration.ofSeconds(1));
		provider.currentToken();
		assertEquals(2, tokenRequests.get());
		assertEquals(authorizations.get(0), authorizations.get(1));
	}

	@Test
	void anUnreadableAnswerShouldBeRetriedAfterTheBackoff() throws Exception {
		// The payload is base64url for "notjson": a parser failure, not one this class names
		script.add(new Answer(200, "{\"access_token\":\"x.bm90anNvbg.y\",\"token_type\":\"Bearer\"}", null));
		final CentralTokenProvider provider = provider();

		final IOException failure = assertThrows(IOException.class, provider::currentToken);
		assertThrows(IOException.class, provider::currentToken);

		assertEquals(1, tokenRequests.get());
		assertFalse(failure.getMessage().contains("notjson"), failure.getMessage());
	}

	@Test
	void aCleartextIssuerShouldBeRefused() {
		assertThrows(
			IllegalArgumentException.class,
			() -> new CentralTokenProvider(
				"http://issuer.example.com",
				() -> new Credential(AGENT_UID, SECRET.toCharArray()),
				CentralTokenProvider.httpClient(null),
				clock,
				new Random(),
				CentralTokenProvider.DEFAULT_MARGIN
			)
		);
	}

	@Test
	void aRetryAfterDateShouldBeHonoured() throws Exception {
		final String inThirtySeconds = DateTimeFormatter.RFC_1123_DATE_TIME.format(
			ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(30)
		);
		script.add(new Answer(503, "{}", inThirtySeconds));
		final CentralTokenProvider provider = provider();

		assertThrows(IOException.class, provider::currentToken);
		clock.advance(Duration.ofSeconds(20));
		assertThrows(IOException.class, provider::currentToken);
		assertEquals(1, tokenRequests.get());

		clock.advance(Duration.ofSeconds(15));
		provider.currentToken();
		assertEquals(2, tokenRequests.get());
	}

	@Test
	void aTokenThatExpiredDuringAFailedRenewalShouldNotBeReturned() throws Exception {
		lifetime = 120;
		final CentralTokenProvider provider = provider();
		provider.currentToken();
		clock.advance(Duration.ofSeconds(60));
		script.add(new Answer(503, "{}", null));
		clockAdvanceBeforeAnswer = Duration.ofSeconds(61);

		assertThrows(IOException.class, provider::currentToken);
	}

	@Test
	void aFailedRenewalShouldKeepTheValidToken() throws Exception {
		final CentralTokenProvider provider = provider();
		final String first = provider.currentToken();
		script.add(new Answer(503, "{}", null));

		clock.advance(Duration.ofSeconds(480));

		assertEquals(first, provider.currentToken());
		assertEquals(2, tokenRequests.get());
	}

	@Test
	void invalidClientShouldStopEveryRequestAndNeverEnroll() throws Exception {
		script.add(new Answer(401, "{\"error\":\"invalid_client\"}", null));
		final CentralTokenProvider provider = provider();

		assertThrows(InvalidClientException.class, provider::currentToken);
		clock.advance(Duration.ofHours(1));
		provider.invalidate();
		assertThrows(InvalidClientException.class, provider::currentToken);

		assertEquals(1, tokenRequests.get());
		assertEquals(0, enrollRequests.get());
	}

	@Test
	void aSuspendedWorkspaceShouldBeRetriedWithAGrowingBackoff() throws Exception {
		script.add(new Answer(400, "{\"error\":\"unauthorized_client\"}", null));
		script.add(new Answer(400, "{\"error\":\"unauthorized_client\"}", null));
		final CentralTokenProvider provider = provider();

		// Without jitter, the backoff is half its nominal delay: 0.5 s, then 1 s
		assertThrows(IOException.class, provider::currentToken);
		clock.advance(Duration.ofMillis(500));
		assertThrows(IOException.class, provider::currentToken);
		assertEquals(2, tokenRequests.get());

		clock.advance(Duration.ofMillis(500));
		assertThrows(IOException.class, provider::currentToken);
		assertEquals(2, tokenRequests.get());

		clock.advance(Duration.ofMillis(500));
		provider.currentToken();
		assertEquals(3, tokenRequests.get());
	}

	@Test
	void neitherTheSecretNorTheTokenShouldBeLogged() throws Exception {
		final StringWriter written = new StringWriter();
		final WriterAppender appender = WriterAppender.newBuilder()
			.setName("token-provider")
			.setTarget(written)
			.setLayout(PatternLayout.newBuilder().withPattern("%m%n").build())
			.build();
		appender.start();
		final LoggerContext context = (LoggerContext) LogManager.getContext(false);
		final LoggerConfig logger = context.getConfiguration().getLoggerConfig(CentralTokenProvider.class.getName());
		final Level level = logger.getLevel();
		logger.addAppender(appender, Level.TRACE, null);
		logger.setLevel(Level.TRACE);
		context.updateLoggers();
		final String token;
		try {
			script.add(new Answer(400, "{\"error\":\"unauthorized_client\"}", null));
			script.add(new Answer(400, "{\"error\":\"invalid_scope\"}", null));
			final CentralTokenProvider provider = provider();
			assertThrows(IOException.class, provider::currentToken);
			clock.advance(Duration.ofMinutes(5));
			assertThrows(IOException.class, provider::currentToken);
			clock.advance(Duration.ofMinutes(5));
			token = provider.currentToken();
			script.add(new Answer(401, "{\"error\":\"invalid_client\"}", null));
			provider.invalidate();
			assertThrows(InvalidClientException.class, provider::currentToken);
		} finally {
			logger.removeAppender("token-provider");
			logger.setLevel(level);
			context.updateLoggers();
			appender.stop();
		}
		final String logged = written.toString();
		assertTrue(logged.contains("jti-3"), logged);
		assertTrue(logged.contains("invalid_client"), logged);
		assertFalse(logged.contains(SECRET), logged);
		assertFalse(logged.contains(token), logged);
		assertFalse(logged.contains(authorizations.get(0).substring("Basic ".length())), logged);
	}
}
