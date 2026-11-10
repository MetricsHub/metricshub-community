package org.metricshub.agent.fleet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
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
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.metricshub.agent.config.CentralAuthConfig;
import org.metricshub.agent.config.CentralConfig;
import org.metricshub.agent.config.OpAmpConfig;
import org.metricshub.agent.config.TunnelConfig;
import org.metricshub.agent.fleet.AgentCredentialStore.LocalIdentity;
import org.metricshub.agent.fleet.AgentCredentialStore.Status;
import org.metricshub.engine.security.SecurityManager;

/**
 * The enrollment against a stub of Central's enrollment and recovery gates, which keeps every attempt it committed
 * and can lose an answer after recording it.
 */
@Timeout(60)
class EnrollmentCoordinatorTest {

	private static final String KEY = "mh_enr_a2V5aWRrZXlpZGtleWlkaw.c2VjcmV0c2VjcmV0c2VjcmV0c2VjcmV0c2VjcmV0c2U";
	private static final String OTHER_KEY = "mh_enr_b3RoZXJvdGhlcm90aGVyb3RoZXI.b3RoZXJvdGhlcm90aGVyb3RoZXJvdGhlcm90aGVyb3RoZXI";
	private static final Map<String, String> DESCRIPTION = Map.of(
		"hostname",
		"srv-01",
		"os",
		"linux",
		"architecture",
		"amd64",
		"agent_version",
		"3.9.09",
		"edition",
		"community"
	);
	private static final ObjectMapper JSON = new ObjectMapper();

	/**
	 * An attempt the stub committed.
	 */
	private record Attempt(String body, String recoverySecret, String agentUid, String secret) {}

	/**
	 * A scripted answer of the enrollment endpoint, served before the stub's own logic.
	 */
	private record Answer(int status, String body, String retryAfter) {}

	@TempDir
	Path directory;

	@TempDir
	Path otherDirectory;

