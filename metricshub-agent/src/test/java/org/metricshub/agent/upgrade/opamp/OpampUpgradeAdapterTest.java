package org.metricshub.agent.upgrade.opamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.agent.upgrade.UpgradeManager;
import org.metricshub.agent.upgrade.UpgradeState;
import org.metricshub.agent.upgrade.api.PackageOffer;
import org.metricshub.agent.upgrade.api.UpgradeEvent;
import org.metricshub.agent.upgrade.api.UpgradeStatusListener;
import org.metricshub.opamp.client.packages.PackageDownloadContext;
import org.metricshub.opamp.client.packages.PackageStatusSink;
import org.metricshub.opamp.proto.DownloadableFile;
import org.metricshub.opamp.proto.Header;
import org.metricshub.opamp.proto.Headers;
import org.metricshub.opamp.proto.PackageAvailable;
import org.metricshub.opamp.proto.PackageDownloadDetails;
import org.metricshub.opamp.proto.PackageStatus;
import org.metricshub.opamp.proto.PackageStatusEnum;
import org.metricshub.opamp.proto.PackagesAvailable;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OpampUpgradeAdapterTest {

	@Mock
	private UpgradeManager upgradeManager;

	@Mock
	private PackageStatusSink sink;

	@Mock
	private PackageDownloadContext downloadContext;

	private OpampUpgradeAdapter newAdapter() {
		return new OpampUpgradeAdapter(upgradeManager);
	}

	private UpgradeStatusListener capturedListener() {
		final ArgumentCaptor<UpgradeStatusListener> captor = ArgumentCaptor.forClass(UpgradeStatusListener.class);
		verify(upgradeManager).setStatusListener(captor.capture());
		return captor.getValue();
	}

	@Test
	void offersShouldBeTranslatedAndForwarded() {
		final OpampUpgradeAdapter adapter = newAdapter();
		final PackagesAvailable offer = PackagesAvailable.newBuilder()
			.putPackages(
				UpgradeManager.PACKAGE_NAME,
				PackageAvailable.newBuilder()
					.setVersion("3.10.00")
					.setFile(
						DownloadableFile.newBuilder()
							.setDownloadUrl("https://repo.metricshub.com/metricshub.deb")
							.setContentHash(ByteString.copyFrom(new byte[] { 1, 2 }))
							.setHeaders(
								Headers.newBuilder().addHeaders(Header.newBuilder().setKey("Authorization").setValue("Bearer t"))
							)
					)
					.build()
			)
			.build();

		adapter.onPackagesAvailable(offer, sink, downloadContext);

		final ArgumentCaptor<PackageOffer> captor = ArgumentCaptor.forClass(PackageOffer.class);
		verify(upgradeManager).onPackageOffer(captor.capture());
		final PackageOffer translated = captor.getValue();
		assertEquals("3.10.00", translated.version());
		assertEquals("https://repo.metricshub.com/metricshub.deb", translated.downloadUrl());
		assertEquals(Map.of("Authorization", "Bearer t"), translated.headers());
		assertEquals("0102", translated.sha256Hex());
	}

	@Test
	void offersWithoutTheTopLevelPackageShouldBeIgnored() {
		final OpampUpgradeAdapter adapter = newAdapter();

		adapter.onPackagesAvailable(PackagesAvailable.getDefaultInstance(), sink, downloadContext);

		verify(upgradeManager, never()).onPackageOffer(any());
	}

	@Test
	void stateTransitionsShouldBeReportedAsPackageStatuses() {
		final OpampUpgradeAdapter adapter = newAdapter();
		final UpgradeStatusListener listener = capturedListener();
		adapter.onPackagesAvailable(offerWithPackage(), sink, downloadContext);

		listener.onUpgradeEvent(
			UpgradeEvent.of(UpgradeManager.PACKAGE_NAME, UpgradeState.FAILED, "3.9.05", "3.10.00", "boom")
		);

		final ArgumentCaptor<PackageStatus> captor = ArgumentCaptor.forClass(PackageStatus.class);
		verify(sink).report(captor.capture());
		final PackageStatus status = captor.getValue();
		assertEquals(PackageStatusEnum.PackageStatusEnum_InstallFailed, status.getStatus());
		assertEquals("3.9.05", status.getAgentHasVersion());
		assertEquals("3.10.00", status.getServerOfferedVersion());
		assertEquals("boom", status.getErrorMessage());
	}

	@Test
	void downloadProgressShouldUseTheProgressChannel() {
		final OpampUpgradeAdapter adapter = newAdapter();
		final UpgradeStatusListener listener = capturedListener();
		adapter.onPackagesAvailable(offerWithPackage(), sink, downloadContext);

		listener.onUpgradeEvent(
			new UpgradeEvent(UpgradeManager.PACKAGE_NAME, UpgradeState.DOWNLOADING, "3.9.05", "3.10.00", null, 42, 1024)
		);

		final ArgumentCaptor<PackageDownloadDetails> captor = ArgumentCaptor.forClass(PackageDownloadDetails.class);
		verify(sink).reportDownloadProgress(org.mockito.ArgumentMatchers.eq(UpgradeManager.PACKAGE_NAME), captor.capture());
		assertEquals(42, captor.getValue().getDownloadPercent());
		verify(sink, never()).report(any());
	}

	@Test
	void statusMappingShouldCoverAllStates() {
		assertEquals(
			PackageStatusEnum.PackageStatusEnum_Installed,
			OpampUpgradeAdapter.toPackageStatusEnum(UpgradeState.IDLE)
		);
		assertEquals(
			PackageStatusEnum.PackageStatusEnum_Installed,
			OpampUpgradeAdapter.toPackageStatusEnum(UpgradeState.SUCCEEDED)
		);
		assertEquals(
			PackageStatusEnum.PackageStatusEnum_InstallPending,
			OpampUpgradeAdapter.toPackageStatusEnum(UpgradeState.UPDATE_AVAILABLE)
		);
		assertEquals(
			PackageStatusEnum.PackageStatusEnum_InstallPending,
			OpampUpgradeAdapter.toPackageStatusEnum(UpgradeState.READY_TO_INSTALL)
		);
		assertEquals(
			PackageStatusEnum.PackageStatusEnum_Downloading,
			OpampUpgradeAdapter.toPackageStatusEnum(UpgradeState.DOWNLOADING)
		);
		assertEquals(
			PackageStatusEnum.PackageStatusEnum_Downloading,
			OpampUpgradeAdapter.toPackageStatusEnum(UpgradeState.VALIDATING)
		);
		assertEquals(
			PackageStatusEnum.PackageStatusEnum_Installing,
			OpampUpgradeAdapter.toPackageStatusEnum(UpgradeState.INSTALLING)
		);
		assertEquals(
			PackageStatusEnum.PackageStatusEnum_Installing,
			OpampUpgradeAdapter.toPackageStatusEnum(UpgradeState.RESTARTING)
		);
		assertEquals(
			PackageStatusEnum.PackageStatusEnum_Installing,
			OpampUpgradeAdapter.toPackageStatusEnum(UpgradeState.VERIFYING)
		);
		assertEquals(
			PackageStatusEnum.PackageStatusEnum_InstallFailed,
			OpampUpgradeAdapter.toPackageStatusEnum(UpgradeState.FAILED)
		);
	}

	@Test
	void currentPackageStatusesShouldReflectTheSnapshot() {
		when(upgradeManager.getCurrentSnapshot()).thenReturn(
			UpgradeEvent.of(UpgradeManager.PACKAGE_NAME, UpgradeState.SUCCEEDED, "3.10.00", "3.10.00", null)
		);
		final OpampUpgradeAdapter adapter = newAdapter();

		final var statuses = adapter.currentPackageStatuses();

		final PackageStatus status = statuses.getPackagesOrThrow(UpgradeManager.PACKAGE_NAME);
		assertEquals(PackageStatusEnum.PackageStatusEnum_Installed, status.getStatus());
		assertEquals("3.10.00", status.getAgentHasVersion());
		assertTrue(status.getErrorMessage().isEmpty());
	}

	private static PackagesAvailable offerWithPackage() {
		return PackagesAvailable.newBuilder()
			.putPackages(UpgradeManager.PACKAGE_NAME, PackageAvailable.newBuilder().setVersion("3.10.00").build())
			.build();
	}
}
