package org.metricshub.extension.bmchelix.shiftright;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class BmcHelixRuleSetTest {

	@Test
	void identityRules_shouldBeInitialized() {
		final BmcHelixRuleSet ruleSet = new BmcHelixRuleSet();

		assertNotNull(ruleSet.getIdentityRules(), "Identity rules map should be initialized");
		assertEquals(0, ruleSet.getIdentityRules().size(), "Identity rules should be empty by default");
	}

	@Test
	void identityRule_shouldStoreValues() {
		final BmcHelixRuleSet ruleSet = new BmcHelixRuleSet();
		final BmcHelixRuleSet.IdentityRule rule = new BmcHelixRuleSet.IdentityRule();
		rule.setEntityNameFrom("host.name");
		rule.setInstanceNameFrom("service.name");
		rule.setEntityTypeId("agent");

		ruleSet.getIdentityRules().put("agent", rule);

		final BmcHelixRuleSet.IdentityRule result = ruleSet.getIdentityRules().get("agent");
		assertEquals("host.name", result.getEntityNameFrom());
		assertEquals("service.name", result.getInstanceNameFrom());
		assertEquals("agent", result.getEntityTypeId());
	}
}
