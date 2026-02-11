package org.metricshub.extension.bmchelix.shiftright;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BmcHelixRuleSetTest {

	@Test
	void testIdentityRulesIsInitialized() {
		final BmcHelixRuleSet ruleSet = new BmcHelixRuleSet();

		assertNotNull(ruleSet.getIdentityRules(), "Identity rules map should be initialized");
		assertEquals(0, ruleSet.getIdentityRules().size(), "Identity rules should be empty by default");
	}

	@Test
	void testIdentityRuleStoresValues() {
		final BmcHelixRuleSet ruleSet = new BmcHelixRuleSet();
		final BmcHelixRuleSet.IdentityRule rule = new BmcHelixRuleSet.IdentityRule();
		rule.setEntityNameFrom("host.name");
		rule.setInstanceNameFrom("service.name");
		rule.setEntityTypeId("agent");

		final Map<String, BmcHelixRuleSet.IdentityRule> rules = new LinkedHashMap<>();
		rules.put("agent", rule);
		ruleSet.setIdentityRules(rules);

		final BmcHelixRuleSet.IdentityRule result = ruleSet.getIdentityRules().get("agent");
		assertEquals("host.name", result.getEntityNameFrom());
		assertEquals("service.name", result.getInstanceNameFrom());
		assertEquals("agent", result.getEntityTypeId());
	}
}
