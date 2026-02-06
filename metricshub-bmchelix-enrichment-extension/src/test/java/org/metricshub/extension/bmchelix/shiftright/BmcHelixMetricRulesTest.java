package org.metricshub.extension.bmchelix.shiftright;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.extension.bmchelix.BmcHelixEnrichmentExtension;

class BmcHelixMetricRulesTest {

	@Test
	void testEnrichAttributesAppliesAgentRule() {
		final Map<String, String> resourceAttributes = new HashMap<>();
		resourceAttributes.put("host.name", "host-1");
		resourceAttributes.put("service.name", "svc-1");

		final BmcHelixMetricRules rules = new BmcHelixMetricRules();
		rules.enrichAttributes("metricshub.agent.uptime", resourceAttributes);

		assertEquals("host-1", resourceAttributes.get(BmcHelixEnrichmentExtension.ENTITY_NAME_KEY));
		assertEquals("svc-1", resourceAttributes.get(BmcHelixEnrichmentExtension.INSTANCE_NAME_KEY));
		assertEquals("agent", resourceAttributes.get(BmcHelixEnrichmentExtension.ENTITY_TYPE_ID_KEY));
	}

	@Test
	void testEnrichAttributesAppliesSiteRule() {
		final Map<String, String> resourceAttributes = new HashMap<>();
		resourceAttributes.put("site", "site-1");

		final BmcHelixMetricRules rules = new BmcHelixMetricRules();
		rules.enrichAttributes("hw.site.pue", resourceAttributes);

		assertEquals("site-1", resourceAttributes.get(BmcHelixEnrichmentExtension.ENTITY_NAME_KEY));
		assertEquals("site-1", resourceAttributes.get(BmcHelixEnrichmentExtension.INSTANCE_NAME_KEY));
		assertEquals("site", resourceAttributes.get(BmcHelixEnrichmentExtension.ENTITY_TYPE_ID_KEY));
	}

	@Test
	void testEnrichAttributesIgnoresNonMatchingMetrics() {
		final Map<String, String> resourceAttributes = new HashMap<>();
		resourceAttributes.put("host.name", "host-1");

		final BmcHelixMetricRules rules = new BmcHelixMetricRules();
		rules.enrichAttributes("system.cpu.utilization", resourceAttributes);

		assertNull(resourceAttributes.get(BmcHelixEnrichmentExtension.ENTITY_TYPE_ID_KEY));
	}
}
