package org.metricshub.it.d;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.connector.model.identity.criterion.Criterion;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * Test-only protocol extension that does <b>not</b> declare any dependency. Its {@link #executeQuery}
 * attempts to load a class owned by extension A and must fail, proving that without a declared
 * {@code Requires} an extension cannot reach a sibling's classes.
 */
public class ProtocolD implements IProtocolExtension {

	@Override
	public boolean isValidConfiguration(IConfiguration configuration) {
		return false;
	}

	@Override
	public Set<Class<? extends Source>> getSupportedSources() {
		return Set.of();
	}

	@Override
	public Map<Class<? extends IConfiguration>, Set<Class<? extends Source>>> getConfigurationToSourceMapping() {
		return Map.of();
	}

	@Override
	public Set<Class<? extends Criterion>> getSupportedCriteria() {
		return Set.of();
	}

	@Override
	public Optional<Boolean> checkProtocol(TelemetryManager telemetryManager) {
		return Optional.empty();
	}

	@Override
	public SourceTable processSource(Source source, String connectorId, TelemetryManager telemetryManager) {
		return SourceTable.empty();
	}

	@Override
	public CriterionTestResult processCriterion(
		Criterion criterion,
		String connectorId,
		TelemetryManager telemetryManager,
		boolean logMode
	) {
		return CriterionTestResult.empty();
	}

	@Override
	public boolean isSupportedConfigurationType(String configurationType) {
		return false;
	}

	@Override
	public IConfiguration buildConfiguration(String configurationType, JsonNode jsonNode, UnaryOperator<char[]> decrypt) {
		return null;
	}

	@Override
	public String getIdentifier() {
		return "it-d";
	}

	@Override
	public String executeQuery(IConfiguration configuration, JsonNode queryNode) {
		try {
			Class.forName("org.metricshub.it.a.ProtocolA", false, getClass().getClassLoader());
			return "found";
		} catch (ClassNotFoundException e) {
			return "not-found";
		}
	}
}
