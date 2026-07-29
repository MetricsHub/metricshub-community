package org.metricshub.it.b;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import javax.xml.parsers.DocumentBuilderFactory;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.connector.model.identity.criterion.Criterion;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * Test-only protocol extension that does <b>not</b> bundle any JAXP service file. Its
 * {@link #executeQuery} reports the {@link DocumentBuilderFactory} resolved under its own class
 * loader, which must be the JDK default (never the fake shipped by {@code it-extension-a}).
 */
public class ProtocolB implements IProtocolExtension {

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
		return "it-b";
	}

	@Override
	public String executeQuery(IConfiguration configuration, JsonNode queryNode) {
		return DocumentBuilderFactory.newInstance().getClass().getName();
	}
}
