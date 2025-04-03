package org.metricshub.extension.wmi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.metricshub.wmi.exceptions.WmiComException;

class WmiRequestExecutorTest {

	@Test
	void testIsAcceptableException() {
		final WmiRequestExecutor requestExecutor = new WmiRequestExecutor();
		assertFalse(requestExecutor.isAcceptableException(null));
		assertFalse(requestExecutor.isAcceptableException(new Exception()));
		assertFalse(requestExecutor.isAcceptableException(new Exception(new Exception())));

		assertFalse(requestExecutor.isAcceptableException(new WmiComException("other")));
		assertFalse(requestExecutor.isAcceptableException(new WmiComException(new Exception())));
		assertTrue(requestExecutor.isAcceptableException(new WmiComException(WmiRequestExecutor.WBEM_E_INVALID_CLASS)));
		assertTrue(requestExecutor.isAcceptableException(new WmiComException(WmiRequestExecutor.WBEM_E_INVALID_NAMESPACE)));
		assertTrue(requestExecutor.isAcceptableException(new WmiComException(WmiRequestExecutor.WBEM_E_NOT_FOUND)));

		assertTrue(
			requestExecutor.isAcceptableException(
				new Exception(new WmiComException(WmiRequestExecutor.WBEM_E_INVALID_NAMESPACE))
			)
		);
	}
}
