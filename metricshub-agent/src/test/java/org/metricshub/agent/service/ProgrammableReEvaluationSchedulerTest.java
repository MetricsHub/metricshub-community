package org.metricshub.agent.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.extension.IConfigurationProvider;
import org.metricshub.engine.extension.ScheduledReEvaluation;
import org.metricshub.programmable.configuration.ProgrammableConfigurationProvider;
import org.metricshub.web.AgentContextHolder;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.CronTrigger;

class ProgrammableReEvaluationSchedulerTest {

	/** Builds a context holder exposing the given providers through an extension manager. */
	private static AgentContextHolder holderFor(final IConfigurationProvider... providers) {
		final ExtensionManager extensionManager = ExtensionManager.builder()
			.withConfigurationProviderExtensions(List.of(providers))
			.build();
		final AgentContext context = mock(AgentContext.class);
		when(context.getExtensionManager()).thenReturn(extensionManager);
		final AgentContextHolder holder = mock(AgentContextHolder.class);
		when(holder.getAgentContext()).thenReturn(context);
		return holder;
	}

	@Test
	void testCronFiringReevaluatesAndReloadsOnlyWhenChanged(@TempDir final Path tempDir) throws Exception {
		// A CSV-backed template scheduled as a whole, loaded through a real provider.
		final Path csv = tempDir.resolve("resources.csv");
		Files.writeString(csv, "host-a,linux,ssh,userA,passA\n");
		final String csvPath = csv.toString().replace('\\', '/');
		final String tpl = String.join(
			"\n",
			"$schedule.cron('0 0/15 * * * ?')",
			"#set($lines = $file.readAllLines(\"__CSV__\"))",
			"resources:",
			"#foreach($line in $lines)",
			"#set($fields = $collection.split($line))",
			"  $fields.get(0): {}",
			"#end"
		).replace("__CSV__", csvPath);
		Files.writeString(tempDir.resolve("hosts.vm"), tpl);

		final var provider = new ProgrammableConfigurationProvider();
		provider.load(tempDir);

		// Capture the scheduled task rather than running a real cron.
		final TaskScheduler taskScheduler = mock(TaskScheduler.class);
		final ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
		when(taskScheduler.schedule(taskCaptor.capture(), any(Trigger.class))).thenReturn(mock(ScheduledFuture.class));

		final AtomicInteger reloadCount = new AtomicInteger();
		final var scheduler = new ProgrammableReEvaluationScheduler(
			holderFor(provider),
			taskScheduler,
			reloadCount::incrementAndGet
		);

		scheduler.start();
		final Runnable cronTask = taskCaptor.getValue();

		// Firing with unchanged data must not reload.
		cronTask.run();
		assertEquals(0, reloadCount.get(), "Unchanged data must not trigger a reload");

		// The CSV changes: firing now must reload exactly once.
		Files.writeString(csv, "host-a,linux,ssh,userA,passA\nhost-c,linux,ssh,userC,passC\n");
		cronTask.run();
		assertEquals(1, reloadCount.get(), "Changed data must trigger a reload");

		// Firing again with no further change must not reload again.
		cronTask.run();
		assertEquals(1, reloadCount.get(), "A subsequent unchanged re-evaluation must not reload again");

		scheduler.stop();
	}

