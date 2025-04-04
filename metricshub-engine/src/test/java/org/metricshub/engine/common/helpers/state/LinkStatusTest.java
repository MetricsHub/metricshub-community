package org.metricshub.engine.common.helpers.state;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LinkStatusTest {

	@Test
	void testInterpret() {
		// {0, ok, plugged} as Plugged
		final String[] pluggedStates = new String[] { "PLUGGED", "OK", "plugged", "ok", "0" };
		for (String state : pluggedStates) {
			assertEquals(
				LinkStatus.PLUGGED,
				LinkStatus.interpret(state).get(),
				"Unexpected plugged state: %s".formatted(state)
			);
		}

		// {1, degraded, failed, 2, unplugged, warn, alarm} as Unplugged
		final String[] unpluggedStates = new String[] {
			"UNPLUGGED",
			"DEGRADED",
			"FAILED",
			"WARN",
			"ALARM",
			"unplugged",
			"degraded",
			"failed",
			"unplugged",
			"warn",
			"alarm",
			"1",
			"2"
		};
		for (String state : unpluggedStates) {
			assertEquals(
				LinkStatus.UNPLUGGED,
				LinkStatus.interpret(state).get(),
				"Unexpected unplugged state: %s".formatted(state)
			);
		}
	}
}
