package org.metricshub.agent.fleet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
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
import java.util.concurrent.TimeUnit;
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
	private final Deque<Answer> recoverScript = new ConcurrentLinkedDeque<>();
	private final List<String> enrollBodies = new CopyOnWriteArrayList<>();
	private final AtomicInteger recoverRequests = new AtomicInteger();
	private final AtomicInteger tokenRequests = new AtomicInteger();
	private final AtomicInteger metadataRequests = new AtomicInteger();
	private volatile boolean tokensUnavailable;
	private volatile boolean tokensRefused;
	private volatile CountDownLatch holdTokenAnswer = new CountDownLatch(0);
	private final List<String> tokensHandedOut = new CopyOnWriteArrayList<>();
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
		server.createContext("/.well-known/oauth-authorization-server", exchange -> {
			metadataRequests.incrementAndGet();
			respond(
				exchange,
				200,
				"{\"issuer\":\"" + issuer + "\",\"token_endpoint\":\"" + issuer + "/oauth2/token\"}",
				null
			);
		});
		server.createContext("/oauth2/token", this::token);
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
		final Answer scripted = recoverScript.poll();
		if (scripted != null) {
			respond(exchange, scripted.status(), scripted.body(), scripted.retryAfter());
			return;
		}
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

	private void token(final HttpExchange exchange) throws IOException {
		final int request = tokenRequests.incrementAndGet();
		try {
			holdTokenAnswer.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		if (tokensUnavailable) {
			respond(exchange, 503, "{}", null);
			return;
		}
		if (tokensRefused) {
			respond(exchange, 401, "{\"error\":\"invalid_client\"}", null);
			return;
		}
		final long iat = System.currentTimeMillis() / 1000;
		final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
		final String jwt = encoder.encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8)) +
			"." +
			encoder.encodeToString(
				("{\"jti\":\"jti-" + request + "\",\"iat\":" + iat + ",\"exp\":" + (iat + 900) + "}").getBytes(
					StandardCharsets.UTF_8
				)
			) +
			".c2lnbmF0dXJl" +
			request;
		tokensHandedOut.add(jwt);
		respond(exchange, 200, "{\"access_token\":\"" + jwt + "\",\"token_type\":\"Bearer\",\"expires_in\":900}", null);
	}

	private byte[] recoveryProofIn(final Path in) throws Exception {
		return SecurityManager.readSecret(
			in.resolve(SecurityManager.METRICSHUB_KEY_STORE_FILE_NAME).toFile(),
			AgentCredentialStore.RECOVERY_ALIAS
		);
	}

	@Test
	void theFirstTokenShouldDeleteTheRecoveryProofAndNeverTouchTheConfiguration() throws Exception {
		final Path configuration = otherDirectory.resolve("metricshub.yaml");
		Files.writeString(configuration, "central:\n  auth:\n    enrollmentKey: " + KEY + "\n");
		final byte[] before = Files.readAllBytes(configuration);

		assertTrue(enrollWith(coordinator(new AgentCredentialStore(directory)), central(KEY)));

		assertEquals(1, tokenRequests.get());
		assertNull(recoveryProofIn(directory), "the recovery proof is gone after the first token");
		assertArrayEquals(before, Files.readAllBytes(configuration));
		final String yaml = Files.readString(configuration);
		final Attempt attempt = attempts.values().iterator().next();
		assertFalse(yaml.contains(attempt.secret()));
		assertFalse(yaml.contains(attempt.agentUid()));
	}

	@Test
	void aReloadMovingTheIssuerShouldTakeTheNextTokenFromThere() throws Exception {
		final EnrollmentCoordinator coordinator = coordinator(new AgentCredentialStore(directory));
		assertTrue(enrollWith(coordinator, central(KEY)));
		assertEquals(1, metadataRequests.get());

		// Another issuer string (the same stub here)
		final CentralConfig moved = central(KEY);
		moved.getAuth().setIssuerUrl(issuer + "/");
		assertTrue(coordinator.ready(moved, DESCRIPTION));
		coordinator.invalidateToken();
		coordinator.currentToken();

		assertEquals(2, metadataRequests.get(), "the token provider was rebuilt for the new issuer");
	}

	@Test
	void removingCentralAuthShouldStopAskingForTheFirstToken() throws Exception {
		tokensUnavailable = true;
		final AgentCredentialStore store = new AgentCredentialStore(directory);
		// A long backoff: the worker waits in it when central.auth goes
		final EnrollmentCoordinator coordinator = new EnrollmentCoordinator(
			() -> store,
			Duration.ofSeconds(20),
			new Random(0)
		);
		coordinator.ready(central(KEY), DESCRIPTION);
		while (tokenRequests.get() == 0) {
			Thread.sleep(10);
		}

		final long removedAt = System.nanoTime();
		assertTrue(coordinator.ready(CentralConfig.builder().enabled(true).build(), DESCRIPTION));
		coordinator.awaitWorker();
		assertTrue(
			Duration.ofNanos(System.nanoTime() - removedAt).toSeconds() < 5,
			"stopped at once, not after its backoff"
		);
		final int asked = tokenRequests.get();
		Thread.sleep(300);

		assertEquals(asked, tokenRequests.get(), "nothing is asked any more");
	}

	@Test
	void anotherIssuerAfterARefusedCredentialShouldGetTheFirstTokenThere() throws Exception {
		tokensRefused = true;
		final EnrollmentCoordinator coordinator = coordinator(new AgentCredentialStore(directory));
		assertTrue(enrollWith(coordinator, central(KEY)));
		assertNotNull(recoveryProofIn(directory), "no token, the proof stays");

		// Another issuer string (the same stub here), which accepts the credential
		tokensRefused = false;
		final CentralConfig moved = central(KEY);
		moved.getAuth().setIssuerUrl(issuer + "/");
		assertTrue(coordinator.ready(moved, DESCRIPTION));
		coordinator.awaitWorker();

		assertNull(recoveryProofIn(directory), "the first token of the new issuer came, and the proof went");
	}

	@Test
	void aTokenOfTheFormerIssuerShouldNotCountAsTheFirstToken() throws Exception {
		holdTokenAnswer = new CountDownLatch(1);
		final EnrollmentCoordinator coordinator = coordinator(new AgentCredentialStore(directory));
		coordinator.ready(central(KEY), DESCRIPTION);
		while (tokenRequests.get() == 0) {
			Thread.sleep(10);
		}

		// The issuer moves while its token is being asked
		final CentralConfig moved = central(KEY);
		moved.getAuth().setIssuerUrl(issuer + "/");
		assertTrue(coordinator.ready(moved, DESCRIPTION));
		holdTokenAnswer.countDown();
		coordinator.awaitWorker();

		assertEquals(2, metadataRequests.get(), "the new issuer was asked");
		assertEquals(2, tokenRequests.get());
		assertNull(recoveryProofIn(directory));
	}

	@Test
	void aFailedDeletionOfTheRecoveryProofShouldBeRetried() throws Exception {
		final File keyStore = directory.resolve(SecurityManager.METRICSHUB_KEY_STORE_FILE_NAME).toFile();
		// Once ready (the second state written, after pending), the KeyStore cannot be written: the deletion after the
		// first token fails
		final AtomicInteger states = new AtomicInteger();
		final AgentCredentialStore store = new AgentCredentialStore(directory, written -> {
			if (written.equals(AgentCredentialStore.STATE_FILENAME) && states.incrementAndGet() == 2) {
				keyStore.setWritable(false);
			}
		});
		final EnrollmentCoordinator coordinator = coordinator(store);
		coordinator.ready(central(KEY), DESCRIPTION);
		while (tokenRequests.get() == 0) {
			Thread.sleep(10);
		}
		Thread.sleep(300);
		keyStore.setWritable(true);
		coordinator.awaitWorker();

		assertNull(recoveryProofIn(directory), "deleted once the KeyStore could be written again");
	}

	@Test
	void reEnablingCentralAuthShouldResumeTheFirstToken() throws Exception {
		tokensUnavailable = true;
		final EnrollmentCoordinator coordinator = coordinator(new AgentCredentialStore(directory));
		coordinator.ready(central(KEY), DESCRIPTION);
		while (tokenRequests.get() == 0) {
			Thread.sleep(10);
		}
		assertTrue(coordinator.ready(CentralConfig.builder().enabled(true).build(), DESCRIPTION));
		coordinator.awaitWorker();

		// Back, with Central answering again
		tokensUnavailable = false;
		assertTrue(coordinator.ready(central(KEY), DESCRIPTION));
		coordinator.awaitWorker();

		assertNull(recoveryProofIn(directory), "the first token came, and the recovery proof went");
	}

	@Test
	void noTokenShouldBeRequestedBeforeReady() throws Exception {
		script.add(new Answer(410, "{\"code\":\"enrollment_expired\"}", null));
		final EnrollmentCoordinator coordinator = coordinator(new AgentCredentialStore(directory));

		assertFalse(enrollWith(coordinator, central(KEY)));

		assertThrows(IOException.class, coordinator::currentToken);
		assertEquals(0, metadataRequests.get() + tokenRequests.get(), "nothing reaches the token endpoint");
	}

	@Test
	void aCrashBetweenTheTokenAndTheDeletionShouldDeleteAfterTheNextToken() throws Exception {
		// The process stopped after its first token, before the deletion: ready, with the recovery proof left
		final AgentCredentialStore store = new AgentCredentialStore(directory);
		store.beginAttempt("a5ccfb10-f1fa-4ae9-803d-32bd1b74b49d", randomSecret().getBytes(StandardCharsets.US_ASCII));
		store.complete("0192a6f4-7c1e-7b3a-9f20-4d5e6a7b8c91", randomSecret().toCharArray());
		final EnrollmentCoordinator restarted = coordinator(store);

		assertTrue(restarted.ready(central(KEY), DESCRIPTION));
		restarted.awaitWorker();

		assertEquals(1, tokenRequests.get());
		assertNull(recoveryProofIn(directory));
		assertTrue(enrollBodies.isEmpty());
		assertEquals(0, recoverRequests.get());
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

	@ParameterizedTest
	@CsvSource({ "409, attempt_closed", "409, idempotency_conflict", "410, enrollment_expired", "400, invalid_request" })
	void aRecoveryRefusalShouldStopWithoutRetrying(final int status, final String code) throws Exception {
		recoverScript.add(new Answer(status, "{\"code\":\"" + code + "\"}", null));
		final AgentCredentialStore store = new AgentCredentialStore(directory);
		store.beginAttempt("a5ccfb10-f1fa-4ae9-803d-32bd1b74b49d", randomSecret().getBytes(StandardCharsets.US_ASCII));

		assertFalse(enrollWith(coordinator(store), central(KEY)));

		assertEquals(1, recoverRequests.get());
		assertTrue(enrollBodies.isEmpty(), "neither retried nor enrolled");
	}

	@Test
	void aNewIssuerShouldNotWaitForTheFormerOnesBackoff() throws Exception {
		script.add(new Answer(429, "{}", "30"));
		final EnrollmentCoordinator coordinator = coordinator(new AgentCredentialStore(directory));
		assertFalse(coordinator.ready(central(KEY), DESCRIPTION));
		while (enrollBodies.isEmpty()) {
			Thread.sleep(10);
		}
		Thread.sleep(200);

		// Another issuer string (the same stub here) while the worker waits 30 s
		final long movedAt = System.nanoTime();
		final CentralConfig moved = central(KEY);
		moved.getAuth().setIssuerUrl(issuer + "/");
		coordinator.ready(moved, DESCRIPTION);
		coordinator.awaitWorker();

		assertTrue(coordinator.isReady());
		assertTrue(Duration.ofNanos(System.nanoTime() - movedAt).toSeconds() < 10, "not after the former Retry-After");
	}

	@ParameterizedTest
	@ValueSource(strings = { "mh_enr_a\nb", "mh_enr_a b", "mh_enr_é" })
	void anEnrollmentKeyThatCannotBeAHeaderShouldRefuseTheStartup(final String key) {
		final IllegalStateException refusal = assertThrows(
			IllegalStateException.class,
			() -> EnrollmentCoordinator.checkConfiguration(central(key), () -> new AgentCredentialStore(directory))
		);
		assertTrue(refusal.getMessage().contains("central.auth.enrollmentKey"), refusal.getMessage());
		assertFalse(refusal.getMessage().contains(key), "never quoted");
	}

	@Test
	void aRefusalForAReplacedKeyShouldGoOnWithTheNewOne() throws Exception {
		holdEnrollAnswer = new CountDownLatch(1);
		script.add(new Answer(410, "{\"code\":\"enrollment_expired\"}", null));
		final EnrollmentCoordinator coordinator = coordinator(new AgentCredentialStore(directory));
		assertFalse(coordinator.ready(central(KEY), DESCRIPTION));
		while (enrollBodies.isEmpty()) {
			Thread.sleep(10);
		}

		// A new key while the old one's request is in flight, then the old one is refused
		assertFalse(coordinator.ready(central(OTHER_KEY), DESCRIPTION));
		holdEnrollAnswer.countDown();
		coordinator.awaitWorker();

		assertTrue(coordinator.isReady(), "enrolled with the new key, without another reload");
	}

	@Test
	void theNewKeysBackoffShouldNotBeCutByTheWakeUpItAlreadyUsed() throws Exception {
		holdEnrollAnswer = new CountDownLatch(1);
		script.add(new Answer(410, "{\"code\":\"enrollment_expired\"}", null));
		script.add(new Answer(429, "{}", "30"));
		final EnrollmentCoordinator coordinator = coordinator(new AgentCredentialStore(directory));
		assertFalse(coordinator.ready(central(KEY), DESCRIPTION));
		while (enrollBodies.isEmpty()) {
			Thread.sleep(10);
		}
		assertFalse(coordinator.ready(central(OTHER_KEY), DESCRIPTION));
		holdEnrollAnswer.countDown();
		while (enrollBodies.size() < 2) {
			Thread.sleep(10);
		}
		Thread.sleep(1_000);

		assertFalse(coordinator.isReady(), "the new key waits its Retry-After");
		coordinator.ready(CentralConfig.builder().enabled(true).build(), DESCRIPTION);
		coordinator.awaitWorker();
	}

	@Test
	void aResponseFromBeforeACertificateChangeShouldNotBeKept() throws Exception {
		holdEnrollAnswer = new CountDownLatch(1);
		final EnrollmentCoordinator coordinator = coordinator(new AgentCredentialStore(directory));
		assertFalse(coordinator.ready(central(KEY), DESCRIPTION));
		while (enrollBodies.isEmpty()) {
			Thread.sleep(10);
		}
		final CentralConfig recertified = central(KEY);
		recertified.setCertificateFile("");
		assertFalse(coordinator.ready(recertified, DESCRIPTION));
		holdEnrollAnswer.countDown();
		coordinator.awaitWorker();

		assertTrue(coordinator.isReady());
		assertEquals(1, recoverRequests.get(), "the identity is asked again with the new certificate");
	}

	@Test
	void aNewCertificateShouldNotWaitForTheFormerBackoff() throws Exception {
		script.add(new Answer(429, "{}", "30"));
		final EnrollmentCoordinator coordinator = coordinator(new AgentCredentialStore(directory));
		assertFalse(coordinator.ready(central(KEY), DESCRIPTION));
		while (enrollBodies.isEmpty()) {
			Thread.sleep(10);
		}
		Thread.sleep(200);

		final long changedAt = System.nanoTime();
		final CentralConfig recertified = central(KEY);
		recertified.setCertificateFile("");
		coordinator.ready(recertified, DESCRIPTION);
		coordinator.awaitWorker();

		assertTrue(coordinator.isReady());
		assertTrue(Duration.ofNanos(System.nanoTime() - changedAt).toSeconds() < 10, "not after the former Retry-After");
	}

	@Test
	void anUnboundedRetryAfterShouldNotEndTheWorker() throws Exception {
		script.add(new Answer(503, "{}", String.valueOf(Long.MAX_VALUE)));
		final EnrollmentCoordinator coordinator = coordinator(new AgentCredentialStore(directory));
		assertFalse(coordinator.ready(central(KEY), DESCRIPTION));
		while (enrollBodies.isEmpty()) {
			Thread.sleep(10);
		}

		final Thread waiting = new Thread(() -> {
			try {
				coordinator.awaitWorker();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
		waiting.start();
		waiting.join(1_000);

		assertTrue(waiting.isAlive(), "the worker waits, it did not die");
		coordinator.ready(CentralConfig.builder().enabled(true).build(), DESCRIPTION);
		waiting.join();
	}

	@Test
	void anEnrolledAgentShouldIgnoreItsKeyWhateverItHolds() throws Exception {
		assertTrue(enrollWith(coordinator(new AgentCredentialStore(directory)), central(KEY)));

		EnrollmentCoordinator.checkConfiguration(central("edited key"), () -> new AgentCredentialStore(directory));
	}

	@ParameterizedTest
	@ValueSource(strings = { "https://central.example.com:99999", "https://central.example.com:0" })
	void anIssuerPortOutOfRangeShouldRefuseTheStartup(final String issuerUrl) {
		final CentralConfig central = central(KEY);
		central.getAuth().setIssuerUrl(issuerUrl);

		final IllegalStateException refusal = assertThrows(
			IllegalStateException.class,
			() -> EnrollmentCoordinator.checkConfiguration(central, () -> new AgentCredentialStore(directory))
		);
		assertTrue(refusal.getMessage().contains("central.auth.issuerUrl"), refusal.getMessage());
	}

	@Test
	void aRefusedKeyShouldNotBeSentAgainAfterAnUnrelatedReload() throws Exception {
		script.add(new Answer(410, "{\"code\":\"enrollment_expired\"}", null));
		final EnrollmentCoordinator coordinator = coordinator(new AgentCredentialStore(directory));
		assertFalse(enrollWith(coordinator, central(KEY)));

		// Only Central's certificate changed
		final CentralConfig reloaded = central(KEY);
		reloaded.setCertificateFile("/tmp/central-ca.pem");
		assertFalse(coordinator.ready(reloaded, DESCRIPTION));
		coordinator.awaitWorker();

		assertEquals(1, enrollBodies.size(), "the refused key is never sent again");
	}

	@Test
	void aMalformedClientSecretShouldNeverBeStored() throws Exception {
		script.add(
			new Answer(201, "{\"agent_uid\":\"0192a6f4-7c1e-7b3a-9f20-4d5e6a7b8c91\",\"client_secret\":12345}", null)
		);
		final AgentCredentialStore store = new AgentCredentialStore(directory);

		assertTrue(enrollWith(coordinator(store), central(KEY)));

		final LocalIdentity identity = store.read();
		assertNotEquals("0192a6f4-7c1e-7b3a-9f20-4d5e6a7b8c91", identity.agentUid(), "the malformed answer was not kept");
		assertEquals(attempts.values().iterator().next().secret(), new String(identity.secret()));
	}

	@Test
	void aRefusalWithAnEmptyBodyShouldStopToo() throws Exception {
		script.add(new Answer(401, "", null));

		assertFalse(enrollWith(coordinator(new AgentCredentialStore(directory)), central(KEY)));

		assertEquals(1, enrollBodies.size(), "never retried with the key");
	}

	@Test
	void centralAuthBackWhileTheStoppedWorkerEndsShouldStartANewOne() throws Exception {
		final CountDownLatch writing = new CountDownLatch(1);
		final CountDownLatch release = new CountDownLatch(1);
		final AtomicBoolean first = new AtomicBoolean(true);
		// The first worker is held in its first write, deaf to its interruption: it is still ending
		final AgentCredentialStore store = new AgentCredentialStore(directory, written -> {
			if (first.getAndSet(false)) {
				writing.countDown();
				boolean interrupted = false;
				while (true) {
					try {
						release.await();
						break;
					} catch (InterruptedException e) {
						interrupted = true;
					}
				}
				if (interrupted) {
					Thread.currentThread().interrupt();
				}
			}
		});
		final EnrollmentCoordinator coordinator = coordinator(store);
		assertFalse(coordinator.ready(central(KEY), DESCRIPTION));
		assertTrue(writing.await(10, TimeUnit.SECONDS));

		assertTrue(coordinator.ready(CentralConfig.builder().enabled(true).build(), DESCRIPTION));
		// Released later from another thread: reading the store waits for the write it is held in
		final Thread releaser = new Thread(() -> {
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			release.countDown();
		});
		releaser.start();
		assertFalse(coordinator.ready(central(KEY), DESCRIPTION));
		releaser.join();
		coordinator.awaitWorker();

		assertTrue(coordinator.isReady(), "a new worker enrolled");
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
		// The whole flow at TRACE: the coordinator, the store and the token provider share this package
		final String fleet = EnrollmentCoordinator.class.getPackageName();
		final LoggerConfig logger = new LoggerConfig(fleet, Level.TRACE, false);
		logger.addAppender(appender, Level.TRACE, null);
		context.getConfiguration().addLogger(fleet, logger);
		context.updateLoggers();
		try {
			loseNextAnswer.set(true);
			script.add(new Answer(503, "{\"code\":\"" + KEY + "\"}", null));
			assertTrue(enrollWith(coordinator(new AgentCredentialStore(directory)), central(KEY)));
			script.add(new Answer(410, "{\"code\":\"enrollment_expired\"}", null));
			assertFalse(enrollWith(coordinator(new AgentCredentialStore(otherDirectory)), central(KEY)));
		} finally {
			context.getConfiguration().removeLogger(fleet);
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
		assertFalse(tokensHandedOut.isEmpty());
		for (final String token : tokensHandedOut) {
			assertFalse(logs.contains(token), logs);
			assertFalse(logs.contains(token.substring(token.indexOf('.') + 1)), logs);
		}
	}
}
