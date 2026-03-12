package org.metricshub.engine.strategy.detection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for the transient failure tracking in {@link CriterionTestResult}.
 */
class CriterionTestResultTransientTest {

	@Test
	void testDefaultIsNotTransient() {
		final CriterionTestResult result = CriterionTestResult.builder().build();
		assertFalse(result.isTransientFailure());
	}

	@Test
	void testTransientFailureFlag() {
		final CriterionTestResult result = CriterionTestResult.builder().transientFailure(true).build();
		assertTrue(result.isTransientFailure());
		assertFalse(result.isSuccess());
	}

	@Test
	void testEmptyIsNotTransient() {
		final CriterionTestResult result = CriterionTestResult.empty();
		assertFalse(result.isTransientFailure());
	}

	@Test
	void testSuccessIsNotTransient() {
		final CriterionTestResult result = CriterionTestResult.builder().success(true).build();
		assertFalse(result.isTransientFailure());
	}
}
