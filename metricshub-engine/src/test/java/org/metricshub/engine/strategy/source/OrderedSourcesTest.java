package org.metricshub.engine.strategy.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.JobInfo;
import org.metricshub.engine.connector.model.common.HttpMethod;
import org.metricshub.engine.connector.model.common.ResultContent;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.TableJoinSource;
import org.metricshub.engine.strategy.source.OrderedSources.OrderedSourcesBuilder;

class OrderedSourcesTest {

	private static final JobInfo JOB_INFO = JobInfo
		.builder()
		.connectorId("connector")
		.hostname("test")
		.monitorType("enclosure")
		.jobName("discovery")
		.build();

	private static final Map<String, Source> EMPTY_MAP = Collections.emptyMap();
	private static final List<Set<String>> EMPTY_DEP_TREE = Collections.emptyList();
	private static final List<String> EMPTY_EXECUTION_ORDER = Collections.emptyList();

	private static final String SOURCE_NAME_1 = "source1";
	private static final String SOURCE_NAME_2 = "source2";
	private static final String SOURCE_NAME_3 = "source3";
	private static final String SOURCE_NAME_4 = "source4";

	private static final HttpSource SOURCE1 = HttpSource
		.builder()
		.method(HttpMethod.GET)
		.url("/system")
		.resultContent(ResultContent.BODY)
		.build();

	private static final HttpSource SOURCE2 = HttpSource
		.builder()
		.method(HttpMethod.GET)
		.url("/health")
		.resultContent(ResultContent.BODY)
		.build();

	private static final TableJoinSource SOURCE3 = TableJoinSource
		.builder()
		.leftTable("${source::monitors.enclosure.discovery.sources.source1}")
		.rightTable("${source::monitors.enclosure.discovery.sources.source2}")
		.rightKeyColumn(1)
		.leftKeyColumn(2)
		.build();

	@Test
	void testOrderThroughExecutionOrder() {
		final Map<String, Source> sources = new LinkedHashMap<>();
		sources.put(SOURCE_NAME_3, SOURCE3);
		sources.put(SOURCE_NAME_2, SOURCE2);
		sources.put(SOURCE_NAME_1, SOURCE1);

		final List<String> executionOrder = List.of(SOURCE_NAME_1, SOURCE_NAME_2, SOURCE_NAME_3);

		final List<Source> result = OrderedSources
			.builder()
			.sources(sources, executionOrder, EMPTY_DEP_TREE, JOB_INFO)
			.build()
			.getSources();

		assertEquals(List.of(SOURCE1, SOURCE2, SOURCE3), result);
	}

	@Test
	void testOrderThrowsExceptionOnSourceNameNotFound() {
		final Map<String, Source> sources = new LinkedHashMap<>();
		sources.put(SOURCE_NAME_3, SOURCE3);
		sources.put(SOURCE_NAME_2, SOURCE2);
		sources.put(SOURCE_NAME_1, SOURCE1);

		final List<String> executionOrder = List.of(SOURCE_NAME_1, SOURCE_NAME_2, SOURCE_NAME_4);

		final OrderedSourcesBuilder builder = OrderedSources.builder();
		assertThrows(IllegalStateException.class, () -> builder.sources(sources, executionOrder, EMPTY_DEP_TREE, JOB_INFO));
	}

	@Test
	void testOrderThrowsExceptionOnOrderHavingDifferentSize() {
		final Map<String, Source> sources = new LinkedHashMap<>();
		sources.put(SOURCE_NAME_3, SOURCE3);
		sources.put(SOURCE_NAME_2, SOURCE2);
		sources.put(SOURCE_NAME_1, SOURCE1);

		final List<String> executionOrder = List.of(SOURCE_NAME_1, SOURCE_NAME_2);

		final OrderedSourcesBuilder builder = OrderedSources.builder();
		assertThrows(IllegalStateException.class, () -> builder.sources(sources, executionOrder, EMPTY_DEP_TREE, JOB_INFO));
	}

	@Test
	void testOrderEmptyOrNullSources() {
		assertEquals(
			EMPTY_DEP_TREE,
			OrderedSources
				.builder()
				.sources(null, List.of(SOURCE_NAME_1, SOURCE_NAME_2), EMPTY_DEP_TREE, JOB_INFO)
				.build()
				.getSources()
		);

		assertEquals(
			EMPTY_DEP_TREE,
			OrderedSources
				.builder()
				.sources(EMPTY_MAP, List.of(SOURCE_NAME_1, SOURCE_NAME_2), EMPTY_DEP_TREE, JOB_INFO)
				.build()
				.getSources()
		);
	}

	@Test
	void testOrderThroughSourceDepTree() {
		final Map<String, Source> sources = new LinkedHashMap<>();
		sources.put(SOURCE_NAME_3, SOURCE3);
		sources.put(SOURCE_NAME_2, SOURCE2);
		sources.put(SOURCE_NAME_1, SOURCE1);

		final List<Set<String>> sourceDepTree = List.of(
			new LinkedHashSet<>(Arrays.asList(SOURCE_NAME_1, SOURCE_NAME_2)),
			Set.of(SOURCE_NAME_3)
		);

		final List<Source> result1 = OrderedSources
			.builder()
			.sources(sources, null, sourceDepTree, JOB_INFO)
			.build()
			.getSources();

		assertEquals(List.of(SOURCE1, SOURCE2, SOURCE3), result1);

		final List<Source> result2 = OrderedSources
			.builder()
			.sources(sources, EMPTY_EXECUTION_ORDER, sourceDepTree, JOB_INFO)
			.build()
			.getSources();

		assertEquals(List.of(SOURCE1, SOURCE2, SOURCE3), result2);
	}

	@Test
	void testOrderKeepsOrgin() {
		final Map<String, Source> sources = new LinkedHashMap<>();
		sources.put(SOURCE_NAME_1, SOURCE1);
		sources.put(SOURCE_NAME_2, SOURCE2);
		sources.put(SOURCE_NAME_3, SOURCE3);

		final List<Source> result1 = OrderedSources
			.builder()
			.sources(sources, EMPTY_EXECUTION_ORDER, null, JOB_INFO)
			.build()
			.getSources();

		assertEquals(List.of(SOURCE1, SOURCE2, SOURCE3), result1);

		final List<Source> result2 = OrderedSources
			.builder()
			.sources(sources, EMPTY_EXECUTION_ORDER, EMPTY_DEP_TREE, JOB_INFO)
			.build()
			.getSources();

		assertEquals(List.of(SOURCE1, SOURCE2, SOURCE3), result2);
	}
}
