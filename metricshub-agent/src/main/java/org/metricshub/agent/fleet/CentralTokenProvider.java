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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Random;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.tunnel.client.TunnelSettings;
import org.metricshub.opamp.client.http.OpampHttpTransport;
import org.metricshub.opamp.client.retry.RetrySchedule;

/**
 * Obtains and renews the agent's access token from Central, the OAuth issuer, with the
 * {@code client_credentials} grant and {@code client_secret_basic} ({@code client_id} = the agent uid).
 * <p>
 * The token stays in memory. {@link #currentToken()} returns the cached token until its renewal instant,
 * {@code max(T/2, T − M)} after it was received, {@code T} being its lifetime and {@code M} the renewal margin
 * ({@code invokeTimeout} + 2 minutes), minus a jitter of at most 30 seconds. Callers are served one at a time, so
 * concurrent callers share one request.
 * </p>
 * <p>
 * A refused or failed request is retried with an exponential backoff, {@code Retry-After} honoured; until then,
 * callers keep the cached token while it is valid, or fail at once without a request. An {@code invalid_client}
 * answer is terminal: the agent is revoked or unknown, every later call fails without a request, and nothing
 * here ever enrolls again.
 * </p>
 * <p>
 * Neither the secret, the {@code Authorization} header nor a token is ever logged; a token is named by its
 * {@code jti}.
 * </p>
 */
@Slf4j
public class CentralTokenProvider {

	/**
	 * {@code invokeTimeout} (5 minutes) + 2 minutes: the default renewal margin.
	 */
	public static final Duration DEFAULT_MARGIN = Duration.ofMinutes(7);

	private static final Duration MAX_JITTER = Duration.ofSeconds(30);
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration MAX_BACKOFF = Duration.ofMinutes(5);
	private static final String METADATA_PATH = "/.well-known/oauth-authorization-server";
	private static final ObjectMapper JSON = new ObjectMapper();

	/**
	 * The agent's credential, as stored at enrollment.
	 *
	 * @param agentUid the agent uid, used as {@code client_id}
	 * @param secret   the client secret
	 */
	public record Credential(String agentUid, char[] secret) {}

	/**
	 * Central refused the credential with {@code invalid_client}: the agent is revoked or unknown. Terminal.
	 */
	public static class InvalidClientException extends IOException {

		private static final long serialVersionUID = 1L;

		/**
		 * @param message the message, which never quotes an answer
		 */
		public InvalidClientException(final String message) {
			super(message);
		}
	}

	private final String issuer;
	private final Supplier<Credential> credentials;
	private final HttpClient httpClient;
	private final Clock clock;
	private final Random random;
	private final Duration margin;
	private final RetrySchedule retrySchedule;

	private URI tokenEndpoint;
	private String token;
	private Instant renewAt;
	private Instant expiresAt;
	private Instant nextAttemptAt = Instant.MIN;
	private String lastFailure;
	private boolean invalidClient;

	/**
	 * Creates a provider.
	 *
	 * @param issuer          Central's issuer URL, an origin without a path
	 * @param credentials     supplies the agent's credential at each request
	 * @param certificateFile the PEM file that verifies Central's certificate, or {@code null} for the system trust
	 *                        store
	 * @throws IOException              when the certificate file cannot be read
	 * @throws IllegalArgumentException when the issuer is neither {@code https} nor {@code http} on a loopback
	 *                                  literal: its metadata names where the credential goes
	 */
	public CentralTokenProvider(final String issuer, final Supplier<Credential> credentials, final String certificateFile)
		throws IOException {
		this(issuer, credentials, httpClient(certificateFile), Clock.systemUTC(), new Random(), DEFAULT_MARGIN);
	}

	/**
	 * Creates a provider with its collaborators (tests).
	 *
	 * @param issuer      Central's issuer URL
	 * @param credentials supplies the agent's credential
	 * @param httpClient  the HTTP client, which must not follow redirections
	 * @param clock       the clock
	 * @param random      the jitter's random source
	 * @param margin      the renewal margin {@code M}
	 */
	CentralTokenProvider(
		final String issuer,
		final Supplier<Credential> credentials,
		final HttpClient httpClient,
		final Clock clock,
		final Random random,
		final Duration margin
	) {
		if (!isAcceptable(URI.create(issuer))) {
			throw new IllegalArgumentException(
				"The issuer URL " + issuer + " must use https:// (http:// is accepted for a loopback address only)."
			);
		}
		this.issuer = issuer;
		this.credentials = credentials;
		this.httpClient = httpClient;
		this.clock = clock;
		this.random = random;
		this.margin = margin;
		this.retrySchedule = new RetrySchedule(Duration.ofSeconds(1), MAX_BACKOFF, random);
	}

