package org.metricshub.agent.m8b;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.m8b.protocol.M8bJson;
import org.metricshub.agent.m8b.protocol.M8bMessage;
import org.metricshub.agent.m8b.protocol.M8bMessage.AgentRegistered;
import org.metricshub.agent.m8b.protocol.M8bMessage.ToolError;
import org.metricshub.agent.m8b.protocol.M8bMessage.ToolInvoke;
import org.metricshub.agent.m8b.protocol.M8bMessage.ToolResult;
import org.metricshub.agent.m8b.protocol.ToolErrorCode;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;

class M8bToolBridgeTest {

	private static final long TIMEOUT_MS = 5_000;

	private final BlockingQueue<M8bMessage> answers = new LinkedBlockingQueue<>();
	private ToolCallback listHosts;
	private ToolCallback slow;
	private M8bToolBridge bridge;

	private static ToolCallback callback(final String name) {
		final ToolDefinition definition = mock(ToolDefinition.class);
		when(definition.name()).thenReturn(name);
		when(definition.description()).thenReturn(name);
		when(definition.inputSchema()).thenReturn("{\"type\":\"object\",\"properties\":{}}");
		final ToolCallback callback = mock(ToolCallback.class);
		when(callback.getToolDefinition()).thenReturn(definition);
		return callback;
	}

	@BeforeEach
	void setUp() {
		listHosts = callback("ListHosts");
		slow = callback("Slow");
		final ToolCallbackProvider provider = mock(ToolCallbackProvider.class);
		when(provider.getToolCallbacks()).thenReturn(new ToolCallback[] { listHosts, slow });
		bridge = new M8bToolBridge(ToolRegistrySnapshot.from(provider, Set.of()), answers::add);
	}

	@AfterEach
	void tearDown() {
		bridge.shutdown();
	}

	private static ToolInvoke invoke(final String tool, final long timeoutMs) {
		return new ToolInvoke("req-" + tool, tool, M8bJson.MAPPER.createObjectNode(), timeoutMs);
	}

	private M8bMessage answer() throws InterruptedException {
		final M8bMessage answer = answers.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
		assertNotNull(answer, "An answer is expected");
		return answer;
	}

	@Test
	void shouldReturnTheToolOutputAsJson() throws Exception {
		when(listHosts.call(anyString())).thenReturn("{\"server-01\":{\"resourceGroupKey\":\"paris\"}}");

		bridge.invoke(new ToolInvoke("req-1", "ListHosts", M8bJson.MAPPER.readTree("{}"), 10_000));

		final ToolResult result = assertInstanceOf(ToolResult.class, answer());
		assertEquals("req-1", result.requestId());
		assertEquals("paris", result.result().at("/server-01/resourceGroupKey").asText());
		assertTrue(result.durationMs() >= 0);
		assertEquals(0, bridge.inFlight());
	}

	@Test
	void shouldWrapANonJsonOutputAsText() throws Exception {
		when(listHosts.call(anyString())).thenReturn("No host configured");

		bridge.invoke(invoke("ListHosts", 10_000));

		final ToolResult result = assertInstanceOf(ToolResult.class, answer());
		final JsonNode node = result.result();
		assertTrue(node.isTextual());
		assertEquals("No host configured", node.asText());
	}

	@Test
	void shouldPassTheArgumentsAndDefaultThemToAnEmptyObject() throws Exception {
		when(listHosts.call("{\"hostname\":[\"a\"]}")).thenReturn("1");
		when(listHosts.call("{}")).thenReturn("2");

		bridge.invoke(new ToolInvoke("req-args", "ListHosts", M8bJson.MAPPER.readTree("{\"hostname\":[\"a\"]}"), 10_000));
		assertEquals(1, assertInstanceOf(ToolResult.class, answer()).result().asInt());

		bridge.invoke(new ToolInvoke("req-null", "ListHosts", null, 10_000));
		assertEquals(2, assertInstanceOf(ToolResult.class, answer()).result().asInt());
	}

	@Test
	void shouldRefuseAToolThatIsNotAdvertised() throws Exception {
		bridge.invoke(invoke("ExecuteSshCommandline", 10_000));

		final ToolError error = assertInstanceOf(ToolError.class, answer());
		assertEquals(ToolErrorCode.TOOL_NOT_AVAILABLE, error.code());
		assertEquals("req-ExecuteSshCommandline", error.requestId());
	}

	@Test
	void shouldMapBadArgumentsAndFailures() throws Exception {
		when(listHosts.call(anyString())).thenThrow(new IllegalArgumentException("hostname is required"));
		bridge.invoke(invoke("ListHosts", 10_000));
		final ToolError invalid = assertInstanceOf(ToolError.class, answer());
		assertEquals(ToolErrorCode.INVALID_ARGUMENTS, invalid.code());
		assertEquals("hostname is required", invalid.message());

		doThrow(new IllegalStateException("boom")).when(listHosts).call(anyString());
		bridge.invoke(invoke("ListHosts", 10_000));
		final ToolError failed = assertInstanceOf(ToolError.class, answer());
		assertEquals(ToolErrorCode.EXECUTION_ERROR, failed.code());
		assertEquals("boom", failed.message());
	}

