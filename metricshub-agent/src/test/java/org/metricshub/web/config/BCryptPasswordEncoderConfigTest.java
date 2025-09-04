package org.metricshub.web.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class BCryptPasswordEncoderConfigTest {

	@Test
	void testShouldCreateBCryptPasswordEncoderBean() {
		final BCryptPasswordEncoderConfig config = new BCryptPasswordEncoderConfig();

		final BCryptPasswordEncoder encoder = config.bCryptPasswordEncoder();

		assertNotNull(encoder, "BCryptPasswordEncoder bean should not be null");

		final String rawPassword = "secret";
		final String encodedPassword = encoder.encode(rawPassword);

		assertNotNull(encodedPassword, "Encoded password should not be null");
		assertTrue(encoder.matches(rawPassword, encodedPassword), "Encoded password should match the raw password");
	}
}
