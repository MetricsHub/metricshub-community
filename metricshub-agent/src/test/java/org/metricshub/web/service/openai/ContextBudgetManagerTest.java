package org.metricshub.web.service.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.metricshub.web.service.openai.ContextBudgetManager.AllocationResult;
import org.metricshub.web.service.openai.ContextBudgetManager.AllocationTier;

class ContextBudgetManagerTest {

	// 3.5 chars per token as defined in ContextBudgetManager
	private static final double CHARS_PER_TOKEN = 3.5;

	@Test
	void testFullAllocationWhenBudgetSufficient() {
		// Given: 128K context, 10K prompt used, 16K reserved → ~102K tokens available
		final ContextBudgetManager manager = new ContextBudgetManager(128000, 10000, 16384);

		// When: requesting 100K chars (~28571 tokens)
		final AllocationResult result = manager.allocate(100000);

		// Then: should get FULL allocation
		assertEquals(AllocationTier.FULL, result.tier());
		assertEquals(100000, result.availableChars());
		assertTrue(result.availableTokens() > 0);
	}

	@Test
	void testTruncatedAllocationWhenBudgetPressured() {
		// Given: limited budget - 128K context, 100K prompt used, 16K reserved → ~12K tokens available
		final ContextBudgetManager manager = new ContextBudgetManager(128000, 100000, 16384);

		// When: requesting 200K chars (more than available)
		final AllocationResult result = manager.allocate(200000);

		// Then: should get TRUNCATED allocation with reduced size
		assertEquals(AllocationTier.TRUNCATED, result.tier());
		assertTrue(result.availableChars() < 200000);
		assertTrue(result.availableChars() > 0);
	}

	@Test
	void testFileReferenceOnlyWhenBudgetExhausted() {
		// Given: budget already exceeded - 128K context, 127K prompt used, 16K reserved
		final ContextBudgetManager manager = new ContextBudgetManager(128000, 127000, 16384);
		// Available: 128000 - 127000 - 16384 = -15384 (clamped to 0)

		// When: requesting any amount
		final AllocationResult result = manager.allocate(50000);

		// Then: should get FILE_REFERENCE_ONLY
		assertEquals(AllocationTier.FILE_REFERENCE_ONLY, result.tier());
		assertEquals(0, result.availableChars());
		assertEquals(0, result.availableTokens());
	}

	@Test
	void testSummaryOnlyWhenVeryLittleBudgetRemains() {
		// Given: very limited budget - configured to leave ~500 tokens
		// We need remaining >= 150 and < 20% of requested
		final ContextBudgetManager manager = new ContextBudgetManager(128000, 127300, 400);
		// Available: 128000 - 127300 - 400 = 300 tokens

		// When: requesting a large amount (300 tokens is < 20% of what we'd need)
		final AllocationResult result = manager.allocate(100000); // ~28571 tokens needed

		// Then: should get SUMMARY_ONLY since 300 >= 150 but < 20% of 28571
		assertEquals(AllocationTier.SUMMARY_ONLY, result.tier());
	}

	@Test
	void testBudgetTracksAcrossMultipleAllocations() {
		// Given: sufficient budget for multiple allocations
		final ContextBudgetManager manager = new ContextBudgetManager(128000, 10000, 16384);
		// Available: 101616 tokens

		// When: making multiple allocations
		final AllocationResult result1 = manager.allocate(100000); // ~28571 tokens
		final AllocationResult result2 = manager.allocate(100000); // ~28571 tokens
		final AllocationResult result3 = manager.allocate(100000); // ~28571 tokens
		final AllocationResult result4 = manager.allocate(100000); // Should be pressured now

		// Then: early allocations should be FULL, later ones degraded
		assertEquals(AllocationTier.FULL, result1.tier());
		assertEquals(AllocationTier.FULL, result2.tier());
		assertEquals(AllocationTier.FULL, result3.tier());
		// Fourth allocation should be truncated or worse since budget is nearly exhausted
		assertTrue(
			result4.tier() == AllocationTier.TRUNCATED ||
			result4.tier() == AllocationTier.SUMMARY_ONLY ||
			result4.tier() == AllocationTier.FILE_REFERENCE_ONLY
		);
	}

	@Test
	void testRemainingTokensDecreasesAfterAllocation() {
		final ContextBudgetManager manager = new ContextBudgetManager(128000, 10000, 16384);
		final int initialRemaining = manager.getRemainingTokens();

		manager.allocate(35000); // ~10000 tokens

		final int afterAllocation = manager.getRemainingTokens();
		assertTrue(afterAllocation < initialRemaining, "Remaining tokens should decrease after allocation");
	}

