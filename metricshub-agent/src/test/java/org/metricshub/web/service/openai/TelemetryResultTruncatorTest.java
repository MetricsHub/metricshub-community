package org.metricshub.web.service.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.web.dto.mcp.MonitorTypeItem;
import org.metricshub.web.dto.mcp.MonitorTypeSummaryVo;
import org.metricshub.web.dto.mcp.MonitorVo;
import org.metricshub.web.dto.mcp.MonitorsVo;
import org.metricshub.web.dto.mcp.TelemetryResult;
import org.metricshub.web.mcp.HostToolResponse;
import org.metricshub.web.mcp.MultiHostToolResponse;

class TelemetryResultTruncatorTest {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	/**
	 * Builds a single monitor type list with the given count of MonitorVo items
	 * followed by one MonitorTypeSummaryVo.
	 */
	private static List<MonitorTypeItem> buildTypeList(final String typeName, final int monitorCount) {
		final List<MonitorTypeItem> items = new ArrayList<>();
		for (int i = 0; i < monitorCount; i++) {
			items.add(
				MonitorVo.builder().attributes(Map.of("id", typeName + "-" + i)).metrics(Map.of("metric1", 1.0)).build()
			);
		}
		items.add(MonitorTypeSummaryVo.builder().totalMonitors(monitorCount).build());
		return items;
	}

	/**
	 * Builds a MonitorsVo with the given types and counts.
	 */
	private static MonitorsVo buildMonitorsVo(final Map<String, Integer> typeToCounts) {
		final Map<String, List<MonitorTypeItem>> monitors = new LinkedHashMap<>();
		for (final var entry : typeToCounts.entrySet()) {
			monitors.put(entry.getKey(), buildTypeList(entry.getKey(), entry.getValue()));
		}
		return MonitorsVo.builder().monitors(monitors).build();
	}

	/**
	 * Builds a single-host response with the given hostname and type counts.
	 */
	private static MultiHostToolResponse<TelemetryResult> buildSingleHostResponse(
		final String hostname,
		final Map<String, Integer> typeToCounts
	) {
		return MultiHostToolResponse
			.<TelemetryResult>builder()
			.hosts(
				List.of(
					HostToolResponse
						.<TelemetryResult>builder()
						.hostname(hostname)
						.response(TelemetryResult.builder().telemetry(buildMonitorsVo(typeToCounts)).build())
						.build()
				)
			)
			.build();
	}

	/**
	 * Builds a multi-host response from a map of hostname to type counts.
	 */
	private static MultiHostToolResponse<TelemetryResult> buildMultiHostResponse(
		final Map<String, Map<String, Integer>> hostToTypeCounts
	) {
		final List<HostToolResponse<TelemetryResult>> hosts = new ArrayList<>();
		for (final var hostEntry : hostToTypeCounts.entrySet()) {
			hosts.add(
				HostToolResponse
					.<TelemetryResult>builder()
					.hostname(hostEntry.getKey())
					.response(TelemetryResult.builder().telemetry(buildMonitorsVo(hostEntry.getValue())).build())
					.build()
			);
		}
		return MultiHostToolResponse.<TelemetryResult>builder().hosts(hosts).build();
	}

	/**
	 * Returns the count of individual monitors (non-summary items) in a type list.
	 */
	private static int monitorCount(final List<MonitorTypeItem> items) {
		return (int) items.stream().filter(MonitorVo.class::isInstance).count();
	}

	/**
	 * Returns true if the last item in the list is a MonitorTypeSummaryVo.
	 */
	private static boolean hasSummary(final List<MonitorTypeItem> items) {
		return !items.isEmpty() && items.get(items.size() - 1) instanceof MonitorTypeSummaryVo;
	}

	@Test
	void testNullResponse() {
		final var result = TelemetryResultTruncator.truncate(null, 1000, OBJECT_MAPPER);
		assertFalse(result.wasTruncated());
		assertTrue(result.truncatedEntries().isEmpty());
		assertEquals("", result.summary());
	}

