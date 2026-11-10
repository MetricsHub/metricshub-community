package org.metricshub.agent.fleet;

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

import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_HOST_NAME_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_OS_TYPE_ATTRIBUTE_KEY;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.config.CentralAuthConfig;
import org.metricshub.agent.config.CentralConfig;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.fleet.AgentCredentialStore.LocalIdentity;
import org.metricshub.agent.fleet.AgentCredentialStore.Status;
import org.metricshub.opamp.client.http.OpampHttpTransport;
import org.metricshub.opamp.client.retry.RetrySchedule;
import org.metricshub.web.service.ApplicationStatusService;

/**
 * Enrolls the agent into Central once, at its first start, when {@code central.auth} is configured, before any
 * OpAMP or tunnel connection (spec 02 §8, §9.1). One per process: it survives the restarts of the agent context.
 * <p>
 * With no local identity, it records a new attempt (an {@code attempt_id} and a {@code recovery_secret}) in the
 * {@link AgentCredentialStore} first, then calls {@code POST /v1/agents/enroll} with the enrollment key. With an
 * attempt already recorded (an answer lost, a crash between two writes), it recovers the same attempt with its
 * recovery proof, and enrolls again under the same {@code attempt_id} when Central has nothing to recover: never a
 * second identity. A refusal of the key or the attempt stops it until the key changes; a network error, a
 * {@code 429} or a {@code 5xx} is retried with an exponential backoff, {@code Retry-After} honoured.
 * </p>
 * <p>
 * Once enrolled, it owns the process's {@link CentralTokenProvider}: no token is requested before, and the recovery
 * proof leaves the KeyStore after the first token (retried after the next one when that deletion fails). The
 * enrollment key is never removed from {@code metricshub.yaml}, which the agent never rewrites.
 * </p>
 * <p>
 * Neither the key, the recovery proof, the client secret nor a request or answer body is ever logged.
 * </p>
 */
@Slf4j
public class EnrollmentCoordinator {

	private static final Duration MAX_BACKOFF = Duration.ofMinutes(5);
	private static final Duration MAX_RETRY_AFTER = Duration.ofHours(1);
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
	private static final ObjectMapper JSON = new ObjectMapper();
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();
	private static final Pattern RANDOM_SECRET = Pattern.compile("^[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]$");
	private static final Set<String> ERROR_CODES = Set.of(
		"invalid_request",
		"enrollment_not_authorized",
		"enrollment_expired",
		"enrollment_limit_reached",
		"idempotency_conflict",
		"attempt_closed",
		"recovery_not_authorized",
		"too_many_requests",
		"unavailable"
	);
	private static final String NEW_COMMAND = "Generate a new Install New Agent command in MetricsHub Central and set its key in central.auth.enrollmentKey.";

	private static EnrollmentCoordinator global;

	/**
	 * What one enrollment runs with. Its {@code toString()} never shows the key.
	 *
	 * @param issuerUrl       Central's issuer URL
	 * @param enrollmentKey   the enrollment key, or {@code null}
	 * @param certificateFile the PEM file that verifies Central, or {@code null}
	 * @param description     what the agent declares about its host
	 */
	record Settings(String issuerUrl, String enrollmentKey, String certificateFile, Map<String, String> description) {
		@Override
		public String toString() {
			return "Settings[issuerUrl=" + issuerUrl + "]";
		}
	}

	/**
	 * The identity an enrollment or a recovery answered.
	 *
	 * @param agentUid the agent uid
	 * @param secret   the client secret
	 */
	private record EnrolledAgent(String agentUid, char[] secret) {}

	/**
	 * Central refused the key or the attempt (logged): nothing is retried until the configuration changes.
	 */
	private static final class RefusedException extends IOException {

		private static final long serialVersionUID = 1L;
	}

	/**
	 * A failure retried after the backoff, never shorter than {@code floor}. Its message never quotes an answer.
	 */
	private static final class RetryableException extends IOException {

		private static final long serialVersionUID = 1L;

		private final transient Duration floor;

		RetryableException(final String message, final Duration floor) {
			super(message);
			this.floor = floor;
		}
	}

	private final Supplier<AgentCredentialStore> storeFactory;
	private AgentCredentialStore store;
	private final RetrySchedule retrySchedule;

	private volatile boolean ready;
	/**
	 * Whether the configuration still asks for the enrollment: an answer is never stored once a reload removed
	 * {@code central.auth}.
	 */
	private volatile boolean enrollmentWanted;
	/** The worker's backoff waits on it; a new issuer or key wakes it up. */
	private final Object backoff = new Object();
	private boolean settingsChanged;
	private volatile Settings settings;
	private Thread worker;
	/** The last worker stopped by a reload, until it ends (tests wait for it). */
	private Thread stopping;
	private Settings stoppedWith;
	private CentralTokenProvider tokenProvider;
	private volatile boolean recoveryCleared;
	private volatile String agentUid;
	private volatile long tokenGeneration;
	/** Whether the worker got the first token, or learnt there will be none (a revoked agent). */
	private volatile boolean firstTokenDone;

