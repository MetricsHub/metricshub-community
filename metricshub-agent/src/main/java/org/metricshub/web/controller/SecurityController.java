package org.metricshub.web.controller;

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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.metricshub.engine.security.MetricsHubSecurityException;
import org.metricshub.web.security.dto.EncryptPasswordRequest;
import org.metricshub.web.security.dto.EncryptPasswordResponse;
import org.metricshub.web.service.PasswordEncryptionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST API for security-related operations (keystore password encryption, etc.).
 */
@RestController
@RequestMapping(value = "/api/security")
@Tag(name = "Security", description = "MetricsHub security management")
public class SecurityController {

	private final PasswordEncryptionService passwordEncryptionService;

	/**
	 * @param passwordEncryptionService encrypts passwords using the agent keystore
	 */
	public SecurityController(final PasswordEncryptionService passwordEncryptionService) {
		this.passwordEncryptionService = passwordEncryptionService;
	}

	/**
	 * Encrypts a plaintext password using the MetricsHub keystore, in the same format as the CLI encrypt command.
	 * <p>
	 * The JSON body must contain {@link EncryptPasswordRequest#passwordBase64()}, which is the Base64 encoding of the
	 * UTF-8 bytes of the password. The server decodes Base64 and delegates to
	 * {@link PasswordEncryptionService#encryptPassword(String)}.
	 * </p>
	 *
	 * @param request JSON payload with Base64-encoded UTF-8 password bytes
	 * @return HTTP 200 with {@link EncryptPasswordResponse} containing the encrypted password
	 * @throws ResponseStatusException HTTP 400 if Base64 is invalid or encryption fails
	 */
	@Operation(
		summary = "Encrypt a password",
		description = "JSON body must include passwordBase64: Base64 encoding of the UTF-8 password bytes (not ciphertext). " +
		"The server decodes it, then encrypts with the MetricsHub keystore. " +
		"Response JSON contains encryptedPassword (same format as the encrypt CLI).",
		responses = {
			@ApiResponse(responseCode = "200", description = "Encrypted password returned"),
			@ApiResponse(responseCode = "400", description = "Invalid or missing body, bad Base64, or encryption failed")
		}
	)
	@PostMapping(
		value = "/encrypt-password",
		consumes = MediaType.APPLICATION_JSON_VALUE,
		produces = MediaType.APPLICATION_JSON_VALUE
	)
	public ResponseEntity<EncryptPasswordResponse> encryptPassword(
		@io.swagger.v3.oas.annotations.parameters.RequestBody(
			description = "passwordBase64: Base64(UTF-8 bytes of the password)",
			required = true
		) @Valid @RequestBody final EncryptPasswordRequest request
	) {
		try {
			final String ciphertext = passwordEncryptionService.encryptPassword(request.passwordBase64());
			return ResponseEntity.ok(new EncryptPasswordResponse(ciphertext));
		} catch (final IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
		} catch (final MetricsHubSecurityException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to encrypt password: " + e.getMessage(), e);
		}
	}
}
