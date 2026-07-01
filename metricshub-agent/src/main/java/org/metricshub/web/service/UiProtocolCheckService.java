package org.metricshub.web.service;

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

import org.metricshub.web.dto.uiconfig.HostUpCheckResponseDto;
import org.metricshub.web.dto.uiconfig.ProtocolCheckRequestDto;
import org.metricshub.web.mcp.ProtocolCheckResponse;
import org.springframework.stereotype.Service;

/**
 * Guided-configuration UI adapter for on-demand protocol health checks.
 */
@Service
public class UiProtocolCheckService {

	private final ProtocolHealthCheckService protocolHealthCheckService;

	/**
	 * Creates a new {@link UiProtocolCheckService}.
	 *
	 * @param protocolHealthCheckService shared protocol health-check executor
	 */
	public UiProtocolCheckService(final ProtocolHealthCheckService protocolHealthCheckService) {
		this.protocolHealthCheckService = protocolHealthCheckService;
	}

	/**
	 * Runs a protocol check using the inline configuration supplied by the UI wizard.
	 *
	 * @param request hostname, protocol id, and protocol configuration map
	 * @return host.up style result for display in the guided configuration UI
	 */
	public HostUpCheckResponseDto checkHostUp(final ProtocolCheckRequestDto request) {
		final ProtocolCheckResponse response = protocolHealthCheckService.checkWithInlineConfiguration(
			request.getHostname(),
			request.getProtocol(),
			request.getProtocolConfig()
		);
		return toHostUpResponse(response);
	}

	private static HostUpCheckResponseDto toHostUpResponse(final ProtocolCheckResponse response) {
		final String errorMessage = response.getErrorMessage();
		final boolean timedOut = isTimeoutMessage(errorMessage);

		if (timedOut) {
			return HostUpCheckResponseDto.builder().timedOut(true).errorMessage("Connection timed out.").build();
		}

		if (errorMessage != null && !errorMessage.isBlank()) {
			return HostUpCheckResponseDto.builder().errorMessage(errorMessage).build();
		}

		return HostUpCheckResponseDto.builder()
			.hostUp(response.isReachable() ? 1 : 0)
			.responseTimeMs(response.getResponseTime() > 0 ? response.getResponseTime() : null)
			.build();
	}

	private static boolean isTimeoutMessage(final String message) {
		if (message == null || message.isBlank()) {
			return false;
		}
		final String lower = message.toLowerCase();
		return lower.contains("timed out") || lower.contains("timeout");
	}
}
