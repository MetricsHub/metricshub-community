package org.metricshub.agent.upgrade.validate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.agent.config.UpgradeConfig;
import org.metricshub.agent.upgrade.UpgradeException;
import org.metricshub.agent.upgrade.api.PackageOffer;
import org.metricshub.agent.upgrade.runner.DeploymentKind;

class PackageValidatorTest {

	private static final String CURRENT_VERSION = "3.9.05";

	@TempDir
	Path tempDir;

	private final PackageValidator validator = new PackageValidator();
	private final UpgradeConfig config = UpgradeConfig.builder().build();

	private static PackageOffer offer(final String version, final String url) {
		return new PackageOffer("metricshub", version, url, new byte[] { 1, 2, 3 }, new byte[] { 7 }, Map.of());
	}

	@Test
	void validOfferShouldPass() {
		assertDoesNotThrow(() ->
			validator.validateOffer(
				offer("3.10.00", "https://repo/metricshub.deb"),
				CURRENT_VERSION,
				config,
				DeploymentKind.DEB
			)
		);
	}

	@Test
	void unsupportedDeploymentShouldBeRejected() {
		assertThrows(UpgradeException.class, () ->
			validator.validateOffer(
				offer("3.10.00", "https://repo/metricshub.deb"),
				CURRENT_VERSION,
				config,
				DeploymentKind.DOCKER
			)
		);
	}

	@Test
	void mismatchedPackageTypeShouldBeRejected() {
		assertThrows(UpgradeException.class, () ->
			validator.validateOffer(
				offer("3.10.00", "https://repo/metricshub.rpm"),
				CURRENT_VERSION,
				config,
				DeploymentKind.DEB
			)
		);
	}

	@Test
	void queryStringShouldNotDefeatTheExtensionCheck() {
		assertDoesNotThrow(() ->
			validator.validateOffer(
				offer("3.10.00", "https://repo/metricshub.deb?token=abc"),
				CURRENT_VERSION,
				config,
				DeploymentKind.DEB
			)
		);
	}

	@Test
	void missingHashShouldBeRejected() {
		final PackageOffer offer = new PackageOffer(
			"metricshub",
			"3.10.00",
			"https://repo/m.deb",
			new byte[0],
			new byte[] { 7 },
			Map.of()
		);
		assertThrows(UpgradeException.class, () ->
			validator.validateOffer(offer, CURRENT_VERSION, config, DeploymentKind.DEB)
		);
	}

	@Test
	void downgradeShouldBeRejectedByDefault() {
		assertThrows(UpgradeException.class, () ->
			validator.validateOffer(
				offer("3.9.00", "https://repo/metricshub.deb"),
				CURRENT_VERSION,
				config,
				DeploymentKind.DEB
			)
		);
	}

	@Test
	void downgradeShouldBeAcceptedWhenAllowed() {
		final UpgradeConfig allowing = UpgradeConfig.builder().allowDowngrade(true).build();
		assertDoesNotThrow(() ->
			validator.validateOffer(
				offer("3.9.00", "https://repo/metricshub.deb"),
				CURRENT_VERSION,
				allowing,
				DeploymentKind.DEB
			)
		);
	}

	@Test
	void msiDowngradeShouldAlwaysBeRejected() {
		final UpgradeConfig allowing = UpgradeConfig.builder().allowDowngrade(true).build();
		assertThrows(UpgradeException.class, () ->
			validator.validateOffer(
				offer("3.9.00", "https://repo/metricshub.msi"),
				CURRENT_VERSION,
				allowing,
				DeploymentKind.MSI
			)
		);
	}

	@Test
	void stagedPackageValidationShouldVerifyTheHash() throws Exception {
		final Path stagedPackage = tempDir.resolve("metricshub.deb");
		final byte[] content = "package-content".getBytes();
		Files.write(stagedPackage, content);
		final byte[] sha256 = MessageDigest.getInstance("SHA-256").digest(content);

		final PackageOffer matching = new PackageOffer(
			"metricshub",
			"3.10.00",
			"https://repo/m.deb",
			sha256,
			new byte[] { 7 },
			Map.of()
		);
		assertDoesNotThrow(() -> validator.validateStagedPackage(stagedPackage, matching, config));

		final PackageOffer mismatching = offer("3.10.00", "https://repo/m.deb");
		final UpgradeException failure = assertThrows(UpgradeException.class, () ->
			validator.validateStagedPackage(stagedPackage, mismatching, config)
		);
		assertTrue(failure.getMessage().contains("SHA-256"));
	}

	@Test
	void missingOrEmptyStagedPackageShouldBeRejected() throws Exception {
		final PackageOffer offer = offer("3.10.00", "https://repo/m.deb");

		assertThrows(UpgradeException.class, () ->
			validator.validateStagedPackage(tempDir.resolve("missing.deb"), offer, config)
		);

		final Path empty = tempDir.resolve("empty.deb");
		Files.createFile(empty);
		assertThrows(UpgradeException.class, () -> validator.validateStagedPackage(empty, offer, config));
	}
}