	@Test
	void testConcurrentAllocations() throws Exception {
		// Given: limited budget that can only handle ~2-3 full allocations
		// 50K context, 10K prompt, 4K reserved → 36K tokens (~126K chars)
		// Each request for 70K chars needs ~20K tokens, so only ~1-2 can be FULL
		final ContextBudgetManager manager = new ContextBudgetManager(50000, 10000, 4000);

		// When: making concurrent allocations
		final ExecutorService executor = Executors.newFixedThreadPool(5);
		final List<Future<AllocationResult>> futures = new ArrayList<>();

		for (int i = 0; i < 5; i++) {
			futures.add(executor.submit(() -> manager.allocate(70000)));
		}

		final List<AllocationResult> results = new ArrayList<>();
		for (final Future<AllocationResult> future : futures) {
			results.add(future.get());
		}
		executor.shutdown();

		// Then: budget should be tracked correctly across threads
		// Some should succeed (FULL), some should degrade
		final long fullCount = results.stream().filter(r -> r.tier() == AllocationTier.FULL).count();
		final long degradedCount = results.stream().filter(r -> r.tier() != AllocationTier.FULL).count();

		assertTrue(fullCount >= 1, "At least one allocation should be FULL");
		assertTrue(degradedCount >= 1, "At least one allocation should be degraded due to budget pressure");
	}

	@Test
	void testZeroBudgetAfterConstruction() {
		// Given: budget that's already exhausted at construction
		final ContextBudgetManager manager = new ContextBudgetManager(100000, 100000, 16384);

		// Then: total budget should be clamped to 0
		assertEquals(0, manager.getTotalBudgetTokens());
		assertEquals(0, manager.getRemainingTokens());
	}

	@Test
	void testNegativePromptTokensClampedToZero() {
		// Given: impossible scenario where prompt_tokens > context_window
		final ContextBudgetManager manager = new ContextBudgetManager(128000, 200000, 16384);

		// Then: budget should be clamped to 0 (not negative)
		assertEquals(0, manager.getTotalBudgetTokens());
	}

	@Test
	void testGetRemainingChars() {
		final ContextBudgetManager manager = new ContextBudgetManager(128000, 10000, 16384);
		final int remainingTokens = manager.getRemainingTokens();
		final int remainingChars = manager.getRemainingChars();

		// Chars should be approximately tokens * 3.5
		assertEquals((int) (remainingTokens * CHARS_PER_TOKEN), remainingChars);
	}

	@Test
	void testSmallRequestDoesNotTriggerTruncation() {
		// Given: plenty of budget
		final ContextBudgetManager manager = new ContextBudgetManager(128000, 10000, 16384);

		// When: requesting a small amount
		final AllocationResult result = manager.allocate(1000);

		// Then: should get FULL allocation
		assertEquals(AllocationTier.FULL, result.tier());
		assertEquals(1000, result.availableChars());
	}

	@Test
	void testRefundRestoresBudget() {
		// Given: budget manager with allocation
		final ContextBudgetManager manager = new ContextBudgetManager(128000, 10000, 16384);
		final int initialRemaining = manager.getRemainingTokens();

		// When: allocating and then refunding
		manager.allocate(35000); // ~10000 tokens
		final int afterAllocation = manager.getRemainingTokens();
		manager.refund(5000); // Refund half
		final int afterRefund = manager.getRemainingTokens();

		// Then: remaining should increase after refund
		assertTrue(afterAllocation < initialRemaining, "Budget should decrease after allocation");
		assertTrue(afterRefund > afterAllocation, "Budget should increase after refund");
		assertTrue(afterRefund < initialRemaining, "Budget after refund should still be less than initial");
	}

	@Test
	void testRefundWithZeroTokensDoesNothing() {
		// Given: budget manager
		final ContextBudgetManager manager = new ContextBudgetManager(128000, 10000, 16384);
		manager.allocate(35000);
		final int remainingBefore = manager.getRemainingTokens();

		// When: refunding zero tokens
		manager.refund(0);

		// Then: remaining should be unchanged
		assertEquals(remainingBefore, manager.getRemainingTokens());
	}

	@Test
	void testRefundWithNegativeTokensDoesNothing() {
		// Given: budget manager
		final ContextBudgetManager manager = new ContextBudgetManager(128000, 10000, 16384);
		manager.allocate(35000);
		final int remainingBefore = manager.getRemainingTokens();

		// When: refunding negative tokens (invalid input)
		manager.refund(-100);

		// Then: remaining should be unchanged (defensive handling)
		assertEquals(remainingBefore, manager.getRemainingTokens());
	}

	@Test
	void testRefundAllowsSubsequentFullAllocation() {
		// Given: limited budget
		final ContextBudgetManager manager = new ContextBudgetManager(50000, 10000, 4000);
		// Available: 36000 tokens (~126K chars)

		// When: allocating, refunding, then allocating again
		final AllocationResult firstAlloc = manager.allocate(70000); // ~20000 tokens - should get FULL
		assertEquals(AllocationTier.FULL, firstAlloc.tier());

		final AllocationResult secondAlloc = manager.allocate(70000); // ~20000 tokens - might be TRUNCATED
		// Refund most of the second allocation
		manager.refund(15000);

		// Third allocation should now succeed as FULL since we refunded
		final AllocationResult thirdAlloc = manager.allocate(50000); // ~14286 tokens
		assertEquals(AllocationTier.FULL, thirdAlloc.tier(), "After refund, budget should support another FULL allocation");
	}
}
