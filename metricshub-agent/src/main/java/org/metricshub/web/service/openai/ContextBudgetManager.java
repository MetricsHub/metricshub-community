package org.metricshub.web.service.openai;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2026 MetricsHub
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

import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages the total context budget across multiple tool calls within a single
 * conversation turn. Uses actual token counts from OpenAI API responses to
 * compute the available budget precisely.
 *
 * <p>Thread-safe: uses AtomicInteger as Spring AI may execute parallel tool calls.</p>
 *
 * <p>Usage: create one instance per conversation turn (per chat request),
 * call {@link #allocate(int)} for each tool result, and use the returned
 * {@link AllocationTier} to decide how much data to include in the manifest.</p>
 */
@Slf4j
public class ContextBudgetManager {

	/**
	 * Approximate characters per token for JSON/English text.
	 * OpenAI tokenizers average ~4 chars per token for English.
	 * JSON with metric names tends to be slightly higher, so we use 3.5 to be conservative.
	 */
	private static final double CHARS_PER_TOKEN = 3.5;

	/**
	 * The total budget for tool outputs in tokens.
	 */
	@Getter
	private final int totalBudgetTokens;

	/**
	 * Tokens consumed so far by tool outputs in this turn.
	 */
	private final AtomicInteger consumedTokens;

	/**
	 * Allocation tier indicating how much data to include in the tool output.
	 */
	public enum AllocationTier {
		/** Full payload can be included in the manifest. */
		FULL,
		/** Payload should be truncated to fit within remaining budget. */
		TRUNCATED,
		/** Only summary text in description, no payload field. */
		SUMMARY_ONLY,
		/** Only file reference, minimal description. */
		FILE_REFERENCE_ONLY
	}

	/**
	 * Result of a budget allocation request.
	 *
	 * @param tier            the tier determining how much data to include
	 * @param availableTokens max tokens available for this tool's output
	 * @param availableChars  approximate max characters (tokens * 3.5)
	 */
	public record AllocationResult(AllocationTier tier, int availableTokens, int availableChars) {}

	/**
	 * Creates a new budget manager using actual token counts from OpenAI.
	 *
	 * @param contextWindowTokens    the model's total context window in tokens
	 * @param promptTokensUsed       actual prompt_tokens from OpenAI's response (conversation so far)
	 * @param reservedResponseTokens tokens to reserve for the model's response
	 */
	public ContextBudgetManager(
		final int contextWindowTokens,
		final int promptTokensUsed,
		final int reservedResponseTokens
	) {
		this.totalBudgetTokens = Math.max(0, contextWindowTokens - promptTokensUsed - reservedResponseTokens);
		this.consumedTokens = new AtomicInteger(0);

		log.debug(
			"Context budget initialized: contextWindow={}t, promptUsed={}t, reserved={}t, available={}t (~{}chars)",
			contextWindowTokens,
			promptTokensUsed,
			reservedResponseTokens,
			totalBudgetTokens,
			tokensToChars(totalBudgetTokens)
		);
	}

	/**
	 * Requests a budget allocation for a tool output.
	 *
	 * @param requestedChars the size of the full tool output in characters
	 * @return an AllocationResult indicating the tier and available budget
	 */
	public AllocationResult allocate(final int requestedChars) {
		final int requestedTokens = charsToTokens(requestedChars);
		final int currentConsumed = consumedTokens.get();
		final int remainingTokens = totalBudgetTokens - currentConsumed;

		if (remainingTokens <= 0) {
			log.debug(
				"Budget exhausted (consumed={}t, total={}t). Returning FILE_REFERENCE_ONLY.",
				currentConsumed,
				totalBudgetTokens
			);
			return new AllocationResult(AllocationTier.FILE_REFERENCE_ONLY, 0, 0);
		}

		// If the full output fits within remaining budget
		if (requestedTokens <= remainingTokens) {
			consumedTokens.addAndGet(requestedTokens);
			log.debug(
				"FULL allocation: requested={}t, remaining={}t, newConsumed={}t",
				requestedTokens,
				remainingTokens,
				consumedTokens.get()
			);
			return new AllocationResult(AllocationTier.FULL, requestedTokens, requestedChars);
		}

		// Budget is pressured — can we fit at least 20%?
		final int twentyPercentTokens = requestedTokens / 5;
		if (remainingTokens >= twentyPercentTokens && remainingTokens > 300) {
			consumedTokens.addAndGet(remainingTokens);
			final int allocatedChars = tokensToChars(remainingTokens);
			log.debug(
				"TRUNCATED allocation: requested={}t, allocated={}t (~{}chars)",
				requestedTokens,
				remainingTokens,
				allocatedChars
			);
			return new AllocationResult(AllocationTier.TRUNCATED, remainingTokens, allocatedChars);
		}

		// Very little budget left — summary only
		if (remainingTokens >= 150) {
			final int summaryCost = Math.min(remainingTokens, 600); // ~2000 chars max for summary
			consumedTokens.addAndGet(summaryCost);
			log.debug("SUMMARY_ONLY allocation: requested={}t, summaryCost={}t", requestedTokens, summaryCost);
			return new AllocationResult(AllocationTier.SUMMARY_ONLY, summaryCost, tokensToChars(summaryCost));
		}

		// Almost nothing left
		log.debug("Near-zero budget (remaining={}t). Returning FILE_REFERENCE_ONLY.", remainingTokens);
		return new AllocationResult(AllocationTier.FILE_REFERENCE_ONLY, 0, 0);
	}

	/**
	 * Returns the remaining budget in tokens.
	 *
	 * @return remaining tokens available for tool outputs
	 */
	public int getRemainingTokens() {
		return Math.max(0, totalBudgetTokens - consumedTokens.get());
	}

	/**
	 * Returns the remaining budget in approximate characters.
	 *
	 * @return remaining characters available for tool outputs
	 */
	public int getRemainingChars() {
		return tokensToChars(getRemainingTokens());
	}

	/**
	 * Converts characters to approximate token count.
	 *
	 * @param chars character count
	 * @return approximate token count
	 */
	private static int charsToTokens(final int chars) {
		return (int) Math.ceil(chars / CHARS_PER_TOKEN);
	}

	/**
	 * Converts tokens to approximate character count.
	 *
	 * @param tokens token count
	 * @return approximate character count
	 */
	private static int tokensToChars(final int tokens) {
		return (int) (tokens * CHARS_PER_TOKEN);
	}
}
