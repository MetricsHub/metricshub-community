package org.metricshub.opamp.client.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.opamp.client.OpampClientSettings;
import org.metricshub.opamp.client.impl.HttpPollingOpampClient;
import org.metricshub.opamp.client.packages.OpampPackagesHandler;
import org.metricshub.opamp.client.packages.PackageDownloadContext;
import org.metricshub.opamp.client.packages.PackageStatusSink;
import org.metricshub.opamp.proto.AgentDescription;
import org.metricshub.opamp.proto.AgentToServer;
import org.metricshub.opamp.proto.AnyValue;
import org.metricshub.opamp.proto.KeyValue;
import org.metricshub.opamp.proto.PackageAvailable;
import org.metricshub.opamp.proto.PackageStatus;
import org.metricshub.opamp.proto.PackageStatusEnum;
import org.metricshub.opamp.proto.PackageStatuses;
import org.metricshub.opamp.proto.PackagesAvailable;
import org.metricshub.opamp.proto.ServerToAgent;
import org.metricshub.opamp.proto.ServerToAgentFlags;

/**
 * End-to-end integration tests of the OpAMP client over real HTTP against {@link FakeOpampServer}.
 */
class OpampClientIT {

	private static final long AWAIT_MS = 10_000;
	private static final String PACKAGE_NAME = "metricshub";

	@TempDir
	Path tempDir;

	private FakeOpampServer server;
	private HttpPollingOpampClient client;

	@BeforeEach
	void setUp() throws Exception {
		server = new FakeOpampServer();
	}

	@AfterEach
	void tearDown() {
		if (client != null) {
			client.close();
		}
		server.close();
	}

	private OpampClientSettings settings() {
		return OpampClientSettings.builder()
			.withEndpoint(server.endpoint())
			.withHeaders(Map.of("Authorization", "Bearer it-token"))
			.withPollInterval(Duration.ofMillis(100))
			.withMaxBackoff(Duration.ofSeconds(1))
			.withInstanceUidFile(tempDir.resolve("instance-uid"))
			.build();
	}

	private static AgentDescription agentDescription() {
		return AgentDescription.newBuilder()
			.addIdentifyingAttributes(
				KeyValue.newBuilder()
					.setKey("service.name")
					.setValue(AnyValue.newBuilder().setStringValue("metricshub").build())
					.build()
			)
			.build();
	}

	@Test
	void clientShouldReportFullStateThenDeltasOverHttp() throws Exception {
		client = HttpPollingOpampClient.builder().withSettings(settings()).build();
		client.setAgentDescription(agentDescription());

		// Second exchange: the server requests a full state report
		server.enqueue(FakeOpampServer.ScriptedReply.ok(ServerToAgent.getDefaultInstance()));
		server.enqueue(
			FakeOpampServer.ScriptedReply.ok(
				ServerToAgent.newBuilder().setFlags(ServerToAgentFlags.ServerToAgentFlags_ReportFullState.getNumber()).build()
			)
		);

		client.start();

		final FakeOpampServer.RecordedRequest first = server.awaitRequest(AWAIT_MS);
		assertEquals("application/x-protobuf", first.contentType());
		assertEquals("Bearer it-token", first.authorization());
		assertTrue(first.message().hasAgentDescription());

		final FakeOpampServer.RecordedRequest second = server.awaitRequest(AWAIT_MS);
		assertTrue(!second.message().hasAgentDescription(), "The second message must be a delta");

		final FakeOpampServer.RecordedRequest third = server.awaitRequest(AWAIT_MS);
		assertTrue(third.message().hasAgentDescription(), "ReportFullState must trigger a full report");
	}

