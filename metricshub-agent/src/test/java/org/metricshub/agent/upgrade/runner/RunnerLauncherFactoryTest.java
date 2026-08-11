package org.metricshub.agent.upgrade.runner;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.agent.config.UpgradeConfig;

class RunnerLauncherFactoryTest {

	@TempDir
	Path tempDir;

	private RunnerLauncherFactory factory() {
		// A resolver returning a fixed unit, so the selection test does not depend on the host
		return new RunnerLauncherFactory(
			() -> tempDir,
			new ServiceNameResolver(command -> List.of(), List.of(), () -> false) {
				@Override
				public String resolve(final String configuredServiceName) {
					return configuredServiceName != null && !configuredServiceName.isBlank()
						? configuredServiceName
						: "metricshub-community-service.service";
				}
			}
		);
	}

	private static UpgradeConfig config() {
		return UpgradeConfig.builder().build();
	}

	@Test
	void linuxPackagesShouldUseTheSystemdLauncher() {
		assertInstanceOf(LinuxSystemdRunnerLauncher.class, factory().forDeployment(DeploymentKind.DEB, config()));
		assertInstanceOf(LinuxSystemdRunnerLauncher.class, factory().forDeployment(DeploymentKind.RPM, config()));
	}

	@Test
	void msiShouldUseTheScheduledTaskLauncher() {
		assertInstanceOf(WindowsScheduledTaskRunnerLauncher.class, factory().forDeployment(DeploymentKind.MSI, config()));
	}

	@Test
	void nonPackageDeploymentsShouldUseTheUnsupportedLauncher() {
		assertInstanceOf(UnsupportedRunnerLauncher.class, factory().forDeployment(DeploymentKind.ARCHIVE, config()));
		assertInstanceOf(UnsupportedRunnerLauncher.class, factory().forDeployment(DeploymentKind.DOCKER, config()));
	}
}
