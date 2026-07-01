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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.extension.oscommand.SshConfiguration;
import org.metricshub.web.dto.uiconfig.HostUpCheckResponseDto;
import org.metricshub.web.dto.uiconfig.ProtocolCheckRequestDto;
import org.metricshub.web.mcp.ProtocolCheckResponse;
import org.mockito.Mockito;

class UiProtocolCheckServiceTest {

	private ProtocolHealthCheckService protocolHealthCheckService;
	private UiProtocolCheckService uiProtocolCheckService;

	@BeforeEach
	void setUp() {
		protocolHealthCheckService = Mockito.mock(ProtocolHealthCheckService.class);
		uiProtocolCheckService = new UiProtocolCheckService(protocolHealthCheckService);
	}

	@Test
	void testHostUpWhenReachable() {
		when(protocolHealthCheckService.checkWithInlineConfiguration(eq("server1"), eq("ssh"), any()))
			.thenReturn(ProtocolCheckResponse.builder().hostname("server1").isReachable(true).responseTime(42.0).build());

		final ProtocolCheckRequestDto request = new ProtocolCheckRequestDto();
		request.setHostname("server1");
		request.setProtocol("ssh");
		request.setProtocolConfig(
			Map.of("ssh", SshConfiguration.sshConfigurationBuilder().username("admin").port(22).build())
		);

		final HostUpCheckResponseDto result = uiProtocolCheckService.checkHostUp(request);

		assertEquals(1, result.getHostUp());
		assertFalse(result.isTimedOut());
		assertEquals(42.0, result.getResponseTimeMs());
	}

	@Test
	void testHostDownWhenUnreachable() {
		when(protocolHealthCheckService.checkWithInlineConfiguration(eq("server1"), eq("ssh"), any()))
			.thenReturn(ProtocolCheckResponse.builder().hostname("server1").isReachable(false).build());

		final ProtocolCheckRequestDto request = new ProtocolCheckRequestDto();
		request.setHostname("server1");
		request.setProtocol("ssh");

		final HostUpCheckResponseDto result = uiProtocolCheckService.checkHostUp(request);

		assertEquals(0, result.getHostUp());
		assertFalse(result.isTimedOut());
	}

	@Test
	void testTimeoutMessage() {
		when(protocolHealthCheckService.checkWithInlineConfiguration(eq("server1"), eq("ssh"), any()))
			.thenReturn(
				ProtocolCheckResponse
					.builder()
					.hostname("server1")
					.errorMessage("Command \"echo test\" execution has timed out after 2 s")
					.build()
			);

		final ProtocolCheckRequestDto request = new ProtocolCheckRequestDto();
		request.setHostname("server1");
		request.setProtocol("ssh");

		final HostUpCheckResponseDto result = uiProtocolCheckService.checkHostUp(request);

		assertTrue(result.isTimedOut());
		assertNull(result.getHostUp());
		assertEquals("Connection timed out.", result.getErrorMessage());
	}
}