	@Test
	void packageOfferShouldRoundTripToInstalledStatus() throws Exception {
		final ByteString offerHash = ByteString.copyFromUtf8("fleet-hash");
		final CountDownLatch offerReceived = new CountDownLatch(1);

		final OpampPackagesHandler handler = new OpampPackagesHandler() {
			@Override
			public void onPackagesAvailable(
				final PackagesAvailable packagesAvailable,
				final PackageStatusSink statusSink,
				final PackageDownloadContext downloadContext
			) {
				final PackageAvailable offer = packagesAvailable.getPackagesOrThrow(PACKAGE_NAME);
				// Simulate the Upgrade Manager: download, install, report terminal status
				statusSink.report(
					PackageStatus.newBuilder()
						.setName(PACKAGE_NAME)
						.setServerOfferedVersion(offer.getVersion())
						.setStatus(PackageStatusEnum.PackageStatusEnum_Downloading)
						.build()
				);
				statusSink.report(
					PackageStatus.newBuilder()
						.setName(PACKAGE_NAME)
						.setAgentHasVersion(offer.getVersion())
						.setStatus(PackageStatusEnum.PackageStatusEnum_Installed)
						.build()
				);
				offerReceived.countDown();
			}

			@Override
			public PackageStatuses currentPackageStatuses() {
				return PackageStatuses.newBuilder()
					.putPackages(
						PACKAGE_NAME,
						PackageStatus.newBuilder()
							.setName(PACKAGE_NAME)
							.setAgentHasVersion("3.9.0")
							.setStatus(PackageStatusEnum.PackageStatusEnum_Installed)
							.build()
					)
					.build();
			}
		};

		server.enqueue(
			FakeOpampServer.ScriptedReply.ok(
				ServerToAgent.newBuilder()
					.setPackagesAvailable(
						PackagesAvailable.newBuilder()
							.setAllPackagesHash(offerHash)
							.putPackages(PACKAGE_NAME, PackageAvailable.newBuilder().setVersion("3.10.0").build())
							.build()
					)
					.build()
			)
		);

		client = HttpPollingOpampClient.builder().withSettings(settings()).withPackagesHandler(handler).build();
		client.start();

		final FakeOpampServer.RecordedRequest first = server.awaitRequest(AWAIT_MS);
		assertEquals("3.9.0", first.message().getPackageStatuses().getPackagesOrThrow(PACKAGE_NAME).getAgentHasVersion());

		assertTrue(offerReceived.await(AWAIT_MS, TimeUnit.MILLISECONDS));

		AgentToServer report = server.awaitRequest(AWAIT_MS).message();
		while (
			!report.hasPackageStatuses() ||
			!"3.10.0".equals(report.getPackageStatuses().getPackagesOrThrow(PACKAGE_NAME).getAgentHasVersion())
		) {
			report = server.awaitRequest(AWAIT_MS).message();
		}
		assertEquals(offerHash, report.getPackageStatuses().getServerProvidedAllPackagesHash());
		assertEquals(
			PackageStatusEnum.PackageStatusEnum_Installed,
			report.getPackageStatuses().getPackagesOrThrow(PACKAGE_NAME).getStatus()
		);
	}

	@Test
	void clientShouldRecoverFromServerOutage() throws Exception {
		server.enqueue(FakeOpampServer.ScriptedReply.httpError(503, "1"));

		client = HttpPollingOpampClient.builder().withSettings(settings()).build();
		client.setAgentDescription(agentDescription());
		client.start();

		final FakeOpampServer.RecordedRequest first = server.awaitRequest(AWAIT_MS);
		assertTrue(first.message().hasAgentDescription());

		final long beforeRetryMs = System.currentTimeMillis();
		final FakeOpampServer.RecordedRequest retry = server.awaitRequest(AWAIT_MS);
		assertTrue(
			System.currentTimeMillis() - beforeRetryMs >= 900,
			"The Retry-After header must delay the retry by about one second"
		);
		assertTrue(retry.message().hasAgentDescription(), "The retry must carry the full state again");
	}

	@Test
	void stopShouldDeliverAgentDisconnect() throws Exception {
		client = HttpPollingOpampClient.builder().withSettings(settings()).build();
		client.start();
		server.awaitRequest(AWAIT_MS);

		client.stop("Integration test shutdown");

		AgentToServer last = server.awaitRequest(AWAIT_MS).message();
		while (!last.hasAgentDisconnect()) {
			last = server.awaitRequest(AWAIT_MS).message();
		}
		assertTrue(last.hasAgentDisconnect());
	}
}
