package org.metricshub.opamp.client.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.opamp.client.OpampClientCallbacks;
import org.metricshub.opamp.client.OpampClientSettings;
import org.metricshub.opamp.client.packages.OpampPackagesHandler;
import org.metricshub.opamp.client.packages.PackageDownloadContext;
import org.metricshub.opamp.client.packages.PackageStatusSink;
import org.metricshub.opamp.client.state.UuidV7;
import org.metricshub.opamp.proto.AgentCapabilities;
import org.metricshub.opamp.proto.AgentDescription;
import org.metricshub.opamp.proto.AgentIdentification;
import org.metricshub.opamp.proto.AgentToServer;
import org.metricshub.opamp.proto.AnyValue;
import org.metricshub.opamp.proto.ComponentHealth;
import org.metricshub.opamp.proto.KeyValue;
import org.metricshub.opamp.proto.PackageStatus;
import org.metricshub.opamp.proto.PackageStatusEnum;
import org.metricshub.opamp.proto.PackageStatuses;
import org.metricshub.opamp.proto.PackagesAvailable;
import org.metricshub.opamp.proto.ServerErrorResponse;
import org.metricshub.opamp.proto.ServerErrorResponseType;
import org.metricshub.opamp.proto.ServerToAgent;
import org.metricshub.opamp.proto.ServerToAgentFlags;

class HttpPollingOpampClientTest {

	private static final Duration AWAIT = Duration.ofSeconds(5);

	@TempDir
	Path tempDir;

	private HttpPollingOpampClient client;
	private RecordingTransport transport;

	@AfterEach
	void tearDown() {
		if (client != null) {
			client.close();
		}
	}

	private OpampClientSettings settings() {
		return OpampClientSettings.builder()
			.withEndpoint(URI.create("http://localhost:0/v1/opamp"))
			.withPollInterval(Duration.ofMillis(50))
			.withMaxBackoff(Duration.ofMillis(200))
			.withInstanceUidFile(tempDir.resolve("instance-uid"))
			.build();
	}

	private HttpPollingOpampClient newClient(final OpampClientCallbacks callbacks, final OpampPackagesHandler handler) {
		transport = new RecordingTransport();
		client = HttpPollingOpampClient.builder()
			.withSettings(settings())
			.withCallbacks(callbacks)
			.withTransport(transport)
			.withPackagesHandler(handler)
			.build();
		return client;
	}

	private static AgentDescription description(final String version) {
		return AgentDescription.newBuilder()
			.addIdentifyingAttributes(
				KeyValue.newBuilder()
					.setKey("service.version")
					.setValue(AnyValue.newBuilder().setStringValue(version).build())
					.build()
			)
			.build();
	}

	@Test
	void firstMessageShouldCarryFullStateAndCapabilities() throws Exception {
		newClient(null, null);
		client.setAgentDescription(description("1.0.0"));
		client.setHealth(ComponentHealth.newBuilder().setHealthy(true).build());
		client.start();

		final AgentToServer first = transport.awaitRequest(AWAIT);

		assertEquals(1, first.getSequenceNum());
		assertTrue(first.hasAgentDescription());
		assertTrue(first.hasHealth());
		assertEquals(16, first.getInstanceUid().size());
		final long capabilities = first.getCapabilities();
		assertTrue((capabilities & AgentCapabilities.AgentCapabilities_ReportsStatus.getNumber()) != 0);
		assertTrue((capabilities & AgentCapabilities.AgentCapabilities_ReportsHealth.getNumber()) != 0);
		assertEquals(0, capabilities & AgentCapabilities.AgentCapabilities_AcceptsPackages.getNumber());
		assertFalse(first.hasPackageStatuses());
	}

	@Test
	void subsequentMessagesShouldOmitUnchangedState() throws Exception {
		newClient(null, null);
		client.setAgentDescription(description("1.0.0"));
		client.start();

		transport.awaitRequest(AWAIT);
		final AgentToServer second = transport.awaitRequest(AWAIT);

		assertFalse(second.hasAgentDescription());
		assertTrue(second.getSequenceNum() > 1);
	}

