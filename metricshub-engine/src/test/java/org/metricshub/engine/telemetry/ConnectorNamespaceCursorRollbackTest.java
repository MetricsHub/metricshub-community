package org.metricshub.engine.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.telemetry.ConnectorNamespace.SourceCursors;

/**
 * The rollback of a poll of an incremental source whose entries were not mapped (its job was cut short by an
 * interrupt): every cursor the poll moved goes back, and a cursor another run moved since is left alone.
 */
class ConnectorNamespaceCursorRollbackTest {

	private static final String KEY = "${source::events}";

	@Test
	void rollsBackTheCursorsThePollMoved() {
		final ConnectorNamespace namespace = new ConnectorNamespace();
		namespace.setEventLogCursor(KEY, 10);
		namespace.getFileSourceCursors(KEY).put("a.log", 100L);
		final long poll = namespace.beginPoll(KEY);
		final SourceCursors before = namespace.getSourceCursors(KEY);

		// The poll moves every cursor, and polls a new file
		namespace.setEventLogCursor(KEY, 20);
		namespace.setSourceCursor(KEY, "sel-20");
		namespace.getFileSourceCursors(KEY).put("a.log", 200L);
		namespace.getFileSourceCursors(KEY).put("b.log", 50L);
		final SourceCursors after = namespace.getSourceCursors(KEY);

		namespace.rollBackSourceCursors(KEY, poll, before, after);

		assertEquals(10, namespace.getEventLogCursor(KEY));
		assertNull(namespace.getSourceCursor(KEY));
		assertEquals(Map.of("a.log", 100L), namespace.getFileSourceCursors(KEY));
	}

	@Test
	void leavesTheCursorsAnotherRunMoved() {
		final ConnectorNamespace namespace = new ConnectorNamespace();
		namespace.setEventLogCursor(KEY, 10);
		namespace.getFileSourceCursors(KEY).put("a.log", 100L);
		final long poll = namespace.beginPoll(KEY);
		final SourceCursors before = namespace.getSourceCursors(KEY);
		namespace.setEventLogCursor(KEY, 20);
		namespace.getFileSourceCursors(KEY).put("a.log", 200L);
		final SourceCursors after = namespace.getSourceCursors(KEY);

		// Another run moved the cursors before the rollback: its entries were mapped, its cursors stay
		namespace.setEventLogCursor(KEY, 30);
		namespace.getFileSourceCursors(KEY).put("a.log", 300L);

		namespace.rollBackSourceCursors(KEY, poll, before, after);

		assertEquals(30, namespace.getEventLogCursor(KEY));
		assertEquals(Map.of("a.log", 300L), namespace.getFileSourceCursors(KEY));
	}

	@Test
	void refusesTheRollbackOnceANewerPollBeganEvenWithTheSameValues() {
		final ConnectorNamespace namespace = new ConnectorNamespace();
		namespace.setEventLogCursor(KEY, 10);
		final long poll = namespace.beginPoll(KEY);
		final SourceCursors before = namespace.getSourceCursors(KEY);
		namespace.setEventLogCursor(KEY, 20);
		final SourceCursors after = namespace.getSourceCursors(KEY);

		// A newer poll found nothing new: it leaves the cursor at 20, the very value the older poll left
		namespace.beginPoll(KEY);
		namespace.setEventLogCursor(KEY, 20);

		namespace.rollBackSourceCursors(KEY, poll, before, after);

		assertEquals(20, namespace.getEventLogCursor(KEY));
	}

	@Test
	void aRollbackBeforeTheNewerPollIsReadByIt() {
		final ConnectorNamespace namespace = new ConnectorNamespace();
		namespace.setEventLogCursor(KEY, 10);
		final long poll = namespace.beginPoll(KEY);
		final SourceCursors before = namespace.getSourceCursors(KEY);
		namespace.setEventLogCursor(KEY, 20);
		final SourceCursors after = namespace.getSourceCursors(KEY);

		namespace.rollBackSourceCursors(KEY, poll, before, after);
		namespace.beginPoll(KEY);

		assertEquals(10, namespace.getEventLogCursor(KEY));
	}

	@Test
	void aLateRollbackNeverClobbersANewerPoll() throws Exception {
		for (int i = 0; i < 500; i++) {
			final ConnectorNamespace namespace = new ConnectorNamespace();
			namespace.setEventLogCursor(KEY, 10);
			final long poll = namespace.beginPoll(KEY);
			final SourceCursors before = namespace.getSourceCursors(KEY);
			namespace.setEventLogCursor(KEY, 20);
			final SourceCursors after = namespace.getSourceCursors(KEY);

			// The newer poll reads the cursor after it began, and leaves it where it read it (nothing new)
			final AtomicInteger read = new AtomicInteger();
			final CountDownLatch go = new CountDownLatch(1);
			final Thread newerPoll = Thread.ofPlatform().start(() -> {
				awaitQuietly(go);
				namespace.beginPoll(KEY);
				read.set(namespace.getEventLogCursor(KEY));
				namespace.setEventLogCursor(KEY, read.get());
			});
			final Thread lateRollback = Thread.ofPlatform().start(() -> {
				awaitQuietly(go);
				namespace.rollBackSourceCursors(KEY, poll, before, after);
			});
			go.countDown();
			newerPoll.join();
			lateRollback.join();

			assertEquals(read.get(), namespace.getEventLogCursor(KEY), "Iteration " + i);
		}
	}

	/**
	 * Waits for the latch.
	 *
	 * @param latch the latch
	 */
	private static void awaitQuietly(final CountDownLatch latch) {
		try {
			latch.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