	/**
	 * Builds the HTTP client of every request to Central's identity endpoints: no redirection is followed, and
	 * Central's certificate is verified with the given file when set.
	 *
	 * @param certificateFile the PEM file, or {@code null}
	 * @return the client
	 * @throws IOException when the certificate file cannot be read
	 */
	public static HttpClient httpClient(final String certificateFile) throws IOException {
		final HttpClient.Builder builder = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NEVER)
			.connectTimeout(Duration.ofSeconds(10));
		if (certificateFile != null && !certificateFile.isBlank()) {
			try {
				builder.sslContext(OpampHttpTransport.createSslContext(certificateFile));
			} catch (Exception e) {
				throw new IOException("Cannot read the certificate file " + certificateFile + ".", e);
			}
		}
		return builder.build();
	}

	/**
	 * Returns a valid token, renewing it when its renewal instant has passed.
	 *
	 * @return the access token
	 * @throws InvalidClientException when Central refused the credential, now or earlier
	 * @throws IOException            when no valid token is available, the next attempt being scheduled
	 * @throws InterruptedException   when interrupted while waiting for Central
	 */
	public synchronized String currentToken() throws IOException, InterruptedException {
		if (invalidClient) {
			throw new InvalidClientException("The agent credential was refused by Central (invalid_client).");
		}
		final Instant now = clock.instant();
		final boolean valid = token != null && now.isBefore(expiresAt);
		if (valid && now.isBefore(renewAt)) {
			return token;
		}
		if (now.isBefore(nextAttemptAt)) {
			if (valid) {
				return token;
			}
			throw new IOException("No token from Central yet: " + lastFailure + "; next attempt at " + nextAttemptAt + ".");
		}
		try {
			fetch();
			retrySchedule.reset();
			nextAttemptAt = Instant.MIN;
			return token;
		} catch (InvalidClientException e) {
			throw e;
		} catch (IOException | RuntimeException e) {
			// Anything else than a classified failure (an unreadable answer, a malformed endpoint) is retried with
			// the backoff too, and named by its type only: a parser's message may quote the answer, a token included
			final boolean classified = e instanceof RetryableException;
			lastFailure = classified ? e.getMessage() : "an unreadable answer (" + e.getClass().getSimpleName() + ")";
			final Duration floor = classified ? ((RetryableException) e).floor : null;
			nextAttemptAt = clock.instant().plus(retrySchedule.nextDelayAfterFailure(floor));
			// Checked again: the token may have expired while the request was waiting for Central
			if (token != null && clock.instant().isBefore(expiresAt)) {
				log.debug(
					"Token renewal failed: {}. The current token is kept; next attempt at {}.",
					lastFailure,
					nextAttemptAt
				);
				return token;
			}
			// Without the cause, which may quote the answer
			throw new IOException("No token from Central: " + lastFailure + "; next attempt at " + nextAttemptAt + ".");
		}
	}

	/**
	 * Drops the cached token, so the next {@link #currentToken()} requests a new one (after a {@code 401}).
	 */
	public synchronized void invalidate() {
		token = null;
	}

	/**
	 * A failure retried after the backoff, never shorter than {@code floor}.
	 */
	private static final class RetryableException extends IOException {

		private static final long serialVersionUID = 1L;

		private final transient Duration floor;

		RetryableException(final String message, final Duration floor) {
			super(message);
			this.floor = floor;
		}
	}

	private void fetch() throws IOException, InterruptedException {
		final URI endpoint = tokenEndpoint();
		final Credential credential = credentials.get();
		final HttpRequest request = HttpRequest.newBuilder(endpoint)
			.timeout(REQUEST_TIMEOUT)
			.header("Authorization", basic(credential))
			.header("Content-Type", "application/x-www-form-urlencoded")
			.header("Accept", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
			.build();
		final HttpResponse<String> response = send(request);
		final int status = response.statusCode();
		if (status == 200) {
			accept(response.body());
			return;
		}
		final String error = errorCode(response.body());
		if (status == 401 && "invalid_client".equals(error)) {
			invalidClient = true;
			log.error(
				"Central refused the credential of agent {} (invalid_client): the agent was revoked or deleted. " +
					"It stops connecting to Central and does not enroll again. To enroll this host as a new agent, " +
					"generate a new Install New Agent command, delete the agent identity from the security directory, " +
					"then restart the agent: it gets a new agent uid.",
				credential.agentUid()
			);
			throw new InvalidClientException("The agent credential was refused by Central (invalid_client).");
		}
		if (status == 400 && "unauthorized_client".equals(error)) {
			log.warn(
				"Central refused a token to agent {}: its workspace is suspended. Retrying later.",
				credential.agentUid()
			);
			throw new RetryableException("the workspace is suspended (unauthorized_client)", null);
		}
		if (status == 429 || status >= 500) {
			throw new RetryableException("the token endpoint answered " + status, retryAfter(response));
		}
		log.error(
			"Central refused a token to agent {} with status {} and error {}. Retrying in {} at the latest.",
			credential.agentUid(),
			status,
			error,
			MAX_BACKOFF
		);
		throw new RetryableException("the token endpoint answered " + status + " " + error, MAX_BACKOFF);
	}

	private void accept(final String body) throws IOException {
		final JsonNode answer = JSON.readTree(body);
		final String accessToken = answer.path("access_token").asText(null);
		if (accessToken == null || !"bearer".equalsIgnoreCase(answer.path("token_type").asText())) {
			throw new RetryableException("the token endpoint answered no bearer token", null);
		}
		final JsonNode claims = claims(accessToken);
		final long issuedAt = claims.path("iat").asLong(0);
		final long expiry = claims.path("exp").asLong(0);
		final long lifetime = expiry > issuedAt && issuedAt > 0 ? expiry - issuedAt : answer.path("expires_in").asLong(0);
		if (lifetime <= 0) {
			throw new RetryableException("the token carries no lifetime", null);
		}
		// Counted from the local clock at reception, never from iat: the agent's clock may differ from Central's
		final Instant received = clock.instant();
		final long renewAfter = Math.max(lifetime / 2, lifetime - margin.toSeconds());
		final long jitterMs = (long) (random.nextDouble() * Math.min(MAX_JITTER.toMillis(), lifetime * 1000 / 10));
		token = accessToken;
		expiresAt = received.plusSeconds(lifetime);
		renewAt = received.plusSeconds(renewAfter).minusMillis(jitterMs);
		log.debug(
			"Obtained token {} from Central, valid {} s, renewed at {}.",
			claims.path("jti").asText("?"),
			lifetime,
			renewAt
		);
	}

	private URI tokenEndpoint() throws IOException, InterruptedException {
		if (tokenEndpoint != null) {
			return tokenEndpoint;
		}
		final String base = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
		final HttpResponse<String> response = send(
			HttpRequest.newBuilder(URI.create(base + METADATA_PATH))
				.timeout(REQUEST_TIMEOUT)
				.header("Accept", "application/json")
				.GET()
				.build()
		);
		if (response.statusCode() != 200) {
			throw new RetryableException("the issuer metadata answered " + response.statusCode(), retryAfter(response));
		}
		final JsonNode metadata = JSON.readTree(response.body());
		final String announcedIssuer = metadata.path("issuer").asText("");
		if (
			!base.equals(
				announcedIssuer.endsWith("/") ? announcedIssuer.substring(0, announcedIssuer.length() - 1) : announcedIssuer
			)
		) {
			log.error(
				"The issuer metadata at {} names the issuer {}, not the configured one.",
				base + METADATA_PATH,
				announcedIssuer
			);
			throw new RetryableException("the issuer metadata names another issuer", MAX_BACKOFF);
		}
		final URI endpoint = URI.create(metadata.path("token_endpoint").asText(""));
		if (!isAcceptable(endpoint)) {
			log.error("The token endpoint {} is neither https nor http on a loopback address.", endpoint);
			throw new RetryableException("the token endpoint is not https", MAX_BACKOFF);
		}
		tokenEndpoint = endpoint;
		return endpoint;
	}

	/**
	 * @param endpoint the token endpoint
	 * @return whether it is {@code https}, or {@code http} on a loopback literal (the tunnel's rule)
	 */
	static boolean isAcceptable(final URI endpoint) {
		if (endpoint.getHost() == null) {
			return false;
		}
		if ("https".equalsIgnoreCase(endpoint.getScheme())) {
			return true;
		}
		if (!"http".equalsIgnoreCase(endpoint.getScheme())) {
			return false;
		}
		return TunnelSettings.isLiteralLoopback(endpoint.getHost());
	}

	private HttpResponse<String> send(final HttpRequest request) throws IOException, InterruptedException {
		try {
			return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (IOException e) {
			throw new RetryableException("Central is unreachable (" + e.getClass().getSimpleName() + ")", null);
		}
	}

	/**
	 * @param credential the credential
	 * @return {@code Basic} with the form-encoded uid and secret (RFC 6749 §2.3.1)
	 */
	private static String basic(final Credential credential) {
		final String uid = URLEncoder.encode(credential.agentUid(), StandardCharsets.UTF_8);
		final String secret = URLEncoder.encode(CharBuffer.wrap(credential.secret()).toString(), StandardCharsets.UTF_8);
		final ByteBuffer pair = StandardCharsets.UTF_8.encode(uid + ":" + secret);
		final byte[] bytes = new byte[pair.remaining()];
		pair.get(bytes);
		final String header = "Basic " + Base64.getEncoder().encodeToString(bytes);
		Arrays.fill(bytes, (byte) 0);
		return header;
	}

	/**
	 * Reads a JWT's payload without verifying it: Central verifies the token, the agent only schedules its renewal.
	 *
	 * @param jwt the token
	 * @return its claims
	 * @throws IOException when it is not a JWT
	 */
	static JsonNode claims(final String jwt) throws IOException {
		final String[] parts = jwt.split("\\.");
		if (parts.length != 3) {
			throw new RetryableException("the access token is not a JWT", null);
		}
		try {
			return JSON.readTree(Base64.getUrlDecoder().decode(parts[1]));
		} catch (IllegalArgumentException e) {
			throw new RetryableException("the access token is not a JWT", null);
		}
	}

	private static String errorCode(final String body) {
		try {
			return JSON.readTree(body).path("error").asText(null);
		} catch (IOException e) {
			return null;
		}
	}

	private static Duration retryAfter(final HttpResponse<String> response) {
		return response.headers().firstValue("Retry-After").flatMap(OpampHttpTransport::parseRetryAfter).orElse(null);
	}
}
