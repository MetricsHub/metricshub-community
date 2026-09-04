package org.metricshub.web.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class AgentStartupRunnerTest {

	/**
	 * Builds an {@link ObjectProvider} over the given hooks. {@code orderedStream()} is answered with a
	 * fresh {@link Stream} on every call so the provider can be resolved more than once.
	 */
	private static ObjectProvider<StartupHook> provider(final StartupHook... hooks) {
		@SuppressWarnings("unchecked")
		final ObjectProvider<StartupHook> objectProvider = mock(ObjectProvider.class);
		when(objectProvider.orderedStream()).thenAnswer(invocation -> Stream.of(hooks));
		return objectProvider;
	}

	@Test
	void testRunsEveryHookOnce() {
		final StartupHook first = mock(StartupHook.class);
		final StartupHook second = mock(StartupHook.class);

		new AgentStartupRunner(provider(first, second)).runAll();

		verify(first, times(1)).onStartup();
		verify(second, times(1)).onStartup();
	}

	@Test
	void testIsolatesFailingHook() {
		final StartupHook failing = mock(StartupHook.class);
		doThrow(new IllegalStateException("boom")).when(failing).onStartup();
		final StartupHook healthy = mock(StartupHook.class);

		final AgentStartupRunner runner = new AgentStartupRunner(provider(failing, healthy));

		// A throwing hook must not propagate nor stop the remaining hooks.
		assertDoesNotThrow(runner::runAll);
		verify(healthy, times(1)).onStartup();
	}

	@Test
	void testRunsHooksOnlyOnce() {
		final StartupHook hook = mock(StartupHook.class);
		final AgentStartupRunner runner = new AgentStartupRunner(provider(hook));

		runner.runAll();
		runner.runAll();

		verify(hook, times(1)).onStartup();
	}

	@Test
	void testNoHooksIsANoop() {
		// Community edition: no StartupHook bean is declared, the provider resolves to nothing.
		assertDoesNotThrow(() -> new AgentStartupRunner(provider()).runAll());
	}
}
