package org.metricshub.web.dto.uiconfig;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2026 MetricsHub
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
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Summary of an installed connector for the UI resource wizard.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class UiConnectorSummaryDto {

	private String id;

	private String displayName;

	/** Short connector description from connector identity metadata. */
	private String information;

	@Builder.Default
	private List<String> platforms = new ArrayList<>();

	/** Connector tags from detection (used with {@code #tag} / {@code !#tag} directives). */
	@Builder.Default
	private List<String> tags = new ArrayList<>();

	/** host.type values this connector applies to (e.g. linux, storage). */
	@Builder.Default
	private List<String> appliesToHostTypes = new ArrayList<>();

	/** Human-readable device kind names from detection.appliesTo. */
	@Builder.Default
	private List<String> appliesToDisplayNames = new ArrayList<>();

	@Builder.Default
	private List<String> connectionTypes = new ArrayList<>();

	/** Protocol keys required to run this connector (ssh, snmp, …). */
	@Builder.Default
	private List<String> requiredProtocols = new ArrayList<>();

	private boolean autoDetectionDisabled;

	private boolean compatible;

	@Builder.Default
	private List<String> incompatibilityReasons = new ArrayList<>();

	/** True when the connector template defines variables (stored under additionalConnectors). */
	private boolean hasVariables;

	/** Connector template id referenced by {@code uses} in additionalConnectors. */
	private String usesTemplateId;

	@Builder.Default
	private List<UiConnectorVariableDto> variables = new ArrayList<>();
}