	@Test
	void packagesHandlerShouldEnableCapabilitiesAndReceiveOffers() throws Exception {
		final CountDownLatch offerReceived = new CountDownLatch(1);
		final AtomicReference<PackagesAvailable> receivedOffer = new AtomicReference<>();
		final AtomicReference<PackageStatusSink> receivedSink = new AtomicReference<>();
		final OpampPackagesHandler handler = new OpampPackagesHandler() {
			@Override
			public void onPackagesAvailable(
				final PackagesAvailable packagesAvailable,
				final PackageStatusSink statusSink,
				final PackageDownloadContext downloadContext
			) {
				receivedOffer.set(packagesAvailable);
				receivedSink.set(statusSink);
				offerReceived.countDown();
			}

			@Override
			public PackageStatuses currentPackageStatuses() {
				return PackageStatuses.newBuilder()
					.putPackages(
						"metricshub",
						PackageStatus.newBuilder()
							.setName("metricshub")
							.setAgentHasVersion("1.0.0")
							.setStatus(PackageStatusEnum.PackageStatusEnum_Installed)
							.build()
					)
					.build();
			}
		};

		final ByteString allPackagesHash = ByteString.copyFromUtf8("offer-hash");
		newClient(null, handler);
		transport.enqueue(
			RecordingTransport.ScriptedReply.ok(
				ServerToAgent.newBuilder()
					.setPackagesAvailable(PackagesAvailable.newBuilder().setAllPackagesHash(allPackagesHash).build())
					.build()
			)
		);
		client.start();

		final AgentToServer first = transport.awaitRequest(AWAIT);
		assertTrue((first.getCapabilities() & AgentCapabilities.AgentCapabilities_AcceptsPackages.getNumber()) != 0);
		assertTrue((first.getCapabilities() & AgentCapabilities.AgentCapabilities_ReportsPackageStatuses.getNumber()) != 0);
		assertTrue(first.hasPackageStatuses());
		assertEquals("1.0.0", first.getPackageStatuses().getPackagesOrThrow("metricshub").getAgentHasVersion());

		assertTrue(offerReceived.await(AWAIT.toMillis(), TimeUnit.MILLISECONDS));
		assertEquals(allPackagesHash, receivedOffer.get().getAllPackagesHash());

		// A terminal status report triggers an immediate poll carrying the new statuses
		receivedSink
			.get()
			.report(
				PackageStatus.newBuilder()
					.setName("metricshub")
					.setAgentHasVersion("2.0.0")
					.setStatus(PackageStatusEnum.PackageStatusEnum_Installed)
					.build()
			);

		AgentToServer statusReport = transport.awaitRequest(AWAIT);
		while (!statusReport.hasPackageStatuses()) {
			statusReport = transport.awaitRequest(AWAIT);
		}
		assertEquals("2.0.0", statusReport.getPackageStatuses().getPackagesOrThrow("metricshub").getAgentHasVersion());
		assertEquals(allPackagesHash, statusReport.getPackageStatuses().getServerProvidedAllPackagesHash());
	}

	@Test
	void reportFullStateFlagShouldTriggerFullResend() throws Exception {
		newClient(null, null);
		client.setAgentDescription(description("1.0.0"));
		transport.enqueue(RecordingTransport.ScriptedReply.ok(ServerToAgent.getDefaultInstance()));
		transport.enqueue(
			RecordingTransport.ScriptedReply.ok(
				ServerToAgent.newBuilder().setFlags(ServerToAgentFlags.ServerToAgentFlags_ReportFullState.getNumber()).build()
			)
		);
		client.start();

		transport.awaitRequest(AWAIT);
		final AgentToServer delta = transport.awaitRequest(AWAIT);
		assertFalse(delta.hasAgentDescription());

		final AgentToServer fullState = transport.awaitRequest(AWAIT);
		assertTrue(fullState.hasAgentDescription());
	}

	@Test
	void transportFailureShouldBackOffAndResendFullState() throws Exception {
		final CountDownLatch failureReported = new CountDownLatch(1);
		final CountDownLatch reconnected = new CountDownLatch(2);
		final OpampClientCallbacks callbacks = new OpampClientCallbacks() {
			@Override
			public void onConnect() {
				reconnected.countDown();
			}

			@Override
			public void onConnectFailed(final Throwable error, final Duration nextAttemptDelay) {
				failureReported.countDown();
			}
		};

		newClient(callbacks, null);
		client.setAgentDescription(description("1.0.0"));
		transport.enqueue(RecordingTransport.ScriptedReply.ok(ServerToAgent.getDefaultInstance()));
		transport.enqueue(RecordingTransport.ScriptedReply.networkFailure());
		client.start();

		transport.awaitRequest(AWAIT);
		transport.awaitRequest(AWAIT);
		assertTrue(failureReported.await(AWAIT.toMillis(), TimeUnit.MILLISECONDS));

		final AgentToServer resent = transport.awaitRequest(AWAIT);
		assertTrue(resent.hasAgentDescription(), "The message following a failure must carry the full state");
		assertTrue(reconnected.await(AWAIT.toMillis(), TimeUnit.MILLISECONDS));
	}

