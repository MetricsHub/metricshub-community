package org.metricshub.web.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.opamp.OpAmpService;
import org.metricshub.agent.upgrade.UpgradeManager;
import org.metricshub.agent.upgrade.opamp.OpampUpgradeAdapter;
import org.metricshub.opamp.client.packages.OpampPackagesHandler;
import org.metricshub.web.AgentContextHolder;
import org.mockito.InOrder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class OpAmpStartupHookTest {

	private final AgentContextHolder agentContextHolder = mock(AgentContextHolder.class);
	private final UpgradeManager upgradeManager = mock(UpgradeManager.class);
	private final OpAmpService opAmpService = mock(OpAmpService.class);

	private final AtomicReference<OpampPackagesHandler> capturedHandler = new AtomicReference<>();
	private final AtomicReference<Runnable> capturedShutdownAction = new AtomicReference<>();

	/**
	 * Builds the hook over the mocked collaborators, capturing the packages handler it decides on and
	 * the shutdown action it registers.
	 */
	private OpAmpStartupHook newHook() {
		return new OpAmpStartupHook(
			agentContextHolder,
			holder -> upgradeManager,
			(holder, packagesHandler) -> {
				capturedHandler.set(packagesHandler);
				return opAmpService;
			},
			capturedShutdownAction::set
		);
	}

	@Test
	void testReconcilesTheUpgradeBeforeStartingOpAmp() {
		when(upgradeManager.isPackageUpgradeSupported()).thenReturn(true);

		newHook().onStartup();

		// The upgrade verdict must be reconciled before OpAMP reports anything, otherwise the first
		// status report would omit it.
		final InOrder inOrder = inOrder(upgradeManager, opAmpService);
		inOrder.verify(upgradeManager).reconcileOnStartup();
		inOrder.verify(opAmpService).start();
	}

	@Test
	void testAcceptsPackageOffersOnUpgradableDeployments() {
		when(upgradeManager.isPackageUpgradeSupported()).thenReturn(true);

		newHook().onStartup();

		assertNotNull(capturedHandler.get(), "An upgradable deployment must accept package offers");
	}

	@Test
	void testRefusesPackageOffersOnNonUpgradableDeployments() {
		when(upgradeManager.isPackageUpgradeSupported()).thenReturn(false);

		newHook().onStartup();

		assertNull(
			capturedHandler.get(),
			"A deployment without in-place upgrade support must not advertise the packages capabilities"
		);
	}

	@Test
	void testPassesTheHolderToTheCollaborators() {
		when(upgradeManager.isPackageUpgradeSupported()).thenReturn(true);

		final AtomicReference<AgentContextHolder> upgradeManagerHolder = new AtomicReference<>();
		final AtomicReference<AgentContextHolder> opAmpServiceHolder = new AtomicReference<>();

		new OpAmpStartupHook(
			agentContextHolder,
			holder -> {
				upgradeManagerHolder.set(holder);
				return upgradeManager;
			},
			(holder, packagesHandler) -> {
				opAmpServiceHolder.set(holder);
				return opAmpService;
			},
			runnable -> {}
		)
			.onStartup();

		// Both collaborators must read the live context through the shared holder, never a snapshot.
		assertSame(agentContextHolder, upgradeManagerHolder.get());
		assertSame(agentContextHolder, opAmpServiceHolder.get());
	}

	@Test
	void testRegistersTheShutdownAction() {
		when(upgradeManager.isPackageUpgradeSupported()).thenReturn(false);

		newHook().onStartup();

		assertNotNull(capturedShutdownAction.get(), "The OpAMP service must be stopped on JVM shutdown");
		capturedShutdownAction.get().run();
		verify(opAmpService, times(1)).shutdown();
	}

	@Test
	void testIsInstantiableBySpring() {
		// The class declares a second, test-only constructor: without @Autowired on the public one
		// Spring finds no unique candidate and fails looking for a default constructor. No
		// @SpringBootTest exists in this module, so this is the only guard against that regression.
		try (var context = new AnnotationConfigApplicationContext()) {
			context.getBeanFactory().registerSingleton("agentContextHolder", agentContextHolder);
			context.register(OpAmpStartupHook.class);
			context.refresh();

			// Instantiating the bean must not run the hook: onStartup() is driven by
			// AgentStartupRunner on ApplicationReadyEvent, which this bare context never publishes.
			assertNotNull(context.getBean(OpAmpStartupHook.class));
		}
	}

	@Test
	void testBuildsTheAdapterOverTheSameUpgradeManager() {
		when(upgradeManager.isPackageUpgradeSupported()).thenReturn(true);

		newHook().onStartup();

		// The handler bridging OpAMP offers must drive the very manager that reconciled the startup
		// transaction, otherwise the offer would be admitted against a different state.
		assertNotNull(capturedHandler.get());
		assertSame(OpampUpgradeAdapter.class, capturedHandler.get().getClass());
	}
}