	@Test
	void testEmptyHostsList() {
		final var response = MultiHostToolResponse.<TelemetryResult>builder().hosts(List.of()).build();
		final var result = TelemetryResultTruncator.truncate(response, 1000, OBJECT_MAPPER);
		assertFalse(result.wasTruncated());
		assertTrue(result.truncatedEntries().isEmpty());
		assertSame(response, result.truncatedResponse());
	}

	@Test
	void testNullHostsList() {
		final var response = MultiHostToolResponse.<TelemetryResult>builder().hosts(null).build();
		final var result = TelemetryResultTruncator.truncate(response, 1000, OBJECT_MAPPER);
		assertFalse(result.wasTruncated());
		assertTrue(result.truncatedEntries().isEmpty());
	}

	// ---- Under size limit ----

	@Test
	void testUnderSizeLimitReturnsOriginal() throws JsonProcessingException {
		final var response = buildSingleHostResponse("host1", Map.of("disk", 5, "cpu", 3));
		final String serialized = OBJECT_MAPPER.writeValueAsString(response);

		final var result = TelemetryResultTruncator.truncate(response, serialized.length() + 100, OBJECT_MAPPER);

		assertFalse(result.wasTruncated());
		assertTrue(result.truncatedEntries().isEmpty());
		// Should return original response (same reference)
		assertSame(response, result.truncatedResponse());
	}

	@Test
	void testUnderSizeLimitExactBoundary() throws Exception {
		final var response = buildSingleHostResponse("host1", Map.of("disk", 2));
		final String serialized = OBJECT_MAPPER.writeValueAsString(response);

		// Exactly at the limit
		final var result = TelemetryResultTruncator.truncate(response, serialized.length(), OBJECT_MAPPER);
		assertFalse(result.wasTruncated());
		assertSame(response, result.truncatedResponse());
	}

	// ---- Truncation: only largest type ----

	@Test
	void testTruncatesOnlyLargestType() throws Exception {
		// disk=50 monitors, fan=2 monitors
		final var response = buildSingleHostResponse("server1", new LinkedHashMap<>(Map.of("disk", 50, "fan", 2)));

		// Set limit smaller than full but large enough to avoid truncating 'fan'
		final String fullJson = OBJECT_MAPPER.writeValueAsString(response);
		// Target: truncated disk should be enough to fit
		final int limit = fullJson.length() / 2;

		final var result = TelemetryResultTruncator.truncate(response, limit, OBJECT_MAPPER);

		assertTrue(result.wasTruncated());
		assertNotNull(result.truncatedResponse());

		// fan should be untouched (still 2 monitors)
		final var monitors = result.truncatedResponse().getHosts().get(0).getResponse().getTelemetry().getMonitors();
		assertEquals(2, monitorCount(monitors.get("fan")));

		// disk should be truncated (fewer than 50)
		assertTrue(monitorCount(monitors.get("disk")) < 50);

		// Summary must be preserved for both types
		assertTrue(hasSummary(monitors.get("disk")));
		assertTrue(hasSummary(monitors.get("fan")));

		// Verify truncatedEntries only contains disk
		assertTrue(result.truncatedEntries().stream().allMatch(e -> e.monitorType().equals("disk")));
	}

	// ---- Truncation: multiple types ----

	@Test
	void testTruncatesMultipleTypesLargestFirst() {
		// Large response: disk=100, cpu=80, memory=5
		final var typeCounts = new LinkedHashMap<String, Integer>();
		typeCounts.put("disk", 100);
		typeCounts.put("cpu", 80);
		typeCounts.put("memory", 5);
		final var response = buildSingleHostResponse("server1", typeCounts);

		// Very tight limit to force truncating both disk and cpu
		final int limit = 500;

		final var result = TelemetryResultTruncator.truncate(response, limit, OBJECT_MAPPER);

		assertTrue(result.wasTruncated());

		final var monitors = result.truncatedResponse().getHosts().get(0).getResponse().getTelemetry().getMonitors();

		// disk was largest → truncated first and most aggressively
		assertTrue(monitorCount(monitors.get("disk")) < 100);
		// cpu was second largest → also truncated
		assertTrue(monitorCount(monitors.get("cpu")) < 80);
		// memory was small → might or might not be truncated depending on limit

		// Summaries always preserved
		assertTrue(hasSummary(monitors.get("disk")));
		assertTrue(hasSummary(monitors.get("cpu")));
		assertTrue(hasSummary(monitors.get("memory")));
	}

