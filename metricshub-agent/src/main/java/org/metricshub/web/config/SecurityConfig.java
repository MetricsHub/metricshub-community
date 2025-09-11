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
import org.metricshub.web.security.ApiKeyAuthFilter;
import org.metricshub.web.security.SecurityHelper;
import org.metricshub.web.security.jwt.JwtAuthFilter;
import org.metricshub.web.security.jwt.JwtComponent;
import org.metricshub.web.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
public class SecurityConfig {

	private ApiKeyAuthFilter apiKeyAuthFilter;
	private JwtComponent jwtComponent;
	private UserService userService;

	/**
	 * Constructor for SecurityConfig.
	 *
	 * @param apiKeyAuthFilter the API key authentication filter
	 * @param jwtComponent     the JWT component for handling JWT tokens
	 * @param userService      the user service for user-related operations
	 */
	@Autowired
	public SecurityConfig(ApiKeyAuthFilter apiKeyAuthFilter, JwtComponent jwtComponent, UserService userService) {
		this.apiKeyAuthFilter = apiKeyAuthFilter;
		this.jwtComponent = jwtComponent;
		this.userService = userService;
	}

	/**
	 * Configures the security filter chain for the web application.
	 *
	 * @param http the HttpSecurity object to configure security settings
	 * @return the configured SecurityFilterChain
	 * @throws Exception if an error occurs during configuration
	 */
	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		return http
			.csrf(csrf -> csrf.disable())
			.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			// Order matters: API key first, JWT after it.
			.addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
			.addFilterAfter(new JwtAuthFilter(jwtComponent, userService), ApiKeyAuthFilter.class)
			.authorizeHttpRequests(authz ->
				authz.requestMatchers("/api/**", "/sse", "/mcp/message").authenticated().anyRequest().permitAll()
			)
			.exceptionHandling(ex ->
				ex.authenticationEntryPoint(jsonAuthEntryPoint()).accessDeniedHandler(jsonForbiddenHandler())
			)
			.build();
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
}
