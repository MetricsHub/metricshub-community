package org.metricshub.programmable.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScheduleToolTest {

	@Test
	void testNoDeclarationYieldsNoCron() {
		assertTrue(new ScheduleTool().getCron().isEmpty(), "A template declaring nothing has no schedule");
	}

	@Test
	void testCronIsRecordedAndPrintsNothing() {
		final ScheduleTool tool = new ScheduleTool();

		assertEquals("", tool.cron("0/5 * * * * ?"), "The directive must render to the empty string");
		assertEquals(Optional.of("0/5 * * * * ?"), tool.getCron());
	}

	@Test
	void testLastDeclarationWins() {
		final ScheduleTool tool = new ScheduleTool();

		tool.cron("0/5 * * * * ?");
		tool.cron("0 0/30 * * * ?");

		assertEquals(Optional.of("0 0/30 * * * ?"), tool.getCron(), "Only the last declaration is applied");
	}

	@Test
	void testBlankCronIsIgnored() {
		final ScheduleTool tool = new ScheduleTool();

		assertEquals("", tool.cron("   "));
		assertTrue(tool.getCron().isEmpty(), "A blank expression must not declare a schedule");

		// A blank expression must not wipe an already-declared schedule either.
		tool.cron("0/5 * * * * ?");
		tool.cron(null);
		assertEquals(Optional.of("0/5 * * * * ?"), tool.getCron());
	}

	@Test
	void testCronIsTrimmed() {
		final ScheduleTool tool = new ScheduleTool();

		tool.cron("  0/5 * * * * ?  ");

		assertEquals(Optional.of("0/5 * * * * ?"), tool.getCron());
	}

	@Test
	void testResetClearsTheDeclaration() {
		final ScheduleTool tool = new ScheduleTool();
		tool.cron("0/5 * * * * ?");

		tool.reset();

		assertTrue(tool.getCron().isEmpty(), "reset() must clear the schedule so a render starts clean");
	}
}
