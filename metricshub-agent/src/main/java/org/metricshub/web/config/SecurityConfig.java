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

import org.metricshub.web.security.ApiKeyAuthFilter;
import org.metricshub.web.security.jwt.JwtAuthenticationFilter;
import org.metricshub.web.security.jwt.JwtComponent;
import org.metricshub.web.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Security configuration class for the MetricsHub web application.
 */
@Configuration
public class SecurityConfig {

	private ApiKeyAuthFilter apiKeyAuthFilter;
	private JwtComponent jwtComponent;
	private  UserService userService;
	private  ObjectMapper objectMapper;

	@Autowired
	public SecurityConfig(ApiKeyAuthFilter apiKeyAuthFilter, JwtComponent jwtComponent, UserService userService, ObjectMapper objectMapper) {
		this.apiKeyAuthFilter = apiKeyAuthFilter;
		this.jwtComponent = jwtComponent;
		this.userService = userService;
		this.objectMapper = objectMapper;
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
	            .addFilterAfter(new JwtAuthenticationFilter(jwtComponent, userService, objectMapper), ApiKeyAuthFilter.class)
	            .authorizeHttpRequests(authz -> authz
	                .requestMatchers("/auth").permitAll()
	                .anyRequest().authenticated()
	            )
	            .exceptionHandling(ex -> ex
	                .authenticationEntryPoint(jsonAuthEntryPoint())
	                .accessDeniedHandler(jsonAccessDeniedHandler())
	            )
	            .build();
	}

    // JSON 401 for unauthenticated
    @Bean
    public AuthenticationEntryPoint jsonAuthEntryPoint() {
        return (HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Unauthorized\"}");
        };
    }

    // JSON 403 for forbidden
    @Bean
    public AccessDeniedHandler jsonAccessDeniedHandler() {
        return (HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) -> {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Forbidden\"}");
        };
    }
}
