package org.metricshub.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.web.dto.SearchMatch;
import org.metricshub.web.dto.telemetry.AgentTelemetry;
import org.metricshub.web.dto.telemetry.ConnectorTelemetry;
import org.metricshub.web.dto.telemetry.InstanceTelemetry;
import org.metricshub.web.dto.telemetry.MonitorTypeTelemetry;
import org.metricshub.web.dto.telemetry.ResourceGroupTelemetry;
import org.metricshub.web.dto.telemetry.ResourceTelemetry;

class SearchServiceTest {

	private SearchService searchService;

	@BeforeEach
	void setUp() {
		searchService = new SearchService();
	}

	@Test
	void testSearchReturnsFuzzyMatchesAcrossHierarchy() {
		AgentTelemetry hierarchy = buildTelemetryHierarchy();

		List<SearchMatch> matches = searchService.search("core", hierarchy);
		List<SearchMatch> instanceMatches = matches.stream().filter(match -> "instance".equals(match.getType()))
				.toList();

		assertEquals(
				List.of("core-0", "core-1"),
				instanceMatches.stream().map(SearchMatch::getName).toList(),
				() -> "Expected two core matches but got " + instanceMatches);
		assertEquals(
				"/explorer/resource-groups/rg-alpha/resources/server-east/monitors/cpu#core-0",
				instanceMatches.get(0).getPath(),
				() -> "Unexpected path for first core match -> " + instanceMatches.get(0).getPath());
		assertEquals(
				"/explorer/resource-groups/rg-alpha/resources/server-east/monitors/cpu#core-1",
				instanceMatches.get(1).getPath(),
				() -> "Unexpected path for second core match -> " + instanceMatches.get(1).getPath());
	}

	@Test
	void testSearchSkipsNodesBelowSimilarityThreshold() {
		AgentTelemetry hierarchy = buildThresholdTelemetryHierarchy();

		List<SearchMatch> matches = searchService.search("zzz-nope", hierarchy);

		assertTrue(matches.isEmpty(), () -> "Expected no matches but found " + matches);
	}

	@Test
	void testSearchSortsByScoreThenPath() {
		AgentTelemetry hierarchy = buildOrderingTelemetryHierarchy();

		List<SearchMatch> matches = searchService.search("match", hierarchy);

		assertEquals(
				List.of("match", "match", "mash"),
				matches.stream().map(SearchMatch::getName).toList(),
				() -> "Unexpected match order -> " + matches);
		assertEquals(
				"/explorer/resource-groups/group-alpha/resources/match",
				matches.get(0).getPath(),
				() -> "First match should use the lexicographically smaller path but was " + matches.get(0).getPath());
		assertEquals(
				"/explorer/resources/match",
				matches.get(1).getPath(),
				() -> "Second match should be the top-level resource but was " + matches.get(1).getPath());
		assertTrue(
				matches.get(0).getJaroWinklerScore() == matches.get(1).getJaroWinklerScore(),
				() -> "First two matches should have identical JW scores but were " + matches);
		assertTrue(
				matches.get(1).getJaroWinklerScore() > matches.get(2).getJaroWinklerScore(),
				() -> "Third match should have a lower JW score than the first two but was " + matches);
	}

	/**
	 * Builds a telemetry hierarchy for testing the search service.
	 * 
	 * @return The telemetry hierarchy
	 */
	private AgentTelemetry buildTelemetryHierarchy() {
		InstanceTelemetry core0 = InstanceTelemetry.builder().name("core-0").build();
		InstanceTelemetry core1 = InstanceTelemetry.builder().name("core-1").build();
		MonitorTypeTelemetry cpu = MonitorTypeTelemetry.builder().name("cpu").instances(List.of(core0, core1)).build();
		ConnectorTelemetry snmp = ConnectorTelemetry.builder().name("snmp").monitors(List.of(cpu)).build();
		ResourceTelemetry eastServer = ResourceTelemetry.builder().name("server-east").connectors(List.of(snmp))
				.build();

		ResourceGroupTelemetry resourceGroupTelemetry = ResourceGroupTelemetry
				.builder()
				.name("rg-alpha")
				.resources(List.of(eastServer))
				.build();
		ResourceTelemetry westServer = ResourceTelemetry.builder().name("server-west").build();

		return AgentTelemetry
				.builder()
				.name("EdgeAgent")
				.resourceGroups(List.of(resourceGroupTelemetry))
				.resources(List.of(westServer))
				.build();
	}

	/**
	 * Builds a telemetry hierarchy for testing the search service sorting.
	 * 
	 * @return The telemetry hierarchy
	 */
	private AgentTelemetry buildOrderingTelemetryHierarchy() {
		ResourceTelemetry nestedMatch = ResourceTelemetry.builder().name("match").build();
		ResourceGroupTelemetry matchGroup = ResourceGroupTelemetry
				.builder()
				.name("group-alpha")
				.resources(List.of(nestedMatch))
				.build();

		ResourceTelemetry topLevelMatch = ResourceTelemetry.builder().name("match").build();
		ResourceTelemetry mashResource = ResourceTelemetry.builder().name("mash").build();

		return AgentTelemetry
				.builder()
				.name("TopAgent")
				.resourceGroups(List.of(matchGroup))
				.resources(List.of(topLevelMatch, mashResource))
				.build();
	}

	/**
	 * Builds a telemetry hierarchy for testing the search service threshold.
	 * 
	 * @return The telemetry hierarchy
	 */
	private AgentTelemetry buildThresholdTelemetryHierarchy() {
		ResourceTelemetry nodeA = ResourceTelemetry.builder().name("alpha").build();
		ResourceTelemetry nodeB = ResourceTelemetry.builder().name("beta").build();

		return AgentTelemetry.builder().name("root").resources(List.of(nodeA, nodeB)).build();
	}
}
