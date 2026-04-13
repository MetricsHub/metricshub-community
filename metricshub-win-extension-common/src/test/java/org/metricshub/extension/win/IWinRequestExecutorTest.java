package org.metricshub.extension.win;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IWinRequestExecutorTest {

	@Test
	void testIsAcceptableWmiComError() {
		assertFalse(IWinRequestExecutor.isAcceptableWmiComError(null));
		assertFalse(IWinRequestExecutor.isAcceptableWmiComError(" "));
		assertTrue(IWinRequestExecutor.isAcceptableWmiComError("WBEM_E_INVALID_CLASS"));
		assertTrue(IWinRequestExecutor.isAcceptableWmiComError("WBEM_E_INVALID_NAMESPACE"));
		assertTrue(IWinRequestExecutor.isAcceptableWmiComError("WBEM_E_NOT_FOUND"));
	}
}
