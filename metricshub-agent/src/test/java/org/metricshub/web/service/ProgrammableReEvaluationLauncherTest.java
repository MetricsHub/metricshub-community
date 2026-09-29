package org.metricshub.web.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.service.ProgrammableReEvaluationLauncher.ReloadFailedException;

class ProgrammableReEvaluationLauncherTest {

	/**
	 * A reload that fails must reach the caller. It used to be logged and swallowed, so the reload
	 * endpoint reported a success and the scheduler moved its baseline on a change never applied.
	 */
	@Test
	void testAFailedReloadIsReportedToTheCaller() {
		final AgentContext context = mock(AgentContext.class);
		when(context.getConfigDirectory()).thenThrow(new IllegalStateException("The directory is gone."));
		final AgentContextHolder holder = mock(AgentContextHolder.class);
		when(holder.getAgentContext()).thenReturn(context);
		final AgentLifecycleService lifecycle = mock(AgentLifecycleService.class);

		final var launcher = new ProgrammableReEvaluationLauncher(holder, lifecycle);

		assertThrows(ReloadFailedException.class, launcher::reloadConfiguration);
		verify(lifecycle, never()).restartAsync(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void testAReloadWithoutContextIsRejected() {
		final var launcher = new ProgrammableReEvaluationLauncher(
			mock(AgentContextHolder.class),
			mock(AgentLifecycleService.class)
		);

		assertThrows(IllegalStateException.class, launcher::reloadConfiguration);
	}
}
