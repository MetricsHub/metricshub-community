package org.metricshub.web.service.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ContextBudgetHolderTest {

	@AfterEach
	void cleanup() {
		// Always clear after each test to prevent interference
		ContextBudgetHolder.clear();
	}

	@Test
	void testSetAndGet() {
		// Given: a budget manager
		final ContextBudgetManager manager = new ContextBudgetManager(128000, 10000, 16384);

		// When: setting it in the holder
		ContextBudgetHolder.set(manager);

		// Then: should be retrievable
		assertNotNull(ContextBudgetHolder.get());
		assertEquals(manager, ContextBudgetHolder.get());
	}

	@Test
	void testClear() {
		// Given: a budget manager set in the holder
		final ContextBudgetManager manager = new ContextBudgetManager(128000, 10000, 16384);
		ContextBudgetHolder.set(manager);

		// When: clearing the holder
		ContextBudgetHolder.clear();

		// Then: get should return null
		assertNull(ContextBudgetHolder.get());
	}

	@Test
	void testGetReturnsNullWhenNotSet() {
		// Given: nothing set

		// Then: get should return null
		assertNull(ContextBudgetHolder.get());
	}

	@Test
	void testThreadIsolation() throws Exception {
		// Given: a budget manager for the main thread
		final ContextBudgetManager mainManager = new ContextBudgetManager(128000, 10000, 16384);
		ContextBudgetHolder.set(mainManager);

		// When: checking from another thread
		final AtomicReference<ContextBudgetManager> otherThreadManager = new AtomicReference<>();
		final Thread otherThread = new Thread(() -> {
			otherThreadManager.set(ContextBudgetHolder.get());
		});
		otherThread.start();
		otherThread.join();

		// Then: other thread should not see the main thread's manager
		assertNull(otherThreadManager.get(), "Thread-local should be isolated between threads");
		assertEquals(mainManager, ContextBudgetHolder.get(), "Main thread should still see its manager");
	}

	@Test
	void testEachThreadHasItsOwnInstance() throws Exception {
		// Given: different managers for different threads
		final ContextBudgetManager manager1 = new ContextBudgetManager(128000, 10000, 16384);
		final ContextBudgetManager manager2 = new ContextBudgetManager(100000, 5000, 8192);

		final AtomicReference<ContextBudgetManager> thread2Result = new AtomicReference<>();

		// Set manager1 in main thread
		ContextBudgetHolder.set(manager1);

		// When: setting different manager in another thread
		final Thread thread2 = new Thread(() -> {
			ContextBudgetHolder.set(manager2);
			thread2Result.set(ContextBudgetHolder.get());
			ContextBudgetHolder.clear();
		});
		thread2.start();
		thread2.join();

		// Then: each thread should see its own manager
		assertEquals(manager1, ContextBudgetHolder.get(), "Main thread should see manager1");
		assertEquals(manager2, thread2Result.get(), "Thread 2 should have seen manager2");
	}

	@Test
	void testSetNullExplicitly() {
		// Given: a manager set in the holder
		final ContextBudgetManager manager = new ContextBudgetManager(128000, 10000, 16384);
		ContextBudgetHolder.set(manager);

		// When: setting null
		ContextBudgetHolder.set(null);

		// Then: get should return null
		assertNull(ContextBudgetHolder.get());
	}
}