	// ---- All entries halved to 0 (best effort) ----

	@Test
	void testAllEntriesHalvedToZeroBestEffort() {
		// Create a response and set an impossibly small limit
		final var response = buildSingleHostResponse("server1", Map.of("disk", 10, "cpu", 8));

		final var result = TelemetryResultTruncator.truncate(response, 1, OBJECT_MAPPER);

		assertTrue(result.wasTruncated());

		final var monitors = result.truncatedResponse().getHosts().get(0).getResponse().getTelemetry().getMonitors();

		// All monitors should be at 0 (summary only)
		assertEquals(0, monitorCount(monitors.get("disk")));
		assertEquals(0, monitorCount(monitors.get("cpu")));

		// But summaries are always preserved!
		assertTrue(hasSummary(monitors.get("disk")));
		assertTrue(hasSummary(monitors.get("cpu")));

		// truncatedEntries should show 0 as currentCount
		for (final var entry : result.truncatedEntries()) {
			assertEquals(0, entry.currentCount());
		}
	}

	// ---- MonitorTypeSummaryVo always preserved ----

	@Test
	void testSummaryAlwaysPreservedEvenAfterFullTruncation() {
		final var response = buildSingleHostResponse("server1", Map.of("disk", 20));

		// Very small limit → will truncate everything to summary only
		final var result = TelemetryResultTruncator.truncate(response, 1, OBJECT_MAPPER);

		assertTrue(result.wasTruncated());

		final var diskList = result
			.truncatedResponse()
			.getHosts()
			.get(0)
			.getResponse()
			.getTelemetry()
			.getMonitors()
			.get("disk");
		assertNotNull(diskList);
		assertFalse(diskList.isEmpty());
		// Last element is summary
		assertTrue(diskList.get(diskList.size() - 1) instanceof MonitorTypeSummaryVo);
		// Summary's totalMonitors still reflects original count
		final MonitorTypeSummaryVo summary = (MonitorTypeSummaryVo) diskList.get(diskList.size() - 1);
		assertEquals(20, summary.getTotalMonitors());
	}

	// ---- Error-only TelemetryResult ----

	@Test
	void testErrorOnlyTelemetryResult() {
		final var hosts = new ArrayList<HostToolResponse<TelemetryResult>>();
		hosts.add(
			HostToolResponse
				.<TelemetryResult>builder()
				.hostname("error-host")
				.response(new TelemetryResult("Connection refused"))
				.build()
		);
		final var response = MultiHostToolResponse.<TelemetryResult>builder().hosts(hosts).build();

		// Even at a very small limit, there are no monitors to truncate
		final var result = TelemetryResultTruncator.truncate(response, 1, OBJECT_MAPPER);

		// No truncation possible (no monitors)
		assertFalse(result.wasTruncated());
		assertTrue(result.truncatedEntries().isEmpty());
	}

	// ---- Multiple hosts ----

	@Test
	void testMultipleHostsIndependentTruncation() throws JsonProcessingException {
		// server1: disk=60, server2: disk=30
		final var hostToTypeCounts = new LinkedHashMap<String, Map<String, Integer>>();
		hostToTypeCounts.put("server1", Map.of("disk", 60));
		hostToTypeCounts.put("server2", Map.of("disk", 30));
		final var response = buildMultiHostResponse(hostToTypeCounts);

		final String fullJson = OBJECT_MAPPER.writeValueAsString(response);
		// Set limit to ~60% of full size to force truncation of the larger host first
		final int limit = (int) (fullJson.length() * 0.6);

		final var result = TelemetryResultTruncator.truncate(response, limit, OBJECT_MAPPER);

		assertTrue(result.wasTruncated());

		final var host1Monitors = result.truncatedResponse().getHosts().get(0).getResponse().getTelemetry().getMonitors();
		final var host2Monitors = result.truncatedResponse().getHosts().get(1).getResponse().getTelemetry().getMonitors();

		// server1's disk (60) should be truncated more than server2's disk (30)
		final int server1DiskCount = monitorCount(host1Monitors.get("disk"));
		assertTrue(server1DiskCount < 60);
		// server2 may or may not be truncated depending on how much truncation was needed

		// Summaries always preserved
		assertTrue(hasSummary(host1Monitors.get("disk")));
		assertTrue(hasSummary(host2Monitors.get("disk")));
	}