	@Test
	void shouldTimeOutALongInvocationExactlyOnce() throws Exception {
		final CountDownLatch release = new CountDownLatch(1);
		when(slow.call(anyString())).thenAnswer(invocation -> {
			release.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
			return "late";
		});

		bridge.invoke(invoke("Slow", 200));

		final ToolError error = assertInstanceOf(ToolError.class, answer());
		assertEquals(ToolErrorCode.TIMEOUT, error.code());
		release.countDown();
		// The late result must not produce a second answer
		assertEquals(null, answers.poll(500, TimeUnit.MILLISECONDS));
	}

	@Test
	void shouldHonorTheConcurrencyCap() throws Exception {
		bridge.setLimits(new AgentRegistered(30, 1_048_576, 1));
		final CountDownLatch release = new CountDownLatch(1);
		when(slow.call(anyString())).thenAnswer(invocation -> {
			release.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
			return "done";
		});
		when(listHosts.call(anyString())).thenReturn("{}");

		bridge.invoke(invoke("Slow", 10_000));
		bridge.invoke(invoke("ListHosts", 10_000));

		final ToolError refused = assertInstanceOf(ToolError.class, answer());
		assertEquals(ToolErrorCode.TOO_MANY_INFLIGHT, refused.code());
		assertEquals("req-ListHosts", refused.requestId());

		release.countDown();
		final ToolResult done = assertInstanceOf(ToolResult.class, answer());
		assertEquals("req-Slow", done.requestId());
		assertEquals(0, bridge.inFlight());
	}

	@Test
	void shouldDropTheDeadlineOnceTheToolAnswered() throws Exception {
		when(listHosts.call(anyString())).thenReturn("{}");

		// A long server timeout must not keep the request queued in the timer once it is answered
		bridge.invoke(invoke("ListHosts", 600_000));

		assertInstanceOf(ToolResult.class, answer());
		await()
			.atMost(TIMEOUT_MS, TimeUnit.MILLISECONDS)
			.until(() -> bridge.pendingDeadlines() == 0);
	}

	@Test
	void shouldNotAnswerAfterItsSessionDisconnected() throws Exception {
		final CountDownLatch release = new CountDownLatch(1);
		when(slow.call(anyString())).thenAnswer(invocation -> {
			release.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
			return "{}";
		});

		bridge.invoke(invoke("Slow", 10_000));
		bridge.cancelSessionWork();
		release.countDown();

		// The tunnel session that asked is gone: the answer must not land on the next one
		assertEquals(null, answers.poll(1_000, TimeUnit.MILLISECONDS));
	}

	@Test
	void shouldFreeTheSlotOfWorkThatOutlivesItsSession() throws Exception {
		bridge.setLimits(new AgentRegistered(30, 8L * 1024 * 1024, 1));
		final CountDownLatch stuck = new CountDownLatch(1);
		when(slow.call(anyString())).thenAnswer(invocation -> {
			// A callback that ignores its interruption — a socket read that does not observe one,
			// as most of them do not. Its thread cannot be taken back; its slot must be.
			stuck.await();
			return "{}";
		});

		bridge.invoke(invoke("Slow", 100));
		bridge.cancelSessionWork();

		await()
			.atMost(TIMEOUT_MS, TimeUnit.MILLISECONDS)
			.untilAsserted(() -> assertEquals(0, bridge.inFlight(), "The deadline must release the slot"));
		assertEquals(null, answers.poll(200, TimeUnit.MILLISECONDS), "The session that asked is gone");

		// And the cap is genuinely free again: the next invocation runs rather than being refused
		when(listHosts.call(anyString())).thenReturn("{}");
		bridge.invoke(invoke("ListHosts", 10_000));
		assertInstanceOf(ToolResult.class, answer());
		stuck.countDown();
	}

	@Test
	void shouldTrimAFailureDetailThatWouldNotFitAnErrorFrame() throws Exception {
		doThrow(new IllegalStateException("x".repeat(50_000))).when(slow).call(anyString());

		bridge.invoke(invoke("Slow", 10_000));

		final ToolError error = assertInstanceOf(ToolError.class, answer());
		assertEquals(ToolErrorCode.EXECUTION_ERROR, error.code());
		assertTrue(
			error.message().length() < M8bToolBridge.MAX_ERROR_DETAIL_CHARS + 100,
			"A failure must be reportable, so its detail is cut rather than the report refused"
		);
		assertTrue(error.message().endsWith("(truncated)"), "And the cut must be visible");
	}

	@Test
	void shouldRefuseAResultAboveThePayloadCap() throws Exception {
		bridge.setLimits(new AgentRegistered(30, 64, 4));
		when(listHosts.call(anyString())).thenReturn("{\"data\":\"" + "x".repeat(200) + "\"}");

		bridge.invoke(invoke("ListHosts", 10_000));

		final ToolError error = assertInstanceOf(ToolError.class, answer());
		assertEquals(ToolErrorCode.RESULT_TOO_LARGE, error.code());
	}
}
