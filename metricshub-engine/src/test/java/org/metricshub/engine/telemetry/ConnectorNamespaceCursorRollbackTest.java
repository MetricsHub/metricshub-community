package org.metricshub.engine.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
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
		final SourceCursors before = namespace.getSourceCursors(KEY);

		// The poll moves every cursor, and polls a new file
		namespace.setEventLogCursor(KEY, 20);
		namespace.setSourceCursor(KEY, "sel-20");
		namespace.getFileSourceCursors(KEY).put("a.log", 200L);
		namespace.getFileSourceCursors(KEY).put("b.log", 50L);
		final SourceCursors after = namespace.getSourceCursors(KEY);

		namespace.rollBackSourceCursors(KEY, before, after);

		assertEquals(10, namespace.getEventLogCursor(KEY));
		assertNull(namespace.getSourceCursor(KEY));
		assertEquals(Map.of("a.log", 100L), namespace.getFileSourceCursors(KEY));
	}

	@Test
	void leavesTheCursorsAnotherRunMoved() {
		final ConnectorNamespace namespace = new ConnectorNamespace();
		namespace.setEventLogCursor(KEY, 10);
		namespace.getFileSourceCursors(KEY).put("a.log", 100L);
		final SourceCursors before = namespace.getSourceCursors(KEY);
		namespace.setEventLogCursor(KEY, 20);
		namespace.getFileSourceCursors(KEY).put("a.log", 200L);
		final SourceCursors after = namespace.getSourceCursors(KEY);

		// The next run polled again before the rollback: its entries were mapped, its cursors stay
		namespace.setEventLogCursor(KEY, 30);
		namespace.getFileSourceCursors(KEY).put("a.log", 300L);

		namespace.rollBackSourceCursors(KEY, before, after);

		assertEquals(30, namespace.getEventLogCursor(KEY));
		assertEquals(Map.of("a.log", 300L), namespace.getFileSourceCursors(KEY));
	}
}