	@Test
	void httpErrorWithRetryAfterShouldDelayTheNextPoll() throws Exception {
		newClient(null, null);
		transport.enqueue(RecordingTransport.ScriptedReply.httpError(503, Duration.ofMillis(300)));
		client.start();

		transport.awaitRequest(AWAIT);
		final long beforeRetryMs = System.currentTimeMillis();
		transport.awaitRequest(AWAIT);
		final long elapsedMs = System.currentTimeMillis() - beforeRetryMs;

		assertTrue(
			elapsedMs >= 250,
			"The Retry-After floor must delay the next poll, but only " + elapsedMs + " ms elapsed"
		);
	}

	@Test
	void newInstanceUidShouldBeAdoptedAndPersisted() throws Exception {
		final byte[] newUid = UuidV7.generate();
		newClient(null, null);
		transport.enqueue(
			RecordingTransport.ScriptedReply.ok(
				ServerToAgent.newBuilder()
					.setAgentIdentification(
						AgentIdentification.newBuilder().setNewInstanceUid(ByteString.copyFrom(newUid)).build()
					)
					.build()
			)
		);
		client.start();

		final AgentToServer first = transport.awaitRequest(AWAIT);
		final AgentToServer second = transport.awaitRequest(AWAIT);

		assertNotEquals(first.getInstanceUid(), second.getInstanceUid());
		assertEquals(ByteString.copyFrom(newUid), second.getInstanceUid());
		assertEquals(UuidV7.toCanonicalString(newUid), Files.readString(tempDir.resolve("instance-uid")).trim());
	}

	@Test
	void malformedNewInstanceUidShouldBeIgnored() throws Exception {
		newClient(null, null);
		transport.enqueue(
			RecordingTransport.ScriptedReply.ok(
				ServerToAgent.newBuilder()
					.setAgentIdentification(
						AgentIdentification.newBuilder().setNewInstanceUid(ByteString.copyFrom(new byte[] { 1, 2, 3 })).build()
					)
					.build()
			)
		);
		client.start();

		final AgentToServer first = transport.awaitRequest(AWAIT);
		final AgentToServer second = transport.awaitRequest(AWAIT);

		// A UID that is not 16 bytes long is rejected: the client keeps its own identity and the
		// persisted identity is untouched
		assertEquals(first.getInstanceUid(), second.getInstanceUid());
		assertEquals(16, second.getInstanceUid().size());
		assertEquals(
			UuidV7.toCanonicalString(first.getInstanceUid().toByteArray()),
			Files.readString(tempDir.resolve("instance-uid")).trim()
		);
	}

	@Test
	void failingCallbacksShouldNotBreakThePollingChain() throws Exception {
		final CountDownLatch pollsAfterFailure = new CountDownLatch(3);
		final OpampClientCallbacks throwingCallbacks = new OpampClientCallbacks() {
			@Override
			public void onConnect() {
				throw new IllegalStateException("Simulated onConnect failure");
			}

			@Override
			public void onConnectFailed(final Throwable error, final Duration nextAttemptDelay) {
				throw new IllegalStateException("Simulated onConnectFailed failure");
			}

			@Override
			public void onMessage(final ServerToAgent message) {
				throw new IllegalStateException("Simulated onMessage failure");
			}
		};

		newClient(throwingCallbacks, null);
		// First exchange fails at the transport level (onConnectFailed throws), the rest succeed
		// (onConnect and onMessage throw)
		transport.enqueue(RecordingTransport.ScriptedReply.networkFailure());
		client.start();

		// The polling chain must survive every callback failure
		for (int i = 0; i < 3; i++) {
			transport.awaitRequest(AWAIT);
			pollsAfterFailure.countDown();
		}
		assertEquals(0, pollsAfterFailure.getCount());
		assertTrue(client.isStarted());
	}

