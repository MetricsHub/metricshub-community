package org.metricshub.opamp.client.impl;

import com.google.protobuf.ByteString;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.metricshub.opamp.proto.AgentDescription;
import org.metricshub.opamp.proto.AgentToServer;
import org.metricshub.opamp.proto.AnyValue;
import org.metricshub.opamp.proto.ComponentHealth;
import org.metricshub.opamp.proto.KeyValue;
import org.metricshub.opamp.proto.PackageDownloadDetails;
import org.metricshub.opamp.proto.PackageStatus;
import org.metricshub.opamp.proto.PackageStatusEnum;
import org.metricshub.opamp.proto.PackageStatuses;

/**
 * Writes the golden OpAMP wire fixtures consumed by the MetricsHub Fleet contract tests
 * ({@code metricshub-fleet}, {@code testdata/golden}).
 * <p>
 * Every fixture is a binary {@code AgentToServer} message produced by the <b>real</b>
 * {@link AgentToServerAssembler} — the exact bytes the shipped agent puts on the wire — with fixed
 * inputs so regeneration is byte-for-byte reproducible. The fleet repository commits these bytes
 * and replays them through its production HTTP mux; they may only change through a reviewed PR on
 * both sides.
 * </p>
 * <p>
 * Regenerate with {@code mvn -pl metricshub-opamp-client test -Dtest=GoldenFixtureWriterTest},
 * then copy {@code target/golden-fixtures/} into the fleet repository.
 * </p>
 */
public final class GoldenFixtureWriter {

