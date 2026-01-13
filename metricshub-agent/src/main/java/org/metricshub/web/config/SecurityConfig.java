package org.metricshub.web.config;

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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.StringHelper;
import org.metricshub.web.security.ApiKeyAuthFilter;
import org.metricshub.web.security.SecurityHelper;
import org.metricshub.web.security.jwt.JwtAuthFilter;
import org.metricshub.web.security.jwt.JwtComponent;
import org.metricshub.web.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration class for the MetricsHub web application.
 */
@Configuration
@EnableConfigurationProperties(TlsConfigurationProperties.class)
@Slf4j
public class SecurityConfig {

	private ApiKeyAuthFilter apiKeyAuthFilter;
	private JwtComponent jwtComponent;
	private UserService userService;
	private TlsConfigurationProperties tlsConfigurationProperties;

	/**
	 * Constructor for SecurityConfig.
	 *
	 * @param apiKeyAuthFilter           the API key authentication filter
	 * @param jwtComponent               the JWT component for handling JWT tokens
	 * @param userService                the user service for user-related operations
	 * @param tlsConfigurationProperties the TLS configuration properties
	 */
	@Autowired
	public SecurityConfig(
		ApiKeyAuthFilter apiKeyAuthFilter,
		JwtComponent jwtComponent,
		UserService userService,
		TlsConfigurationProperties tlsConfigurationProperties
	) {
		this.apiKeyAuthFilter = apiKeyAuthFilter;
		this.jwtComponent = jwtComponent;
		this.userService = userService;
		this.tlsConfigurationProperties = tlsConfigurationProperties;
	}

	/**
	 * Configures the security filter chain for the API and MCP endpoints.
	 *
	 * @param http the HttpSecurity object to configure security settings
	 * @return the configured SecurityFilterChain
	 * @throws Exception if an error occurs during configuration
	 */
	@Bean
	@Order(1)
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		return http
			.securityMatcher("/api/**", "/sse", "/mcp/message")
			.csrf(csrf -> csrf.disable())
			.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
			.addFilterAfter(new JwtAuthFilter(jwtComponent, userService), ApiKeyAuthFilter.class)
			.authorizeHttpRequests(authz -> authz.anyRequest().authenticated())
			.exceptionHandling(ex ->
				ex.authenticationEntryPoint(jsonAuthEntryPoint()).accessDeniedHandler(jsonForbiddenHandler())
			)
			.build();
	}

	/**
	 * Configures the default security filter chain for all other requests, permitting all access.
	 *
	 * @param http the HttpSecurity object to configure security settings
	 * @return the configured SecurityFilterChain
	 * @throws Exception if an error occurs during configuration
	 */
	@Bean
	@Order(2)
	public SecurityFilterChain defaultFilterChain(HttpSecurity http) throws Exception {
		return http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(authz -> authz.anyRequest().permitAll()).build();
	}

	/**
	 * JSON 401 for unauthenticated.
	 *
	 * @return the authentication entry point
	 */
	@Bean
	public AuthenticationEntryPoint jsonAuthEntryPoint() {
		return (HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) -> {
			SecurityHelper.writeUnauthorizedResponse(response);
		};
	}

	/**
	 * JSON 403 for forbidden.
	 *
	 * @return the access denied handler
	 */
	@Bean
	public AccessDeniedHandler jsonForbiddenHandler() {
		return (HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) -> {
			SecurityHelper.writeForbiddenResponse(response);
		};
	}

	/**
	 * Configure the embedded web server to use HTTPS when TLS is enabled, falling back to HTTP otherwise.
	 * <p>
	 * Defaults to the packaged {@code classpath:m8b-keystore.p12} with password {@code NOPWD}
	 * and alias {@code tls-selfsigned}, but will use a user-provided keystore when configured.
	 * </p>
	 *
	 * @return customizer to configure SSL on the servlet web server factory
	 */
	@Bean
	public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> tlsWebServerCustomizer() {
		return factory -> {
			if (!tlsConfigurationProperties.isEnabled()) {
				return;
			}

			final var keystore = tlsConfigurationProperties.getKeystore();
			if (keystore == null) {
				throw new IllegalStateException("tls.keystore must be configured");
			}

			final String keystoreLocation = keystore.getPath();
			if (!StringHelper.nonNullNonBlank(keystoreLocation)) {
				throw new IllegalStateException("tls.keystore.path must be set");
			}

			final String keystorePassword = keystore.getPassword();

			if (!StringHelper.nonNullNonBlank(keystorePassword)) {
				throw new IllegalStateException("tls.keystore.password must be set");
			}

			// Default key password to keystore password when not set
			final String keyPassword = StringHelper.nonNullNonBlank(keystore.getKeyPassword())
				? keystore.getKeyPassword()
				: keystorePassword;

			log.info("Enabling TLS on embedded web server using keystore at {}", keystoreLocation);

			final Ssl ssl = new Ssl();
			ssl.setEnabled(true);
			ssl.setKeyStore(keystoreLocation);
			ssl.setKeyStoreType("PKCS12");
			ssl.setKeyStorePassword(keystorePassword);
			ssl.setKeyPassword(keyPassword);

			if (StringHelper.nonNullNonBlank(keystore.getKeyAlias())) {
				ssl.setKeyAlias(keystore.getKeyAlias());
			}

			factory.setSsl(ssl);
		};
	}
}