	@Test
	void stopShouldSendAgentDisconnect() throws Exception {
		newClient(null, null);
		client.start();
		transport.awaitRequest(AWAIT);

		client.stop("Test shutdown");

		AgentToServer last = transport.awaitRequest(AWAIT);
		while (!last.hasAgentDisconnect()) {
			last = transport.awaitRequest(AWAIT);
		}
		assertTrue(last.hasAgentDisconnect());
		assertTrue(transport.isClosed());
		assertFalse(client.isStarted());
	}

	@Test
	void unavailableErrorShouldNotCommitPendingState() throws Exception {
		newClient(null, null);
		client.setAgentDescription(description("1.0.0"));
		transport.enqueue(
			RecordingTransport.ScriptedReply.ok(
				ServerToAgent.newBuilder()
					.setErrorResponse(
						ServerErrorResponse.newBuilder()
							.setType(ServerErrorResponseType.ServerErrorResponseType_Unavailable)
							.build()
					)
					.build()
			)
		);
		client.start();

		final AgentToServer first = transport.awaitRequest(AWAIT);
		assertTrue(first.hasAgentDescription());

		// The server did not process the first message: the retry must carry the state again
		final AgentToServer retry = transport.awaitRequest(AWAIT);
		assertTrue(retry.hasAgentDescription(), "State reported in an unprocessed message must be sent again");

		// The retry got a plain 200: from now on unchanged state is compressed away
		final AgentToServer delta = transport.awaitRequest(AWAIT);
		assertFalse(delta.hasAgentDescription());
	}

	@Test
	void badRequestErrorShouldNotBeRetried() throws Exception {
		newClient(null, null);
		client.setAgentDescription(description("1.0.0"));
		transport.enqueue(
			RecordingTransport.ScriptedReply.ok(
				ServerToAgent.newBuilder()
					.setErrorResponse(
						ServerErrorResponse.newBuilder().setType(ServerErrorResponseType.ServerErrorResponseType_BadRequest).build()
					)
					.build()
			)
		);
		client.start();

		final AgentToServer first = transport.awaitRequest(AWAIT);
		assertTrue(first.hasAgentDescription());

		// A BadRequest rejection must not be retried: the baseline advances
		final AgentToServer next = transport.awaitRequest(AWAIT);
		assertFalse(next.hasAgentDescription(), "State rejected with BadRequest must not be resent");
	}

	@Test
	void mismatchedInstanceUidShouldBeIgnored() throws Exception {
		final CountDownLatch offerReceived = new CountDownLatch(1);
		final OpampPackagesHandler handler = new OpampPackagesHandler() {
			@Override
			public void onPackagesAvailable(
				final PackagesAvailable packagesAvailable,
				final PackageStatusSink statusSink,
				final PackageDownloadContext downloadContext
			) {
				offerReceived.countDown();
			}

			@Override
			public PackageStatuses currentPackageStatuses() {
				return PackageStatuses.getDefaultInstance();
			}
		};

		newClient(null, handler);
		client.setAgentDescription(description("1.0.0"));
		transport.enqueue(
			RecordingTransport.ScriptedReply.ok(
				ServerToAgent.newBuilder()
					.setInstanceUid(ByteString.copyFrom(UuidV7.generate()))
					.setPackagesAvailable(PackagesAvailable.newBuilder().setAllPackagesHash(ByteString.copyFromUtf8("h")))
					.build()
			)
		);
		client.start();

		final AgentToServer first = transport.awaitRequest(AWAIT);
		assertTrue(first.hasAgentDescription());

		// The misrouted response must be ignored entirely: no dispatch, no commit
		final AgentToServer next = transport.awaitRequest(AWAIT);
		assertTrue(next.hasAgentDescription(), "An ignored response must not commit the reported state");
		assertFalse(offerReceived.await(200, TimeUnit.MILLISECONDS), "The package offer must not be dispatched");
	}

	@Test
	void stopFromPollingThreadShouldSendDisconnect() throws Exception {
		final AtomicReference<HttpPollingOpampClient> clientRef = new AtomicReference<>();
		final CountDownLatch stopExecuted = new CountDownLatch(1);
		final OpampClientCallbacks callbacks = new OpampClientCallbacks() {
			private boolean stopRequested;

			@Override
			public void onMessage(final ServerToAgent message) {
				if (!stopRequested) {
					stopRequested = true;
					clientRef.get().stop("Stopped from the polling thread");
					stopExecuted.countDown();
				}
			}
		};

		newClient(callbacks, null);
		clientRef.set(client);
		client.start();

		assertTrue(stopExecuted.await(AWAIT.toMillis(), TimeUnit.MILLISECONDS));

		AgentToServer last = transport.awaitRequest(AWAIT);
		while (!last.hasAgentDisconnect()) {
			last = transport.awaitRequest(AWAIT);
		}
		assertTrue(last.hasAgentDisconnect());
		assertFalse(client.isStarted());
	}

