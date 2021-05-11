package com.sentrysoftware.matrix.connector.parser.state.compute.leftconcat;

import com.sentrysoftware.matrix.connector.model.Connector;
import com.sentrysoftware.matrix.connector.model.monitor.HardwareMonitor;
import com.sentrysoftware.matrix.connector.model.monitor.MonitorType;
import com.sentrysoftware.matrix.connector.model.monitor.job.collect.Collect;
import com.sentrysoftware.matrix.connector.model.monitor.job.discovery.Discovery;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.Source;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.Compute;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.DuplicateColumn;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.LeftConcat;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.type.snmp.SNMPGetTableSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeftConcatProcessorTest {

	private LeftConcatProcessor typeProcessor;
	private LeftConcatProcessor columnProcessor;

	private Connector connector;

	private static final String FOO = "FOO";
	private static final String LEFT_CONCAT_DISCOVERY_TYPE_KEY = "enclosure.discovery.source(1).compute(1).type";
	private static final String LEFT_CONCAT_COLLECT_TYPE_KEY = "enclosure.collect.source(1).compute(1).type";
	private static final String LEFT_CONCAT_TYPE_VALUE = "LeftConcat";
	private static final String LEFT_CONCAT_COLUMN_KEY = "enclosure.collect.source(1).compute(1).column";


	@BeforeEach
	void setUp() {

		typeProcessor = new TypeProcessor();
		columnProcessor = new ColumnProcessor();
		connector = new Connector();
	}

	@Test
	void testDetect() {

		assertFalse(typeProcessor.detect(null, null, null));
		assertFalse(typeProcessor.detect(null, FOO, null));
		assertFalse(typeProcessor.detect(FOO, FOO, null));
		assertFalse(typeProcessor.detect(LEFT_CONCAT_DISCOVERY_TYPE_KEY, FOO, null));
		assertTrue(typeProcessor.detect(LEFT_CONCAT_DISCOVERY_TYPE_KEY, LEFT_CONCAT_TYPE_VALUE, null));

		assertThrows(IllegalArgumentException.class, () -> columnProcessor.detect(LEFT_CONCAT_COLUMN_KEY, FOO, null));
		assertFalse(columnProcessor.detect(LEFT_CONCAT_COLUMN_KEY, FOO, connector));

		// Source not null
		Source source = SNMPGetTableSource
			.builder()
			.index(1)
			.build();

		connector
			.getHardwareMonitors()
			.add(HardwareMonitor
				.builder()
				.type(MonitorType.ENCLOSURE)
				.collect(Collect
					.builder()
					.sources(Collections.singletonList(source))
					.build())
				.build());

		assertFalse(columnProcessor.detect(LEFT_CONCAT_COLUMN_KEY, FOO, connector));

		// Source not null, source.getComputes() null
		source.setComputes(null);
		assertFalse(columnProcessor.detect(LEFT_CONCAT_COLUMN_KEY, FOO, connector));

		// Source not null, source.getComputes() not null, LeftConcat not found, wrong Compute index
		Compute duplicateColumn = DuplicateColumn.builder().index(2).build();
		source.setComputes(Collections.singletonList(duplicateColumn));
		assertFalse(columnProcessor.detect(LEFT_CONCAT_COLUMN_KEY, FOO, connector));

		// Source not null, source.getComputes() not null, LeftConcat found, wrong Compute index
		LeftConcat leftConcat = LeftConcat.builder().index(2).build();
		source.setComputes(Collections.singletonList(leftConcat));
		assertFalse(columnProcessor.detect(LEFT_CONCAT_COLUMN_KEY, FOO, connector));

		// Source not null, source.getComputes() not null, LeftConcat found, correct Compute index
		leftConcat.setIndex(1);
		assertTrue(columnProcessor.detect(LEFT_CONCAT_COLUMN_KEY, FOO, connector));
	}

	@Test
	void testParse() {

		assertThrows(IllegalArgumentException.class, () -> typeProcessor.parse(null, null, null));
		assertThrows(IllegalArgumentException.class, () -> typeProcessor.parse(FOO, null, null));
		assertThrows(IllegalArgumentException.class, () -> typeProcessor.parse(FOO, FOO, null));
		assertDoesNotThrow(() -> typeProcessor.parse(LEFT_CONCAT_DISCOVERY_TYPE_KEY, LEFT_CONCAT_TYPE_VALUE, connector));
	}

	@Test
	void testGetCompute() {

		// No Source found
		Matcher matcher = typeProcessor.getMatcher(LEFT_CONCAT_DISCOVERY_TYPE_KEY);
		assertTrue(matcher.matches());
		assertNull(typeProcessor.getCompute(typeProcessor.getSource(matcher, connector),
			typeProcessor.getComputeIndex(matcher)));

		// Source found
		connector
			.getHardwareMonitors()
			.add(HardwareMonitor
				.builder()
				.type(MonitorType.ENCLOSURE)
				.discovery(Discovery
					.builder()
					.sources(Collections.singletonList(SNMPGetTableSource
						.builder()
						.index(1)
						.build()))
					.build())
				.build());

		assertNull(typeProcessor.getCompute(typeProcessor.getSource(matcher, connector),
			typeProcessor.getComputeIndex(matcher)));
	}

	@Test
	void testGetSource() {

		Matcher matcher = typeProcessor.getMatcher(LEFT_CONCAT_COLLECT_TYPE_KEY);
		assertTrue(matcher.matches());

		// HardwareMonitor found, job is collect, HardwareMonitor.getCollect() is null
		connector
			.getHardwareMonitors()
			.add(HardwareMonitor
				.builder()
				.type(MonitorType.ENCLOSURE)
				.build());

		assertNull(typeProcessor.getSource(matcher, connector));

		// HardwareMonitor found, job is discovery, HardwareMonitor.getDiscovery() is not null,
		// HardwareMonitor.getDiscovery().getSources() is null
		matcher = typeProcessor.getMatcher(LEFT_CONCAT_DISCOVERY_TYPE_KEY);
		assertTrue(matcher.matches());

		Discovery discovery = Discovery.builder().build();
		discovery.setSources(null);

		connector
			.getHardwareMonitors()
			.get(0)
			.setDiscovery(discovery);

		assertNull(typeProcessor.getSource(matcher, connector));

		// HardwareMonitor found, job is discovery, HardwareMonitor.getDiscovery() is not null,
		// HardwareMonitor.getDiscovery().getSources() is not null, wrong source index
		SNMPGetTableSource source = SNMPGetTableSource
			.builder()
			.index(2)
			.build();

		connector
			.getHardwareMonitors()
			.get(0)
			.getDiscovery()
			.setSources(Collections.singletonList(source));

		assertNull(typeProcessor.getSource(matcher, connector));
	}
}