	private HttpServer server;
	private String issuer;
	private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
	private final Deque<Answer> script = new ConcurrentLinkedDeque<>();
	private final List<String> enrollBodies = new CopyOnWriteArrayList<>();
	private final AtomicInteger recoverRequests = new AtomicInteger();
	private final AtomicBoolean loseNextAnswer = new AtomicBoolean();
	private volatile CountDownLatch holdEnrollAnswer = new CountDownLatch(0);
	private volatile long retryAfterSentAt;
	private volatile long firstRequestAfterRetryAfter;
	private final List<String> secretsHandedOut = Collections.synchronizedList(new ArrayList<>());

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.setExecutor(Executors.newCachedThreadPool());
		issuer = "http://127.0.0.1:" + server.getAddress().getPort();
		server.createContext("/v1/agents/enroll", this::enroll);
		server.createContext("/v1/enrollment-attempts/", this::recover);
		server.start();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	private void enroll(final HttpExchange exchange) throws IOException {
		final String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		enrollBodies.add(body);
		try {
			holdEnrollAnswer.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		noteRequest();
		final Answer scripted = script.poll();
		if (scripted != null) {
			respond(exchange, scripted.status(), scripted.body(), scripted.retryAfter());
			if (scripted.retryAfter() != null) {
				retryAfterSentAt = System.nanoTime();
			}
			return;
		}
		final String authorization = exchange.getRequestHeaders().getFirst("Authorization");
		if (!Set.of("Bearer " + KEY, "Bearer " + OTHER_KEY).contains(authorization)) {
			respond(exchange, 401, "{\"code\":\"enrollment_not_authorized\"}", null);
			return;
		}
		final JsonNode request = JSON.readTree(body);
		final String attemptId = request.path("attempt_id").asText();
		final Attempt known = attempts.get(attemptId);
		if (known != null) {
			if (!known.body().equals(body)) {
				respond(exchange, 409, "{\"code\":\"idempotency_conflict\"}", null);
				return;
			}
			respond(exchange, 200, identity(known), null);
			return;
		}
		final String secret = randomSecret();
		secretsHandedOut.add(secret);
		final Attempt attempt = new Attempt(
			body,
			request.path("recovery_secret").asText(),
			"0192a6f4-7c1e-7b3a-9f20-" + UUID.randomUUID().toString().substring(24),
			secret
		);
		attempts.put(attemptId, attempt);
		if (loseNextAnswer.getAndSet(false)) {
			respond(exchange, 502, "{}", null);
			return;
		}
		respond(exchange, 201, identity(attempt), null);
	}

	private void recover(final HttpExchange exchange) throws IOException {
		noteRequest();
		recoverRequests.incrementAndGet();
		final String attemptId = exchange.getRequestURI().getPath().split("/")[3];
		final Attempt attempt = attempts.get(attemptId);
		if (
			attempt == null ||
				!("Bearer " + attempt.recoverySecret()).equals(exchange.getRequestHeaders().getFirst("Authorization"))
		) {
			respond(exchange, 401, "{\"code\":\"recovery_not_authorized\"}", null);
			return;
		}
		respond(exchange, 200, identity(attempt), null);
	}

	private void noteRequest() {
		if (retryAfterSentAt != 0 && firstRequestAfterRetryAfter == 0) {
			firstRequestAfterRetryAfter = System.nanoTime();
		}
	}

	private static String identity(final Attempt attempt) {
		return "{\"agent_uid\":\"" +
			attempt.agentUid() +
			"\",\"client_secret\":\"" +
			attempt.secret() +
			"\",\"bootstrap\":{\"issuer_url\":\"http://localhost:4320\"}}";
	}

	private static String randomSecret() {
		final byte[] bytes = new byte[32];
		new Random().nextBytes(bytes);
		return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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

	private CentralConfig central(final String key) {
		return CentralConfig.builder()
			.enabled(true)
			.auth(CentralAuthConfig.builder().issuerUrl(issuer).enrollmentKey(key).build())
			.build();
	}

	private static EnrollmentCoordinator coordinator(final AgentCredentialStore store) {
		return new EnrollmentCoordinator(() -> store, Duration.ofMillis(10), new Random(0));
	}

	/**
	 * Runs an enrollment to its end.
	 *
	 * @return whether the agent is enrolled
	 */
	private boolean enrollWith(final EnrollmentCoordinator coordinator, final CentralConfig central)
		throws InterruptedException {
		final boolean readyAtOnce = coordinator.ready(central, DESCRIPTION);
		coordinator.awaitWorker();
		return readyAtOnce || coordinator.ready(central, DESCRIPTION);
	}

	private String storedUid(final Path in) throws IOException {
		final LocalIdentity identity = new AgentCredentialStore(in).read();
		assertEquals(Status.READY, identity.status());
		return identity.agentUid();
	}

	@Test
	void theFirstStartShouldEnrollOnceAndReachReady() throws Exception {
		final EnrollmentCoordinator coordinator = coordinator(new AgentCredentialStore(directory));
		assertFalse(coordinator.ready(central(KEY), DESCRIPTION), "nothing connects before the enrollment");
		coordinator.awaitWorker();

		assertTrue(coordinator.isReady());
		assertEquals(1, enrollBodies.size());
		final JsonNode body = JSON.readTree(enrollBodies.get(0));
		final Attempt attempt = attempts.get(body.path("attempt_id").asText());
		assertEquals(attempt.agentUid(), storedUid(directory));
		assertTrue(body.path("recovery_secret").asText().matches("^[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]$"));
		assertEquals(DESCRIPTION, JSON.convertValue(body.path("description"), Map.class));
		assertEquals(0, recoverRequests.get());
	}

	@Test
	void aRestartWithTheKeyStillPresentShouldSendNoEnrollment() throws Exception {
		assertTrue(enrollWith(coordinator(new AgentCredentialStore(directory)), central(KEY)));
		final String uid = storedUid(directory);

		final EnrollmentCoordinator restarted = coordinator(new AgentCredentialStore(directory));
		assertTrue(restarted.ready(central(KEY), DESCRIPTION), "an enrolled agent connects at once");

		assertEquals(1, enrollBodies.size());
		assertEquals(0, recoverRequests.get());
		assertEquals(uid, storedUid(directory));
	}

	@Test
	void twoMachinesWithTheSameKeyShouldGetTwoAgentUids() throws Exception {
		assertTrue(enrollWith(coordinator(new AgentCredentialStore(directory)), central(KEY)));
		assertTrue(enrollWith(coordinator(new AgentCredentialStore(otherDirectory)), central(KEY)));

		assertNotEquals(storedUid(directory), storedUid(otherDirectory));
		assertEquals(2, attempts.size());
	}

	@Test
	void aLostAnswerShouldBeRecoveredWithoutASecondEnrollment() throws Exception {
		loseNextAnswer.set(true);

		assertTrue(enrollWith(coordinator(new AgentCredentialStore(directory)), central(KEY)));

		assertEquals(1, enrollBodies.size(), "never a second enrollment");
		assertEquals(1, recoverRequests.get());
		assertEquals(1, attempts.size());
		assertEquals(attempts.values().iterator().next().agentUid(), storedUid(directory));
	}

	@Test
	void aLostAnswerShouldBeRecoveredWithoutTheKey() throws Exception {
		// Central committed the attempt, the agent lost the answer, then the key left metricshub.yaml
		final String attemptId = "a5ccfb10-f1fa-4ae9-803d-32bd1b74b49d";
		final String recoverySecret = randomSecret();
		final Attempt committed = new Attempt("{}", recoverySecret, "0192a6f4-7c1e-7b3a-9f20-4d5e6a7b8c91", randomSecret());
		attempts.put(attemptId, committed);
		final AgentCredentialStore store = new AgentCredentialStore(directory);
		store.beginAttempt(attemptId, recoverySecret.getBytes(StandardCharsets.US_ASCII));

		assertTrue(enrollWith(coordinator(store), central(null)));

		assertEquals(committed.agentUid(), storedUid(directory));
		assertEquals(1, recoverRequests.get());
		assertTrue(enrollBodies.isEmpty());
	}

	@Test
	void nothingToRecoverAndNoKeyShouldStop() throws Exception {
		final AgentCredentialStore store = new AgentCredentialStore(directory);
		store.beginAttempt("a5ccfb10-f1fa-4ae9-803d-32bd1b74b49d", randomSecret().getBytes(StandardCharsets.US_ASCII));

		assertFalse(enrollWith(coordinator(store), central(null)));

		assertEquals(1, recoverRequests.get());
		assertTrue(enrollBodies.isEmpty());
		assertEquals(Status.PENDING, store.read().status());
	}

	@ParameterizedTest
	@ValueSource(strings = { SecurityManager.METRICSHUB_KEY_STORE_FILE_NAME, AgentInstanceUid.FILENAME })
	void aCrashAfterAWriteShouldResumeTheSameAttempt(final String crashAfter) throws Exception {
		final AtomicInteger writes = new AtomicInteger();
		final AtomicBoolean crashed = new AtomicBoolean();
		final AgentCredentialStore crashing = new AgentCredentialStore(directory, written -> {
			// The beginning of the attempt writes the KeyStore once; the completion writes it, then the uid
			if (
				written.equals(crashAfter) && writes.incrementAndGet() == (crashAfter.equals(AgentInstanceUid.FILENAME) ? 1 : 2)
			) {
				crashed.set(true);
				throw new IllegalStateException("crash after " + written);
			}
		});

		assertTrue(enrollWith(coordinator(crashing), central(KEY)));

		assertTrue(crashed.get());
		assertEquals(1, enrollBodies.size(), "the same attempt, never a new one");
		assertEquals(1, recoverRequests.get());
		assertEquals(1, attempts.size());
		assertEquals(attempts.values().iterator().next().agentUid(), storedUid(directory));
	}

	@Test
	void aCrashBeforeTheFirstRequestShouldResumeTheSameAttempt() throws Exception {
		final AgentCredentialStore store = new AgentCredentialStore(directory);
		store.beginAttempt("a5ccfb10-f1fa-4ae9-803d-32bd1b74b49d", randomSecret().getBytes(StandardCharsets.US_ASCII));

		assertTrue(enrollWith(coordinator(store), central(KEY)));

		assertEquals(1, recoverRequests.get(), "Central has nothing to recover");
		assertEquals(1, enrollBodies.size());
		assertEquals(
			"a5ccfb10-f1fa-4ae9-803d-32bd1b74b49d",
			JSON.readTree(enrollBodies.get(0)).path("attempt_id").asText()
		);
	}

	@ParameterizedTest
	@CsvSource(
		{
			"401, enrollment_not_authorized",
			"410, enrollment_expired",
			"409, enrollment_limit_reached",
			"409, idempotency_conflict",
			"409, attempt_closed",
			"400, invalid_request" }
	)
	void aRefusalShouldStopWithoutANewAttempt(final int status, final String code) throws Exception {
		script.add(new Answer(status, "{\"code\":\"" + code + "\",\"message\":\"no\",\"request_id\":\"r\"}", null));
		final AgentCredentialStore store = new AgentCredentialStore(directory);
		final EnrollmentCoordinator coordinator = coordinator(store);

		assertFalse(enrollWith(coordinator, central(KEY)));
		final String attemptId = store.read().attemptId();
		assertFalse(coordinator.ready(central(KEY), DESCRIPTION));
		coordinator.awaitWorker();

		assertEquals(1, enrollBodies.size(), "the same configuration never tries again");
		assertFalse(Files.exists(store.uidFile()), "no uid before ready");

		// A new key resumes the same attempt
		assertTrue(enrollWith(coordinator, central(OTHER_KEY)));
		assertEquals(attemptId, JSON.readTree(enrollBodies.get(enrollBodies.size() - 1)).path("attempt_id").asText());
	}

	@Test
	void anOutageShouldBeRetriedUntilCentralAnswers() throws Exception {
		script.add(new Answer(503, "{}", null));
		script.add(new Answer(500, "{}", null));
		script.add(new Answer(429, "{}", "1"));
		final AgentCredentialStore store = new AgentCredentialStore(directory);

		assertTrue(enrollWith(coordinator(store), central(KEY)));

		assertTrue(
			Duration.ofNanos(firstRequestAfterRetryAfter - retryAfterSentAt).compareTo(Duration.ofMillis(990)) >= 0,
			"Retry-After is honoured"
		);
		assertEquals(Status.READY, store.read().status());
		assertEquals(1, attempts.size());
	}

	@Test
	void withoutCentralAuthNothingShouldBeNeeded() throws Exception {
		final EnrollmentCoordinator coordinator = new EnrollmentCoordinator(() -> {
			throw new AssertionError("no store without central.auth");
		}, Duration.ofMillis(10), new Random(0));

		assertTrue(coordinator.ready(CentralConfig.builder().enabled(true).url(issuer).build(), DESCRIPTION));
		assertTrue(coordinator.ready(null, DESCRIPTION));
		assertTrue(enrollBodies.isEmpty());
		EnrollmentCoordinator.checkConfiguration(
			CentralConfig.builder().enabled(true).headers(Map.of("Authorization", "Bearer x")).build(),
			() -> {
				throw new AssertionError("no store without central.auth");
			}
		);
	}

	@Test
	void aMissingIssuerShouldRefuseTheStartupNamingIt() {
		final CentralConfig central = CentralConfig.builder()
			.enabled(true)
			.auth(CentralAuthConfig.builder().enrollmentKey(KEY).build())
			.build();

		final IllegalStateException refusal = assertThrows(
			IllegalStateException.class,
			() -> EnrollmentCoordinator.checkConfiguration(central, () -> new AgentCredentialStore(directory))
		);
		assertTrue(refusal.getMessage().contains("central.auth.issuerUrl"), refusal.getMessage());
	}

	@Test
	void anIssuerChangedDuringTheEnrollmentShouldNotKeepTheFormerIdentity() throws Exception {
		holdEnrollAnswer = new CountDownLatch(1);
		final EnrollmentCoordinator coordinator = coordinator(new AgentCredentialStore(directory));
		assertFalse(coordinator.ready(central(KEY), DESCRIPTION));
		while (enrollBodies.isEmpty()) {
			Thread.sleep(10);
		}
		// Another issuer string (the same stub here), while the first answer is in flight
		final CentralConfig moved = central(KEY);
		moved.getAuth().setIssuerUrl(issuer + "/");
		assertFalse(coordinator.ready(moved, DESCRIPTION));
		holdEnrollAnswer.countDown();
		coordinator.awaitWorker();

		assertTrue(coordinator.isReady());
		assertEquals(1, recoverRequests.get(), "the identity is asked again from the issuer now configured");
	}

	@ParameterizedTest
	@ValueSource(strings = { "/v1", "/?tenant=x", "#x", "?tenant=x" })
	void anIssuerThatIsNotAnOriginShouldRefuseTheStartup(final String suffix) {
		final CentralConfig central = central(KEY);
		central.getAuth().setIssuerUrl(issuer + suffix);

		final IllegalStateException refusal = assertThrows(
			IllegalStateException.class,
			() -> EnrollmentCoordinator.checkConfiguration(central, () -> new AgentCredentialStore(directory))
		);
		assertTrue(refusal.getMessage().contains("central.auth.issuerUrl"), refusal.getMessage());
	}

	@Test
	void anIssuerWithUserInfoShouldRefuseTheStartup() {
		final CentralConfig central = central(KEY);
		central.getAuth().setIssuerUrl(issuer.replace("http://", "http://user@"));

		assertThrows(
			IllegalStateException.class,
			() -> EnrollmentCoordinator.checkConfiguration(central, () -> new AgentCredentialStore(directory))
		);
	}

	@Test
	void anIncompleteIdentityWithoutAttemptShouldRefuseTheStartup() throws Exception {
		assertTrue(enrollWith(coordinator(new AgentCredentialStore(directory)), central(KEY)));
		Files.delete(new AgentCredentialStore(directory).uidFile());

		final IllegalStateException refusal = assertThrows(
			IllegalStateException.class,
			() -> EnrollmentCoordinator.checkConfiguration(central(KEY), () -> new AgentCredentialStore(directory))
		);
		assertTrue(refusal.getMessage().contains("incomplete"), refusal.getMessage());
	}

	@Test
	void anAuthorizationHeaderWithoutValueShouldNotRefuseTheStartup() throws Exception {
		final CentralConfig central = central(KEY);
		final Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", null);
		central.setHeaders(headers);

		EnrollmentCoordinator.checkConfiguration(central, () -> new AgentCredentialStore(directory));
	}

	@Test
	void anEnrollmentNoLongerConfiguredShouldStop() throws Exception {
		for (int failure = 0; failure < 20; failure++) {
			script.add(new Answer(503, "{}", null));
		}
		final AgentCredentialStore store = new AgentCredentialStore(directory);
		final EnrollmentCoordinator coordinator = coordinator(store);
		assertFalse(coordinator.ready(central(KEY), DESCRIPTION));
		while (enrollBodies.isEmpty()) {
			Thread.sleep(10);
		}

		// A reload removes central.auth while the enrollment retries
		assertTrue(coordinator.ready(CentralConfig.builder().enabled(true).build(), DESCRIPTION));
		coordinator.awaitWorker();
		final int sent = enrollBodies.size() + recoverRequests.get();
		Thread.sleep(300);

		assertEquals(sent, enrollBodies.size() + recoverRequests.get(), "nothing is sent any more");
		assertFalse(coordinator.isReady());
		assertFalse(Files.exists(store.uidFile()));
	}

	@Test
	void anErrorCodeOutsideTheContractShouldNotBeLogged() throws Exception {
		final StringWriter written = new StringWriter();
		final WriterAppender appender = WriterAppender.newBuilder()
			.setName("error-code")
			.setTarget(written)
			.setLayout(PatternLayout.newBuilder().withPattern("%m%n").build())
			.build();
		appender.start();
		final LoggerContext context = (LoggerContext) LogManager.getContext(false);
		final String fleet = EnrollmentCoordinator.class.getPackageName();
		final LoggerConfig logger = new LoggerConfig(fleet, Level.TRACE, false);
		logger.addAppender(appender, Level.TRACE, null);
		context.getConfiguration().addLogger(fleet, logger);
		context.updateLoggers();
		try {
			script.add(new Answer(409, "{\"code\":\"echoed_recoveryproofsecretvalue\"}", null));
			assertFalse(enrollWith(coordinator(new AgentCredentialStore(directory)), central(KEY)));
		} finally {
			context.getConfiguration().removeLogger(fleet);
			context.updateLoggers();
			appender.stop();
		}

		final String logs = written.toString();
		assertTrue(logs.contains("409 unknown"), logs);
		assertFalse(logs.contains("recoveryproofsecretvalue"), logs);
	}

	@Test
	void aStaticAuthorizationHeaderShouldRefuseTheStartupNamingBoth() {
		final CentralConfig shared = central(KEY);
		shared.setHeaders(Map.of("authorization", "Bearer x"));
		final CentralConfig opamp = central(KEY);
		opamp.setOpamp(OpAmpConfig.builder().headers(Map.of("AUTHORIZATION", "Bearer x")).build());
		final CentralConfig tunnel = central(KEY);
		tunnel.setTunnel(TunnelConfig.builder().headers(Map.of("Authorization", "Bearer x")).build());
		final List<CentralConfig> configurations = List.of(shared, opamp, tunnel);
		for (final CentralConfig central : configurations) {
			final IllegalStateException refusal = assertThrows(
				IllegalStateException.class,
				() -> EnrollmentCoordinator.checkConfiguration(central, () -> new AgentCredentialStore(directory))
			);
			assertTrue(refusal.getMessage().contains("central.auth"), refusal.getMessage());
			assertTrue(refusal.getMessage().contains("Authorization"), refusal.getMessage());
		}
	}

	@Test
	void noIdentityAndNoKeyShouldRefuseTheStartupNamingTheKey() throws Exception {
		final IllegalStateException refusal = assertThrows(
			IllegalStateException.class,
			() -> EnrollmentCoordinator.checkConfiguration(central(null), () -> new AgentCredentialStore(directory))
		);
		assertTrue(refusal.getMessage().contains("central.auth.enrollmentKey"), refusal.getMessage());

		// Enrolled, the key may go
		assertTrue(enrollWith(coordinator(new AgentCredentialStore(directory)), central(KEY)));
		EnrollmentCoordinator.checkConfiguration(central(null), () -> new AgentCredentialStore(directory));
	}

	@Test
	void neitherTheKeyNorTheSecretsShouldBeLogged() throws Exception {
		final StringWriter written = new StringWriter();
		final WriterAppender appender = WriterAppender.newBuilder()
			.setName("enrollment")
			.setTarget(written)
			.setLayout(PatternLayout.newBuilder().withPattern("%m%n").build())
			.build();
		appender.start();
		final LoggerContext context = (LoggerContext) LogManager.getContext(false);
		final LoggerConfig logger = context.getConfiguration().getLoggerConfig(EnrollmentCoordinator.class.getName());
		final Level level = logger.getLevel();
		logger.addAppender(appender, Level.TRACE, null);
		logger.setLevel(Level.TRACE);
		context.updateLoggers();
		try {
			loseNextAnswer.set(true);
			script.add(new Answer(503, "{\"code\":\"" + KEY + "\"}", null));
			assertTrue(enrollWith(coordinator(new AgentCredentialStore(directory)), central(KEY)));
			script.add(new Answer(410, "{\"code\":\"enrollment_expired\"}", null));
			assertFalse(enrollWith(coordinator(new AgentCredentialStore(otherDirectory)), central(KEY)));
		} finally {
			logger.removeAppender("enrollment");
			logger.setLevel(level);
			context.updateLoggers();
			appender.stop();
		}

		final String logs = written.toString();
		assertTrue(logs.contains("enrollment_expired"), logs);
		assertFalse(logs.contains(KEY), logs);
		assertFalse(logs.contains(KEY.substring(KEY.indexOf('.') + 1)), logs);
		for (final Attempt attempt : attempts.values()) {
			assertFalse(logs.contains(attempt.recoverySecret()), logs);
		}
		for (final String secret : secretsHandedOut) {
			assertFalse(logs.contains(secret), logs);
		}
	}
}
