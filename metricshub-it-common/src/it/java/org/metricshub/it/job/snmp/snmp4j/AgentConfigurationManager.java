package org.metricshub.it.job.snmp.snmp4j;

import org.snmp4j.MessageDispatcher;
import org.snmp4j.agent.AgentConfigManager;
import org.snmp4j.agent.CommandProcessor;
import org.snmp4j.agent.MOServer;
import org.snmp4j.agent.SnmpRequestProcessor;
import org.snmp4j.agent.io.MOInputFactory;
import org.snmp4j.agent.io.MOPersistenceProvider;
import org.snmp4j.agent.security.VACM;
import org.snmp4j.cfg.EngineBootsProvider;
import org.snmp4j.smi.OctetString;
import org.snmp4j.util.WorkerPool;

/**
 * Extends the default {@link AgentConfigManager} in order to create our {@link SnmpRequestProcessor}
 */
public class AgentConfigurationManager extends AgentConfigManager {

	public AgentConfigurationManager(
		OctetString agentsOwnEngineID,
		MessageDispatcher messageDispatcher,
		VACM vacm,
		MOServer[] moServers,
		WorkerPool workerPool,
		MOInputFactory configurationFactory,
		MOPersistenceProvider persistenceProvider,
		EngineBootsProvider engineBootsProvider
	) {
		super(
			agentsOwnEngineID,
			messageDispatcher,
			vacm,
			moServers,
			workerPool,
			configurationFactory,
			persistenceProvider,
			engineBootsProvider
		);
	}

	@Override
	protected CommandProcessor createCommandProcessor(OctetString engineID) {
		// Our SnmpRequestProcessor
		return new SnmpRequestProcessor(engineID);
	}
}
