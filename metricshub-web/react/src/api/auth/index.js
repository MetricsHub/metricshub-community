import { httpRequest } from "../../utils/axios-request";

/**
 * Authentication API for user sign-in, sign-out, and fetching user info
 */
class AuthApi {
	/**
	 * Sign in user with username and password
	 * @param {*} request  { username, password }
	 * @returns  { jwt }
	 */
	signIn(request) {
		const { username, password } = request;

		return new Promise((resolve, reject) => {
			httpRequest({
				url: "/auth",
				method: "POST",
				data: {
					username: username,
					password: password,
				},
			})
				.then((response) => {
					const jwt = response.data.token;
					resolve({ jwt });
				})
				.catch((error) => reject(error));
		});
	}

	/**
	 * Get current user information
	 * @returns  { user }
	 */
	me() {
		return new Promise((resolve, reject) => {
			httpRequest({
				url: `/api/users/me`,
				method: "GET",
			})
				.then(({ data }) => resolve(data))
				.catch((error) => reject(error));
		});
	}

	/**
	 * Sign out user
	 * @returns  { success }
	 */
	signOut() {
		return new Promise((resolve, reject) => {
			httpRequest({
				url: "/auth",
				method: "DELETE",
			})
				.then(({ data }) => resolve(data))
				.catch((error) => reject(error));
		});
	}
}

// Export a singleton instance of AuthApi
export const authApi = new AuthApi();