	@Test
	void handlerFailureShouldNotAcknowledgeOfferHash() throws Exception {
		final ByteString offerHash = ByteString.copyFromUtf8("offer-hash");
		final AtomicReference<PackageStatusSink> sink = new AtomicReference<>();
		final CountDownLatch firstDispatch = new CountDownLatch(1);
		final CountDownLatch secondDispatch = new CountDownLatch(2);
		final OpampPackagesHandler handler = new OpampPackagesHandler() {
			private int calls;

			@Override
			public void onPackagesAvailable(
				final PackagesAvailable packagesAvailable,
				final PackageStatusSink statusSink,
				final PackageDownloadContext downloadContext
			) {
				sink.set(statusSink);
				calls++;
				firstDispatch.countDown();
				secondDispatch.countDown();
				if (calls == 1) {
					throw new RuntimeException("Simulated handler failure");
				}
			}

			@Override
			public PackageStatuses currentPackageStatuses() {
				return PackageStatuses.getDefaultInstance();
			}
		};

		newClient(null, handler);
		transport.enqueue(
			RecordingTransport.ScriptedReply.ok(
				ServerToAgent.newBuilder()
					.setPackagesAvailable(PackagesAvailable.newBuilder().setAllPackagesHash(offerHash).build())
					.build()
			)
		);
		client.start();

		assertTrue(firstDispatch.await(AWAIT.toMillis(), TimeUnit.MILLISECONDS));

		// The handler rejected the offer: the reported statuses must NOT echo the offer hash
		sink
			.get()
			.report(
				PackageStatus.newBuilder()
					.setName("metricshub")
					.setStatus(PackageStatusEnum.PackageStatusEnum_InstallFailed)
					.build()
			);
		AgentToServer failedReport = transport.awaitRequest(AWAIT);
		while (!failedReport.hasPackageStatuses()) {
			failedReport = transport.awaitRequest(AWAIT);
		}
		assertEquals(ByteString.EMPTY, failedReport.getPackageStatuses().getServerProvidedAllPackagesHash());

		// The server offers again; this time the handler accepts and the hash is acknowledged
		transport.enqueue(
			RecordingTransport.ScriptedReply.ok(
				ServerToAgent.newBuilder()
					.setPackagesAvailable(PackagesAvailable.newBuilder().setAllPackagesHash(offerHash).build())
					.build()
			)
		);
		assertTrue(secondDispatch.await(AWAIT.toMillis(), TimeUnit.MILLISECONDS));

		sink
			.get()
			.report(
				PackageStatus.newBuilder()
					.setName("metricshub")
					.setStatus(PackageStatusEnum.PackageStatusEnum_Installed)
					.build()
			);
		AgentToServer ackedReport = transport.awaitRequest(AWAIT);
		while (
			!ackedReport.hasPackageStatuses() ||
			!offerHash.equals(ackedReport.getPackageStatuses().getServerProvidedAllPackagesHash())
		) {
			ackedReport = transport.awaitRequest(AWAIT);
		}
		assertEquals(offerHash, ackedReport.getPackageStatuses().getServerProvidedAllPackagesHash());
	}

	@Test
	void lifecycleGuardsShouldBeEnforced() {
		newClient(null, null);
		client.start();

		assertThrows(IllegalStateException.class, () -> client.start());
		assertThrows(IllegalStateException.class, () ->
			client.setPackagesHandler(
				new OpampPackagesHandler() {
					@Override
					public void onPackagesAvailable(
						final PackagesAvailable packagesAvailable,
						final PackageStatusSink statusSink,
						final PackageDownloadContext downloadContext
					) {}

					@Override
					public PackageStatuses currentPackageStatuses() {
						return PackageStatuses.getDefaultInstance();
					}
				}
			)
		);

		client.stop("Test");
		assertThrows(IllegalStateException.class, () -> client.start());
	}
}
