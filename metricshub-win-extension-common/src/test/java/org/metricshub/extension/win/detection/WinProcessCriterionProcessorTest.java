package org.metricshub.extension.win.detection;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.extension.win.IWinConfiguration;
import org.metricshub.extension.win.WmiTestConfiguration;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.ProcessCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.WmiCriterion;
import org.sentrysoftware.metricshub.engine.strategy.detection.CriterionTestResult;

@ExtendWith(MockitoExtension.class)
class WinProcessCriterionProcessorTest {

	@Mock
	WmiDetectionService wmiDetectionServiceMock;

	@InjectMocks
	WinProcessCriterionProcessor winProcessCriterionProcessor;

	@Test
	void testProcess() {
		final ProcessCriterion processCriterion = new ProcessCriterion();
		processCriterion.setCommandLine("MBM[5-9]\\.exe");

		doReturn(CriterionTestResult.success(processCriterion, "success"))
			.when(wmiDetectionServiceMock)
			.performDetectionTest(eq(MetricsHubConstants.LOCALHOST), any(IWinConfiguration.class), any(WmiCriterion.class));

		assertTrue(
			winProcessCriterionProcessor.process(processCriterion, WmiTestConfiguration.builder().build()).isSuccess()
		);
	}
}