	/**
	 * Fixed 16-byte instance UID shared with the fleet contract tests.
	 */
	static final byte[] INSTANCE_UID = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16 };

	/**
	 * ReportsStatus | AcceptsPackages | ReportsPackageStatuses | ReportsHealth: the capabilities
	 * of an upgrade-capable MetricsHub agent.
	 */
	static final long CAPABILITIES = 0x819L;

	/**
	 * Fixed base timestamp (2023-11-14T22:13:20Z in nanoseconds) keeping the fixtures stable.
	 */
	static final long BASE_TIME_UNIX_NANO = 1_700_000_000_000_000_000L;

	private GoldenFixtureWriter() {}

	/**
	 * One golden fixture: the file name it is stored under, its human description and the
	 * assembled message.
	 *
	 * @param file        the fixture file name
	 * @param description what the message represents on the wire
	 * @param message     the assembled message
	 */
	record Fixture(String file, String description, AgentToServer message) {}

	/**
	 * Assembles the representative wire messages through the real assembler.
	 *
	 * @return the fixtures, in protocol order
	 */
	static List<Fixture> generate() {
		final AgentToServerAssembler assembler = new AgentToServerAssembler(
			ByteString.copyFrom(INSTANCE_UID),
			CAPABILITIES
		);
		final AtomicReference<PackageStatuses> statuses = new AtomicReference<>(installedStatuses());
		assembler.setAgentDescription(description());
		assembler.setHealth(health(BASE_TIME_UNIX_NANO + 1_000_000_000L));
		assembler.setPackageStatusesSupplier(statuses::get);

		final List<Fixture> fixtures = new ArrayList<>();
		fixtures.add(
			new Fixture(
				"01-first-full-state.bin",
				"First message of a fresh client: description, health and package statuses, sequence 1",
				assembler.assemble()
			)
		);
		assembler.commit();

		assembler.setHealth(health(BASE_TIME_UNIX_NANO + 2_000_000_000L));
		fixtures.add(
			new Fixture(
				"02-delta-health-only.bin",
				"In-sync delta: only the changed health rides, sequence 2",
				assembler.assemble()
			)
		);
		assembler.commit();

		assembler.requestFullState();
		fixtures.add(
			new Fixture(
				"03-report-full-state.bin",
				"Answer to the ReportFullState flag: every component re-sent, sequence 3",
				assembler.assemble()
			)
		);
		assembler.commit();

		statuses.set(downloadingStatuses());
		fixtures.add(
			new Fixture(
				"04-delta-package-downloading.bin",
				"Upgrade in flight: only the package statuses ride (Downloading 42.5%), sequence 4",
				assembler.assemble()
			)
		);
		assembler.commit();

		fixtures.add(
			new Fixture(
				"05-agent-disconnect.bin",
				"Best-effort goodbye on shutdown, sequence 5",
				assembler.assembleDisconnect()
			)
		);
		return fixtures;
	}

	/**
	 * Writes the fixtures and their MANIFEST.json into the given directory.
	 *
	 * @param directory the output directory, created if needed
	 * @throws IOException when a file cannot be written
	 */
	static void writeTo(final Path directory) throws IOException {
		Files.createDirectories(directory);
		// A renamed or removed fixture must not survive from a previous run:
		// the directory is copied wholesale into the fleet repository, so
		// stale binaries would poison the golden set.
		try (var existing = Files.list(directory)) {
			for (final Path file : existing.toList()) {
				final String name = file.getFileName().toString();
				if (name.endsWith(".bin") || name.equals("MANIFEST.json")) {
					Files.delete(file);
				}
			}
		}
		final List<Fixture> fixtures = generate();
		final StringBuilder manifest = new StringBuilder()
			.append("{\n")
			.append("  \"generator\": \"metricshub-opamp-client GoldenFixtureWriter\",\n")
			.append("  \"instanceUid\": \"")
			.append(HexFormat.of().formatHex(INSTANCE_UID))
			.append("\",\n")
			.append("  \"capabilities\": \"0x")
			.append(Long.toHexString(CAPABILITIES))
			.append("\",\n")
			.append("  \"fixtures\": [\n");

		for (int i = 0; i < fixtures.size(); i++) {
			final Fixture fixture = fixtures.get(i);
			final byte[] bytes = fixture.message().toByteArray();
			Files.write(directory.resolve(fixture.file()), bytes);
			manifest
				.append("    {\n")
				.append("      \"file\": \"")
				.append(fixture.file())
				.append("\",\n")
				.append("      \"sequence\": ")
				.append(fixture.message().getSequenceNum())
				.append(",\n")
				.append("      \"description\": \"")
				.append(fixture.description())
				.append("\",\n")
				.append("      \"sha256\": \"")
				.append(sha256(bytes))
				.append("\"\n")
				.append(i < fixtures.size() - 1 ? "    },\n" : "    }\n");
		}
		manifest.append("  ]\n}\n");
		Files.write(directory.resolve("MANIFEST.json"), manifest.toString().getBytes(StandardCharsets.UTF_8));
	}

	private static String sha256(final byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is unavailable", e);
		}
	}

	/**
	 * Builds the fixed agent description, mirroring the attribute shape the MetricsHub agent
	 * reports (including the {@code installer.type} selection attribute).
	 *
	 * @return the description
	 */
	private static AgentDescription description() {
		return AgentDescription.newBuilder()
			.addIdentifyingAttributes(attribute("service.name", "MetricsHub Agent"))
			.addIdentifyingAttributes(attribute("service.version", "1.0.15"))
			.addIdentifyingAttributes(attribute("host.name", "golden-host"))
			.addNonIdentifyingAttributes(attribute("os.type", "linux"))
			.addNonIdentifyingAttributes(attribute("host.arch", "amd64"))
			.addNonIdentifyingAttributes(attribute("build_number", "golden00"))
			.addNonIdentifyingAttributes(attribute("installer.type", "deb"))
			.build();
	}

	private static KeyValue attribute(final String key, final String value) {
		return KeyValue.newBuilder().setKey(key).setValue(AnyValue.newBuilder().setStringValue(value).build()).build();
	}

	private static ComponentHealth health(final long statusTimeUnixNano) {
		return ComponentHealth.newBuilder()
			.setHealthy(true)
			.setStatus("UP")
			.setStartTimeUnixNano(BASE_TIME_UNIX_NANO)
			.setStatusTimeUnixNano(statusTimeUnixNano)
			.putComponentHealthMap(
				"otel_collector",
				ComponentHealth.newBuilder().setHealthy(true).setStatus("running").build()
			)
			.build();
	}

	private static PackageStatuses installedStatuses() {
		return PackageStatuses.newBuilder()
			.putPackages(
				"metricshub",
				PackageStatus.newBuilder()
					.setName("metricshub")
					.setAgentHasVersion("1.0.15")
					.setStatus(PackageStatusEnum.PackageStatusEnum_Installed)
					.build()
			)
			.build();
	}

	private static PackageStatuses downloadingStatuses() {
		return PackageStatuses.newBuilder()
			.putPackages(
				"metricshub",
				PackageStatus.newBuilder()
					.setName("metricshub")
					.setAgentHasVersion("1.0.15")
					.setServerOfferedVersion("99.0.0")
					.setServerOfferedHash(ByteString.copyFrom(new byte[] { (byte) 0xAA, (byte) 0xBB, (byte) 0xCC }))
					.setStatus(PackageStatusEnum.PackageStatusEnum_Downloading)
					.setDownloadDetails(
						PackageDownloadDetails.newBuilder().setDownloadPercent(42.5).setDownloadBytesPerSecond(1024).build()
					)
					.build()
			)
			.build();
	}
}
