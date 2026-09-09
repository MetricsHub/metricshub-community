package org.metricshub.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.config.AgentConfig;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.service.ReloadService.ReloadResult;

class ConfigurationReloadServiceTest {

	/**
	 * Builds a context whose configuration equals the given one, so ReloadService concludes
	 * NO_CHANGE, which is the outcome that needs no scheduling machinery to observe.
	 */
	private static AgentContext contextWith(final AgentConfig agentConfig) {
		final AgentContext context = mock(AgentContext.class);
		when(context.getAgentConfig()).thenReturn(agentConfig);
		return context;
	}

	@Test
	void testShouldCloseTheComparisonContextWhenNothingChanged() {
		final AgentConfig config = AgentConfig.builder().build();
		final AgentContext running = contextWith(config);
		final AgentContext comparison = contextWith(config);

		final ReloadResult result = ConfigurationReloadService.builder()
			.withRunningAgentContext(running)
			.withComparisonContextSupplier(() -> comparison)
			.withRestartContextSupplier(() -> mock(AgentContext.class))
			.withRestartRequester(supplier -> true)
			.build()
			.reload();

		assertEquals(ReloadResult.NO_CHANGE, result);
		// The throwaway context must never outlive the diff, whatever the outcome.
		verify(comparison, times(1)).close();
	}

	@Test
	void testShouldNotRunTheAfterLocalChangesActionWhenNothingChanged() {
		final AgentConfig config = AgentConfig.builder().build();
		final AtomicBoolean ran = new AtomicBoolean();

		ConfigurationReloadService.builder()
			.withRunningAgentContext(contextWith(config))
			.withComparisonContextSupplier(() -> contextWith(config))
			.withRestartContextSupplier(() -> mock(AgentContext.class))
			.withRestartRequester(supplier -> true)
			.withAfterLocalChanges(() -> ran.set(true))
			.build()
			.reload();

		assertFalse(ran.get(), "Nothing changed, so no resource-level follow-up must run");
	}

	@Test
	void testShouldNotBuildTheRestartContextEagerly() {
		final AgentConfig config = AgentConfig.builder().build();
		final AtomicInteger restartContextBuilds = new AtomicInteger();

		ConfigurationReloadService.builder()
			.withRunningAgentContext(contextWith(config))
			.withComparisonContextSupplier(() -> contextWith(config))
			.withRestartContextSupplier(() -> {
				restartContextBuilds.incrementAndGet();
				return mock(AgentContext.class);
			})
			.withRestartRequester(supplier -> true)
			.build()
			.reload();

		// The supplier is handed over, never invoked here: a coalesced restart must build nothing.
		assertEquals(0, restartContextBuilds.get(), "The restart context must be built lazily");
	}

	@Test
	void testShouldCloseTheComparisonContextWhenTheDiffThrows() {
		final AgentContext running = mock(AgentContext.class);
		when(running.getAgentConfig()).thenThrow(new IllegalStateException("diff blew up"));
		final AgentContext comparison = mock(AgentContext.class);

		final ConfigurationReloadService service = ConfigurationReloadService.builder()
			.withRunningAgentContext(running)
			.withComparisonContextSupplier(() -> comparison)
			.withRestartContextSupplier(() -> mock(AgentContext.class))
			.withRestartRequester(supplier -> true)
			.build();

		assertThrows(IllegalStateException.class, service::reload);

		// A failed diff must not leak the context it had already built.
		verify(comparison, times(1)).close();
	}

	@Test
	void testShouldRequestARestartWhenAGlobalSettingChanged() {
		// A global setting differs, so the diff calls for a full restart.
		final AgentContext running = contextWith(AgentConfig.builder().loggerLevel("info").build());
		final AgentContext comparison = contextWith(AgentConfig.builder().loggerLevel("debug").build());
		final AtomicBoolean asked = new AtomicBoolean();

		final ReloadResult result = ConfigurationReloadService.builder()
			.withRunningAgentContext(running)
			.withComparisonContextSupplier(() -> comparison)
			.withRestartContextSupplier(() -> mock(AgentContext.class))
			.withRestartRequester(supplier -> {
				asked.set(true);
				return true;
			})
			.build()
			.reload();

		assertEquals(ReloadResult.GLOBAL_RESTART_REQUIRED, result);
		assertTrue(asked.get(), "A global change must request a restart");
		// Closed before the restart is served, so a coalesced request leaves nothing running.
		verify(comparison, times(1)).close();
	}

	@Test
	void testShouldSurviveARestartRequesterThatRefuses() {
		final AgentContext running = contextWith(AgentConfig.builder().loggerLevel("info").build());
		final AgentContext comparison = contextWith(AgentConfig.builder().loggerLevel("debug").build());

		// The lifecycle service is not up yet: the refusal is logged, never thrown, and the
		// comparison context is still released.
		final ReloadResult result = ConfigurationReloadService.builder()
			.withRunningAgentContext(running)
			.withComparisonContextSupplier(() -> comparison)
			.withRestartContextSupplier(() -> mock(AgentContext.class))
			.withRestartRequester(supplier -> false)
			.build()
			.reload();

		assertEquals(ReloadResult.GLOBAL_RESTART_REQUIRED, result);
		verify(comparison, times(1)).close();
	}
}