	@Test
	void testSameTypeDifferentHostsDifferentTruncationLevels() throws JsonProcessingException {
		// server1: disk=100, server2: disk=10
		final var hostToTypeCounts = new LinkedHashMap<String, Map<String, Integer>>();
		hostToTypeCounts.put("server1", Map.of("disk", 100));
		hostToTypeCounts.put("server2", Map.of("disk", 10));
		final var response = buildMultiHostResponse(hostToTypeCounts);

		final String fullJson = OBJECT_MAPPER.writeValueAsString(response);
		// Set limit slightly smaller than full to only require minimal truncation
		final int limit = (int) (fullJson.length() * 0.6);

		final var result = TelemetryResultTruncator.truncate(response, limit, OBJECT_MAPPER);

		assertTrue(result.wasTruncated());

		// At least server1/disk should be truncated
		final boolean server1Truncated = result
			.truncatedEntries()
			.stream()
			.anyMatch(e -> e.hostname().equals("server1") && e.monitorType().equals("disk"));
		assertTrue(server1Truncated);

		// server2/disk may or may not need truncation — different truncation levels are valid
		final var s1Entry = result
			.truncatedEntries()
			.stream()
			.filter(e -> e.hostname().equals("server1") && e.monitorType().equals("disk"))
			.findFirst()
			.orElseThrow();
		assertTrue(s1Entry.currentCount() < s1Entry.originalCount());
	}

	// ---- Halving convergence ----

	@Test
	void testHalvingConvergenceSequence() {
		// Verify that halving naturally converges: 200 → 100 → 50 → 25 → 12 → 6 → 3 → 1 → 0
		final var response = buildSingleHostResponse("server1", Map.of("disk", 200));

		// Impossibly small limit forces maximum halving
		final var result = TelemetryResultTruncator.truncate(response, 1, OBJECT_MAPPER);

		assertTrue(result.wasTruncated());
		// Should reach 0 (summary only)
		final var diskEntry = result
			.truncatedEntries()
			.stream()
			.filter(e -> e.monitorType().equals("disk"))
			.findFirst()
			.orElseThrow();
		assertEquals(200, diskEntry.originalCount());
		assertEquals(0, diskEntry.currentCount());
	}

	// ---- wasTruncated flag ----

	@Test
	void testWasTruncatedFalseWhenUnderLimit() {
		final var response = buildSingleHostResponse("host1", Map.of("disk", 3));
		final var result = TelemetryResultTruncator.truncate(response, Integer.MAX_VALUE, OBJECT_MAPPER);
		assertFalse(result.wasTruncated());
	}

	@Test
	void testWasTruncatedTrueWhenOverLimit() {
		final var response = buildSingleHostResponse("host1", Map.of("disk", 50));
		final var result = TelemetryResultTruncator.truncate(response, 100, OBJECT_MAPPER);
		assertTrue(result.wasTruncated());
	}

	// ---- truncatedEntries correctness ----

	@Test
	void testTruncatedEntriesContainCorrectOriginalAndCurrentCounts() {
		final var response = buildSingleHostResponse("server1", Map.of("disk", 64));

		// Small limit to force significant truncation
		final var result = TelemetryResultTruncator.truncate(response, 1, OBJECT_MAPPER);

		assertTrue(result.wasTruncated());
		final var diskEntry = result
			.truncatedEntries()
			.stream()
			.filter(e -> e.monitorType().equals("disk"))
			.findFirst()
			.orElseThrow();
		assertEquals("server1", diskEntry.hostname());
		assertEquals("disk", diskEntry.monitorType());
		assertEquals(64, diskEntry.originalCount());
		assertEquals(0, diskEntry.currentCount());
	}

