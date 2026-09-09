package org.metricshub.programmable.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.engine.extension.ScheduledReEvaluation;

/**
 * Covers the schedule-driven side of {@link ProgrammableConfigurationProvider}: discovering the
 * {@code $schedule.cron(...)} a template declares, and re-evaluating that template on demand.
 */
class ProgrammableConfigurationScheduleTest {

	/**
	 * Writes a template reading its hosts from the given CSV, declaring the given cron (or no cron
	 * at all when {@code cron} is {@code null}).
	 */
	private static void writeTemplate(final Path vmPath, final Path csv, final String cron) throws IOException {
		final String csvPath = csv.toAbsolutePath().toString().replace('\\', '/');
		final String schedule = cron == null ? "" : "$schedule.cron('" + cron + "')\n";
		Files.writeString(
			vmPath,
			schedule +
				"#set($lines = $file.readAllLines(\"" +
				csvPath +
				"\"))\n" +
				"resources:\n" +
				"#foreach($line in $lines)\n" +
				"#set($fields = $collection.split($line))\n" +
				"  $fields.get(0): {}\n" +
				"#end\n"
		);
	}

	@Test
	void testDeclaredCronIsDiscovered(@TempDir final Path tempDir) throws Exception {
		final Path csv = tempDir.resolve("hosts.csv");
		Files.writeString(csv, "host-a\n");
		writeTemplate(tempDir.resolve("hosts.vm"), csv, "0/5 * * * * ?");

		final var provider = new ProgrammableConfigurationProvider();
		provider.load(tempDir);

		final Collection<ScheduledReEvaluation> reEvaluations = provider.getScheduledReEvaluations();

		assertEquals(1, reEvaluations.size(), "The template declares one schedule");
		final ScheduledReEvaluation reEvaluation = reEvaluations.iterator().next();
		assertEquals("0/5 * * * * ?", reEvaluation.cron());
		assertEquals(tempDir.resolve("hosts.vm").toAbsolutePath().toString(), reEvaluation.id());
	}

	@Test
	void testTemplateWithoutScheduleDeclaresNothing(@TempDir final Path tempDir) throws Exception {
		final Path csv = tempDir.resolve("hosts.csv");
		Files.writeString(csv, "host-a\n");
		writeTemplate(tempDir.resolve("hosts.vm"), csv, null);

		final var provider = new ProgrammableConfigurationProvider();
		provider.load(tempDir);

		assertTrue(provider.getScheduledReEvaluations().isEmpty(), "No $schedule.cron means no re-evaluation");
	}

	@Test
	void testLastDeclaredCronWins(@TempDir final Path tempDir) throws Exception {
		Files.writeString(
			tempDir.resolve("hosts.vm"),
			"$schedule.cron('0/5 * * * * ?')\n$schedule.cron('0 0/30 * * * ?')\nresources: {}\n"
		);

		final var provider = new ProgrammableConfigurationProvider();
		provider.load(tempDir);

		final List<ScheduledReEvaluation> reEvaluations = List.copyOf(provider.getScheduledReEvaluations());
		assertEquals(1, reEvaluations.size(), "A template declares at most one schedule");
		assertEquals("0 0/30 * * * ?", reEvaluations.get(0).cron());
	}

	@Test
	void testReevaluateRendersTheWholeTemplateAgain(@TempDir final Path tempDir) throws Exception {
		final Path csv = tempDir.resolve("hosts.csv");
		Files.writeString(csv, "host-a\n");
		writeTemplate(tempDir.resolve("hosts.vm"), csv, "0/5 * * * * ?");

		final var provider = new ProgrammableConfigurationProvider();
		provider.load(tempDir);

		final String id = provider.getScheduledReEvaluations().iterator().next().id();
		final JsonNode initial = provider.currentFragment(id).orElseThrow();
		assertTrue(initial.at("/resources/host-a").isObject(), "The initial fragment holds host-a");

		// The data source changes: a re-evaluation must re-read it, not replay a cached value.
		Files.writeString(csv, "host-a\nhost-b\n");
		final JsonNode reevaluated = provider.reevaluate(id).orElseThrow();

		assertTrue(reevaluated.at("/resources/host-b").isObject(), "The re-evaluated fragment holds the new host");
		assertNotEquals(initial, reevaluated, "The fragment must reflect the new data");
		assertEquals(reevaluated, provider.currentFragment(id).orElseThrow(), "currentFragment tracks the last result");
	}

