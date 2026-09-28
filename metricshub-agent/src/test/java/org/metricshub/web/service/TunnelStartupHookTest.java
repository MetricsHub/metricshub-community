package org.metricshub.web.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.tunnel.TunnelService;
import org.metricshub.web.AgentContextHolder;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class TunnelStartupHookTest {

	private final AgentContextHolder agentContextHolder = mock(AgentContextHolder.class);
	private final ToolCallbackProvider toolCallbackProvider = mock(ToolCallbackProvider.class);

	@Test
	void onStartupShouldStartTheServiceAndRegisterItsShutdown() {
		final TunnelService service = mock(TunnelService.class);
		final AtomicReference<Runnable> shutdown = new AtomicReference<>();
		final TunnelStartupHook hook = new TunnelStartupHook(
			agentContextHolder,
			toolCallbackProvider,
			(holder, provider) -> {
				assertSame(agentContextHolder, holder);
				assertSame(toolCallbackProvider, provider);
				return service;
			},
			shutdown::set
		);

		hook.onStartup();

		verify(service).start();
		assertNotNull(shutdown.get(), "The service shutdown must be registered as a JVM shutdown hook");
		shutdown.get().run();
		verify(service).shutdown();
	}

	@Test
	void testIsInstantiableBySpring() {
		// The class declares a second, test-only constructor: without @Autowired on the public one
		// Spring finds no unique candidate. No @SpringBootTest exists in this module, so this is the
		// only guard against that regression.
		try (var context = new AnnotationConfigApplicationContext()) {
			context.getBeanFactory().registerSingleton("agentContextHolder", agentContextHolder);
			context.getBeanFactory().registerSingleton("metricshubTools", toolCallbackProvider);
			context.register(TunnelStartupHook.class);
			context.refresh();

			// Instantiating the bean must not run the hook: onStartup() is driven by
			// AgentStartupRunner on ApplicationReadyEvent, which this bare context never publishes.
			assertNotNull(context.getBean(TunnelStartupHook.class));
		}
	}
}
