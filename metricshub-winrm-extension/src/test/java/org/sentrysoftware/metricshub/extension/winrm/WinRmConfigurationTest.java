package org.sentrysoftware.metricshub.extension.winrm;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.sentrysoftware.metricshub.engine.common.exception.InvalidConfigurationException;
import org.sentrysoftware.metricshub.engine.configuration.TransportProtocols;
import org.sentrysoftware.winrm.service.client.auth.AuthenticationEnum;

class WinRmConfigurationTest {

	@Test
	void testValidateConfiguration() throws InvalidConfigurationException {
		assertDoesNotThrow(() ->
			WinRmConfiguration
				.builder()
				.username("user")
				.password("pass".toCharArray())
				.namespace("namespace")
				.port(443)
				.timeout(15L)
				.build()
				.validateConfiguration("resourceKey")
		);
		assertThrows(
			InvalidConfigurationException.class,
			() ->
				WinRmConfiguration
					.builder()
					.username("user")
					.password("pass".toCharArray())
					.timeout(-15L) // Bad timeout
					.build()
					.validateConfiguration("resourceKey")
		);
	}

	@Test
	void testToString() {
		assertEquals(
			"WinRm as Administrator",
			WinRmConfiguration
				.builder()
				.username("Administrator")
				.password("passwd".toCharArray())
				.timeout(15L)
				.build()
				.toString()
		);
	}

	@Test
	void testCopy() {
		final WinRmConfiguration winRmConfiguration = WinRmConfiguration
			.builder()
			.authentications(List.of(AuthenticationEnum.KERBEROS))
			.namespace("namespace")
			.password("password".toCharArray())
			.port(100)
			.protocol(TransportProtocols.HTTPS)
			.timeout(100L)
			.username("username")
			.build();

		assertEquals(winRmConfiguration, winRmConfiguration.copy());
	}
}