	/**
	 * Creates a coordinator (tests).
	 *
	 * @param storeFactory creates the credential store, the first time an enrollment is configured
	 * @param baseBackoff the first delay of the backoff
	 * @param random      the jitter's random source
	 */
	EnrollmentCoordinator(
		final Supplier<AgentCredentialStore> storeFactory,
		final Duration baseBackoff,
		final Random random
	) {
		this.storeFactory = storeFactory;
		this.retrySchedule = new RetrySchedule(baseBackoff, MAX_BACKOFF, random);
	}

	/**
	 * @return the coordinator of this process, on the MetricsHub {@code security} directory
	 */
	public static synchronized EnrollmentCoordinator global() {
		if (global == null) {
			global = new EnrollmentCoordinator(AgentCredentialStore::standard, Duration.ofSeconds(1), new Random());
		}
		return global;
	}

	/**
	 * Says whether the channels toward Central may open, and starts the enrollment in the background when it is
	 * needed and not running. Without {@code central.auth}, or with Central disabled, nothing is needed: the agent
	 * behaves as before.
	 *
	 * @param agentContext the current agent context
	 * @return {@code true} when no enrollment is configured or the agent is enrolled
	 */
	public boolean ready(final AgentContext agentContext) {
		final CentralConfig central = agentContext.getAgentConfig().getCentral();
		if (!isConfigured(central)) {
			// Stops an enrollment that a reload no longer asks for
			return ready(central, Map.of());
		}
		final Map<String, String> attributes = agentContext.getAgentInfo().getAttributes();
		final Map<String, String> description = new LinkedHashMap<>();
		description.put("hostname", clean(attributes.get(AGENT_RESOURCE_HOST_NAME_ATTRIBUTE_KEY), false, 253, "unknown"));
		description.put("os", clean(attributes.get(AGENT_RESOURCE_OS_TYPE_ATTRIBUTE_KEY), true, 32, "unknown"));
		description.put("architecture", clean(System.getProperty("os.arch"), true, 32, "unknown"));
		description.put(
			"agent_version",
			clean(agentContext.getAgentInfo().getApplicationProperties().project().version(), false, 64, "unknown")
		);
		description.put("edition", clean(ApplicationStatusService.determineLicenseType(agentContext), true, 32, "unknown"));
		return ready(central, description);
	}

	/**
	 * {@link #ready(AgentContext)} with the description already built.
	 *
	 * @param central     the {@code central} configuration
	 * @param description what the agent declares about its host
	 * @return {@code true} when no enrollment is configured or the agent is enrolled
	 */
	synchronized boolean ready(final CentralConfig central, final Map<String, String> description) {
		if (!isConfigured(central)) {
			enrollmentWanted = false;
			// The enrollment, or the first token after it
			if (worker != null && worker.isAlive()) {
				log.info("central.auth is no longer configured: the enrollment into MetricsHub Central stops.");
				worker.interrupt();
				// Forgotten at once: if central.auth comes back while it is still ending, a new worker starts
				stopping = worker;
				worker = null;
			}
			return true;
		}
		enrollmentWanted = true;
		final CentralAuthConfig auth = central.getAuth();
		final Settings latest = new Settings(
			auth.getIssuerUrl(),
			auth.getEnrollmentKey(),
			central.getCertificateFile(),
			Map.copyOf(description)
		);
		final Settings previous = settings;
		// Published before the worker is woken: it reads the new settings when it goes on
		settings = latest;
		if (
			worker != null &&
				worker.isAlive() &&
				previous != null &&
				(!Objects.equals(previous.issuerUrl(), latest.issuerUrl()) ||
					!Objects.equals(previous.enrollmentKey(), latest.enrollmentKey()) ||
					!Objects.equals(previous.certificateFile(), latest.certificateFile()))
		) {
			// Wakes the worker (enrolling, or waiting for the first token) from a backoff the former issuer, key or
			// certificate earned: the new ones are used at once
			synchronized (backoff) {
				settingsChanged = true;
				backoff.notifyAll();
			}
		}
		if (ready) {
			if (
				previous != null &&
					(!Objects.equals(latest.issuerUrl(), previous.issuerUrl()) ||
						!Objects.equals(latest.certificateFile(), previous.certificateFile()))
			) {
				// A reload moved the issuer or its certificate: the next token comes from there
				if (tokenProvider != null) {
					tokenProvider.shutdown();
				}
				tokenProvider = null;
				tokenGeneration++;
				if (!recoveryCleared) {
					// The recovery proof goes after the first token of the new issuer
					firstTokenDone = false;
				}
			}
			if (!firstTokenDone && (worker == null || !worker.isAlive())) {
				// Stopped by a reload that removed central.auth before the first token: resumed now it is back
				startWorker();
			}
			return true;
		}
		if (store == null) {
			store = storeFactory.get();
		}
		if (worker == null) {
			// A restart of an enrolled agent opens its channels at once, without waiting for the worker
			try {
				final LocalIdentity identity = store.read();
				wipe(identity.secret());
				wipe(identity.recoverySecret());
				if (identity.status() == Status.READY) {
					agentUid = identity.agentUid();
					ready = true;
					// Still started, for the first token: a recovery proof left by a crash goes after it
					startWorker();
					return true;
				}
			} catch (IOException e) {
				log.debug("Cannot read the agent identity yet: {}", e.getMessage());
			}
		} else if (worker.isAlive()) {
			return false;
		} else if (refusedAgain(latest)) {
			// Stopped by a refusal that only a new configuration (a new key) can change, unless another process sharing
			// the security directory completed the identity meanwhile
			try {
				final LocalIdentity identity = store.read();
				wipe(identity.secret());
				wipe(identity.recoverySecret());
				if (identity.status() == Status.READY) {
					agentUid = identity.agentUid();
					ready = true;
					// As at a restart: the first token, and the recovery proof after it
					startWorker();
					return true;
				}
			} catch (IOException e) {
				log.debug("Cannot read the agent identity: {}", e.getMessage());
			}
			return false;
		}
		startWorker();
		return false;
	}

