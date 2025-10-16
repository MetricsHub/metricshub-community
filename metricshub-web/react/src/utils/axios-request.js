import axios from "axios";

/**
 * Primary Axios client.
 * - Sends cookies (access/refresh) with each request.
 * - Has a response interceptor that handles 401 by attempting a single refresh.
 */
const instance = axios.create({});

/**
 * Dedicated client for the refresh call.
 * - No interceptors (prevents recursion/loops).
 */
const refreshClient = axios.create({});

// Interceptor state (used on `instance` only)
let isRefreshing = false;
let q = [];
const enqueue = (cb) => q.push(cb);
const flush = (err) => {
	q.forEach((cb) => cb(err));
	q = [];
};

/**
 * Helper to trigger a logout across the app.
 * Uses a CustomEvent to allow listening in other parts of the app.
 */
const triggerLogout = () => {
	try {
		window.dispatchEvent(new CustomEvent("auth:logout"));
	} catch {
		// ignore
	}
};

/**
 * Response interceptor for 401 handling.
 *
 * Behavior:
 * - Only triggers on HTTP 401 responses (not on network errors where `response` is undefined).
 * - Skips refresh for /auth and /auth/refresh endpoints to avoid loops.
 * - Ensures each request is only retried once via a custom `_retry` flag.
 * - Deduplicates concurrent 401s: first request performs refresh; others wait in a queue.
 * - If refresh succeeds, queued requests are retried.
 * - If refresh fails, queued requests are rejected with the refresh error.
 */
instance.interceptors.response.use(
	(r) => r,
	async (error) => {
		const { config, response } = error;

		// No response (network error) or not a 401 → do not attempt refresh
		if (!response || response.status !== 401) return Promise.reject(error);

		// Don't try to refresh for the auth endpoints themselves
		const url = config?.url || "";
		const isAuth = url.includes("/auth") && !url.includes("/auth/refresh");
		const isRefresh = url.includes("/auth/refresh");
		if (isAuth || isRefresh) {
			return Promise.reject(error);
		}

		// Avoid infinite retry loops per request
		if (config._retry) {
			return Promise.reject(error);
		}
		config._retry = true; // custom flag to track retries

		// If a refresh is already in flight, queue this request
		// To be retried or rejected when the refresh completes
		if (isRefreshing) {
			return new Promise((resolve, reject) => {
				enqueue((err) => (err ? reject(err) : resolve(instance(config))));
			});
		}

		// Perform the refresh (using the bare client to avoid interceptor recursion)
		isRefreshing = true;
		try {
			// The backend sets HttpOnly cookies; no body/token needed here
			// cookies handle authentication
			await refreshClient.post("/auth/refresh");
			// Refresh worked, retry the original request
			isRefreshing = false;
			// release queued requests without error. See isRefreshing check above.
			flush(null);
			// retry the original request
			return instance(config);
		} catch (e) {
			// Refresh failed, logout the user
			isRefreshing = false;
			// fail all queued requests with the refresh error
			flush(e);
			// Trigger the event to sign out the user
			// We are safe here as we have already encountered a 401
			// and are logging out anyway
			triggerLogout();
			// forward the refresh error
			return Promise.reject(e);
		}
	},
);

/**
 * Utility function to make HTTP requests using the configured Axios instance.
 *
 * @param {object} options The Axios request configuration options.
 * @returns {Promise} The Axios response promise.
 */
export const httpRequest = (options) => instance(options);
