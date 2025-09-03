package org.metricshub.web.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class User {

	private String username;
	private String password;

	/**
	 * Copy the current user instance
	 * @return new {@link User} instance
	 */
	public User copy() {
		return User
			.builder()
			.username(username)
			.password(password)
			.build();
	}
}
