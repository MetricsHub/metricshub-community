package org.metricshub.agent.upgrade.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.helpers.LocalOsHandler;

class DeploymentDetectorTest {

	@Test
	void packageManagerDeploymentsShouldBeDetected() {
		final DeploymentDetector detector = new DeploymentDetector(command -> true);

		final DeploymentKind kind = detector.detect();

		if (LocalOsHandler.isWindows()) {
			assertEquals(DeploymentKind.MSI, kind);
		} else {
			assertEquals(DeploymentKind.DEB, kind);
		}
		assertTrue(kind.isUpgradable());
	}

	@Test
	void archiveDeploymentShouldBeDetectedWhenNoPackageOwnsTheInstallation() {
		final DeploymentDetector detector = new DeploymentDetector(command -> false);

		final DeploymentKind kind = detector.detect();

		assertEquals(DeploymentKind.ARCHIVE, kind);
		assertFalse(kind.isUpgradable());
	}

	@Test
	void rpmShouldBeProbedWhenDpkgIsAbsent() {
		org.junit.jupiter.api.Assumptions.assumeFalse(LocalOsHandler.isWindows());
		final List<String> probes = new ArrayList<>();
		final DeploymentDetector detector = new DeploymentDetector(command -> {
			probes.add(command[0]);
			return "rpm".equals(command[0]);
		});

		assertEquals(DeploymentKind.RPM, detector.detect());
		assertEquals(List.of("dpkg", "rpm"), probes);
	}

	@Test
	void detectionShouldBeCached() {
		final List<String> probes = new ArrayList<>();
		final DeploymentDetector detector = new DeploymentDetector(command -> {
			probes.add(command[0]);
			return true;
		});

		detector.detect();
		final int probesAfterFirstDetection = probes.size();
		detector.detect();

		assertEquals(probesAfterFirstDetection, probes.size());
	}
}
