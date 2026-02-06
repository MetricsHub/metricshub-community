package org.metricshub.extension.bmchelix.shiftleft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.bmchelix.BmcHelixEnrichmentExtension;

class BmcHelixTelemetryManagerEnricherTest {

	@Test
	void enrich_shouldApplyCandidateInstanceName() {
		final TelemetryManager telemetryManager = new TelemetryManager();
		final Monitor monitor = new Monitor();
		monitor.setId("resource-1");
		monitor.setType("site");
		monitor.addAttribute("service.name", "svc-1");

		final Map<String, Monitor> monitorsByType = new HashMap<>();
		monitorsByType.put(monitor.getId(), monitor);
		final Map<String, Map<String, Monitor>> monitors = new HashMap<>();
		monitors.put(monitor.getType(), monitorsByType);
		telemetryManager.setMonitors(monitors);

		final BmcHelixTelemetryManagerEnricher enricher = new BmcHelixTelemetryManagerEnricher();
		enricher.enrich(telemetryManager);

		assertEquals("resource-1", monitor.getAttribute(BmcHelixEnrichmentExtension.ENTITY_NAME_KEY));
		assertEquals("site", monitor.getAttribute(BmcHelixEnrichmentExtension.ENTITY_TYPE_ID_KEY));
		assertEquals("svc-1", monitor.getAttribute(BmcHelixEnrichmentExtension.INSTANCE_NAME_KEY));
	}

	@Test
	void enrich_shouldFallbackToEntityNameWhenNoCandidate() {
		final TelemetryManager telemetryManager = new TelemetryManager();
		final Monitor monitor = new Monitor();
		monitor.setId("resource-2");
		monitor.setType("host");

		final Map<String, Monitor> monitorsByType = new HashMap<>();
		monitorsByType.put(monitor.getId(), monitor);
		final Map<String, Map<String, Monitor>> monitors = new HashMap<>();
		monitors.put(monitor.getType(), monitorsByType);
		telemetryManager.setMonitors(monitors);

		final BmcHelixTelemetryManagerEnricher enricher = new BmcHelixTelemetryManagerEnricher();
		enricher.enrich(telemetryManager);

		assertEquals("resource-2", monitor.getAttribute(BmcHelixEnrichmentExtension.INSTANCE_NAME_KEY));
	}
}
