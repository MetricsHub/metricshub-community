package org.metricshub.web.service;

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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import lombok.NonNull;
import org.metricshub.web.dto.SearchMatch;
import org.metricshub.web.dto.telemetry.AbstractBaseTelemetry;
import org.metricshub.web.dto.telemetry.AgentTelemetry;
import org.metricshub.web.dto.telemetry.ConnectorTelemetry;
import org.metricshub.web.dto.telemetry.MonitorTypeTelemetry;
import org.metricshub.web.dto.telemetry.ResourceGroupTelemetry;
import org.metricshub.web.dto.telemetry.ResourceTelemetry;
import org.springframework.stereotype.Service;

@Service
public class SearchService {

	/**
	 * Minimum Jaro-Winkler similarity required for a result to be returned.
	 * Values in [0,1]. 0.85 is a reasonable balance to cut mid-quality matches
	 * like incidental substring overlaps while keeping relevant fuzzy hits.
	 */
	private static final double MIN_JW_SCORE = 0.50D;

	/**
	 * Performs a search across hierarchy elements (excluding virtual container nodes) using Jaro–Winkler
	 *
	 * @param query raw query string
	 * @return ranked list of matches
	 */
	public List<SearchMatch> search(@NonNull String q, @NonNull final AgentTelemetry hierarchy) {
		final List<SearchMatch> matches = new ArrayList<>();

		final Queue<TraversalNode> queue = new LinkedList<>();
		queue.add(new TraversalNode(hierarchy, hierarchy.getName()));
		while (!queue.isEmpty()) {
			final TraversalNode tn = queue.poll();
			final AbstractBaseTelemetry current = tn.node;
			final double jw = jaroWinkler(current.getName(), q);
			if (jw >= MIN_JW_SCORE) {
				matches.add(
					SearchMatch
						.builder()
						.name(current.getName())
						.type(current.getType())
						.path(tn.path)
						.jaroWinklerScore(jw)
						.build()
				);
			}

			// enqueue children based on type
			if (current instanceof AgentTelemetry at) {
				if (at.getResourceGroups() != null) {
					at.getResourceGroups().forEach(child -> queue.add(new TraversalNode(child, tn.path + "/" + child.getName())));
				}
				if (at.getResources() != null) {
					at.getResources().forEach(child -> queue.add(new TraversalNode(child, tn.path + "/" + child.getName())));
				}
			} else if (current instanceof ResourceGroupTelemetry rgt) {
				if (rgt.getResources() != null) {
					rgt.getResources().forEach(child -> queue.add(new TraversalNode(child, tn.path + "/" + child.getName())));
				}
			} else if (current instanceof ResourceTelemetry rt) {
				if (rt.getConnectors() != null) {
					rt.getConnectors().forEach(child -> queue.add(new TraversalNode(child, tn.path + "/" + child.getName())));
				}
			} else if (current instanceof ConnectorTelemetry ct) {
				if (ct.getMonitors() != null) {
					ct.getMonitors().forEach(child -> queue.add(new TraversalNode(child, tn.path + "/" + child.getName())));
				}
			} else if (current instanceof MonitorTypeTelemetry mtt) {
				if (mtt.getInstances() != null) {
					mtt.getInstances().forEach(child -> queue.add(new TraversalNode(child, tn.path + "/" + child.getName())));
				}
			}
		}

		return matches
			.stream()
			.sorted((SearchMatch a, SearchMatch b) -> {
				int cmp = Double.compare(b.getJaroWinklerScore(), a.getJaroWinklerScore());
				if (cmp == 0) {
					cmp = a.getPath().compareToIgnoreCase(b.getPath());
				}
				return cmp;
			})
			// safety cap
			.limit(250L)
			.toList();
	}

	/**
	 * Helper record to hold a node and its path during traversal.
	 */
	private static record TraversalNode(AbstractBaseTelemetry node, String path) {}

	/**
	 * Computes the Jaro-Winkler similarity between two strings.
	 *
	 * @param source the first string
	 * @param target the second string
	 * @return the Jaro-Winkler similarity score in [0,1]
	 *
	 */
	private static double jaroWinkler(final String source, final String target) {
		if (source == null || target == null) {
			return 0D;
		}
		final String a = source.toLowerCase(Locale.getDefault());
		final String b = target.toLowerCase(Locale.getDefault());
		final int maxDist = Math.max(a.length(), b.length()) / 2 - 1;
		final var aMatches = new boolean[a.length()];
		final var bMatches = new boolean[b.length()];
		var matches = 0;
		for (var i = 0; i < a.length(); i++) {
			int start = Math.max(0, i - maxDist);
			int end = Math.min(b.length() - 1, i + maxDist);
			for (var j = start; j <= end; j++) {
				if (bMatches[j]) {
					continue;
				}
				if (a.charAt(i) != b.charAt(j)) {
					continue;
				}
				aMatches[i] = true;
				bMatches[j] = true;
				matches++;
				break;
			}
		}
		if (matches == 0) {
			return 0D;
		}
		var transpositions = 0;
		var k = 0;
		for (var i = 0; i < a.length(); i++) {
			if (!aMatches[i]) {
				continue;
			}
			while (!bMatches[k]) {
				k++;
			}
			if (a.charAt(i) != b.charAt(k)) {
				transpositions++;
			}
			k++;
		}
		final double m = matches;
		double jaro = (m / a.length() + m / b.length() + (m - transpositions / 2.0) / m) / 3.0;
		var prefix = 0;
		for (var i = 0; i < Math.min(4, Math.min(a.length(), b.length())); i++) {
			if (a.charAt(i) == b.charAt(i)) {
				prefix++;
			} else {
				break;
			}
		}
		return jaro + prefix * 0.1 * (1 - jaro);
	}
}
