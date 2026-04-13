package org.metricshub.extension.winrm;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.metricshub.winrm.exceptions.WindowsRemoteException;
import org.metricshub.winrm.exceptions.WqlQuerySyntaxException;

class WinRmRequestExecutorTest {

	@Test
	void testIsAcceptableException() {
		WinRmRequestExecutor winRmRequestExecutor = new WinRmRequestExecutor();
		assertFalse(winRmRequestExecutor.isAcceptableException(null));
		assertFalse(winRmRequestExecutor.isAcceptableException(new Exception()));
		assertFalse(winRmRequestExecutor.isAcceptableException(new Exception(new Exception())));
		assertFalse(winRmRequestExecutor.isAcceptableException(new WindowsRemoteException("other")));
		assertFalse(winRmRequestExecutor.isAcceptableException(new WindowsRemoteException(new Exception())));
		assertTrue(
			winRmRequestExecutor.isAcceptableException(
				new RuntimeException(new WindowsRemoteException("WBEM_E_INVALID_NAMESPACE"))
			)
		);
		assertTrue(
			winRmRequestExecutor.isAcceptableException(new RuntimeException(new WindowsRemoteException("WBEM_E_NOT_FOUND")))
		);
		assertTrue(
			winRmRequestExecutor.isAcceptableException(
				new RuntimeException(new WindowsRemoteException("WBEM_E_INVALID_CLASS"))
			)
		);
		assertTrue(winRmRequestExecutor.isAcceptableException(new RuntimeException(new WqlQuerySyntaxException(""))));
	}
}
