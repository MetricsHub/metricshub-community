package org.metricshub.web.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.Test;

class AgentStartupRunnerTest {

	@Test
	void testRunsEveryHookOnce() {
		final StartupHook first = mock(StartupHook.class);
		final StartupHook second = mock(StartupHook.class);

		new AgentStartupRunner(List.of(first, second)).runAll();

		verify(first, times(1)).onStartup();
		verify(second, times(1)).onStartup();
	}

	@Test
	void testIsolatesFailingHook() {
		final StartupHook failing = mock(StartupHook.class);
		doThrow(new IllegalStateException("boom")).when(failing).onStartup();
		final StartupHook healthy = mock(StartupHook.class);

		final AgentStartupRunner runner = new AgentStartupRunner(List.of(failing, healthy));

		// A throwing hook must not propagate nor stop the remaining hooks.
		assertDoesNotThrow(runner::runAll);
		verify(healthy, times(1)).onStartup();
	}

	@Test
	void testRunsHooksOnlyOnce() {
		final StartupHook hook = mock(StartupHook.class);
		final AgentStartupRunner runner = new AgentStartupRunner(List.of(hook));

		runner.runAll();
		runner.runAll();

		verify(hook, times(1)).onStartup();
	}

	@Test
	void testNoHooksIsANoop() {
		assertDoesNotThrow(() -> new AgentStartupRunner(List.of()).runAll());
	}
}