	@Test
	void testSlowReEvaluationDoesNotBlockAnother() throws Exception {
		// "slow" blocks on a latch released by the test; "fast" must complete without waiting for it.
		final CountDownLatch slowStarted = new CountDownLatch(1);
		final CountDownLatch releaseSlow = new CountDownLatch(1);
		final IConfigurationProvider provider = new IConfigurationProvider() {
			@Override
			public Collection<JsonNode> load(final Path path) {
				return Collections.emptyList();
			}

			@Override
			public Set<String> getFileExtensions() {
				return Collections.emptySet();
			}

			@Override
			public Optional<JsonNode> reevaluate(final String reEvaluationId) {
				if ("slow".equals(reEvaluationId)) {
					slowStarted.countDown();
					try {
						// Only the slow id blocks; the test releases it after checking the fast id ran.
						if (!releaseSlow.await(5, TimeUnit.SECONDS)) {
							throw new IllegalStateException("Test did not release the slow re-evaluation in time");
						}
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				}
				return Optional.of(TextNode.valueOf(reEvaluationId));
			}
		};

		final var scheduler = new ProgrammableReEvaluationScheduler(
			holderFor(provider),
			mock(TaskScheduler.class),
			() -> {}
		);

		// Fire "slow" on its own thread; it parks inside reevaluate() until the test releases it.
		final Thread slowFiring = new Thread(() -> scheduler.onReEvaluation(provider, "slow"));
		slowFiring.start();
		assertTrue(slowStarted.await(5, TimeUnit.SECONDS), "The slow re-evaluation must have started");

		// While "slow" is still rendering, another template's firing must not queue behind its I/O.
		// Only the compare-and-merge step is serialized, so this must complete promptly.
		assertTimeoutPreemptively(Duration.ofSeconds(2), () -> scheduler.onReEvaluation(provider, "fast"));

		releaseSlow.countDown();
		slowFiring.join(5000);
		assertFalse(slowFiring.isAlive(), "The slow re-evaluation must have completed");
	}

	@Test
	void testConcurrentFiringsAreMergedOneAtATime() throws Exception {
		// Records whether a reload ever runs while another one is in flight.
		final AtomicInteger inFlight = new AtomicInteger();
		final AtomicInteger overlaps = new AtomicInteger();
		final AtomicInteger reloads = new AtomicInteger();

		final IConfigurationProvider provider = new IConfigurationProvider() {
			@Override
			public Collection<JsonNode> load(final Path path) {
				return Collections.emptyList();
			}

			@Override
			public Set<String> getFileExtensions() {
				return Collections.emptySet();
			}

			@Override
			public Optional<JsonNode> reevaluate(final String reEvaluationId) {
				// A distinct value per call, so every firing is seen as a change and reloads.
				return Optional.of(TextNode.valueOf(reEvaluationId + "-" + System.nanoTime()));
			}
		};

		final var scheduler = new ProgrammableReEvaluationScheduler(holderFor(provider), mock(TaskScheduler.class), () -> {
			if (inFlight.incrementAndGet() > 1) {
				overlaps.incrementAndGet();
			}
			try {
				Thread.sleep(20);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			reloads.incrementAndGet();
			inFlight.decrementAndGet();
		});

		// Several templates fire at the same instant.
		final int templates = 6;
		final CountDownLatch startLine = new CountDownLatch(1);
		final List<Thread> threads = new ArrayList<>();
		for (int i = 0; i < templates; i++) {
			final String id = "template-" + i + ".vm";
			final Thread thread = new Thread(() -> {
				try {
					startLine.await();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
				scheduler.onReEvaluation(provider, id);
			});
			threads.add(thread);
			thread.start();
		}
		startLine.countDown();
		for (final Thread thread : threads) {
			thread.join(10_000);
		}

		assertEquals(templates, reloads.get(), "Every firing must have been applied");
		assertEquals(0, overlaps.get(), "Merges must be serialized, never interleaved");
	}

	@Test
	void testRediscoverNewRegistrationsSchedulesOnlyNewOnes() {
		// A provider whose declared re-evaluations grow over time, simulating a new .vm file picked up
		// by a LOCAL_ONLY reload.
		final List<ScheduledReEvaluation> registrations = new ArrayList<>(
			List.of(new ScheduledReEvaluation("script.vm", "0 0/15 * * * ?"))
		);
		final IConfigurationProvider provider = new IConfigurationProvider() {
			@Override
			public Collection<JsonNode> load(final Path path) {
				return Collections.emptyList();
			}

			@Override
			public Set<String> getFileExtensions() {
				return Collections.emptySet();
			}

			@Override
			public Collection<ScheduledReEvaluation> getScheduledReEvaluations() {
				return List.copyOf(registrations);
			}
		};

		final TaskScheduler taskScheduler = mock(TaskScheduler.class);
		when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenReturn(mock(ScheduledFuture.class));

		final var scheduler = new ProgrammableReEvaluationScheduler(holderFor(provider), taskScheduler, () -> {});

		// Initial discovery schedules the one known template.
		scheduler.start();
		verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Trigger.class));

		// Re-discovering with nothing new must not schedule anything again.
		scheduler.rediscoverNewRegistrations();
		verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Trigger.class));

