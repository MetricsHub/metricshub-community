package org.metricshub.web.service.openai;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2025 MetricsHub
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.metricshub.web.dto.mcp.MonitorTypeItem;
import org.metricshub.web.dto.mcp.TelemetryResult;
import org.metricshub.web.mcp.HostToolResponse;
import org.metricshub.web.mcp.MultiHostToolResponse;

/**
 * Truncates telemetry results by progressively halving the (host, type) entry
 * with the most monitors until the response fits within the size limit.
 * Preserves the MonitorTypeSummaryVo (last element) in each monitor type list.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TelemetryResultTruncator {

	/**
	 * Mutable tracking class used internally during the truncation loop.
	 * Tracks the hostname, monitor type, original count, and current count
	 * for a single (host, monitorType) entry.
	 */
	private static class TruncationEntry {

		final String hostname;
		final String monitorType;
		final int originalCount;
		int currentCount;

		TruncationEntry(final String hostname, final String monitorType, final int count) {
			this.hostname = hostname;
			this.monitorType = monitorType;
			this.originalCount = count;
			this.currentCount = count;
		}

		MonitorEntry toMonitorEntry() {
			return new MonitorEntry(hostname, monitorType, originalCount, currentCount);
		}
	}

	/**
	 * Truncates a MultiHostToolResponse by progressively halving the largest
	 * (host, monitorType) entry until the response fits within the size limit.
	 *
	 * <p>Algorithm:
	 * <ol>
	 *   <li>Serialize the full response. If it fits, return as-is.</li>
	 *   <li>Build a priority list of (host, monitorType, currentCount) entries, sorted by count descending.</li>
	 *   <li><strong>Fast path:</strong> If maxOutputSize == 0, skip halving and immediately truncate all
	 *       entries to summaries only (0 individual monitors).</li>
	 *   <li>Pick the entry with the largest currentCount. Halve it: newCount = currentCount / 2.</li>
	 *   <li>Truncate that specific host+type list to newCount monitors (+ keep summary as last element).</li>
	 *   <li>Re-serialize and check. If it fits, stop. Otherwise, re-sort and pick the new largest entry.</li>
	 *   <li>When an entry reaches 0 monitors (summary only), move to the next largest entry.</li>
	 *   <li>Repeat until the response fits or all entries are at 0 (best effort).</li>
	 * </ol>
	 *
	 * <p>The MonitorTypeSummaryVo at the end of each type list is ALWAYS preserved, even when
	 * all individual monitors are truncated. This ensures the AI always has aggregated stats.
	 *
	 * @param response       the original multi-host response
	 * @param maxOutputSize  maximum allowed serialized size in bytes (UTF-8); if 0, keeps summaries only
	 * @param objectMapper   the ObjectMapper for serialization size checks
	 * @return a TruncationResult with the (possibly truncated) response, summary, and stats
	 */
	public static TruncationResult truncate(
		final MultiHostToolResponse<TelemetryResult> response,
		final long maxOutputSize,
		final ObjectMapper objectMapper
	) {
		// 1. If null/empty, return immediately with an empty summary
		if (response == null || response.getHosts() == null || response.getHosts().isEmpty()) {
			return new TruncationResult(response, "", List.of(), false);
		}

		// 2. Serialize full response — if fits, return as-is (wasTruncated = false)
		final String fullJson;
		try {
			fullJson = objectMapper.writeValueAsString(response);
		} catch (JsonProcessingException e) {
			return new TruncationResult(response, "Failed to serialize response", List.of(), false);
		}

		if (sizeInBytes(fullJson) <= maxOutputSize) {
			return new TruncationResult(response, "", List.of(), false);
		}

		// 3. Build entries sorted by monitor count descending
		final List<TruncationEntry> entries = buildTruncationEntries(response);

		if (entries.isEmpty()) {
			// No monitors to truncate (all error hosts or empty types)
			return new TruncationResult(response, "", List.of(), false);
		}

		// 4. Deep copy the response (working copy)
		final MultiHostToolResponse<TelemetryResult> workingCopy;
		try {
			workingCopy =
				objectMapper.readValue(
					objectMapper.writeValueAsBytes(response),
					new TypeReference<MultiHostToolResponse<TelemetryResult>>() {}
				);
		} catch (Exception e) {
			return new TruncationResult(response, "Failed to deep copy response", List.of(), false);
		}

		// 5. Fast path: if maxOutputSize == 0, skip halving and keep summaries only
		if (maxOutputSize == 0) {
			for (final TruncationEntry entry : entries) {
				truncateEntryInResponse(workingCopy, entry.hostname, entry.monitorType, 0);
				entry.currentCount = 0;
			}
		} else {
			// 6. Progressive halving loop
			while (true) {
				// Re-sort entries by currentCount descending
				entries.sort((a, b) -> Integer.compare(b.currentCount, a.currentCount));

				// Pick entry with largest currentCount
				final TruncationEntry largest = entries.get(0);
				if (largest.currentCount == 0) {
					break; // All entries at summary-only → return best effort
				}

				// Halve: newCount = currentCount / 2
				final int newCount = largest.currentCount / 2;

				// Truncate that host+type list in the working copy
				truncateEntryInResponse(workingCopy, largest.hostname, largest.monitorType, newCount);
				largest.currentCount = newCount;

				// Re-serialize and check
				try {
					final String serialized = objectMapper.writeValueAsString(workingCopy);
					if (sizeInBytes(serialized) <= maxOutputSize) {
						break;
					}
				} catch (JsonProcessingException e) {
					break;
				}
			}
		}

		// 7. Build summary string and return TruncationResult
		final List<MonitorEntry> truncatedEntries = entries
			.stream()
			.filter(e -> e.currentCount < e.originalCount)
			.map(TruncationEntry::toMonitorEntry)
			.toList();

		final List<HostStats> hostStatsList = buildHostStats(response);
		final String summary = buildSummary(hostStatsList, truncatedEntries);

		return new TruncationResult(workingCopy, summary, truncatedEntries, !truncatedEntries.isEmpty());
	}

	/**
	 * Builds the list of mutable truncation entries across all hosts, each tracking
	 * (hostname, monitorType, currentMonitorCount). Sorted by count descending.
	 *
	 * @param response the multi-host response
	 * @return list of TruncationEntry sorted by monitor count descending
	 */
	private static List<TruncationEntry> buildTruncationEntries(final MultiHostToolResponse<TelemetryResult> response) {
		final List<TruncationEntry> entries = new ArrayList<>();
		for (final HostToolResponse<TelemetryResult> host : response.getHosts()) {
			final TelemetryResult result = host.getResponse();
			if (result == null || result.getTelemetry() == null || result.getTelemetry().getMonitors() == null) {
				continue;
			}
			for (final var entry : result.getTelemetry().getMonitors().entrySet()) {
				final int count = entry.getValue().size() - 1; // exclude summary
				if (count > 0) {
					entries.add(new TruncationEntry(host.getHostname(), entry.getKey(), count));
				}
			}
		}
		entries.sort((a, b) -> Integer.compare(b.currentCount, a.currentCount));
		return entries;
	}

	/**
	 * Truncates a single (host, monitorType) entry in the working copy response.
	 *
	 * @param workingCopy  the working copy of the response being truncated
	 * @param hostname     the hostname to target
	 * @param monitorType  the monitor type to target
	 * @param maxMonitors  maximum individual monitors to keep
	 */
	private static void truncateEntryInResponse(
		final MultiHostToolResponse<TelemetryResult> workingCopy,
		final String hostname,
		final String monitorType,
		final int maxMonitors
	) {
		for (final HostToolResponse<TelemetryResult> host : workingCopy.getHosts()) {
			if (hostname.equals(host.getHostname())) {
				host
					.getResponse()
					.getTelemetry()
					.getMonitors()
					.computeIfPresent(monitorType, (key, items) -> truncateTypeList(items, maxMonitors));
				break;
			}
		}
	}

	/**
	 * Truncates a single monitor type list to keep at most maxMonitors individual monitors
	 * plus the summary as the last element.
	 *
	 * @param items        the polymorphic list (MonitorVo items + MonitorTypeSummaryVo at the end)
	 * @param maxMonitors  maximum individual monitors to keep
	 * @return truncated list with at most maxMonitors MonitorVo items + the original summary
	 */
	static List<MonitorTypeItem> truncateTypeList(final List<MonitorTypeItem> items, final int maxMonitors) {
		if (items.isEmpty()) {
			return items;
		}
		// 1. Extract the summary (last element, which is MonitorTypeSummaryVo)
		final MonitorTypeItem summary = items.get(items.size() - 1);
		// 2. Extract individual monitors (all elements except the last)
		final List<MonitorTypeItem> monitors = items.subList(0, items.size() - 1);
		// 3. Keep first min(monitors.size(), maxMonitors) monitors
		final int keep = Math.min(monitors.size(), maxMonitors);
		// 4. Build new list with kept monitors + summary
		final List<MonitorTypeItem> result = new ArrayList<>(monitors.subList(0, keep));
		result.add(summary);
		return result;
	}

	/**
	 * Builds per-host statistics from the original (untruncated) response.
	 *
	 * @param response the original multi-host response
	 * @return list of HostStats for each host in the response
	 */
	private static List<HostStats> buildHostStats(final MultiHostToolResponse<TelemetryResult> response) {
		final List<HostStats> stats = new ArrayList<>();
		for (final HostToolResponse<TelemetryResult> host : response.getHosts()) {
			final TelemetryResult result = host.getResponse();
			if (result == null) {
				stats.add(new HostStats(host.getHostname(), Map.of(), 0, null));
				continue;
			}
			if (result.getTelemetry() == null || result.getTelemetry().getMonitors() == null) {
				stats.add(new HostStats(host.getHostname(), Map.of(), 0, result.getErrorMessage()));
				continue;
			}
			final Map<String, Integer> typeCounts = new LinkedHashMap<>();
			int total = 0;
			for (final var entry : result.getTelemetry().getMonitors().entrySet()) {
				final int count = Math.max(0, entry.getValue().size() - 1);
				typeCounts.put(entry.getKey(), count);
				total += count;
			}
			stats.add(new HostStats(host.getHostname(), typeCounts, total, null));
		}
		return stats;
	}

	/**
	 * Builds a human-readable summary string showing which (host, type) entries were truncated.
	 *
	 * @param hostStatsList      per-host statistics
	 * @param truncatedEntries   list of (host, type) entries that were actually truncated
	 * @return formatted summary string
	 */
	static String buildSummary(final List<HostStats> hostStatsList, final List<MonitorEntry> truncatedEntries) {
		final StringBuilder sb = new StringBuilder();
		sb.append("TRUNCATED SUMMARY (full data in uploaded file):");

		for (final HostStats hostStats : hostStatsList) {
			sb.append("\n\nHost '").append(hostStats.hostname()).append("':");

			if (hostStats.errorMessage() != null) {
				sb.append("\n  Error: ").append(hostStats.errorMessage());
				continue;
			}

			for (final var entry : hostStats.typeCounts().entrySet()) {
				sb.append("\n  - ").append(entry.getKey()).append(": ");
				sb.append(entry.getValue()).append(" monitor(s)");

				final Optional<MonitorEntry> truncated = truncatedEntries
					.stream()
					.filter(te -> te.hostname().equals(hostStats.hostname()) && te.monitorType().equals(entry.getKey()))
					.findFirst();
				if (truncated.isPresent()) {
					if (truncated.get().currentCount() == 0) {
						sb.append(" (summary only)");
					} else {
						sb.append(" (showing first ").append(truncated.get().currentCount()).append(" + summary)");
					}
				}
			}

			sb.append("\n  Total: ").append(hostStats.totalCount()).append(" monitor(s)");
		}

		return sb.toString();
	}

	/**
	 * Tracks a (host, monitorType) entry with its original and current monitor count.
	 * Used by the truncation loop to always target the largest entry.
	 *
	 * @param hostname      the hostname
	 * @param monitorType   the monitor type name
	 * @param originalCount original number of individual monitors (excluding summary), before truncation
	 * @param currentCount  current number of individual monitors (excluding summary), after truncation
	 */
	public record MonitorEntry(String hostname, String monitorType, int originalCount, int currentCount) {}

	/**
	 * Result of truncation containing the truncated response, summary, and metadata.
	 *
	 * @param truncatedResponse the truncated multi-host response (summaries always preserved)
	 * @param summary           human-readable summary indicating which (host, type) entries were truncated
	 * @param truncatedEntries  list of MonitorEntry for each (host, type) that was actually truncated,
	 *                          with both original and final counts
	 * @param wasTruncated      true if any monitors were actually removed
	 */
	public record TruncationResult(
		MultiHostToolResponse<TelemetryResult> truncatedResponse,
		String summary,
		List<MonitorEntry> truncatedEntries,
		boolean wasTruncated
	) {}

	/**
	 * Per-host statistics collected during truncation.
	 *
	 * @param hostname     the hostname
	 * @param typeCounts   map of monitor type to total count (before truncation)
	 * @param totalCount   total monitors across all types for this host
	 * @param errorMessage error message if any (null when telemetry is present)
	 */
	public record HostStats(String hostname, Map<String, Integer> typeCounts, int totalCount, String errorMessage) {}

	/**
	 * Returns the UTF-8 byte length of the given string.
	 *
	 * @param s the string to measure
	 * @return byte length in UTF-8 encoding
	 */
	private static long sizeInBytes(final String s) {
		return s.getBytes(StandardCharsets.UTF_8).length;
	}
}
