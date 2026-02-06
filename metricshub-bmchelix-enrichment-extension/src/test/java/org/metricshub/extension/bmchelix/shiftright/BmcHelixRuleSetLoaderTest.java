package org.metricshub.extension.bmchelix.shiftright;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BmcHelixRuleSetLoaderTest {

	@Test
	void getRuleSet_shouldLoadRulesFromYaml() {
		final BmcHelixRuleSet ruleSet = BmcHelixRuleSetLoader.getRuleSet();

		assertNotNull(ruleSet, "Rule set should be loaded");
		assertTrue(ruleSet.getIdentityRules().containsKey("agent"), "Agent rule should exist");
		assertTrue(ruleSet.getIdentityRules().containsKey("site"), "Site rule should exist");
		assertEquals(
			"host.name",
			ruleSet.getIdentityRules().get("agent").getEntityNameFrom(),
			"Agent rule should map entityNameFrom to host.name"
		);
	}
}