	private void startWorker() {
		retrySchedule.reset();
		synchronized (backoff) {
			// A wake-up meant for a former worker never cuts this one's first backoff
			settingsChanged = false;
		}
		worker = new Thread(this::run, "metricshub-central-enrollment");
		worker.setDaemon(true);
		worker.start();
	}

	/**
	 * Returns the agent's access token, once the agent is enrolled. The recovery proof of the enrollment is deleted
	 * after the first token of the process: Central no longer recovers that attempt once the agent authenticated.
	 *
	 * @return the access token
	 * @throws CentralTokenProvider.InvalidClientException when Central refused the agent's credential
	 * @throws IOException                                 when the agent is not enrolled, or no token is available yet
	 * @throws InterruptedException                        when interrupted while waiting for Central
	 */
	public String currentToken() throws IOException, InterruptedException {
		final CentralTokenProvider provider = tokenProvider();
		final String token;
		try {
			token = provider.currentToken();
		} catch (CentralTokenProvider.InvalidClientException e) {
			// Under the lock ready() replaces the provider with: a refusal of a replaced provider never ends the work
			// the new one has to do
			synchronized (this) {
				if (provider != currentProvider()) {
					// The former issuer refused: the new one is asked next
					throw new IOException("The issuer changed while its token was asked.");
				}
				// Revoked: there will be no first token, and nothing is retried
				firstTokenDone = true;
			}
			throw e;
		}
		// Defence: Central binds each token to the agent uid; a channel never presents another agent's
		final String tokenUid = CentralTokenProvider.claims(token).path("agent_uid").asText(null);
		if (!agentUid.equals(tokenUid)) {
			provider.invalidate();
			log.error(
				"MetricsHub Central issued a token for agent {} to agent {}: it is refused and nothing connects with it.",
				tokenUid,
				agentUid
			);
			throw new IOException("The token Central issued is not this agent's.");
		}
		// Under the lock ready() replaces the provider with: the check and the deletion of the recovery proof below are
		// one step, so a reload never falls between them
		synchronized (this) {
			if (provider != currentProvider()) {
				// Obtained from the former issuer: neither used nor counted as the first token of the new one
				throw new IOException("The issuer changed while its token was asked.");
			}
			clearRecoveryOnce();
		}
		return token;
	}

	/**
	 * Deletes the recovery proof after the first token; a failure is retried after the next one.
	 */
	private void clearRecoveryOnce() {
		if (!recoveryCleared) {
			try {
				store.clearRecovery();
				recoveryCleared = true;
			} catch (IOException e) {
				log.warn("Cannot delete the enrollment recovery proof yet: {}. Retried after the next token.", e.getMessage());
			}
		}
	}

	/**
	 * @return whether the first token came, or a refusal said none ever will (tests)
	 */
	boolean isFirstTokenDone() {
		return firstTokenDone;
	}