	// ---- Truncation stops early ----

	@Test
	void testTruncationStopsAsSoonAsResponseFits() throws JsonProcessingException {
		// disk=100, fan=2 — a limit that allows ~50 disk monitors should stop early
		final var typeCounts = new LinkedHashMap<String, Integer>();
		typeCounts.put("disk", 100);
		typeCounts.put("fan", 2);
		final var response = buildSingleHostResponse("server1", typeCounts);

		final String fullJson = OBJECT_MAPPER.writeValueAsString(response);
		// Set limit to ~55% of full — should only need to halve disk once or twice
		final int limit = (int) (fullJson.length() * 0.55);

		final var result = TelemetryResultTruncator.truncate(response, limit, OBJECT_MAPPER);

		assertTrue(result.wasTruncated());

		final var monitors = result.truncatedResponse().getHosts().get(0).getResponse().getTelemetry().getMonitors();

		// fan should remain untouched
		assertEquals(2, monitorCount(monitors.get("fan")));

		// disk truncated but not all the way to 0
		final int diskCount = monitorCount(monitors.get("disk"));
		assertTrue(diskCount > 0 && diskCount < 100);
	}

	// ---- Summary format ----

	@Test
	void testSummaryContainsCorrectCountsAndTruncationIndicators() throws JsonProcessingException {
		final var typeCounts = new LinkedHashMap<String, Integer>();
		typeCounts.put("disk", 50);
		typeCounts.put("fan", 3);
		final var response = buildSingleHostResponse("server1", typeCounts);

		// Use a limit that requires truncating disk but not fan
		final String fullJson = OBJECT_MAPPER.writeValueAsString(response);
		final int limit = (int) (fullJson.length() * 0.55);

		final var result = TelemetryResultTruncator.truncate(response, limit, OBJECT_MAPPER);

		assertTrue(result.wasTruncated());
		final String summary = result.summary();

		// Summary should contain host name
		assertTrue(summary.contains("Host 'server1'"), "Summary should contain hostname");
		// Summary should show total original counts
		assertTrue(summary.contains("disk: 50 monitor(s)"), "Summary should show original disk count");
		assertTrue(summary.contains("fan: 3 monitor(s)"), "Summary should show original fan count");
		// Summary should show truncation indicator for disk
		assertTrue(summary.contains("(showing first"), "Summary should indicate truncation");
		// fan should NOT be annotated with truncation indicator
		assertFalse(summary.contains("fan: 3 monitor(s) (showing first"), "Fan should not be annotated as truncated");
		// Should contain total
		assertTrue(summary.contains("Total: 53 monitor(s)"), "Summary should show total count");
	}

	// ---- truncateTypeList ----

	@Test
	void testTruncateTypeListKeepsSummaryAndLimitsMonitors() {
		final List<MonitorTypeItem> items = buildTypeList("disk", 10);
		assertEquals(11, items.size()); // 10 monitors + 1 summary

		final List<MonitorTypeItem> truncated = TelemetryResultTruncator.truncateTypeList(items, 3);

		// 3 monitors + 1 summary
		assertEquals(4, truncated.size());
		// Last element is summary
		assertTrue(truncated.get(truncated.size() - 1) instanceof MonitorTypeSummaryVo);
		// First 3 are monitors
		for (int i = 0; i < 3; i++) {
			assertTrue(truncated.get(i) instanceof MonitorVo);
		}
	}

	@Test
	void testTruncateTypeListToZeroKeepsOnlySummary() {
		final List<MonitorTypeItem> items = buildTypeList("disk", 5);

		final List<MonitorTypeItem> truncated = TelemetryResultTruncator.truncateTypeList(items, 0);

		assertEquals(1, truncated.size());
		assertTrue(truncated.get(0) instanceof MonitorTypeSummaryVo);
	}

