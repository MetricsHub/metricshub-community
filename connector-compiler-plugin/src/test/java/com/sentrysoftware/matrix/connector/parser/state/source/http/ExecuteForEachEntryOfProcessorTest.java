package com.sentrysoftware.matrix.connector.parser.state.source.http;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.sentrysoftware.matrix.connector.model.Connector;
import com.sentrysoftware.matrix.connector.model.monitor.HardwareMonitor;
import com.sentrysoftware.matrix.connector.model.monitor.MonitorType;
import com.sentrysoftware.matrix.connector.model.monitor.job.discovery.Discovery;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.type.http.HTTPSource;

class ExecuteForEachEntryOfProcessorTest {

	private static final String ENTRY_CONCAT_END_KEY = "enclosure.discovery.source(3).executeforeachentryof";
	private static final String VALUE = "%Enclosure.Discovery.Source(2)%";
	private static final String RESULT = "Enclosure.Discovery.Source(2)";

	@Test
	void testParse() {

		HTTPSource httpSource = HTTPSource.builder().index(3).build();
		Discovery discovery = Discovery.builder().sources(Collections.singletonList(httpSource)).build();
		HardwareMonitor hardwareMonitor = HardwareMonitor
				.builder()
				.type(MonitorType.ENCLOSURE)
				.discovery(discovery)
				.build();
		Connector connector = new Connector();
		connector.setHardwareMonitors(Collections.singletonList(hardwareMonitor));

		new ExecuteForEachEntryOfProcessor().parse(ENTRY_CONCAT_END_KEY, VALUE, connector);
		assertEquals(RESULT, httpSource.getExecuteForEachEntryOf());
	}
}
