package org.metricshub.web.security;

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

import java.util.Map;

/**
 * Registry for managing users. This class provides a method to retrieve users by their username.
 */
public class UserRegistry {

	private final Map<String, User> users;

	/**
	 * Constructor for UserRegistry.
	 *
	 * @param users a map of users where the key is the username and the value is the User object.
	 */
	public UserRegistry(final Map<String, User> users) {
		this.users = users;
	}

	/**
	 * Retrieves a user by their username.
	 *
	 * @param username the username of the user to retrieve
	 * @return the User object if found, otherwise null
	 */
	public User getUserByUsername(final String username) {
		return users.get(username);
	}
}
