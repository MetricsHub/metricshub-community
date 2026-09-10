package org.metricshub.opamp.client.impl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.metricshub.opamp.proto.AgentToServer;
import org.metricshub.opamp.proto.PackageStatusEnum;

class GoldenFixtureWriterTest {

	@Test
	void fixturesShouldMatchTheWireContract() throws Exception {
		final List<GoldenFixtureWriter.Fixture> fixtures = GoldenFixtureWriter.generate();
		assertEquals(5, fixtures.size());

		final AgentToServer first = fixtures.get(0).message();
		assertEquals(1, first.getSequenceNum());
		assertEquals(ByteString.copyFrom(GoldenFixtureWriter.INSTANCE_UID), first.getInstanceUid());
		assertEquals(GoldenFixtureWriter.CAPABILITIES, first.getCapabilities());
		assertTrue(first.hasAgentDescription(), "the first message carries full state");
		assertTrue(first.hasHealth());
		assertTrue(first.hasPackageStatuses());
		assertTrue(
			first
				.getAgentDescription()
				.getNonIdentifyingAttributesList()
				.stream()
				.anyMatch(
					attribute ->
						attribute.getKey().equals("installer.type") && attribute.getValue().getStringValue().equals("deb")
				),
			"the description must carry the installer.type selection attribute"
		);

		final AgentToServer delta = fixtures.get(1).message();
		assertEquals(2, delta.getSequenceNum());
		assertFalse(delta.hasAgentDescription(), "an unchanged description must not ride a delta");
		assertTrue(delta.hasHealth());
		assertFalse(delta.hasPackageStatuses());

		final AgentToServer full = fixtures.get(2).message();
		assertEquals(3, full.getSequenceNum());
		assertTrue(full.hasAgentDescription(), "the ReportFullState answer re-sends everything");
		assertTrue(full.hasHealth());
		assertTrue(full.hasPackageStatuses());

		final AgentToServer downloading = fixtures.get(3).message();
		assertEquals(4, downloading.getSequenceNum());
		assertFalse(downloading.hasAgentDescription());
		assertFalse(downloading.hasHealth());
		assertEquals(
			PackageStatusEnum.PackageStatusEnum_Downloading,
			downloading.getPackageStatuses().getPackagesMap().get("metricshub").getStatus()
		);

		final AgentToServer disconnect = fixtures.get(4).message();
		assertEquals(5, disconnect.getSequenceNum());
		assertTrue(disconnect.hasAgentDisconnect());
	}

	@Test
	void generationShouldBeByteForByteReproducible() {
		final List<GoldenFixtureWriter.Fixture> first = GoldenFixtureWriter.generate();
		final List<GoldenFixtureWriter.Fixture> second = GoldenFixtureWriter.generate();
		for (int i = 0; i < first.size(); i++) {
			assertArrayEquals(
				first.get(i).message().toByteArray(),
				second.get(i).message().toByteArray(),
				"fixture " + first.get(i).file() + " must be reproducible"
			);
		}
	}

	@Test
	void writeToShouldProduceParseableFixturesAndAManifest() throws Exception {
		final Path directory = Paths.get("target", "golden-fixtures");
		GoldenFixtureWriter.writeTo(directory);

		for (final GoldenFixtureWriter.Fixture fixture : GoldenFixtureWriter.generate()) {
			final byte[] bytes = Files.readAllBytes(directory.resolve(fixture.file()));
			final AgentToServer parsed = AgentToServer.parseFrom(bytes);
			assertEquals(fixture.message(), parsed, fixture.file() + " must round-trip");
		}

		final String manifest = Files.readString(directory.resolve("MANIFEST.json"));
		assertTrue(manifest.contains("\"01-first-full-state.bin\""));
		assertTrue(manifest.contains("\"sha256\""));
	}

	@Test
	void writeToShouldRemoveStaleFixturesFromPreviousRuns() throws Exception {
		final Path directory = Paths.get("target", "golden-fixtures-stale");
		Files.createDirectories(directory);
		Files.write(directory.resolve("99-removed-fixture.bin"), new byte[] { 1, 2, 3 });

		GoldenFixtureWriter.writeTo(directory);

		assertFalse(
			Files.exists(directory.resolve("99-removed-fixture.bin")),
			"a fixture from a previous run must not survive regeneration: the directory is copied wholesale"
		);
		assertTrue(Files.exists(directory.resolve("MANIFEST.json")));
	}
}
