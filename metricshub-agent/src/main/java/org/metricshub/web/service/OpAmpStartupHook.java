package org.metricshub.web.service;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2026 MetricsHub
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.opamp.OpAmpService;
import org.metricshub.agent.process.runtime.ProcessControl;
import org.metricshub.agent.upgrade.UpgradeManager;
import org.metricshub.agent.upgrade.opamp.OpampUpgradeAdapter;
import org.metricshub.opamp.client.packages.OpampPackagesHandler;
import org.metricshub.web.AgentContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Starts fleet management when the agent is ready: reconciles a pending upgrade, then starts the
 * OpAMP supervisor.
 * <p>
 * This runs as a {@link StartupHook} rather than from the community bootstrap so that the
 * enterprise agent — which boots the same Spring context — gets the exact same startup sequence
 * without any edition-specific wiring.
 * </p>
 * <p>
 * The {@link OpAmpService} deliberately lives at application level, outside the restartable
 * {@code AgentContext}: its supervisor re-reads the {@code opamp:} configuration from the
 * {@link AgentContextHolder} and keeps the OpAMP connection alive across configuration reloads.
 * The hook itself runs once, so a configuration reload never starts a second supervisor.
 * </p>
 */
@Service
@Slf4j
public class OpAmpStartupHook implements StartupHook {

	private final AgentContextHolder agentContextHolder;
	private final Function<AgentContextHolder, UpgradeManager> upgradeManagerFactory;
	private final BiFunction<AgentContextHolder, OpampPackagesHandler, OpAmpService> opAmpServiceFactory;
	private final Consumer<Runnable> shutdownHookRegistrar;

	/**
	 * Creates the hook with the real collaborators.
	 * <p>
	 * Explicitly annotated because the class declares a second, test-only constructor: without the
	 * annotation Spring would find no unique candidate and fall back to a default constructor that
	 * does not exist.
	 * </p>
	 *
	 * @param agentContextHolder the holder of the current agent context
	 */
	@Autowired
	public OpAmpStartupHook(final AgentContextHolder agentContextHolder) {
		this(agentContextHolder, UpgradeManager::new, OpAmpService::new, ProcessControl::addShutdownHook);
	}

	/**
	 * Creates the hook with caller-provided collaborators (used by tests).
	 *
	 * @param agentContextHolder    the holder of the current agent context
	 * @param upgradeManagerFactory builds the upgrade manager from the holder
	 * @param opAmpServiceFactory   builds the OpAMP service from the holder and the packages handler
	 * @param shutdownHookRegistrar registers the shutdown action; the real one adds a JVM shutdown hook
	 */
	OpAmpStartupHook(
		final AgentContextHolder agentContextHolder,
		final Function<AgentContextHolder, UpgradeManager> upgradeManagerFactory,
		final BiFunction<AgentContextHolder, OpampPackagesHandler, OpAmpService> opAmpServiceFactory,
		final Consumer<Runnable> shutdownHookRegistrar
	) {
		this.agentContextHolder = agentContextHolder;
		this.upgradeManagerFactory = upgradeManagerFactory;
		this.opAmpServiceFactory = opAmpServiceFactory;
		this.shutdownHookRegistrar = shutdownHookRegistrar;
	}

	@Override
	public void onStartup() {
		// Reconcile a pending upgrade transaction before anything reports to OpAMP, so the
		// upgrade verdict (SUCCEEDED/FAILED) is part of the first OpAMP status report.
		final UpgradeManager upgradeManager = upgradeManagerFactory.apply(agentContextHolder);
		upgradeManager.reconcileOnStartup();

		// Package offers are accepted only on deployments supporting in-place upgrades (deb/rpm/msi).
		final OpampPackagesHandler packagesHandler = upgradeManager.isPackageUpgradeSupported()
			? new OpampUpgradeAdapter(upgradeManager)
			: null;

		final OpAmpService opAmpService = opAmpServiceFactory.apply(agentContextHolder, packagesHandler);
		opAmpService.start();
		shutdownHookRegistrar.accept(opAmpService::shutdown);

		log.debug("OpAMP service started.");
	}
}