	@Test
	void testTruncateTypeListEmptyList() {
		final List<MonitorTypeItem> empty = new ArrayList<>();
		final List<MonitorTypeItem> truncated = TelemetryResultTruncator.truncateTypeList(empty, 5);
		assertTrue(truncated.isEmpty());
	}

	// ---- Mixed: hosts with errors and telemetry ----

	@Test
	void testMixedHostsWithErrorAndTelemetry() {
		final var hosts = new ArrayList<HostToolResponse<TelemetryResult>>();
		// Host with telemetry
		hosts.add(
			HostToolResponse
				.<TelemetryResult>builder()
				.hostname("good-host")
				.response(TelemetryResult.builder().telemetry(buildMonitorsVo(Map.of("disk", 50))).build())
				.build()
		);
		// Host with error
		hosts.add(
			HostToolResponse.<TelemetryResult>builder().hostname("bad-host").response(new TelemetryResult("Timeout")).build()
		);
		final var response = MultiHostToolResponse.<TelemetryResult>builder().hosts(hosts).build();

		final var result = TelemetryResultTruncator.truncate(response, 100, OBJECT_MAPPER);

		assertTrue(result.wasTruncated());

		// good-host should have truncated disk
		final var goodHostMonitors = result
			.truncatedResponse()
			.getHosts()
			.get(0)
			.getResponse()
			.getTelemetry()
			.getMonitors();
		assertTrue(monitorCount(goodHostMonitors.get("disk")) < 50);

		// bad-host should still have error message
		final var badHostResponse = result.truncatedResponse().getHosts().get(1).getResponse();
		assertEquals("Timeout", badHostResponse.getErrorMessage());
	}

	// ---- buildSummary static tests ----

	@Test
	void testBuildSummaryWithNoTruncation() {
		final var hostStats = List.of(
			new TelemetryResultTruncator.HostStats("host1", Map.of("disk", 5, "cpu", 3), 8, null)
		);
		final String summary = TelemetryResultTruncator.buildSummary(hostStats, List.of());

		assertTrue(summary.contains("Host 'host1'"));
		assertTrue(summary.contains("disk: 5 monitor(s)"));
		assertTrue(summary.contains("cpu: 3 monitor(s)"));
		assertTrue(summary.contains("Total: 8 monitor(s)"));
		assertFalse(summary.contains("(showing first"));
	}

	@Test
	void testBuildSummaryWithErrorHost() {
		final var hostStats = List.of(
			new TelemetryResultTruncator.HostStats("error-host", Map.of(), 0, "Connection failed")
		);
		final String summary = TelemetryResultTruncator.buildSummary(hostStats, List.of());

		assertTrue(summary.contains("Host 'error-host'"));
		assertTrue(summary.contains("Error: Connection failed"));
	}

	@Test
	void testBuildSummaryWithTruncatedEntries() {
		final var hostStats = List.of(
			new TelemetryResultTruncator.HostStats("server1", new LinkedHashMap<>(Map.of("disk", 200, "fan", 5)), 205, null)
		);
		final var truncatedEntries = List.of(new TelemetryResultTruncator.MonitorEntry("server1", "disk", 200, 25));

		final String summary = TelemetryResultTruncator.buildSummary(hostStats, truncatedEntries);

		assertTrue(summary.contains("disk: 200 monitor(s) (showing first 25 + summary)"));
		assertTrue(summary.contains("fan: 5 monitor(s)"));
		assertFalse(summary.contains("fan: 5 monitor(s) (showing"));
		assertTrue(summary.contains("Total: 205 monitor(s)"));
	}

	@Test
	void testBuildSummaryShowsSummaryOnlyWhenCurrentCountIsZero() {
		final var hostStats = List.of(
			new TelemetryResultTruncator.HostStats("server1", new LinkedHashMap<>(Map.of("disk", 10)), 10, null)
		);
		final var truncatedEntries = List.of(new TelemetryResultTruncator.MonitorEntry("server1", "disk", 10, 0));

		final String summary = TelemetryResultTruncator.buildSummary(hostStats, truncatedEntries);

		assertTrue(summary.contains("disk: 10 monitor(s) (summary only)"));
		assertFalse(summary.contains("showing first 0"));
	}
}
