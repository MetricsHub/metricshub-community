package org.metricshub.web.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
import org.metricshub.web.security.SecurityHelper;
import org.metricshub.web.security.jwt.JwtAuthToken;
import org.metricshub.web.security.login.LoginAuthenticationRequest;
import org.metricshub.web.service.UserService;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthenticationControllerTest {

	private MockMvc mockMvc;
	private UserService userService;

	@BeforeEach
	void setup() {
		userService = Mockito.mock(UserService.class);
		final AuthenticationController controller = new AuthenticationController(userService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@AfterEach
	void cleanupSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void testShouldLoginSetCookiesAndReturnToken() throws Exception {
		final String token = "abc.def.ghi";
		final long expiresIn = 3600L;
		final String refreshToken = "jkl.mno.pqr";
		final long refreshExpiresIn = 7200L;

		final JwtAuthToken auth = Mockito.mock(JwtAuthToken.class);
		when(auth.getToken()).thenReturn(token);
		when(auth.getExpiresIn()).thenReturn(expiresIn);
		when(auth.getRefreshToken()).thenReturn(refreshToken);
		when(auth.getRefreshExpiresIn()).thenReturn(refreshExpiresIn);

		when(userService.performSecurity(Mockito.any(LoginAuthenticationRequest.class))).thenReturn(auth);

		final String body =
			"""
			{
			  "username": "john",
			  "password": "secret"
			}
			""";

		// Act
		MvcResult result = mockMvc
			.perform(post("/auth").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.token").value(token))
			.andReturn();

		// Assert cookies (two Set-Cookie headers)
		final List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);

		assertTrue(
			setCookies
				.stream()
				.anyMatch(h ->
					h.contains(SecurityHelper.TOKEN_KEY + "=" + token) &&
					h.contains("Max-Age=" + expiresIn) &&
					h.contains("Path=/")
				),
			"Access token cookie not found or malformed"
		);

		assertTrue(
			setCookies
				.stream()
				.anyMatch(h ->
					h.contains(SecurityHelper.REFRESH_TOKEN_KEY + "=" + refreshToken) &&
					h.contains("Max-Age=" + refreshExpiresIn) &&
					h.contains("Path=/")
				),
			"Refresh token cookie not found or malformed"
		);
	}

	@Test
	void testShouldRefreshSetCookiesAndReturnNewToken() throws Exception {
		// Arrange
		final String newToken = "new.abc.xyz";
		final long newExpiresIn = 1800L;
		final String newRefreshToken = "new.r1.r2";
		final long newRefreshExpiresIn = 86400L;

		final JwtAuthToken refreshed = Mockito.mock(JwtAuthToken.class);
		when(refreshed.getToken()).thenReturn(newToken);
		when(refreshed.getExpiresIn()).thenReturn(newExpiresIn);
		when(refreshed.getRefreshToken()).thenReturn(newRefreshToken);
		when(refreshed.getRefreshExpiresIn()).thenReturn(newRefreshExpiresIn);

		when(userService.refreshSecurity(any(HttpServletRequest.class))).thenReturn(refreshed);

		// Act
		final MvcResult result = mockMvc
			.perform(post("/auth/refresh"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.token").value(newToken))
			.andReturn();

		// Assert cookies (two Set-Cookie headers)
		List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);

		assertTrue(
			setCookies
				.stream()
				.anyMatch(h ->
					h.contains(SecurityHelper.TOKEN_KEY + "=" + newToken) &&
					h.contains("Max-Age=" + newExpiresIn) &&
					h.contains("Path=/")
				),
			"Refreshed access token cookie not found or malformed"
		);

		assertTrue(
			setCookies
				.stream()
				.anyMatch(h ->
					h.contains(SecurityHelper.REFRESH_TOKEN_KEY + "=" + newRefreshToken) &&
					h.contains("Max-Age=" + newRefreshExpiresIn) &&
					h.contains("Path=/")
				),
			"Refreshed refresh token cookie not found or malformed"
		);
	}

	@Test
	void testShouldLogoutClearAuthAndExpireCookie() throws Exception {
		// Pre-populate the security context to simulate a logged-in user
		final JwtAuthToken existingAuth = Mockito.mock(JwtAuthToken.class);
		SecurityContextHolder.getContext().setAuthentication(existingAuth);

		// Act
		final MvcResult result = mockMvc.perform(delete("/auth")).andExpect(status().isOk()).andReturn();

		// Assert both cookies cleared (Max-Age=0)
		final List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);

		assertTrue(
			setCookies
				.stream()
				.anyMatch(h -> h.contains(SecurityHelper.TOKEN_KEY + "=" + MetricsHubConstants.EMPTY) && h.contains("Max-Age=0")
				),
			"Access token cookie not cleared correctly"
		);

		assertTrue(
			setCookies
				.stream()
				.anyMatch(h ->
					h.contains(SecurityHelper.REFRESH_TOKEN_KEY + "=" + MetricsHubConstants.EMPTY) && h.contains("Max-Age=0")
				),
			"Refresh token cookie not cleared correctly"
		);
	}
}
