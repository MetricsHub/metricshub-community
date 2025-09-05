package org.metricshub.web.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
import org.metricshub.web.security.SecurityHelper;
import org.metricshub.web.security.jwt.JwtAuthToken;
import org.metricshub.web.security.login.LoginAuthenticationRequest;
import org.metricshub.web.service.UserService;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
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
	void testShouldLoginAndReturnTokenAndCookie() throws Exception {
		// Arrange
		final String token = "abc.def.ghi";
		final long expiresIn = 3600L;

		final JwtAuthToken auth = Mockito.mock(JwtAuthToken.class);
		when(auth.getToken()).thenReturn(token);
		when(auth.getExpiresIn()).thenReturn(expiresIn);

		when(userService.performSecurity(ArgumentMatchers.any(LoginAuthenticationRequest.class))).thenReturn(auth);

		final String body =
			"""
			{
			  "username": "john",
			  "password": "secret"
			}
			""";

		// Act + Assert
		mockMvc
			.perform(post("/auth").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.token").value(token))
			.andExpect(jsonPath("$.expiresIn").value((int) expiresIn))
			// Cookie should be set with our token and correct Max-Age/path
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(SecurityHelper.TOKEN_KEY + "=" + token)))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=" + expiresIn)))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/")));
	}

	@Test
	void testShouldLogoutClearAuthAndExpireCookie() throws Exception {
		// Pre-populate the security context to simulate a logged-in user
		final JwtAuthToken existingAuth = Mockito.mock(JwtAuthToken.class);
		SecurityContextHolder.getContext().setAuthentication(existingAuth);

		// Act + Assert
		mockMvc
			.perform(delete("/auth"))
			.andExpect(status().isOk())
			// Cookie cleared: name set with empty value and Max-Age=0
			.andExpect(
				header()
					.string(HttpHeaders.SET_COOKIE, containsString(SecurityHelper.TOKEN_KEY + "=" + MetricsHubConstants.EMPTY))
			)
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
	}
}
