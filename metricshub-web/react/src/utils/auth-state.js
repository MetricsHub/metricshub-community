let currentUserRole = null;

/**
 * Set the current user role for access checks.
 *
 * @param {string|null} role the current user role
 */
export const setUserRole = (role) => {
	currentUserRole = role || null;
};

/**
 * Get the current user role for access checks.
 *
 * @returns {string|null} the current user role
 */
export const getUserRole = () => currentUserRole;
