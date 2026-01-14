package org.metricshub.web.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import org.junit.jupiter.api.Test;

class ChatOpenAiConfigurationPropertiesTest {

	@Test
	void defaultsShouldBeInitialized() {
		var props = new ChatOpenAiConfigurationProperties();

		assertEquals("gpt-5.2", props.getModel());
		assertTrue(props.getReasoning().isEnabled(), "Reasoning should default to enabled");
		assertEquals(ReasoningEffort.MEDIUM, props.getReasoning().getEffort());
		assertEquals(Reasoning.Summary.AUTO, props.getReasoning().getSummary());
	}

	@Test
	void shouldParseEffortFromStringWithNormalizationAndDefault() {
		assertEquals(ReasoningEffort.HIGH, ChatOpenAiConfigurationProperties.ReasoningProperties.parseEffort("high"));
		assertEquals(ReasoningEffort.MEDIUM, ChatOpenAiConfigurationProperties.ReasoningProperties.parseEffort("unknown"));
	}

	@Test
	void shouldParseSummaryFromStringWithNormalizationAndDefault() {
		assertEquals(
			Reasoning.Summary.DETAILED,
			ChatOpenAiConfigurationProperties.ReasoningProperties.parseSummary(" detailed ")
		);
		assertEquals(Reasoning.Summary.AUTO, ChatOpenAiConfigurationProperties.ReasoningProperties.parseSummary(null));
	}

	@Test
	void convertersShouldReturnEnumsFromStrings() {
		var converters = new OpenAiReasoningConverters();

		var effortConverter = converters.reasoningEffortConverter();
		var summaryConverter = converters.reasoningSummaryConverter();

		assertNotNull(effortConverter.convert("low"));
		assertEquals(ReasoningEffort.LOW, effortConverter.convert("low"));
		assertEquals(ReasoningEffort.MEDIUM, effortConverter.convert(null));

		assertNotNull(summaryConverter.convert("concise"));
		assertEquals(Reasoning.Summary.CONCISE, summaryConverter.convert("concise"));
		assertEquals(Reasoning.Summary.AUTO, summaryConverter.convert(null));
	}
}