	@Test
	void testReevaluateUnchangedDataYieldsAnEqualFragment(@TempDir final Path tempDir) throws Exception {
		final Path csv = tempDir.resolve("hosts.csv");
		Files.writeString(csv, "host-a\n");
		writeTemplate(tempDir.resolve("hosts.vm"), csv, "0/5 * * * * ?");

		final var provider = new ProgrammableConfigurationProvider();
		provider.load(tempDir);
		final String id = provider.getScheduledReEvaluations().iterator().next().id();

		// Change detection upstream relies on this equality when nothing moved.
		assertEquals(provider.currentFragment(id).orElseThrow(), provider.reevaluate(id).orElseThrow());
	}

	@Test
	void testScopedReEvaluationDoesNotReRunOtherTemplates(@TempDir final Path tempDir) throws Exception {
		final Path csvA = tempDir.resolve("a.csv");
		final Path csvB = tempDir.resolve("b.csv");
		Files.writeString(csvA, "host-a\n");
		Files.writeString(csvB, "host-b\n");
		writeTemplate(tempDir.resolve("a.vm"), csvA, "0/5 * * * * ?");
		writeTemplate(tempDir.resolve("b.vm"), csvB, "0/5 * * * * ?");

		final var provider = new ProgrammableConfigurationProvider();
		provider.load(tempDir);

		final String idA = tempDir.resolve("a.vm").toAbsolutePath().toString();

		// Both data sources change, but only template A is re-evaluated.
		Files.writeString(csvA, "host-a2\n");
		Files.writeString(csvB, "host-b2\n");
		provider.reevaluate(idA);

		final List<JsonNode> fragments = new ArrayList<>();
		provider.runReusingCachedFragments(() -> fragments.addAll(provider.load(tempDir)));

		final String rendered = fragments.toString();
		assertTrue(rendered.contains("host-a2"), "The re-evaluated template must carry its new data");
		assertTrue(rendered.contains("host-b"), "The other template must still be present");
		assertFalse(
			rendered.contains("host-b2"),
			"The other template's source must not be re-run: it should be served from cache"
		);
	}

	@Test
	void testScopeIsLiftedAfterTheAction(@TempDir final Path tempDir) throws Exception {
		final Path csv = tempDir.resolve("hosts.csv");
		Files.writeString(csv, "host-a\n");
		writeTemplate(tempDir.resolve("hosts.vm"), csv, "0/5 * * * * ?");

		final var provider = new ProgrammableConfigurationProvider();
		provider.load(tempDir);

		provider.runReusingCachedFragments(() -> provider.load(tempDir));

		// Outside the scope a load must render again, so a later change is picked up.
		Files.writeString(csv, "host-a\nhost-c\n");
		assertTrue(
			provider.load(tempDir).toString().contains("host-c"),
			"Once the scope is lifted, a load must render the templates again"
		);
	}

	@Test
	void testUnknownIdIsRejected(@TempDir final Path tempDir) {
		final var provider = new ProgrammableConfigurationProvider();
		provider.load(tempDir);

		final String unknown = tempDir.resolve("missing.vm").toAbsolutePath().toString();
		assertEquals(Optional.empty(), provider.reevaluate(unknown));
		assertEquals(Optional.empty(), provider.currentFragment(unknown));
	}

	@Test
	void testRemovedTemplateStopsBeingScheduled(@TempDir final Path tempDir) throws Exception {
		final Path vm = tempDir.resolve("hosts.vm");
		Files.writeString(vm, "$schedule.cron('0/5 * * * * ?')\nresources: {}\n");

		final var provider = new ProgrammableConfigurationProvider();
		provider.load(tempDir);
		assertEquals(1, provider.getScheduledReEvaluations().size());

		Files.delete(vm);
		provider.load(tempDir);

		assertTrue(provider.getScheduledReEvaluations().isEmpty(), "A deleted template must not stay scheduled");
	}
}