	synchronized CentralTokenProvider currentProvider() {
		return tokenProvider;
	}

	/**
	 * Drops the cached token, so the next {@link #currentToken()} requests a new one (after a {@code 401}). Does
	 * nothing before the first token.
	 */
	public synchronized void invalidateToken() {
		if (tokenProvider != null) {
			tokenProvider.invalidate();
		}
	}

	/**
	 * @return the generation of the token provider, which changes when a reload moves the issuer or its certificate
	 */
	public long tokenGeneration() {
		return tokenGeneration;
	}

	/**
	 * The headers of a request to Central: the given ones with {@code Authorization: Bearer <current token>}.
	 * For the channels' header suppliers, which cannot throw a checked exception.
	 *
	 * @param headers    the channel's static headers
	 * @param generation the {@link #tokenGeneration()} the channel was built with: once the issuer moved, a channel
	 *                   still open toward the former Central gets no token of the new one, until it is rebuilt
	 * @return a new map with the bearer token
	 * @throws UncheckedIOException when no token is available, its cause a
	 *                              {@link CentralTokenProvider.InvalidClientException} when the agent is revoked
	 */
	public Map<String, String> headers(final Map<String, String> headers, final long generation) {
		if (generation != tokenGeneration) {
			throw new UncheckedIOException(new IOException("The issuer changed: the channel is rebuilt for it."));
		}
		try {
			final String token = currentToken();
			// Checked again once the token is there: a reload may have replaced the provider while it was asked, and a
			// token of the new issuer never goes to a channel still open toward the former Central
			if (generation != tokenGeneration) {
				throw new IOException("The issuer changed: the channel is rebuilt for it.");
			}
			final Map<String, String> result = new HashMap<>(headers);
			result.put("Authorization", "Bearer " + token);
			return result;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new UncheckedIOException(new InterruptedIOException("Interrupted while waiting for a token."));
		}
	}

	/**
	 * @return when the current token expires, counted on the local clock from its reception, or {@code null}
	 */
	public synchronized Instant tokenExpiresAt() {
		return tokenProvider == null ? null : tokenProvider.expiresAt();
	}

	/**
	 * @return the uid the agent was enrolled with, or {@code null} before {@link #isReady()}
	 */
	public String agentUid() {
		return agentUid;
	}

	/**
	 * @param agentContext the current agent context
	 * @return whether the channels toward Central authenticate with the enrolled identity's token
	 */
	public static boolean usesToken(final AgentContext agentContext) {
		return isConfigured(agentContext.getAgentConfig().getCentral());
	}

