import { createAsyncThunk } from "@reduxjs/toolkit";
import { statusApi } from "../../api/status";
import { httpRequest } from "../../utils/axios-request";

/**
 * Timeout (in milliseconds) for the initial POST /api/agent/restart request.
 * The backend acknowledges the request quickly (202 Accepted) then performs the
 * actual restart asynchronously, so this only guards the scheduling call itself.
 */
const RESTART_SCHEDULE_TIMEOUT_MS = 15_000;

/**
 * Timeout for each GET /api/agent/restart/status poll.
 */
const RESTART_STATUS_POLL_TIMEOUT_MS = 10_000;

/**
 * Delay between two consecutive restart-status polls.
 */
const RESTART_STATUS_POLL_INTERVAL_MS = 1_500;

/**
 * Maximum total time (in milliseconds) the client will wait for the backend
 * restart to complete before giving up and surfacing an error to the user.
 */
const RESTART_STATUS_POLL_MAX_MS = 120_000;

/**
 * Small delay after a successful restart before refreshing the application
 * status, giving Spring beans / OTEL Collector a moment to settle.
 */
const POST_RESTART_STATUS_REFRESH_DELAY_MS = 1_500;

/**
 * Thunk to fetch application status from backend
 */
export const fetchApplicationStatus = createAsyncThunk(
	"applicationStatus/fetchApplicationStatus",
	async (_, { rejectWithValue }) => {
		try {
			const data = await statusApi.getStatus();
			return data;
		} catch (err) {
			return rejectWithValue(err?.message || "Failed to fetch application status");
		}
	},
);

/**
 * Promise-based sleep helper.
 * @param {number} ms delay in milliseconds
 * @returns {Promise<void>}
 */
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Polls GET /api/agent/restart/status until the backend reports SUCCEEDED /
 * FAILED for OUR restart request, or until the overall client deadline is
 * reached.
 *
 * A terminal state is only accepted when the status `requestId` covers the
 * `requestId` returned by our POST (`status.requestId >= requestId`). Under
 * the queue-and-run-after policy a QUEUED/COALESCED request can briefly
 * observe the SUCCEEDED status of the restart that was already running when
 * it was submitted; without this check the UI would report success before
 * the queued restart even starts. A higher `requestId` is accepted too: it
 * means a newer request coalesced ours away and the restart that ran covers
 * our intent (the freshest configuration wins). When either side has no id
 * (e.g. an older agent backend), any terminal state is accepted as before.
 *
 * Returns one of:
 *   - `{ state: "SUCCEEDED", ... }` — the backend reported a successful restart.
 *   - `{ state: "FAILED", message?: string }` — the backend reported a failed restart.
 *   - `{ state: "PENDING", message: string }` — the client deadline was reached
 *     without the backend reaching a terminal state. Transient HTTP errors are
 *     expected while the agent restarts (the web server may briefly refuse
 *     connections) and do NOT count as a failure. The restart is likely still
 *     running on the backend and will complete on its own; the caller should
 *     surface an informational message rather than an error.
 *
 * This function never throws; callers can rely on the returned `state`.
 *
 * @param {number|null} requestId the requestId returned by POST /api/agent/restart,
 *                                or null when the backend did not provide one
 * @returns {Promise<{state: string, message?: string}>}
 */
const pollRestartStatus = async (requestId) => {
	const deadline = Date.now() + RESTART_STATUS_POLL_MAX_MS;

	while (Date.now() < deadline) {
		try {
			const response = await httpRequest({
				url: "/api/agent/restart/status",
				method: "GET",
				timeout: RESTART_STATUS_POLL_TIMEOUT_MS,
			});
			const payload = response?.data ?? {};
			if (payload.state === "SUCCEEDED" || payload.state === "FAILED") {
				const matchesOurRequest =
					requestId == null || payload.requestId == null || payload.requestId >= requestId;
				if (matchesOurRequest) {
					return payload;
				}
				// Terminal state of an EARLIER restart — ours is still queued. Keep polling.
			}
		} catch {
			// Transient errors are expected while the agent restarts (the web
			// server may briefly refuse connections). Keep polling until the
			// deadline; we do not treat them as failures.
		}

		await sleep(RESTART_STATUS_POLL_INTERVAL_MS);
	}

	return {
		state: "PENDING",
		message:
			"MetricsHub Agent restart is still in progress in the background. " +
			"It should complete shortly — refresh the page in a few moments to see the updated status.",
	};
};

/**
 * Thunk to restart the agent.
 *
 * Sends POST /api/agent/restart, which the backend accepts immediately with
 * 202 Accepted (returning a `result` of SCHEDULED, QUEUED or COALESCED under
 * the queue-and-run-after policy). The thunk then polls the restart status
 * endpoint until the backend reports the outcome and refreshes the
 * application status once the restart succeeds.
 */
export const restartAgent = createAsyncThunk(
	"applicationStatus/restartAgent",
	async (_, { dispatch, rejectWithValue }) => {
		try {
			let requestId = null;
			try {
				const scheduleResponse = await httpRequest({
					url: "/api/agent/restart",
					method: "POST",
					timeout: RESTART_SCHEDULE_TIMEOUT_MS,
				});
				requestId = scheduleResponse?.data?.requestId ?? null;
			} catch (err) {
				// The backend now always accepts the request (schedule / queue / coalesce),
				// but we defensively swallow 409 in case an older agent is on the other end
				// and start polling the status endpoint anyway (without request correlation).
				const status = err?.response?.status;
				if (status !== 409) {
					throw err;
				}
			}

			const finalStatus = await pollRestartStatus(requestId);

			if (finalStatus.state === "FAILED") {
				return rejectWithValue(finalStatus.message || "MetricsHub Agent restart failed.");
			}

			// Give the freshly rebuilt agent context a moment to settle before
			// refreshing the UI so the values shown reflect the new state.
			// This runs for both SUCCEEDED and PENDING so the user sees fresh data
			// as soon as the backend finishes.
			setTimeout(() => {
				dispatch(fetchApplicationStatus());
			}, POST_RESTART_STATUS_REFRESH_DELAY_MS);

			return {
				success: true,
				pending: finalStatus.state === "PENDING",
				message:
					finalStatus.state === "PENDING" ? finalStatus.message : "Agent restarted successfully",
				status: finalStatus,
			};
		} catch (err) {
			return rejectWithValue(err?.message || "Failed to restart agent");
		}
	},
);
