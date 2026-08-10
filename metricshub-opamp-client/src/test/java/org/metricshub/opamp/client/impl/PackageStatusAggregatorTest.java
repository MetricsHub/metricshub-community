package org.metricshub.opamp.client.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.opamp.proto.PackageDownloadDetails;
import org.metricshub.opamp.proto.PackageStatus;
import org.metricshub.opamp.proto.PackageStatusEnum;
import org.metricshub.opamp.proto.PackageStatuses;

class PackageStatusAggregatorTest {

	private static final String PACKAGE_NAME = "metricshub";

	private AtomicInteger terminalNotifications;
	private PackageStatusAggregator aggregator;

	@BeforeEach
	void setUp() {
		terminalNotifications = new AtomicInteger();
		aggregator = new PackageStatusAggregator(terminalNotifications::incrementAndGet);
	}

	private static PackageStatus status(final PackageStatusEnum statusEnum) {
		return PackageStatus.newBuilder().setName(PACKAGE_NAME).setStatus(statusEnum).build();
	}

	@Test
	void reportShouldAggregateStatuses() {
		aggregator.report(status(PackageStatusEnum.PackageStatusEnum_Downloading));

		final PackageStatuses proto = aggregator.toProto();

		assertEquals(1, proto.getPackagesCount());
		assertEquals(PackageStatusEnum.PackageStatusEnum_Downloading, proto.getPackagesOrThrow(PACKAGE_NAME).getStatus());
		assertEquals(0, terminalNotifications.get());
	}

	@Test
	void terminalStatusesShouldTriggerTheNotifier() {
		aggregator.report(status(PackageStatusEnum.PackageStatusEnum_Installing));
		assertEquals(0, terminalNotifications.get());

		aggregator.report(status(PackageStatusEnum.PackageStatusEnum_Installed));
		assertEquals(1, terminalNotifications.get());

		aggregator.report(status(PackageStatusEnum.PackageStatusEnum_InstallFailed));
		assertEquals(2, terminalNotifications.get());
	}

	@Test
	void reportShouldIgnoreStatusesWithoutName() {
		aggregator.report(PackageStatus.newBuilder().setStatus(PackageStatusEnum.PackageStatusEnum_Installed).build());

		assertEquals(0, aggregator.toProto().getPackagesCount());
		assertEquals(0, terminalNotifications.get());
	}

	@Test
	void downloadProgressShouldBeRecordedAndThrottled() {
		aggregator.report(status(PackageStatusEnum.PackageStatusEnum_Downloading));

		aggregator.reportDownloadProgress(PACKAGE_NAME, PackageDownloadDetails.newBuilder().setDownloadPercent(10).build());
		// Immediately following update is throttled
		aggregator.reportDownloadProgress(PACKAGE_NAME, PackageDownloadDetails.newBuilder().setDownloadPercent(20).build());

		assertEquals(10, aggregator.toProto().getPackagesOrThrow(PACKAGE_NAME).getDownloadDetails().getDownloadPercent());

		// A 100% update is never throttled
		aggregator.reportDownloadProgress(
			PACKAGE_NAME,
			PackageDownloadDetails.newBuilder().setDownloadPercent(100).build()
		);
		assertEquals(100, aggregator.toProto().getPackagesOrThrow(PACKAGE_NAME).getDownloadDetails().getDownloadPercent());
	}

	@Test
	void downloadProgressForUnknownPackageShouldBeIgnored() {
		aggregator.reportDownloadProgress("unknown", PackageDownloadDetails.newBuilder().setDownloadPercent(50).build());

		assertEquals(0, aggregator.toProto().getPackagesCount());
	}

	@Test
	void serverProvidedHashShouldBeEchoed() {
		final ByteString hash = ByteString.copyFromUtf8("all-packages-hash");
		aggregator.setServerProvidedAllPackagesHash(hash);

		assertEquals(hash, aggregator.toProto().getServerProvidedAllPackagesHash());
	}

	@Test
	void seedShouldImportExistingStatuses() {
		final ByteString hash = ByteString.copyFromUtf8("hash");
		aggregator.seed(
			PackageStatuses.newBuilder()
				.putPackages(PACKAGE_NAME, status(PackageStatusEnum.PackageStatusEnum_Installed))
				.setServerProvidedAllPackagesHash(hash)
				.build()
		);

		final PackageStatuses proto = aggregator.toProto();
		assertTrue(proto.containsPackages(PACKAGE_NAME));
		assertEquals(hash, proto.getServerProvidedAllPackagesHash());
		assertFalse(proto.getPackagesOrThrow(PACKAGE_NAME).hasDownloadDetails());
		// Seeding must not fire the terminal notifier
		assertEquals(0, terminalNotifications.get());
	}
}
