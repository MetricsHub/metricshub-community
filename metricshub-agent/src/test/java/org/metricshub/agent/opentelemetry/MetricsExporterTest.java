package org.metricshub.agent.opentelemetry;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.agent.config.CentralAuthConfig;
import org.metricshub.agent.config.CentralConfig;
import org.metricshub.agent.fleet.CentralTokenProvider;
import org.metricshub.agent.fleet.EnrollmentCoordinator;
import org.metricshub.agent.opentelemetry.client.GrpcClient;
import org.metricshub.agent.opentelemetry.client.HttpProtobufClient;
import org.metricshub.agent.opentelemetry.client.IOtelClient;
import org.metricshub.agent.opentelemetry.client.NoopClient;
import org.metricshub.agent.service.TestHelper;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetricsExporterTest {

	@Mock
	private IOtelClient mockClient;

	private MetricsExporter exporter;

	@BeforeEach
	void setUp() {
		TestHelper.configureGlobalLogger();
		exporter = MetricsExporter.builder().withClient(mockClient).build();
	}

	@Test
	void export_shouldCallClientSend() {
		final List<ResourceMetrics> metrics = List.of(ResourceMetrics.getDefaultInstance());

		exporter.export(metrics, () -> {});

		verify(mockClient, times(1)).send(any(ExportMetricsServiceRequest.class), any());
	}

	@Test
	void export_shouldHandleExceptionGracefully() {
		doThrow(new RuntimeException("Test exception")).when(mockClient).send(any(), any());

		assertDoesNotThrow(
			() -> exporter.export(List.of(ResourceMetrics.getDefaultInstance()), () -> {}),
			"Exporter should handle exceptions gracefully"
		);
	}

	@Test
	void withConfiguration_shouldCreateGrpcClient() throws InterruptedException {
		final Map<String, String> config = Map.of(
			OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_PROTOCOL,
			OtelConfigConstants.GRPC,
			OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_ENDPOINT,
			"http://localhost:4317"
		);

		final MetricsExporter exporter = MetricsExporter.builder().withConfiguration(config).build();
		assertTrue(exporter.getClient() instanceof GrpcClient, "Should use GrpcClient");
	}

	@Test
	void withConfiguration_shouldCreateHttpProtobufClient() {
		final Map<String, String> config = Map.of(
			OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_PROTOCOL,
			OtelConfigConstants.HTTP_PROTOBUF,
			OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_ENDPOINT,
			"http://localhost:4318"
		);

		final MetricsExporter exporter = MetricsExporter.builder().withConfiguration(config).build();
		assertTrue(exporter.getClient() instanceof HttpProtobufClient, "Should use HttpProtobufClient");
	}

	@Test
	void withConfiguration_shouldCreateNoopClientForInvalidProtocol() {
		final Map<String, String> config = Map.of(
			OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_PROTOCOL,
			"invalid_protocol"
		);

		final MetricsExporter exporter = MetricsExporter.builder().withConfiguration(config).build();
		assertTrue(exporter.getClient() instanceof NoopClient, "Should fallback to NoopClient");
	}

	/** The fake vmauth: the status of each answer, 200 once the script is empty, and each Authorization received. */
	private HttpServer vmauth;

	private final Queue<Integer> answers = new ConcurrentLinkedQueue<>();

	private final List<String> received = new CopyOnWriteArrayList<>();

	/** The token the fake provider hands out; an invalidation moves to the next one. */
	private final AtomicInteger token = new AtomicInteger(1);

	private final List<String> invalidated = new CopyOnWriteArrayList<>();

	@AfterEach
	void stopVmauth() {
		if (vmauth != null) {
			vmauth.stop(0);
		}
	}

	private String vmauthEndpoint() throws IOException {
		if (vmauth == null) {
			vmauth = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			vmauth.createContext("/opentelemetry/v1/metrics", exchange -> {
				exchange.getRequestBody().readAllBytes();
				final String authorization = String.valueOf(exchange.getRequestHeaders().getFirst("Authorization"));
				received.add(authorization);
				final Integer status = answers.poll();
				if (status == null || status / 100 == 2) {
					exchange.sendResponseHeaders(status == null ? 200 : status, -1);
				} else {
					// An error body that echoes the request's headers
					final byte[] body = ("echo Authorization: " + authorization).getBytes(StandardCharsets.UTF_8);
					exchange.sendResponseHeaders(status, body.length);
					exchange.getResponseBody().write(body);
				}
				exchange.close();
			});
			vmauth.start();
		}
		return "http://127.0.0.1:" + vmauth.getAddress().getPort() + "/opentelemetry/v1/metrics";
	}

	private HttpProtobufClient centralClient() throws IOException {
		return HttpProtobufClient.builder()
			.withEndpoint(vmauthEndpoint())
			.withHeaders(Map.of())
			.withTimeout(5)
			.withPoolSize(2)
			.withCredentialHeaders(() -> Map.of("Authorization", "Bearer token-" + token.get()))
			.withInvalidateCredential(authorization -> {
				invalidated.add(authorization);
				token.incrementAndGet();
			})
			.build();
	}

	/**
	 * Exports one batch and waits until the client has handled {@code responses} answers.
	 */
	private static void exportAndAwait(final HttpProtobufClient client, final int responses) throws InterruptedException {
		final CountDownLatch handled = new CountDownLatch(responses);
		MetricsExporter.builder()
			.withClient(client)
			.build()
			.export(List.of(ResourceMetrics.getDefaultInstance()), handled::countDown);
		assertTrue(handled.await(10, TimeUnit.SECONDS), "The client should handle " + responses + " answer(s)");
	}

	/**
	 * Runs the action with the HttpProtobufClient logs captured.
	 */
	private static String logsOf(final ThrowingRunnable action) throws Exception {
		final StringWriter written = new StringWriter();
		final WriterAppender appender = WriterAppender.newBuilder()
			.setName("otlp-client")
			.setTarget(written)
			.setLayout(PatternLayout.newBuilder().withPattern("%m%n").build())
			.build();
		appender.start();
		final LoggerContext context = (LoggerContext) LogManager.getContext(false);
		final LoggerConfig logger = context.getConfiguration().getLoggerConfig(HttpProtobufClient.class.getName());
		final Level level = logger.getLevel();
		logger.addAppender(appender, Level.TRACE, null);
		logger.setLevel(Level.TRACE);
		context.updateLoggers();
		try {
			action.run();
		} finally {
			logger.removeAppender("otlp-client");
			logger.setLevel(level);
			context.updateLoggers();
			appender.stop();
		}
		return written.toString();
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	@Test
	void centralAuth_shouldSendTheTokenOfTheMomentAtEachExport() throws Exception {
		final HttpProtobufClient client = centralClient();
		exportAndAwait(client, 1);
		// A renewal between two exports
		token.set(2);
		exportAndAwait(client, 1);
		client.shutdown();

		assertEquals(List.of("Bearer token-1", "Bearer token-2"), received);
		assertTrue(invalidated.isEmpty(), "Nothing to invalidate");
	}

	@Test
	void centralAuth_shouldRenewOnceAndResendOnceAfter401() throws Exception {
		answers.add(401);
		final HttpProtobufClient client = centralClient();
		final String logged = logsOf(() -> {
			exportAndAwait(client, 2);
			client.shutdown();
		});

		assertEquals(List.of("Bearer token-1", "Bearer token-2"), received);
		assertEquals(List.of("Bearer token-1"), invalidated);
		assertFalse(logged.contains("Failed to send metrics"), logged);
		assertFalse(logged.contains("token-"), logged);
	}

	@Test
	void centralAuth_shouldNeverLoopOn401() throws Exception {
		answers.addAll(List.of(401, 401, 401, 401));
		final HttpProtobufClient client = centralClient();
		final String logged = logsOf(() -> {
			exportAndAwait(client, 2);
			client.shutdown();
		});

		assertEquals(List.of("Bearer token-1", "Bearer token-2"), received);
		assertEquals(List.of("Bearer token-1"), invalidated);
		assertTrue(logged.contains("HTTP Response Code: 401"), logged);
		assertFalse(logged.contains("token-"), logged);
	}

	@Test
	void anyTwoHundredShouldBeASuccess() throws Exception {
		answers.add(204);
		final HttpProtobufClient client = centralClient();
		final String logged = logsOf(() -> {
			exportAndAwait(client, 1);
			client.shutdown();
		});

		assertEquals(List.of("Bearer token-1"), received);
		assertTrue(logged.contains("Metrics sent successfully"), logged);
		assertFalse(logged.contains("Failed to send metrics"), logged);
	}

	@Test
	void centralAuth_shouldSendNothingOnceTheAgentIsRevoked() throws Exception {
		final AtomicInteger asked = new AtomicInteger();
		final HttpProtobufClient revoked = HttpProtobufClient.builder()
			.withEndpoint(vmauthEndpoint())
			.withHeaders(Map.of())
			.withTimeout(5)
			.withPoolSize(2)
			.withCredentialHeaders(() -> {
				asked.incrementAndGet();
				throw new UncheckedIOException(new CentralTokenProvider.InvalidClientException("invalid_client"));
			})
			.withInvalidateCredential(invalidated::add)
			.build();
		final String logged = logsOf(() -> {
			revoked.send(ExportMetricsServiceRequest.getDefaultInstance(), () -> {});
			revoked.send(ExportMetricsServiceRequest.getDefaultInstance(), () -> {});
			revoked.shutdown();
		});

		assertEquals(2, asked.get());
		assertTrue(received.isEmpty(), "Nothing reaches vmauth without a token");
		assertTrue(invalidated.isEmpty(), "Nothing to invalidate");
		assertTrue(logged.contains("Metrics not sent"), logged);
	}

	/** A configured central.auth: never enrolled in these tests, so nothing is sent with it. */
	private static final CentralConfig CENTRAL = CentralConfig.builder()
		.enabled(true)
		.auth(CentralAuthConfig.builder().issuerUrl("https://central.example.com").build())
		.build();

	private static Map<String, String> centralConfig(final String protocol, final String endpoint, final String headers) {
		final Map<String, String> config = new HashMap<>();
		config.put(OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_AUTH, "central");
		if (protocol != null) {
			config.put(OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, protocol);
		}
		if (endpoint != null) {
			config.put(OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_ENDPOINT, endpoint);
		}
		if (headers != null) {
			config.put(OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_HEADERS, headers);
		}
		return config;
	}

	@Test
	void centralAuth_shouldBuildAnHttpProtobufClient() {
		final MetricsExporter exporter = MetricsExporter.builder()
			.withConfiguration(
				centralConfig("http/protobuf", "http://127.0.0.1:8427/opentelemetry/v1/metrics", "X-A=b"),
				CENTRAL
			)
			.build();
		assertTrue(exporter.getClient() instanceof HttpProtobufClient, "Should use HttpProtobufClient");
		exporter.shutdown();
	}

	@Test
	void centralAuth_shouldRefuseGrpc() {
		final IllegalStateException grpc = assertThrows(
			IllegalStateException.class,
			() -> MetricsExporter.builder().withConfiguration(centralConfig("grpc", "http://127.0.0.1:4317", null), CENTRAL)
		);
		assertTrue(grpc.getMessage().contains(OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_PROTOCOL), grpc.getMessage());
		// No protocol means gRPC
		assertThrows(
			IllegalStateException.class,
			() -> MetricsExporter.builder().withConfiguration(centralConfig(null, "http://127.0.0.1:4317", null), CENTRAL)
		);
	}

	@Test
	void centralAuth_shouldRefuseAMissingEndpointAStaticAuthorizationAndAnotherValue() {
		final IllegalStateException noEndpoint = assertThrows(
			IllegalStateException.class,
			() -> MetricsExporter.builder().withConfiguration(centralConfig("http/protobuf", null, null), CENTRAL)
		);
		assertTrue(
			noEndpoint.getMessage().contains(OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_ENDPOINT),
			noEndpoint.getMessage()
		);
		final IllegalStateException staticAuthorization = assertThrows(
			IllegalStateException.class,
			() -> MetricsExporter.builder()
				.withConfiguration(centralConfig("http/protobuf", "http://127.0.0.1:8427", "authorization=Bearer x"), CENTRAL)
		);
		assertTrue(staticAuthorization.getMessage().contains("Authorization"), staticAuthorization.getMessage());
		final Map<String, String> other = centralConfig("http/protobuf", "http://127.0.0.1:8427", null);
		other.put(OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_AUTH, "basic");
		assertThrows(IllegalStateException.class, () -> MetricsExporter.builder().withConfiguration(other, CENTRAL));
	}

	@Test
	void centralAuth_shouldRefuseWithoutCentralAuth() {
		final IllegalStateException refused = assertThrows(
			IllegalStateException.class,
			() -> MetricsExporter.builder()
				.withConfiguration(centralConfig("http/protobuf", "https://metrics.example.com/opentelemetry/v1/metrics", null))
		);
		assertTrue(refused.getMessage().contains("central.auth"), refused.getMessage());
	}

	@Test
	void centralAuth_shouldRefuseACleartextEndpoint() {
		final IllegalStateException refused = assertThrows(
			IllegalStateException.class,
			() -> MetricsExporter.builder()
				.withConfiguration(
					centralConfig("http/protobuf", "http://metrics.example.com/opentelemetry/v1/metrics", null),
					CENTRAL
				)
		);
		assertTrue(
			refused.getMessage().contains(OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_ENDPOINT),
			refused.getMessage()
		);
		// https, and http on a loopback address, are accepted
		MetricsExporter.builder()
			.withConfiguration(
				centralConfig("http/protobuf", "https://metrics.example.com/opentelemetry/v1/metrics", null),
				CENTRAL
			)
			.build()
			.shutdown();
	}

	@Test
	void centralCredential_shouldSendNothingBeforeTheReloadIsReconciled() {
		final EnrollmentCoordinator coordinator = mock(EnrollmentCoordinator.class);
		when(coordinator.tokenGeneration()).thenReturn(7L);
		when(coordinator.isReadyFor(CENTRAL)).thenReturn(false);
		final var credential = MetricsExporter.MetricsExporterBuilder.centralCredential(coordinator, CENTRAL, Map.of());

		assertThrows(UncheckedIOException.class, credential::get);
		// The former issuer's token is never asked for
		verify(coordinator, never()).headers(anyMap(), anyLong());

		when(coordinator.isReadyFor(CENTRAL)).thenReturn(true);
		when(coordinator.headers(Map.of(), 7L)).thenReturn(Map.of("Authorization", "Bearer new"));
		assertEquals(Map.of("Authorization", "Bearer new"), credential.get());
	}

	@Test
	void centralAuth_shouldNotRenewAtEachBatchOnceARenewedTokenIsRefused() throws Exception {
		answers.addAll(List.of(401, 401, 401, 401));
		final HttpProtobufClient client = centralClient();
		final String logged = logsOf(() -> {
			exportAndAwait(client, 2);
			// The next batch, with the renewed token, refused again
			exportAndAwait(client, 1);
			client.shutdown();
		});

		assertEquals(List.of("Bearer token-1", "Bearer token-2", "Bearer token-2"), received);
		// The token shared with the Central channels is dropped once, not at each batch
		assertEquals(List.of("Bearer token-1"), invalidated);
		assertFalse(logged.contains("token-"), logged);
	}

	@Test
	void centralAuth_shouldNeverBlockTheCollectionWhileTheTokenIsAsked() throws Exception {
		final CountDownLatch centralAnswers = new CountDownLatch(1);
		final HttpProtobufClient slow = HttpProtobufClient.builder()
			.withEndpoint(vmauthEndpoint())
			.withHeaders(Map.of())
			.withTimeout(5)
			.withPoolSize(1)
			.withCredentialHeaders(() -> {
				try {
					// Central is slow to answer the token request
					centralAnswers.await(10, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				return Map.of("Authorization", "Bearer token-1");
			})
			.withInvalidateCredential(invalidated::add)
			.build();
		final CountDownLatch handled = new CountDownLatch(1);
		assertTimeoutPreemptively(
			Duration.ofSeconds(2),
			() -> slow.send(ExportMetricsServiceRequest.getDefaultInstance(), handled::countDown)
		);
		centralAnswers.countDown();
		assertTrue(handled.await(10, TimeUnit.SECONDS), "The batch should be sent once the token is there");
		slow.shutdown();

		assertEquals(List.of("Bearer token-1"), received);
	}

	@Test
	void centralAuth_shouldDropTheBatchesBeyondThePoolWhileTheTokenIsAsked() throws Exception {
		final CountDownLatch centralAnswers = new CountDownLatch(1);
		final AtomicInteger asked = new AtomicInteger();
		final HttpProtobufClient slow = HttpProtobufClient.builder()
			.withEndpoint(vmauthEndpoint())
			.withHeaders(Map.of())
			.withTimeout(5)
			.withPoolSize(1)
			.withCredentialHeaders(() -> {
				asked.incrementAndGet();
				try {
					// Central is slow to answer the token request
					centralAnswers.await(10, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				return Map.of("Authorization", "Bearer token-1");
			})
			.withInvalidateCredential(invalidated::add)
			.build();
		final CountDownLatch handled = new CountDownLatch(1);
		final String logged = logsOf(() -> {
			slow.send(ExportMetricsServiceRequest.getDefaultInstance(), handled::countDown);
			// Sent while the first one waits for its token: dropped, never queued and flushed late
			slow.send(ExportMetricsServiceRequest.getDefaultInstance(), () -> {});
			slow.send(ExportMetricsServiceRequest.getDefaultInstance(), () -> {});
			centralAnswers.countDown();
			assertTrue(handled.await(10, TimeUnit.SECONDS), "The first batch should be sent once the token is there");
			slow.shutdown();
		});

		assertEquals(List.of("Bearer token-1"), received);
		assertEquals(1, asked.get());
		assertTrue(logged.contains("still wait for a token"), logged);
	}
}
