package org.metricshub.it.job.snmp;

import java.util.function.BiConsumer;
import lombok.NonNull;
import org.metricshub.it.job.AbstractITJob;
import org.metricshub.it.job.ITJob;
import org.metricshub.it.job.snmp.snmp4j.SnmpAgent;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;

public class SnmpITJob extends AbstractITJob {

	public SnmpITJob(
		@NonNull TelemetryManager telemetryManager,
		@NonNull BiConsumer<TelemetryManager, Integer> configurationPortUpdater
	) {
		super(telemetryManager);
		this.configurationPortUpdater = configurationPortUpdater;
	}

	private SnmpAgent snmpAgent;

	private BiConsumer<TelemetryManager, Integer> configurationPortUpdater;

	@Override
	public ITJob withServerRecordData(String... dataPaths) throws Exception {
		stopServer();

		snmpAgent = new SnmpAgent();

		snmpAgent.start(dataPaths);

		configurationPortUpdater.accept(telemetryManager, snmpAgent.getPort());

		return this;
	}

	@Override
	public void stopServer() {
		if (isServerStarted()) {
			snmpAgent.stop();
		}
	}

	@Override
	public boolean isServerStarted() {
		return snmpAgent != null && snmpAgent.isStarted();
	}
}