	/**
	 * @return the process's token provider, created the first time the agent is enrolled, and again after a reload
	 *         changed the issuer or its certificate
	 * @throws IOException when the agent is not enrolled, or the certificate file cannot be read
	 */
	private synchronized CentralTokenProvider tokenProvider() throws IOException {
		if (!ready || store == null) {
			throw new IOException("The agent is not enrolled into MetricsHub Central yet.");
		}
		if (tokenProvider == null) {
			// Built from the current settings; ready() drops it when a reload changes them
			final Settings current = settings;
			final AgentCredentialStore credentials = store;
			tokenProvider = new CentralTokenProvider(current.issuerUrl().trim(), () -> {
				try {
					final LocalIdentity identity = credentials.read();
					if (identity.status() != Status.READY) {
						throw new IOException("The agent identity is not complete.");
					}
					return new CentralTokenProvider.Credential(identity.agentUid(), identity.secret());
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}, current.certificateFile());
		}
		return tokenProvider;
	}

	/**
	 * Waits for the background enrollment to stop (tests).
	 *
	 * @throws InterruptedException when interrupted
	 */
	void awaitWorker() throws InterruptedException {
		final Thread current;
		final Thread stopped;
		synchronized (this) {
			current = worker;
			stopped = stopping;
		}
		if (stopped != null) {
			stopped.join();
		}
		if (current != null) {
			current.join();
		}
	}

	/**
	 * @return whether the agent is enrolled
	 */
	public boolean isReady() {
		return ready;
	}

	/**
	 * @param central the {@code central} configuration a channel is about to open with
	 * @return whether the agent is enrolled and the coordinator already runs with that configuration's issuer and
	 *         certificate: a channel never opens for a Central the coordinator has not reconciled yet
	 */
	public boolean isReadyFor(final CentralConfig central) {
		final Settings current = settings;
		return (ready &&
			current != null &&
			central != null &&
			central.getAuth() != null &&
			Objects.equals(current.issuerUrl(), central.getAuth().getIssuerUrl()) &&
			Objects.equals(current.certificateFile(), central.getCertificateFile()));
	}

	/**
	 * Refuses a {@code central.auth} the agent cannot run with, naming the key to fix. Called when the agent context
	 * is built, so a wrong configuration refuses the startup.
	 *
	 * @param central the {@code central} configuration
	 * @param store   the credential store, created only when {@code central.auth} is configured
	 * @throws IOException              when the local identity cannot be read
	 * @throws IllegalStateException    when the configuration is refused
	 */
	public static void checkConfiguration(final CentralConfig central, final Supplier<AgentCredentialStore> store)
		throws IOException {
		if (!isConfigured(central)) {
			return;
		}
		final CentralAuthConfig auth = central.getAuth();
		final String issuerUrl = auth.getIssuerUrl();
		if (issuerUrl == null || issuerUrl.isBlank()) {
			throw new IllegalStateException("central.auth.issuerUrl is required when central.auth is configured.");
		}
		final URI issuer;
		try {
			issuer = URI.create(issuerUrl.trim());
		} catch (IllegalArgumentException e) {
			throw new IllegalStateException("central.auth.issuerUrl is not a valid URL.", e);
		}
		if (!CentralTokenProvider.isAcceptable(issuer)) {
			throw new IllegalStateException(
				"central.auth.issuerUrl must use https:// (http:// is accepted for a loopback address only)."
			);
		}
		final String path = issuer.getRawPath();
		if (
			issuer.getRawUserInfo() != null ||
				issuer.getPort() == 0 ||
				issuer.getPort() > 65535 ||
				issuer.getRawQuery() != null ||
				issuer.getRawFragment() != null ||
				(path != null && !path.isEmpty() && !"/".equals(path))
		) {
			throw new IllegalStateException(
				"central.auth.issuerUrl must be an origin, scheme://host[:port], without a path, a query or a fragment."
			);
		}
		if (
			central.opamp().getHeaders().get("Authorization") != null ||
				central.tunnel().getHeaders().get("Authorization") != null
		) {
			throw new IllegalStateException(
				"central.auth and a static Authorization header (central.headers, central.opamp.headers or " +
					"central.tunnel.headers) cannot both be configured: remove the Authorization header."
			);
		}
		final LocalIdentity identity = store.get().read();
		wipe(identity.secret());
		wipe(identity.recoverySecret());
		if (identity.status() == Status.INCOMPLETE && identity.attemptId() == null) {
			throw new IllegalStateException(
				"The agent identity in " +
					store.get().uidFile().getParent() +
					" is incomplete and no enrollment attempt can be resumed: delete the agent identity from that directory, " +
					"then set a new key in central.auth.enrollmentKey. " +
					NEW_COMMAND
			);
		}
		final String key = auth.getEnrollmentKey();
		// Only a key the agent may still send: once enrolled, it is ignored, whatever it holds
		if (identity.status() != Status.READY && key != null && !key.isBlank() && !key.trim().matches("[!-~]+")) {
			// Never quoted: it is a secret
			throw new IllegalStateException(
				"central.auth.enrollmentKey is not a valid key: it may only hold printable characters, without spaces. " +
					NEW_COMMAND
			);
		}
		if (identity.status() == Status.ABSENT && (key == null || key.isBlank())) {
			throw new IllegalStateException(
				"central.auth.enrollmentKey is required: this agent is not enrolled into Central yet. " + NEW_COMMAND
			);
		}
	}

	/**
	 * What of {@code central.auth} a channel reconnects for: the issuer and Central's certificate, and, until the agent
	 * is enrolled, the enrollment key too, so a new key reaches the enrollment whatever the channels. Once enrolled,
	 * the key is ignored: replacing or removing it reconnects nothing.
	 *
	 * @param central  the {@code central} configuration
	 * @param enrolled whether the agent is enrolled
	 * @return the values to compare, {@code null} without {@code central.auth}
	 */
	public static List<String> authState(final CentralConfig central, final boolean enrolled) {
		if (central == null || central.getAuth() == null) {
			return null;
		}
		final CentralAuthConfig auth = central.getAuth();
		return Arrays.asList(auth.getIssuerUrl(), central.getCertificateFile(), enrolled ? null : auth.getEnrollmentKey());
	}

	/**
	 * @param latest the settings now configured
	 * @return whether Central refused the enrollment with the same issuer and key: only a new key, or another issuer,
	 *         asks again
	 */
	private boolean refusedAgain(final Settings latest) {
		return (stoppedWith != null &&
			Objects.equals(latest.issuerUrl(), stoppedWith.issuerUrl()) &&
			Objects.equals(latest.enrollmentKey(), stoppedWith.enrollmentKey()) &&
			Objects.equals(latest.certificateFile(), stoppedWith.certificateFile()));
	}

	private static boolean isConfigured(final CentralConfig central) {
		return central != null && central.isEnabled() && central.getAuth() != null;
	}

	/**
	 * Runs the enrollment until it is done or refused, waiting the backoff after each failure.
	 */
	private void run() {
		try {
			while (true) {
				final Settings current = settings;
				try (HttpClient httpClient = CentralTokenProvider.httpClient(current.certificateFile())) {
					final String agentUid = enroll(current, httpClient);
					if (agentUid == null) {
						if (stopUnlessReplaced(current)) {
							return;
						}
						continue;
					}
					this.agentUid = agentUid;
					ready = true;
					log.info("The agent is enrolled into MetricsHub Central as {}.", agentUid);
					firstToken();
					return;
				} catch (RefusedException e) {
					if (stopUnlessReplaced(current)) {
						return;
					}
				} catch (RetryableException e) {
					pause("The enrollment into MetricsHub Central did not complete", e.getMessage(), e.floor);
				} catch (IOException e) {
					// The store's failures and the certificate file's, which quote no answer
					pause("The enrollment into MetricsHub Central did not complete", e.getMessage(), null);
				} catch (RuntimeException e) {
					pause("The enrollment into MetricsHub Central did not complete", e.getClass().getSimpleName(), null);
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Obtains the first token, which deletes the recovery proof, retrying with the token provider's own backoff
	 * until Central answers or refuses the credential, and until the proof is gone.
	 */
	private void firstToken() throws InterruptedException {
		retrySchedule.reset();
		while (enrollmentWanted) {
			try {
				currentToken();
				if (recoveryCleared) {
					firstTokenDone = true;
					return;
				}
				// The deletion failed: retried with the next token
				pause("The enrollment recovery proof is not deleted yet", "the KeyStore could not be written", null);
			} catch (CentralTokenProvider.InvalidClientException e) {
				// Logged by the provider, recorded by currentToken(): the agent is revoked, nothing is retried
				return;
			} catch (IOException e) {
				// The provider schedules its next attempt itself and names its failures without quoting an answer
				pause("The agent has no token from MetricsHub Central yet", e.getMessage(), null);
			}
		}
	}

	/**
	 * Stops the worker after a refusal, or a lack of key, that answered the settings still configured. When a reload
	 * replaced the issuer, the key or the certificate meanwhile, the worker goes on at once with the new ones instead,
	 * that reload's wake-up used up: it must not cut the backoff the new ones may earn.
	 *
	 * @param current the settings the stopped pass ran with
	 * @return whether the worker stops
	 */
	private boolean stopUnlessReplaced(final Settings current) {
		synchronized (this) {
			if (
				Objects.equals(current.issuerUrl(), settings.issuerUrl()) &&
					Objects.equals(current.enrollmentKey(), settings.enrollmentKey()) &&
					Objects.equals(current.certificateFile(), settings.certificateFile())
			) {
				stoppedWith = current;
				return true;
			}
		}
		synchronized (backoff) {
			settingsChanged = false;
		}
		// The new settings owe nothing to the failures of the former ones
		retrySchedule.reset();
		return false;
	}

	private void pause(final String step, final String failure, final Duration floor) throws InterruptedException {
		// A server's Retry-After is bounded: any value, however large, must stay a delay the worker can wait
		final Duration bounded = floor == null || floor.compareTo(MAX_RETRY_AFTER) <= 0 ? floor : MAX_RETRY_AFTER;
		final Duration delay = retrySchedule.nextDelayAfterFailure(bounded);
		log.warn("{}: {}. Next attempt in {}.", step, failure, delay);
		final long until = System.nanoTime() + delay.toNanos();
		synchronized (backoff) {
			long left;
			while (!settingsChanged && (left = until - System.nanoTime()) > 0) {
				TimeUnit.NANOSECONDS.timedWait(backoff, left);
			}
			if (settingsChanged) {
				// A new issuer or key owes nothing to the former one's backoff
				settingsChanged = false;
				retrySchedule.reset();
			}
		}
	}

	/**
	 * One pass of the enrollment.
	 *
	 * @param current    the settings
	 * @param httpClient the HTTP client
	 * @return the agent uid once enrolled, or {@code null} when refused
	 * @throws IOException          when the pass failed and is retried
	 * @throws InterruptedException when interrupted
	 */
	private String enroll(final Settings current, final HttpClient httpClient) throws IOException, InterruptedException {
		final LocalIdentity identity = store.read();
		wipe(identity.secret());
		switch (identity.status()) {
			case READY:
				return identity.agentUid();
			case ABSENT:
				if (current.enrollmentKey() == null || current.enrollmentKey().isBlank()) {
					log.error("The agent is not enrolled into MetricsHub Central and has no enrollment key. {}", NEW_COMMAND);
					return null;
				}
				final String attemptId = UUID.randomUUID().toString();
				final byte[] random = new byte[32];
				SECURE_RANDOM.nextBytes(random);
				final byte[] recoverySecret = Base64.getUrlEncoder()
					.withoutPadding()
					.encodeToString(random)
					.getBytes(StandardCharsets.US_ASCII);
				wipe(random);
				try {
					store.beginAttempt(attemptId, recoverySecret);
					log.info("Enrolling the agent into MetricsHub Central (attempt {}).", attemptId);
					return complete(current, post(current, httpClient, attemptId, recoverySecret, false));
				} finally {
					wipe(recoverySecret);
				}
			default:
				final byte[] proof = identity.recoverySecret();
				if (identity.attemptId() == null) {
					log.error(
						"The agent identity in the security directory is incomplete and no enrollment attempt can be resumed. " +
							"Delete the agent identity from the security directory, then: {}",
						NEW_COMMAND
					);
					return null;
				}
				try {
					log.info("Resuming the enrollment into MetricsHub Central (attempt {}).", identity.attemptId());
					EnrolledAgent answer = post(current, httpClient, identity.attemptId(), proof, true);
					if (answer == null) {
						if (current.enrollmentKey() == null || current.enrollmentKey().isBlank()) {
							log.error(
								"Central has no answer to recover for enrollment attempt {}, and the agent has no enrollment key. {}",
								identity.attemptId(),
								NEW_COMMAND
							);
							return null;
						}
						// Never committed, or its answer is no longer kept: the same attempt, never a new one
						answer = post(current, httpClient, identity.attemptId(), proof, false);
					}
					return complete(current, answer);
				} finally {
					wipe(proof);
				}
		}
	}

	private String complete(final Settings current, final EnrolledAgent answer) throws IOException, InterruptedException {
		if (answer == null) {
			return null;
		}
		try {
			// Under the lock ready() takes to change the settings, so no reload falls between the check and the write
			synchronized (this) {
				if (!enrollmentWanted) {
					throw new InterruptedException("central.auth is no longer configured");
				}
				if (
					!current.issuerUrl().equals(settings.issuerUrl()) ||
						!Objects.equals(current.certificateFile(), settings.certificateFile())
				) {
					// The configuration moved to another issuer, or trusts another certificate, meanwhile: never keep an
					// identity of the server it left; the same attempt is resumed there
					throw new RetryableException(
						"central.auth.issuerUrl or central.certificateFile changed during the enrollment",
						null
					);
				}
				store.complete(answer.agentUid(), answer.secret());
				// Published under the same lock: a reload cannot fall between the write and the readiness, and a channel
				// never sees the readiness without the uid
				agentUid = answer.agentUid();
				ready = true;
			}
		} finally {
			Arrays.fill(answer.secret(), '\0');
		}
		return answer.agentUid();
	}

	/**
	 * Enrolls with the key, or recovers with the recovery proof.
	 *
	 * @param current        the settings
	 * @param httpClient     the HTTP client
	 * @param attemptId      the attempt id
	 * @param recoverySecret the recovery proof, ASCII
	 * @param recover        whether to recover rather than enroll
	 * @return the identity; {@code null} when a recovery found nothing to recover
	 * @throws RefusedException     when Central refused the key or the attempt (logged)
	 * @throws IOException          when the request failed and is retried
	 * @throws InterruptedException when interrupted
	 */
	private EnrolledAgent post(
		final Settings current,
		final HttpClient httpClient,
		final String attemptId,
		final byte[] recoverySecret,
		final boolean recover
	) throws IOException, InterruptedException {
		final String base = current.issuerUrl().trim().replaceAll("/+$", "");
		final HttpRequest.Builder request = HttpRequest.newBuilder()
			.timeout(REQUEST_TIMEOUT)
			.header("Accept", "application/json");
		if (recover) {
			request.uri(URI.create(base + "/v1/enrollment-attempts/" + attemptId + "/recover"))
				.header("Authorization", "Bearer " + new String(recoverySecret, StandardCharsets.US_ASCII))
				.POST(HttpRequest.BodyPublishers.noBody());
		} else {
			final Map<String, Object> body = new LinkedHashMap<>();
			body.put("attempt_id", attemptId);
			body.put("recovery_secret", new String(recoverySecret, StandardCharsets.US_ASCII));
			body.put("description", current.description());
			request.uri(URI.create(base + "/v1/agents/enroll"))
				.header("Authorization", "Bearer " + current.enrollmentKey().trim())
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body)));
		}
		final HttpResponse<String> response;
		try {
			response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
		} catch (IOException e) {
			throw new RetryableException("Central is unreachable (" + e.getClass().getSimpleName() + ")", null);
		}
		final int status = response.statusCode();
		if (status == 200 || status == 201) {
			return accept(response.body());
		}
		if (status == 429 || status >= 500) {
			throw new RetryableException(
				"Central answered " + status,
				response.headers().firstValue("Retry-After").flatMap(OpampHttpTransport::parseRetryAfter).orElse(null)
			);
		}
		final String code = errorCode(response.body());
		// Only the recovery's own refusal means "nothing to recover"; another 401 (an intermediary) is a refusal
		if (recover && status == 401 && "recovery_not_authorized".equals(code)) {
			log.info("Central has no answer to recover for enrollment attempt {}: enrolling again with it.", attemptId);
			return null;
		}
		if (status == 400 || status == 401 || status == 409 || status == 410) {
			log.error(
				"Central refused the enrollment of this agent ({} {}): {} No new identity is created.",
				status,
				code,
				explain(code)
			);
			throw new RefusedException();
		}
		throw new RetryableException("Central answered " + status + " " + code, MAX_BACKOFF);
	}

	private static String explain(final String code) {
		return switch (Objects.requireNonNullElse(code, "")) {
			case "enrollment_not_authorized" ->
				"The enrollment key is not recognised, or its session is closed. " + NEW_COMMAND;
			case "enrollment_expired" -> "The enrollment key expired or was replaced. " + NEW_COMMAND;
			case "enrollment_limit_reached" -> "The installation session has no place left. " + NEW_COMMAND;
			case "idempotency_conflict", "attempt_closed" ->
				"The enrollment attempt cannot be resumed. " +
					"Delete the agent identity from the security directory, then: " +
					NEW_COMMAND;
			default -> NEW_COMMAND;
		};
	}

	private static EnrolledAgent accept(final String body) throws IOException {
		final JsonNode answer;
		try {
			answer = JSON.readTree(body);
		} catch (IOException e) {
			// Never its message, which may quote the answer
			throw new RetryableException("Central answered a body that is not JSON", null);
		}
		if (answer == null) {
			throw new RetryableException("Central answered an empty body", null);
		}
		final String agentUid = answer.path("agent_uid").asText("");
		final JsonNode secretNode = answer.path("client_secret");
		final String secret = secretNode.isTextual() ? secretNode.asText() : "";
		// The contract's RandomSecret: stored, a malformed one would leave the agent ready with a secret nothing accepts
		if (!RANDOM_SECRET.matcher(secret).matches()) {
			throw new RetryableException("Central answered no valid client secret", null);
		}
		try {
			if (!UUID.fromString(agentUid).toString().equals(agentUid)) {
				throw new IllegalArgumentException();
			}
		} catch (IllegalArgumentException e) {
			throw new RetryableException("Central answered an agent uid that is not a canonical UUID", null);
		}
		return new EnrolledAgent(agentUid, secret.toCharArray());
	}

	/**
	 * @param body an error answer
	 * @return its {@code code} when it is one of the contract's, else {@code "unknown"}: an answer may echo anything,
	 *         a secret included, and only these are ever logged
	 */
	private static String errorCode(final String body) {
		try {
			final JsonNode answer = JSON.readTree(body);
			// An empty body reads as no node at all
			final String code = answer == null ? "" : answer.path("code").asText("");
			return ERROR_CODES.contains(code) ? code : "unknown";
		} catch (IOException e) {
			return "unknown";
		}
	}

	/**
	 * Keeps a declared value within what Central accepts: printable ASCII without a space, or, for an identifier,
	 * lower-case letters, digits, {@code _} and {@code -}.
	 *
	 * @param value      the value
	 * @param identifier whether it is an identifier
	 * @param maxLength  its maximum length
	 * @param fallback   the value when nothing is left
	 * @return the cleaned value
	 */
	static String clean(final String value, final boolean identifier, final int maxLength, final String fallback) {
		if (value == null) {
			return fallback;
		}
		final String source = identifier ? value.toLowerCase(Locale.ROOT) : value;
		final String cleaned = source.replaceAll(identifier ? "[^a-z0-9_-]" : "[^!-~]", identifier ? "_" : "-");
		final String bounded = cleaned.length() > maxLength ? cleaned.substring(0, maxLength) : cleaned;
		return bounded.isEmpty() ? fallback : bounded;
	}

	private static void wipe(final byte[] secret) {
		if (secret != null) {
			Arrays.fill(secret, (byte) 0);
		}
	}

	private static void wipe(final char[] secret) {
		if (secret != null) {
			Arrays.fill(secret, '\0');
		}
	}
}
