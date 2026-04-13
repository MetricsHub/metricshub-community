package org.metricshub.extension;

import java.util.List;

import org.metricshub.engine.extension.IStrategyProviderExtension;
import org.metricshub.engine.strategy.IStrategy;
import org.metricshub.engine.telemetry.TelemetryManager;

public class StrategyProvider implements IStrategyProviderExtension {

	@Override
	public List<IStrategy> generate(TelemetryManager telemetryManager, Long strategyTime) {
		return List.of(new IStrategy() {
			
			@Override
			public void run() {
				
			}
			
			@Override
			public long getStrategyTimeout() {
				return 30;
			}
			
			@Override
			public Long getStrategyTime() {
				return System.currentTimeMillis();
			}
		});
	}

}