		// A second template appears; rediscovering must schedule only that new one.
		registrations.add(new ScheduledReEvaluation("new-config.vm", "0/15 * * * * ?"));
		scheduler.rediscoverNewRegistrations();
		verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Trigger.class));

		scheduler.stop();
	}

	@Test
	void testDiscoverySweepPicksUpATemplateAddedLater() {
		// The reload path that notices a new .vm file is not always this scheduler's own, so discovery
		// must also run on its periodic sweep.
		final List<ScheduledReEvaluation> registrations = new ArrayList<>(
			List.of(new ScheduledReEvaluation("script.vm", "0/20 * * * * ?"))
		);
		final IConfigurationProvider provider = new IConfigurationProvider() {
			@Override
			public Collection<JsonNode> load(final Path path) {
				return Collections.emptyList();
			}

			@Override
			public Set<String> getFileExtensions() {
				return Collections.emptySet();
			}

			@Override
			public Collection<ScheduledReEvaluation> getScheduledReEvaluations() {
				return List.copyOf(registrations);
			}
		};

		final TaskScheduler taskScheduler = mock(TaskScheduler.class);
		when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenReturn(mock(ScheduledFuture.class));
		final ArgumentCaptor<Runnable> sweepCaptor = ArgumentCaptor.forClass(Runnable.class);
		when(taskScheduler.scheduleAtFixedRate(sweepCaptor.capture(), any(Duration.class))).thenReturn(
			mock(ScheduledFuture.class)
		);

		final var scheduler = new ProgrammableReEvaluationScheduler(holderFor(provider), taskScheduler, () -> {});

		scheduler.start();
		verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Trigger.class));

		// start() must have registered a recurring sweep.
		final Runnable sweep = sweepCaptor.getValue();
		assertNotNull(sweep, "start() must register a periodic discovery sweep");

		// A template appears through a reload path this scheduler never sees; the sweep must catch it.
		registrations.add(new ScheduledReEvaluation("new-config-2.vm", "0/15 * * * * ?"));
		sweep.run();

		verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Trigger.class));

		// A further sweep with nothing new must stay quiet.
		sweep.run();
		verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Trigger.class));

		scheduler.stop();
	}

	@Test
	void testDeletedTemplateHasItsScheduleCancelled() {
		// A deleted template stops being declared by its provider; its cron must not keep firing.
		final List<ScheduledReEvaluation> registrations = new ArrayList<>(
			List.of(
				new ScheduledReEvaluation("script.vm", "0/20 * * * * ?"),
				new ScheduledReEvaluation("new-config2.vm", "0/15 * * * * ?")
			)
		);
		final IConfigurationProvider provider = new IConfigurationProvider() {
			@Override
			public Collection<JsonNode> load(final Path path) {
				return Collections.emptyList();
			}

			@Override
			public Set<String> getFileExtensions() {
				return Collections.emptySet();
			}

			@Override
			public Collection<ScheduledReEvaluation> getScheduledReEvaluations() {
				return List.copyOf(registrations);
			}
		};

		final TaskScheduler taskScheduler = mock(TaskScheduler.class);
		final ScheduledFuture<?> scriptTask = mock(ScheduledFuture.class);
		final ScheduledFuture<?> deletedTask = mock(ScheduledFuture.class);
		// doReturn avoids the wildcard-capture mismatch of when(...).thenReturn(...) here.
		doReturn(scriptTask, deletedTask).when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));

		final var scheduler = new ProgrammableReEvaluationScheduler(holderFor(provider), taskScheduler, () -> {});

		scheduler.start();
		verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Trigger.class));

		// The template file is deleted, so the provider stops declaring it.
		registrations.removeIf(reEvaluation -> "new-config2.vm".equals(reEvaluation.id()));
		scheduler.rediscoverNewRegistrations();

		// Its task must be cancelled, and the surviving template's must be left alone.
		verify(deletedTask, times(1)).cancel(false);
		verify(scriptTask, never()).cancel(anyBoolean());

		// It must not be re-scheduled by a later sweep either.
		scheduler.rediscoverNewRegistrations();
		verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Trigger.class));

		scheduler.stop();
	}

	@Test
	void testChangedCronIsRescheduled() {
		// The template edits its $schedule.cron(...); the id is unchanged, but the old trigger must go.
		final List<ScheduledReEvaluation> registrations = new ArrayList<>(
			List.of(new ScheduledReEvaluation("hosts.vm", "0/15 * * * * ?"))
		);
		final IConfigurationProvider provider = new IConfigurationProvider() {
			@Override
			public Collection<JsonNode> load(final Path path) {
				return Collections.emptyList();
			}

			@Override
			public Set<String> getFileExtensions() {
				return Collections.emptySet();
			}

			@Override
			public Collection<ScheduledReEvaluation> getScheduledReEvaluations() {
				return List.copyOf(registrations);
			}
		};

		final TaskScheduler taskScheduler = mock(TaskScheduler.class);
		final ScheduledFuture<?> firstTask = mock(ScheduledFuture.class);
		final ScheduledFuture<?> secondTask = mock(ScheduledFuture.class);
		doReturn(firstTask, secondTask).when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
		final ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);

		final var scheduler = new ProgrammableReEvaluationScheduler(holderFor(provider), taskScheduler, () -> {});

		scheduler.start();
		verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Trigger.class));

		// Rediscovering with the very same cron must change nothing.
		scheduler.rediscoverNewRegistrations();
		verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Trigger.class));
		verify(firstTask, never()).cancel(anyBoolean());

		// The cron is edited: the task firing on the old expression must be cancelled and replaced.
		registrations.set(0, new ScheduledReEvaluation("hosts.vm", "0 0/30 * * * ?"));
		scheduler.rediscoverNewRegistrations();

		verify(firstTask, times(1)).cancel(false);
		verify(taskScheduler, times(2)).schedule(any(Runnable.class), triggerCaptor.capture());
		assertEquals(
			new CronTrigger("0 0/30 * * * ?").getExpression(),
			((CronTrigger) triggerCaptor.getValue()).getExpression(),
			"The new task must fire on the new expression"
		);

		// A further sweep with the cron now stable must not reschedule again.
		scheduler.rediscoverNewRegistrations();
		verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Trigger.class));

		scheduler.stop();
	}

	@Test
	void testFailedReloadIsRetriedOnTheNextFiring() {
		// The reload fails on the first attempt: the baseline must not advance, so the same change is
		// retried instead of being silently swallowed.
		final AtomicInteger reloadAttempts = new AtomicInteger();
		final IConfigurationProvider provider = new IConfigurationProvider() {
			@Override
			public Collection<JsonNode> load(final Path path) {
				return Collections.emptyList();
			}

			@Override
			public Set<String> getFileExtensions() {
				return Collections.emptySet();
			}

			@Override
			public Optional<JsonNode> reevaluate(final String reEvaluationId) {
				// Stable value: only the failed reload can make the next firing act again.
				return Optional.of(TextNode.valueOf("changed"));
			}
		};

		final var scheduler = new ProgrammableReEvaluationScheduler(holderFor(provider), mock(TaskScheduler.class), () -> {
			if (reloadAttempts.incrementAndGet() == 1) {
				throw new IllegalStateException("reload failed");
			}
		});

		// First firing: a change is detected, the reload throws and must not be fatal.
		assertDoesNotThrow(() -> scheduler.onReEvaluation(provider, "hosts.vm"));
		assertEquals(1, reloadAttempts.get(), "The first firing must have attempted a reload");

		// Second firing with the very same fragment must retry, since the change was never applied.
		scheduler.onReEvaluation(provider, "hosts.vm");
		assertEquals(2, reloadAttempts.get(), "A failed reload must be retried on the next firing");

		// Now that it succeeded, the baseline has moved and a third firing must stay quiet.
		scheduler.onReEvaluation(provider, "hosts.vm");
		assertEquals(2, reloadAttempts.get(), "Once applied, an unchanged fragment must not reload again");
	}

	@Test
	void testInvalidCronDoesNotPreventOtherSchedules() {
		final IConfigurationProvider provider = new IConfigurationProvider() {
			@Override
			public Collection<JsonNode> load(final Path path) {
				return Collections.emptyList();
			}

			@Override
			public Set<String> getFileExtensions() {
				return Collections.emptySet();
			}

			@Override
			public Collection<ScheduledReEvaluation> getScheduledReEvaluations() {
				// '* * * * *' has 5 fields; a Spring cron needs 6.
				return List.of(
					new ScheduledReEvaluation("bad.vm", "* * * * *"),
					new ScheduledReEvaluation("good.vm", "0/15 * * * * ?")
				);
			}
		};

		final TaskScheduler taskScheduler = mock(TaskScheduler.class);
		when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenReturn(mock(ScheduledFuture.class));

		final var scheduler = new ProgrammableReEvaluationScheduler(holderFor(provider), taskScheduler, () -> {});

		scheduler.start();

		// The bad cron is logged and skipped; the valid one is still scheduled.
		verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Trigger.class));

		scheduler.stop();
	}
}
