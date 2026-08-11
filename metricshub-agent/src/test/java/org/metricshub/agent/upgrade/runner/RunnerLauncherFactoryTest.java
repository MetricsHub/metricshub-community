package org.metricshub.agent.upgrade.runner;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunnerLauncherFactoryTest {

	@TempDir
	Path tempDir;

	private RunnerLauncherFactory factory() {
		return new RunnerLauncherFactory(() -> tempDir);
	}

	@Test
	void linuxPackagesShouldUseTheSystemdLauncher() {
		assertInstanceOf(LinuxSystemdRunnerLauncher.class, factory().forDeployment(DeploymentKind.DEB, "MetricsHub"));
		assertInstanceOf(LinuxSystemdRunnerLauncher.class, factory().forDeployment(DeploymentKind.RPM, "MetricsHub"));
	}

	@Test
	void msiShouldUseTheScheduledTaskLauncher() {
		assertInstanceOf(
			WindowsScheduledTaskRunnerLauncher.class,
			factory().forDeployment(DeploymentKind.MSI, "MetricsHub")
		);
	}

	@Test
	void nonPackageDeploymentsShouldUseTheUnsupportedLauncher() {
		assertInstanceOf(UnsupportedRunnerLauncher.class, factory().forDeployment(DeploymentKind.ARCHIVE, "MetricsHub"));
		assertInstanceOf(UnsupportedRunnerLauncher.class, factory().forDeployment(DeploymentKind.DOCKER, "MetricsHub"));
	}
}
