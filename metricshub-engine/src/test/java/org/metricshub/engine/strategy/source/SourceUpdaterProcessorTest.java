package org.metricshub.engine.strategy.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_ID;
import static org.metricshub.engine.constants.Constants.EMPTY;
import static org.metricshub.engine.constants.Constants.ENCLOSURE_COLLECT_SOURCE_1;
import static org.metricshub.engine.constants.Constants.EXPECTED_RESULT;
import static org.metricshub.engine.constants.Constants.EXPECTED_SNMP_TABLE_DATA;
import static org.metricshub.engine.constants.Constants.EXPECTED_VAL_1;
import static org.metricshub.engine.constants.Constants.EXPECTED_VAL_1_AND_2;
import static org.metricshub.engine.constants.Constants.EXPECTED_VAL_1_AND_2_ARRAY;
import static org.metricshub.engine.constants.Constants.EXPECTED_VAL_2;
import static org.metricshub.engine.constants.Constants.LOCALHOST;
import static org.metricshub.engine.constants.Constants.MONITOR_ID_ATTRIBUTE_VALUE;
import static org.metricshub.engine.constants.Constants.MY_CONNECTOR_1_NAME;
import static org.metricshub.engine.constants.Constants.OID;
import static org.metricshub.engine.constants.Constants.SNMP_SELECTED_COLUMNS;
import static org.metricshub.engine.constants.Constants.TAB1_REF;
import static org.metricshub.engine.constants.Constants.URL;
import static org.metricshub.engine.constants.Constants.VALUE_A1;
import static org.metricshub.engine.constants.Constants.VALUE_B1;
import static org.metricshub.engine.constants.Constants.VALUE_C1;
import static org.metricshub.engine.constants.Constants.VALUE_VAL1;
import static org.metricshub.engine.constants.Constants.VALUE_VAL2;
import static org.metricshub.engine.constants.Constants.VALUE_VAL3;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.connector.model.common.ContentType;
import org.metricshub.engine.connector.model.common.CustomConcatMethod;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.common.EntryConcatMethod;
import org.metricshub.engine.connector.model.common.ExecuteForEachEntryOf;
import org.metricshub.engine.connector.model.common.HttpMethod;
import org.metricshub.engine.connector.model.monitor.task.source.CommandLineSource;
import org.metricshub.engine.connector.model.monitor.task.source.CopySource;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiSource;
import org.metricshub.engine.connector.model.monitor.task.source.JawkSource;
import org.metricshub.engine.connector.model.monitor.task.source.JmxSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpGetSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpTableSource;
import org.metricshub.engine.connector.model.monitor.task.source.StaticSource;
import org.metricshub.engine.connector.model.monitor.task.source.TableJoinSource;
import org.metricshub.engine.connector.model.monitor.task.source.TableUnionSource;
import org.metricshub.engine.connector.model.monitor.task.source.TextUnionSource;
import org.metricshub.engine.connector.model.monitor.task.source.WbemSource;
import org.metricshub.engine.connector.model.monitor.task.source.WmiSource;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.extension.TestConfiguration;
import org.metricshub.engine.telemetry.HostProperties;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class SourceUpdaterProcessorTest {

	@Mock
	private ISourceProcessor sourceProcessor;

	@Mock
	private IProtocolExtension protocolExtensionMock;

	@Mock
	private ExtensionManager extensionManager;

	@Test
	void testProcessHttpPSource() {
		final TestConfiguration httpConfiguration = TestConfiguration.builder().build();
		final HostConfiguration hostConfiguration = HostConfiguration.builder()
			.hostname(LOCALHOST)
			.hostId(LOCALHOST)
			.hostType(DeviceKind.LINUX)
			.configurations(Collections.singletonMap(TestConfiguration.class, httpConfiguration))
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		doReturn(SourceTable.empty()).when(sourceProcessor).process(any(HttpSource.class));
		assertEquals(
			SourceTable.empty(),
			new SourceUpdaterProcessor(
				sourceProcessor,
				telemetryManager,
				MY_CONNECTOR_1_NAME,
				Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE),
				extensionManager
			).process(HttpSource.builder().url(URL).method(HttpMethod.GET).build())
		);

		final HttpSource httpSource = HttpSource.builder().url(URL).build();
		httpSource.setExecuteForEachEntryOf(ExecuteForEachEntryOf.builder().source(ENCLOSURE_COLLECT_SOURCE_1).build());

		final SourceTable sourceTable = SourceTable.ofTable(
			Arrays.asList(Arrays.asList(VALUE_VAL1, VALUE_VAL2, VALUE_VAL3), Arrays.asList(VALUE_A1, VALUE_B1, VALUE_C1))
		);

		final HostProperties hostProperties = HostProperties.builder().build();

		hostProperties.getConnectorNamespace(MY_CONNECTOR_1_NAME).addSourceTable(ENCLOSURE_COLLECT_SOURCE_1, sourceTable);

		telemetryManager.setHostProperties(hostProperties);

		final SourceTable expected1 = SourceTable.ofText(EXPECTED_VAL_1);
		final SourceTable expected2 = SourceTable.ofText(EXPECTED_VAL_2);
		doReturn(expected1, expected2).when(sourceProcessor).process(any(HttpSource.class));
		SourceTable result = new SourceUpdaterProcessor(
			sourceProcessor,
			telemetryManager,
			MY_CONNECTOR_1_NAME,
			Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE),
			extensionManager
		).process(httpSource);
		assertEquals(EXPECTED_VAL_1_AND_2, result.getText());

		httpSource.setExecuteForEachEntryOf(
			ExecuteForEachEntryOf.builder().source(ENCLOSURE_COLLECT_SOURCE_1).concatMethod(EntryConcatMethod.LIST).build()
		);
		doReturn(expected1, expected2).when(sourceProcessor).process(any(HttpSource.class));
		result = new SourceUpdaterProcessor(
			sourceProcessor,
			telemetryManager,
			MY_CONNECTOR_1_NAME,
			Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE),
			extensionManager
		).process(httpSource);
		assertEquals(EXPECTED_VAL_1_AND_2, result.getText());

		httpSource.setExecuteForEachEntryOf(
			ExecuteForEachEntryOf.builder()
				.source(ENCLOSURE_COLLECT_SOURCE_1)
				.concatMethod(EntryConcatMethod.JSON_ARRAY)
				.build()
		);
		doReturn(expected1, expected2).when(sourceProcessor).process(any(HttpSource.class));
		result = new SourceUpdaterProcessor(
			sourceProcessor,
			telemetryManager,
			MY_CONNECTOR_1_NAME,
			Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE),
			extensionManager
		).process(httpSource);
		assertEquals(EXPECTED_VAL_1_AND_2_ARRAY, result.getText());

		httpSource.setExecuteForEachEntryOf(
			ExecuteForEachEntryOf.builder()
				.source(ENCLOSURE_COLLECT_SOURCE_1)
				.concatMethod(EntryConcatMethod.JSON_ARRAY_EXTENDED)
				.build()
		);
		doReturn(expected1, expected2).when(sourceProcessor).process(any(HttpSource.class));
		result = new SourceUpdaterProcessor(
			sourceProcessor,
			telemetryManager,
			MY_CONNECTOR_1_NAME,
			Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE),
			extensionManager
		).process(httpSource);

		assertEquals(EXPECTED_RESULT, result.getText());
	}

	@Test
	void testProcessHttpSourceCustomExecuteForEachEntry() {
		final TestConfiguration httpConfiguration = TestConfiguration.builder().build();
		final HostConfiguration hostConfiguration = HostConfiguration.builder()
			.hostname(LOCALHOST)
			.hostId(LOCALHOST)
			.hostType(DeviceKind.LINUX)
			.configurations(Collections.singletonMap(TestConfiguration.class, httpConfiguration))
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		final HttpSource httpSource = HttpSource.builder().url(URL).build();
		final CustomConcatMethod customConcatMethod = CustomConcatMethod.builder()
			.concatStart("concatStart:{")
			.concatEnd("}concatEnd;")
			.build();
		httpSource.setExecuteForEachEntryOf(
			ExecuteForEachEntryOf.builder().source(ENCLOSURE_COLLECT_SOURCE_1).concatMethod(customConcatMethod).build()
		);

		final SourceTable sourceTable = SourceTable.ofTable(
			Arrays.asList(Arrays.asList(VALUE_VAL1, VALUE_VAL2, VALUE_VAL3), Arrays.asList(VALUE_A1, VALUE_B1, VALUE_C1))
		);

		final HostProperties hostProperties = HostProperties.builder().build();

		hostProperties.getConnectorNamespace(MY_CONNECTOR_1_NAME).addSourceTable(ENCLOSURE_COLLECT_SOURCE_1, sourceTable);

		telemetryManager.setHostProperties(hostProperties);

		final SourceTable expected1 = SourceTable.ofText(EXPECTED_VAL_1);
		final SourceTable expected2 = SourceTable.ofText(EXPECTED_VAL_2);
		doReturn(expected1, expected2).when(sourceProcessor).process(any(HttpSource.class));

		SourceTable result = new SourceUpdaterProcessor(
			sourceProcessor,
			telemetryManager,
			MY_CONNECTOR_1_NAME,
			Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE),
			extensionManager
		).process(httpSource);
		final String expectedResult = "concatStart:{expectedVal1}concatEnd;concatStart:{expectedVal2}concatEnd;";
		assertEquals(expectedResult, result.getText());

		// A failed entry makes the source fail; an empty entry is an answer, wrapped; a table entry is read as its text
		final SourceTable threeRows = SourceTable.ofTable(
			Arrays.asList(Arrays.asList("1"), Arrays.asList("2"), Arrays.asList("3"))
		);
		hostProperties.getConnectorNamespace(MY_CONNECTOR_1_NAME).addSourceTable(ENCLOSURE_COLLECT_SOURCE_1, threeRows);
		doReturn(SourceTable.ofText("a"), SourceTable.failed("Refused"), SourceTable.ofTable(List.of(List.of("b", "c"))))
			.when(sourceProcessor)
			.process(any(HttpSource.class));
		final SourceUpdaterProcessor updater = new SourceUpdaterProcessor(
			sourceProcessor,
			telemetryManager,
			MY_CONNECTOR_1_NAME,
			Map.of(),
			extensionManager
		);
		assertEquals(SourceTable.failed("Entry [2]: Refused"), updater.process(httpSource));

		// With continueOnFailure, a failed entry is skipped
		httpSource.setContinueOnFailure(true);
		doReturn(SourceTable.ofText("a"), SourceTable.failed("Refused"), SourceTable.ofText("")).when(sourceProcessor)
			.process(any(HttpSource.class));
		assertEquals(SourceTable.ofText("concatStart:{a}concatEnd;concatStart:{}concatEnd;"), updater.process(httpSource));
		httpSource.setContinueOnFailure(false);
		doReturn(SourceTable.ofText("a"), SourceTable.ofText(""), SourceTable.ofTable(List.of(List.of("b", "c")))).when(
			sourceProcessor
		).process(any(HttpSource.class));
		assertEquals(
			SourceTable.ofText("concatStart:{a}concatEnd;concatStart:{}concatEnd;concatStart:{b;c;}concatEnd;"),
			updater.process(httpSource)
		);

		// LIST keeps the text type of an http source: a table entry is converted, never dropped
		httpSource.setExecuteForEachEntryOf(
			ExecuteForEachEntryOf.builder().source(ENCLOSURE_COLLECT_SOURCE_1).concatMethod(EntryConcatMethod.LIST).build()
		);
		doReturn(SourceTable.ofText("a"), SourceTable.empty(), SourceTable.ofTable(List.of(List.of("b", "c")))).when(
			sourceProcessor
		).process(any(HttpSource.class));
		final SourceTable list = new SourceUpdaterProcessor(
			sourceProcessor,
			telemetryManager,
			MY_CONNECTOR_1_NAME,
			Map.of(),
			extensionManager
		).process(httpSource);
		assertEquals(SourceTable.ofText("a\nb;c;"), list);
	}

	@Test
	void testProcessHttpSourceExecuteForEachEntrySleep() {
		final HttpSource httpSource = HttpSource.builder().url(URL).build();
		assertNull(httpSource.getSleepExecuteForEachEntryOf());

		httpSource.setExecuteForEachEntryOf(
			ExecuteForEachEntryOf.builder().source(ENCLOSURE_COLLECT_SOURCE_1).concatMethod(EntryConcatMethod.LIST).build()
		);

		assertEquals(null, httpSource.getSleepExecuteForEachEntryOf());

		httpSource.setExecuteForEachEntryOf(
			ExecuteForEachEntryOf.builder()
				.source(ENCLOSURE_COLLECT_SOURCE_1)
				.concatMethod(EntryConcatMethod.LIST)
				.sleep(200)
				.build()
		);

		assertEquals(200, httpSource.getSleepExecuteForEachEntryOf());

		final CustomConcatMethod customConcatMethod = CustomConcatMethod.builder()
			.concatStart("concatStart:{")
			.concatEnd("}concatEnd;")
			.build();

		httpSource.setExecuteForEachEntryOf(
			ExecuteForEachEntryOf.builder()
				.source(ENCLOSURE_COLLECT_SOURCE_1)
				.concatMethod(customConcatMethod)
				.sleep(400)
				.build()
		);
		assertEquals(400, httpSource.getSleepExecuteForEachEntryOf());
	}

	/**
	 * Run an HTTP source with {@code path: $1} for each of the 4 entries "a", "b", "c" and "d".
	 *
	 * @param executeForEachEntryOf The executeForEachEntryOf definition (its source is overridden)
	 * @param sequentialHost        Whether the host is configured as sequential
	 * @param forceSerialization    Whether the source forces serialization
	 * @param answer                Answer returning the result of each entry
	 * @return The concatenated result
	 */
	private SourceTable runForEachEntry(
		final ExecuteForEachEntryOf executeForEachEntryOf,
		final boolean sequentialHost,
		final boolean forceSerialization,
		final Answer<SourceTable> answer
	) {
		return runForEachEntry(executeForEachEntryOf, sequentialHost, forceSerialization, false, answer);
	}

	/**
	 * Run an HTTP source with {@code path: $1} for each of the 4 entries "a", "b", "c" and "d".
	 *
	 * @param executeForEachEntryOf The executeForEachEntryOf definition (its source is overridden)
	 * @param sequentialHost        Whether the host is configured as sequential
	 * @param forceSerialization    Whether the source forces serialization
	 * @param continueOnFailure     Whether the source continues on failure
	 * @param answer                Answer returning the result of each entry
	 * @return The concatenated result
	 */
	private SourceTable runForEachEntry(
		final ExecuteForEachEntryOf executeForEachEntryOf,
		final boolean sequentialHost,
		final boolean forceSerialization,
		final boolean continueOnFailure,
		final Answer<SourceTable> answer
	) {
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostConfiguration(
				HostConfiguration.builder()
					.hostname(LOCALHOST)
					.hostId(LOCALHOST)
					.hostType(DeviceKind.LINUX)
					.sequential(sequentialHost)
					.build()
			)
			.build();
		final HostProperties hostProperties = HostProperties.builder().build();
		hostProperties.getConnectorNamespace(MY_CONNECTOR_1_NAME)
			.addSourceTable(
				ENCLOSURE_COLLECT_SOURCE_1,
				SourceTable.ofTable(List.of(List.of("a"), List.of("b"), List.of("c"), List.of("d")))
			);
		telemetryManager.setHostProperties(hostProperties);

		executeForEachEntryOf.setSource(ENCLOSURE_COLLECT_SOURCE_1);
		final HttpSource httpSource = HttpSource.builder()
			.url(URL)
			.path("$1")
			.forceSerialization(forceSerialization)
			.continueOnFailure(continueOnFailure)
			.executeForEachEntryOf(executeForEachEntryOf)
			.build();

		doAnswer(answer).when(sourceProcessor).process(any(HttpSource.class));

		return new SourceUpdaterProcessor(
			sourceProcessor,
			telemetryManager,
			MY_CONNECTOR_1_NAME,
			Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE),
			extensionManager
		).process(httpSource);
	}

	/**
	 * Answer recording the maximum number of concurrent calls in {@code maxConcurrent}.
	 */
	private static Answer<SourceTable> concurrencyRecordingAnswer(final AtomicInteger maxConcurrent) {
		final AtomicInteger current = new AtomicInteger();
		return invocation -> {
			maxConcurrent.accumulateAndGet(current.incrementAndGet(), Math::max);
			Thread.sleep(20);
			current.decrementAndGet();
			return SourceTable.ofText("result-" + invocation.<HttpSource>getArgument(0).getPath());
		};
	}

	@Test
	void testProcessExecuteForEachEntryParallel() {
		// Every entry waits until all 4 entries run at the same time, then the first entries finish last
		final CountDownLatch allRunning = new CountDownLatch(4);
		final List<String> notConcurrent = Collections.synchronizedList(new ArrayList<>());
		final Answer<SourceTable> answer = invocation -> {
			final String entry = invocation.<HttpSource>getArgument(0).getPath();
			allRunning.countDown();
			if (!allRunning.await(10, TimeUnit.SECONDS)) {
				notConcurrent.add(entry);
			}
			Thread.sleep(('e' - entry.charAt(0)) * 30L);
			return SourceTable.ofText("result-" + entry);
		};

		final SourceTable parallel = runForEachEntry(
			ExecuteForEachEntryOf.builder().source(ENCLOSURE_COLLECT_SOURCE_1).maxParallel(4).build(),
			false,
			false,
			answer
		);

		assertEquals(List.of(), notConcurrent);
		// Same output, in the same order, as the sequential run
		final SourceTable sequential = runForEachEntry(
			ExecuteForEachEntryOf.builder().source(ENCLOSURE_COLLECT_SOURCE_1).build(),
			false,
			false,
			concurrencyRecordingAnswer(new AtomicInteger())
		);
		assertEquals(SourceTable.ofText("result-a\nresult-b\nresult-c\nresult-d"), parallel);
		assertEquals(sequential, parallel);
	}

	/**
	 * LIST keeps the type of the entries: a table source concatenates the rows of its entries, dropping the rows of no
	 * cell, in the entry order.
	 */
	@Test
	void testProcessExecuteForEachEntryListOfTables() {
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostConfiguration(
				HostConfiguration.builder().hostname(LOCALHOST).hostId(LOCALHOST).hostType(DeviceKind.LINUX).build()
			)
			.hostProperties(HostProperties.builder().build())
			.build();
		telemetryManager.getHostProperties()
			.getConnectorNamespace(MY_CONNECTOR_1_NAME)
			.addSourceTable(
				ENCLOSURE_COLLECT_SOURCE_1,
				SourceTable.ofTable(List.of(List.of("a"), List.of("b"), List.of("c"), List.of("d")))
			);
		final SnmpGetSource snmpGetSource = SnmpGetSource.builder()
			.oid("$1")
			.output(ContentType.TABLE)
			.executeForEachEntryOf(ExecuteForEachEntryOf.builder().source(ENCLOSURE_COLLECT_SOURCE_1).build())
			.build();
		doAnswer(invocation -> {
			final String entry = invocation.<SnmpGetSource>getArgument(0).getOid();
			if ("c".equals(entry)) {
				return SourceTable.ofTable(new ArrayList<>(List.of(new ArrayList<>(), new ArrayList<>(List.of("")))));
			}
			return "d".equals(entry) ? SourceTable.empty() : SourceTable.ofTable(List.of(List.of(entry, "x")));
		}).when(sourceProcessor).process(any(SnmpGetSource.class));

		final SourceTable result = new SourceUpdaterProcessor(
			sourceProcessor,
			telemetryManager,
			MY_CONNECTOR_1_NAME,
			Map.of(),
			extensionManager
		).process(snmpGetSource);

		assertEquals(SourceTable.ofTable(List.of(List.of("a", "x"), List.of("b", "x"), List.of(""))), result);
	}

	@Test
	void testProcessExecuteForEachEntryStopsWhenInterrupted() {
		final AtomicInteger requests = new AtomicInteger();
		try {
			final SourceTable result = runForEachEntry(
				ExecuteForEachEntryOf.builder().source(ENCLOSURE_COLLECT_SOURCE_1).build(),
				false,
				false,
				invocation -> {
					// The job deadline expires during the first request
					requests.incrementAndGet();
					Thread.currentThread().interrupt();
					return SourceTable.ofText("{}");
				}
			);
			assertEquals(1, requests.get());
			assertTrue(result.isEmpty());
			assertTrue(Thread.currentThread().isInterrupted());
		} finally {
			Thread.interrupted();
		}
	}

	/**
	 * A queued entry of the parallel pool sends no request once the coordinator is interrupted, even before the
	 * coordinator notices the interrupt and shuts the pool down.
	 */
	@Test
	void testProcessExecuteForEachEntryParallelQueuedEntryStopsWhenInterrupted() throws InterruptedException {
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostConfiguration(
				HostConfiguration.builder().hostname(LOCALHOST).hostId(LOCALHOST).hostType(DeviceKind.LINUX).build()
			)
			.hostProperties(HostProperties.builder().build())
			.build();
		telemetryManager.getHostProperties()
			.getConnectorNamespace(MY_CONNECTOR_1_NAME)
			.addSourceTable(
				ENCLOSURE_COLLECT_SOURCE_1,
				SourceTable.ofTable(List.of(List.of("a"), List.of("b"), List.of("c"), List.of("d")))
			);
		final HttpSource httpSource = HttpSource.builder()
			.url(URL)
			.path("$1${attribute::gate}")
			.executeForEachEntryOf(ExecuteForEachEntryOf.builder().source(ENCLOSURE_COLLECT_SOURCE_1).maxParallel(2).build())
			.build();

		// The coordinator submits a, b and c (c is queued: both pool threads are busy), then is held in the preparation
		// of d, so that it cannot shut the pool down while c is taken by a pool thread
		final AtomicInteger preparedEntries = new AtomicInteger();
		final CountDownLatch coordinatorHeld = new CountDownLatch(1);
		final CountDownLatch releaseCoordinator = new CountDownLatch(1);
		final Map<String, String> attributes = new HashMap<>() {
			@Override
			public String get(final Object key) {
				if (preparedEntries.incrementAndGet() == 4) {
					coordinatorHeld.countDown();
					awaitUninterruptibly(releaseCoordinator);
				}
				return "";
			}
		};

		final CountDownLatch firstTwoRunning = new CountDownLatch(2);
		final CountDownLatch releaseA = new CountDownLatch(1);
		final CountDownLatch releaseB = new CountDownLatch(1);
		final CountDownLatch queuedEntrySent = new CountDownLatch(1);
		final List<String> sent = Collections.synchronizedList(new ArrayList<>());
		doAnswer(invocation -> {
			final String entry = invocation.<HttpSource>getArgument(0).getPath();
			sent.add(entry);
			if ("a".equals(entry) || "b".equals(entry)) {
				firstTwoRunning.countDown();
				awaitUninterruptibly("a".equals(entry) ? releaseA : releaseB);
			} else {
				queuedEntrySent.countDown();
			}
			return SourceTable.ofText(entry);
		}).when(sourceProcessor).process(any(HttpSource.class));

		final SourceTable[] result = new SourceTable[1];
		final Thread coordinator = new Thread(
			() -> result[0] = new SourceUpdaterProcessor(
				sourceProcessor,
				telemetryManager,
				MY_CONNECTOR_1_NAME,
				attributes,
				extensionManager
			).process(httpSource)
		);
		coordinator.start();
		try {
			assertTrue(firstTwoRunning.await(10, TimeUnit.SECONDS));
			assertTrue(coordinatorHeld.await(10, TimeUnit.SECONDS));
			// The job deadline expires, then a pool thread becomes free and takes the queued entry c
			coordinator.interrupt();
			releaseA.countDown();
			assertFalse(queuedEntrySent.await(1, TimeUnit.SECONDS));
		} finally {
			releaseCoordinator.countDown();
			releaseB.countDown();
			coordinator.join(10_000);
		}

		synchronized (sent) {
			assertEquals(List.of("a", "b"), sent.stream().sorted().toList());
		}
		assertTrue(result[0].isEmpty());
	}

	/**
	 * Waits for the latch without ever clearing the interrupt of the current thread, as a thread blocked in a request
	 * that ignores interrupts does: other threads keep seeing it interrupted.
	 *
	 * @param latch The latch to wait for
	 */
	private static void awaitUninterruptibly(final CountDownLatch latch) {
		while (latch.getCount() > 0) {
			LockSupport.parkNanos(1_000_000);
		}
	}

	/**
	 * The entry the coordinator waits for ends last, while every core is busy: its pool thread may be descheduled
	 * between waking the coordinator up and marking its task done. The coordinator must still see it done.
	 */
	@Test
	void testProcessExecuteForEachEntryParallelWaitedEntryEndingLastWakesTheCoordinator() throws InterruptedException {
		final AtomicBoolean stop = new AtomicBoolean();
		final List<Thread> busyThreads = new ArrayList<>();
		for (int i = 0; i < Runtime.getRuntime().availableProcessors(); i++) {
			busyThreads.add(Thread.ofPlatform().daemon().start(() -> {
				while (!stop.get()) {
					Thread.onSpinWait();
				}
			}));
		}
		try {
			for (int run = 0; run < 200; run++) {
				final SourceTable[] result = new SourceTable[1];
				final Thread coordinator = Thread.ofPlatform()
					.daemon()
					.start(
						() -> result[0] = runForEachEntry(
							ExecuteForEachEntryOf.builder().source(ENCLOSURE_COLLECT_SOURCE_1).maxParallel(4).build(),
							false,
							false,
							invocation -> {
								final String entry = invocation.<HttpSource>getArgument(0).getPath();
								if ("a".equals(entry)) {
									LockSupport.parkNanos(200_000);
								}
								return SourceTable.ofText(entry);
							}
						)
					);
				coordinator.join(5_000);
				final boolean stuck = coordinator.isAlive();
				coordinator.interrupt();
				assertFalse(stuck, "Coordinator still waiting at run " + run);
				assertEquals(SourceTable.ofText("a\nb\nc\nd"), result[0]);
			}
		} finally {
			stop.set(true);
			for (final Thread busyThread : busyThreads) {
				busyThread.join();
			}
		}
	}

	@Test
	void testProcessExecuteForEachEntryParallelJsonArray() {
		final SourceTable result = runForEachEntry(
			ExecuteForEachEntryOf.builder()
				.source(ENCLOSURE_COLLECT_SOURCE_1)
				.concatMethod(EntryConcatMethod.JSON_ARRAY)
				.maxParallel(2)
				.build(),
			false,
			false,
			invocation -> {
				final String entry = invocation.<HttpSource>getArgument(0).getPath();
				Thread.sleep(('e' - entry.charAt(0)) * 20L);
				return SourceTable.ofText("{\"id\":\"" + entry + "\"}");
			}
		);
		assertEquals("[{\"id\":\"a\"},\n{\"id\":\"b\"},\n{\"id\":\"c\"},\n{\"id\":\"d\"}]", result.getText());
	}

	@Test
	void testProcessExecuteForEachEntrySequentialCases() {
		// maxParallel absent
		AtomicInteger maxConcurrent = new AtomicInteger();
		runForEachEntry(
			ExecuteForEachEntryOf.builder().source(ENCLOSURE_COLLECT_SOURCE_1).build(),
			false,
			false,
			concurrencyRecordingAnswer(maxConcurrent)
		);
		assertEquals(1, maxConcurrent.get());

		// maxParallel set, but the host is sequential
		maxConcurrent = new AtomicInteger();
		runForEachEntry(
			ExecuteForEachEntryOf.builder().source(ENCLOSURE_COLLECT_SOURCE_1).maxParallel(4).build(),
			true,
			false,
			concurrencyRecordingAnswer(maxConcurrent)
		);
		assertEquals(1, maxConcurrent.get());

		// maxParallel set, but sleep throttles the entries
		maxConcurrent = new AtomicInteger();
		runForEachEntry(
			ExecuteForEachEntryOf.builder().source(ENCLOSURE_COLLECT_SOURCE_1).maxParallel(4).sleep(1).build(),
			false,
			false,
			concurrencyRecordingAnswer(maxConcurrent)
		);
		assertEquals(1, maxConcurrent.get());

		// maxParallel set, but the source forces serialization
		maxConcurrent = new AtomicInteger();
		final SourceTable result = runForEachEntry(
			ExecuteForEachEntryOf.builder().source(ENCLOSURE_COLLECT_SOURCE_1).maxParallel(4).build(),
			false,
			true,
			concurrencyRecordingAnswer(maxConcurrent)
		);
		assertEquals(1, maxConcurrent.get());
		assertEquals("result-a\nresult-b\nresult-c\nresult-d", result.getText());
	}

	@Test
	void testProcessExecuteForEachEntryParallelException() {
		final ExecuteForEachEntryOf executeForEachEntryOf = ExecuteForEachEntryOf.builder()
			.source(ENCLOSURE_COLLECT_SOURCE_1)
			.maxParallel(4)
			.build();
		final IllegalStateException thrown = assertThrows(
			IllegalStateException.class,
			() -> runForEachEntry(executeForEachEntryOf, false, false, invocation -> {
				throw new IllegalStateException("boom");
			})
		);
		assertEquals("boom", thrown.getMessage());
	}

	@Test
	void testProcessExecuteForEachEntryParallelFailedEntry() {
		// Two workers: a fails once b runs, b and c take 200 ms, d is queued until b ends
		final Set<String> requested = ConcurrentHashMap.newKeySet();
		final AtomicBoolean bEnded = new AtomicBoolean();
		final CountDownLatch bStarted = new CountDownLatch(1);
		final Answer<SourceTable> answer = invocation -> {
			final String entry = invocation.<HttpSource>getArgument(0).getPath();
			requested.add(entry);
			if ("a".equals(entry)) {
				// Without this wait, a slow pool thread could see the failure before b sends its request
				assertTrue(bStarted.await(5, TimeUnit.SECONDS));
				return SourceTable.failed("Refused");
			}
			if ("b".equals(entry)) {
				bStarted.countDown();
			}
			Thread.sleep(200);
			bEnded.compareAndSet(false, "b".equals(entry));
			return SourceTable.ofText("result-" + entry);
		};
		final ExecuteForEachEntryOf executeForEachEntryOf = ExecuteForEachEntryOf.builder()
			.source(ENCLOSURE_COLLECT_SOURCE_1)
			.maxParallel(2)
			.build();

		// The source fails once the entries still running have ended: the queued entries send no request
		assertEquals(
			SourceTable.failed("Entry [a]: Refused"),
			runForEachEntry(executeForEachEntryOf, false, false, false, answer)
		);
		assertTrue(bEnded.get());
		assertFalse(requested.contains("d"));

		// With continueOnFailure, the failed entry is skipped, and the result says it is partial
		final SourceTable partial = runForEachEntry(executeForEachEntryOf, false, false, true, answer);
		assertEquals(SourceTable.ofText("result-b\nresult-c\nresult-d"), partial);
		assertTrue(partial.isPartial());
	}

	@Test
	void testProcessExecuteForEachEntryParallelLaterEntryFailsFirst() {
		// Two workers: b fails at once while a still runs, so c and d are queued after the failure
		final Set<String> requested = ConcurrentHashMap.newKeySet();
		final Answer<SourceTable> answer = invocation -> {
			final String entry = invocation.<HttpSource>getArgument(0).getPath();
			requested.add(entry);
			if ("b".equals(entry)) {
				return SourceTable.failed("Refused");
			}
			Thread.sleep(300);
			return SourceTable.ofText("result-" + entry);
		};
		final ExecuteForEachEntryOf executeForEachEntryOf = ExecuteForEachEntryOf.builder()
			.source(ENCLOSURE_COLLECT_SOURCE_1)
			.maxParallel(2)
			.build();

		// The failure stops the queued entries at once, not when the coordinator reaches b after a
		assertEquals(
			SourceTable.failed("Entry [b]: Refused"),
			runForEachEntry(executeForEachEntryOf, false, false, false, answer)
		);
		assertEquals(Set.of("a", "b"), requested);
	}

	@Test
	void testProcessSNMPGetSource() {
		final TestConfiguration snmpConfiguration = TestConfiguration.builder().build();
		final HostConfiguration hostConfiguration = HostConfiguration.builder()
			.hostname(LOCALHOST)
			.hostId(LOCALHOST)
			.hostType(DeviceKind.LINUX)
			.configurations(Collections.singletonMap(TestConfiguration.class, snmpConfiguration))
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		doReturn(SourceTable.empty()).when(sourceProcessor).process(any(SnmpGetSource.class));
		assertEquals(
			SourceTable.empty(),
			new SourceUpdaterProcessor(
				sourceProcessor,
				telemetryManager,
				MY_CONNECTOR_1_NAME,
				Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE),
				extensionManager
			).process(SnmpGetSource.builder().oid(OID).build())
		);

		final SourceTable expected1 = SourceTable.ofText(EXPECTED_VAL_1);

		doReturn(expected1).when(sourceProcessor).process(any(SnmpGetSource.class));
		assertEquals(
			expected1,
			new SourceUpdaterProcessor(
				sourceProcessor,
				telemetryManager,
				MY_CONNECTOR_1_NAME,
				Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE),
				extensionManager
			).process(SnmpGetSource.builder().oid(OID).build())
		);
	}

	@Test
	void testProcessSNMPGetTableSource() {
		final TestConfiguration snmpConfiguration = TestConfiguration.builder().build();
		final HostConfiguration hostConfiguration = HostConfiguration.builder()
			.hostname(LOCALHOST)
			.hostId(LOCALHOST)
			.hostType(DeviceKind.LINUX)
			.configurations(Collections.singletonMap(TestConfiguration.class, snmpConfiguration))
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();
		doReturn(SourceTable.empty()).when(sourceProcessor).process(any(SnmpTableSource.class));
		assertEquals(
			SourceTable.empty(),
			new SourceUpdaterProcessor(
				sourceProcessor,
				telemetryManager,
				MY_CONNECTOR_1_NAME,
				Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE),
				extensionManager
			).process(SnmpTableSource.builder().oid(OID).selectColumns(SNMP_SELECTED_COLUMNS).build())
		);

		SourceTable expected = SourceTable.ofTable(EXPECTED_SNMP_TABLE_DATA);
		doReturn(expected).when(sourceProcessor).process(any(SnmpTableSource.class));
		assertEquals(
			expected,
			new SourceUpdaterProcessor(
				sourceProcessor,
				telemetryManager,
				MY_CONNECTOR_1_NAME,
				Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE),
				extensionManager
			).process(SnmpTableSource.builder().oid(OID).selectColumns(SNMP_SELECTED_COLUMNS).build())
		);
	}

	@Test
	void testProcessTableJoinSource() {
		final TestConfiguration snmpConfiguration = TestConfiguration.builder().build();
		final HostConfiguration hostConfiguration = HostConfiguration.builder()
			.hostname(LOCALHOST)
			.hostId(LOCALHOST)
			.hostType(DeviceKind.LINUX)
			.configurations(Collections.singletonMap(TestConfiguration.class, snmpConfiguration))
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();
		doReturn(SourceTable.empty()).when(sourceProcessor).process(any(TableJoinSource.class));
		assertEquals(
			SourceTable.empty(),
			new SourceUpdaterProcessor(
				sourceProcessor,
				telemetryManager,
				MY_CONNECTOR_1_NAME,
				Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE),
				extensionManager
			).process(TableJoinSource.builder().build())
		);
	}

	@Test
	void testProcessTableUnionSource() {
		final TestConfiguration snmpConfiguration = TestConfiguration.builder().build();
		final HostConfiguration hostConfiguration = HostConfiguration.builder()
			.hostname(LOCALHOST)
			.hostId(LOCALHOST)
			.hostType(DeviceKind.LINUX)
			.configurations(Collections.singletonMap(TestConfiguration.class, snmpConfiguration))
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();
		doReturn(SourceTable.empty()).when(sourceProcessor).process(any(TableUnionSource.class));
		assertEquals(
			SourceTable.empty(),
			new SourceUpdaterProcessor(
				sourceProcessor,
				telemetryManager,
				MY_CONNECTOR_1_NAME,
				Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE),
				extensionManager
			).process(TableUnionSource.builder().tables(new ArrayList<>()).build())
		);
	}

	@Test
	void testProcessTextUnionSource() {
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostConfiguration(HostConfiguration.builder().hostname(LOCALHOST).hostId(LOCALHOST).build())
			.build();
		final String member = "${source::monitors.cpu.discovery.sources.member}";
		final TextUnionSource textUnion = TextUnionSource.builder().texts(new ArrayList<>(List.of(member))).build();
		doReturn(SourceTable.ofText("x")).when(sourceProcessor).process(any(TextUnionSource.class));
		assertEquals(
			SourceTable.ofText("x"),
			new SourceUpdaterProcessor(sourceProcessor, telemetryManager, MY_CONNECTOR_1_NAME, Map.of(), extensionManager)
				.process(textUnion)
		);
		// The source processor runs a copy whose members are still the references
		verify(sourceProcessor).process(TextUnionSource.builder().texts(List.of(member)).build());
	}

	@Test
	void testProcessCopySource() {
		final TestConfiguration snmpConfiguration = TestConfiguration.builder().build();
		final HostConfiguration hostConfiguration = HostConfiguration.builder()
			.hostname(LOCALHOST)
			.hostId(LOCALHOST)
			.hostType(DeviceKind.LINUX)
			.configurations(Collections.singletonMap(TestConfiguration.class, snmpConfiguration))
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();
		doReturn(SourceTable.empty()).when(sourceProcessor).process(any(CopySource.class));
		assertEquals(
			SourceTable.empty(),
			new SourceUpdaterProcessor(
				sourceProcessor,
				telemetryManager,
				MY_CONNECTOR_1_NAME,
				Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE),
				extensionManager
			).process(CopySource.builder().from(TAB1_REF).build())
		);

		CopySource copySource = CopySource.builder().from(TAB1_REF).build();
		final List<List<String>> result = Collections.singletonList(Collections.singletonList(VALUE_VAL1));
		final SourceTable expected = SourceTable.ofTable(result);

		final SnmpGetSource snmpGetSource = SnmpGetSource.builder().oid(OID).build();

		final List<List<String>> resultSnmp = Collections.singletonList(Collections.singletonList(VALUE_VAL1));
		final SourceTable expectedSnmp = SourceTable.ofTable(resultSnmp);
		doReturn(expectedSnmp).when(sourceProcessor).process(any(SnmpGetSource.class));
		assertEquals(
			expectedSnmp,
			new SourceUpdaterProcessor(
				sourceProcessor,
				telemetryManager,
				MY_CONNECTOR_1_NAME,
				Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE),
				extensionManager
			).process(snmpGetSource)
		);

		doReturn(expected).when(sourceProcessor).process(any(CopySource.class));
		assertEquals(
			expected,
			new SourceUpdaterProcessor(
				sourceProcessor,
				telemetryManager,
				MY_CONNECTOR_1_NAME,
				Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE),
				extensionManager
			).process(copySource)
		);
	}

	@Test
	void testProcessStaticSource() {
		final TestConfiguration snmpConfiguration = TestConfiguration.builder().build();
		final HostConfiguration hostConfiguration = HostConfiguration.builder()
			.hostname(LOCALHOST)
			.hostId(LOCALHOST)
			.hostType(DeviceKind.LINUX)
			.configurations(Collections.singletonMap(TestConfiguration.class, snmpConfiguration))
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		StaticSource staticSource = StaticSource.builder().value(List.of(VALUE_VAL1)).build();
		final List<List<String>> resultTable = Collections.singletonList(Collections.singletonList(VALUE_VAL1));
		final SourceTable expected = SourceTable.ofTable(resultTable);
		doReturn(expected).when(sourceProcessor).process(any(StaticSource.class));
		assertEquals(
			expected,
			new SourceUpdaterProcessor(
				sourceProcessor,
				telemetryManager,
				MY_CONNECTOR_1_NAME,
				Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE),
				extensionManager
			).process(staticSource)
		);
	}

	@Test
	void testReplaceAttributeReferences() {
		String command = "show ${attribute::id}";
		assertEquals("show disk1", SourceUpdaterProcessor.replaceAttributeReferences(command, Map.of("id", "disk1")));
		assertEquals(command, SourceUpdaterProcessor.replaceAttributeReferences(command, Map.of()));
		assertEquals(command, SourceUpdaterProcessor.replaceAttributeReferences(command, null));
		assertNull(SourceUpdaterProcessor.replaceAttributeReferences(null, Map.of()));
		command = "show ${attribute::type} ${attribute::id}";
		assertEquals(
			"show disk 1",
			SourceUpdaterProcessor.replaceAttributeReferences(command, Map.of("id", "1", "type", "disk"))
		);
		// Tool arguments ride on the same map
		command = "ps -ef | grep ${param::name}";
		assertEquals(
			"ps -ef | grep java",
			SourceUpdaterProcessor.replaceAttributeReferences(command, Map.of("name", "java"))
		);
	}

	@Test
	void testReplaceEscapedReferences() {
		assertEquals(
			"journalctl -u 'x'\\''; reboot; '\\'''",
			SourceUpdaterProcessor.replaceAttributeReferences(
				"journalctl -u '${esc(bash)::param::service}'",
				Map.of("service", "x'; reboot; '")
			)
		);
		assertEquals(
			"{\"mode\": \"a\\\"b\"}",
			SourceUpdaterProcessor.replaceAttributeReferences(
				"{\"mode\": \"${ESC(JSON)::attribute::mode}\"}",
				Map.of("mode", "a\"b")
			)
		);
		assertEquals(
			"Bearer a%2Fb",
			SourceUpdaterProcessor.replaceResourceAttributeReferences(
				"Bearer ${esc(url)::resource.attribute::token}",
				Map.of("token", "a/b")
			)
		);
		assertEquals(
			"Get-Service -Name 'a''b'",
			SourceUpdaterProcessor.replaceAttributeReferences(
				"Get-Service -Name '${esc(powershell)::attribute::name}'",
				Map.of("name", "a'b")
			)
		);
		// A value forming a macro, by itself or with its neighbors, is not inserted
		final String percent = "echo '%${esc(bash)::param::suffix}'";
		assertEquals(percent, SourceUpdaterProcessor.replaceAttributeReferences(percent, Map.of("suffix", "{PASSWORD}")));
		final String twoParts = "echo ${param::a}${param::b}";
		assertEquals(
			"echo %${param::b}",
			SourceUpdaterProcessor.replaceAttributeReferences(twoParts, Map.of("a", "%", "b", "{PASSWORD}"))
		);
		assertEquals(
			"show ${attribute::id}",
			SourceUpdaterProcessor.replaceAttributeReferences("show ${attribute::id}", Map.of("id", "%{PASSWORD}"))
		);
		assertEquals(
			"100% {ok}",
			SourceUpdaterProcessor.replaceAttributeReferences("${param::a} {ok}", Map.of("a", "100%"))
		);
		// An unknown escape type leaves the reference rather than inserting the value unescaped
		final String unknown = "ping '${esc(bsh)::param::target}'";
		assertEquals(unknown, SourceUpdaterProcessor.replaceAttributeReferences(unknown, Map.of("target", "$(id)")));
	}

	@Test
	void testReplaceResourceAttributeReferences() {
		String header = "Authorization: Bearer ${resource.attribute::api.token}";
		assertEquals(
			"Authorization: Bearer mySecretToken",
			SourceUpdaterProcessor.replaceResourceAttributeReferences(header, Map.of("api.token", "mySecretToken"))
		);
		assertEquals(header, SourceUpdaterProcessor.replaceResourceAttributeReferences(header, Map.of()));
		assertEquals(header, SourceUpdaterProcessor.replaceResourceAttributeReferences(header, null));
		assertNull(SourceUpdaterProcessor.replaceResourceAttributeReferences(null, Map.of()));
		header = "Authorization: Bearer ${resource.attribute::api.token}/${resource.attribute::user.id}";
		assertEquals(
			"Authorization: Bearer mySecretToken/user123",
			SourceUpdaterProcessor.replaceResourceAttributeReferences(
				header,
				Map.of("api.token", "mySecretToken", "user.id", "user123")
			)
		);
	}

	@Test
	void testProcessWbemSource() {
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostConfiguration(HostConfiguration.builder().build())
			.build();
		doReturn(SourceTable.empty()).when(sourceProcessor).process(any(WbemSource.class));
		assertEquals(
			SourceTable.empty(),
			new SourceUpdaterProcessor(
				sourceProcessor,
				telemetryManager,
				MY_CONNECTOR_1_NAME,
				Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE),
				extensionManager
			).process(WbemSource.builder().query(EMPTY).build())
		);
	}

	@Test
	void testProcessWmiSource() {
		doReturn(SourceTable.empty()).when(sourceProcessor).process(any(WmiSource.class));
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostConfiguration(HostConfiguration.builder().build())
			.build();
		assertEquals(
			SourceTable.empty(),
			new SourceUpdaterProcessor(
				sourceProcessor,
				telemetryManager,
				MY_CONNECTOR_1_NAME,
				Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE),
				extensionManager
			).process(WmiSource.builder().query(EMPTY).build())
		);
	}

	@Test
	void testProcessCommandLineSource() {
		doReturn(SourceTable.empty()).when(sourceProcessor).process(any(CommandLineSource.class));
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostConfiguration(HostConfiguration.builder().build())
			.build();
		assertEquals(
			SourceTable.empty(),
			new SourceUpdaterProcessor(
				sourceProcessor,
				telemetryManager,
				MY_CONNECTOR_1_NAME,
				Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE),
				extensionManager
			).process(
				CommandLineSource.builder().commandLine("/usr/sbin/pvdisplay /dev/dsk/%PhysicalDisk.Collect.DeviceID%").build()
			)
		);
	}

	@Test
	void testProcessIpmiSource() {
		doReturn(SourceTable.empty()).when(sourceProcessor).process(any(IpmiSource.class));
		assertEquals(
			SourceTable.empty(),
			new SourceUpdaterProcessor(
				sourceProcessor,
				TelemetryManager.builder().build(),
				MY_CONNECTOR_1_NAME,
				Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE),
				extensionManager
			).process(IpmiSource.builder().build())
		);
	}

	@Test
	void testReplaceSourceReferenceContent() {
		final TelemetryManager telemetryManager = new TelemetryManager();
		telemetryManager.setHostConfiguration(HostConfiguration.builder().hostname("hostname").build());

		final String vendorSource1Ref = "${source::monitors.cpu.discovery.sources.vendor}";

		{
			final String value = String.format("%s value", vendorSource1Ref);
			telemetryManager.getHostProperties()
				.getConnectorNamespace(MY_CONNECTOR_1_NAME)
				.addSourceTable(vendorSource1Ref, SourceTable.ofText("vendor"));

			final String result = SourceUpdaterProcessor.replaceSourceReferenceContent(
				value,
				telemetryManager,
				MY_CONNECTOR_1_NAME,
				"source",
				"object"
			);
			assertEquals("vendor value", result);
		}

		{
			final String value = String.format("%s %s value", vendorSource1Ref, vendorSource1Ref);
			telemetryManager.getHostProperties()
				.getConnectorNamespace(MY_CONNECTOR_1_NAME)
				.addSourceTable(vendorSource1Ref, SourceTable.ofText("vendor"));

			final String result = SourceUpdaterProcessor.replaceSourceReferenceContent(
				value,
				telemetryManager,
				MY_CONNECTOR_1_NAME,
				"source",
				"object"
			);
			assertEquals("vendor vendor value", result);
		}
		{
			final String value = String.format("%s%s value", vendorSource1Ref, vendorSource1Ref);
			telemetryManager.getHostProperties()
				.getConnectorNamespace(MY_CONNECTOR_1_NAME)
				.addSourceTable(vendorSource1Ref, SourceTable.ofText("vendor"));

			final String result = SourceUpdaterProcessor.replaceSourceReferenceContent(
				value,
				telemetryManager,
				MY_CONNECTOR_1_NAME,
				"source",
				"object"
			);
			assertEquals("vendorvendor value", result);
		}
		{
			// A table is not interpolated: the reference gives an empty text
			telemetryManager.getHostProperties()
				.getConnectorNamespace(MY_CONNECTOR_1_NAME)
				.addSourceTable(vendorSource1Ref, SourceTable.ofTable(List.of(List.of("a", "b"))));

			assertEquals(
				" value",
				SourceUpdaterProcessor.replaceSourceReferenceContent(
					vendorSource1Ref + " value",
					telemetryManager,
					MY_CONNECTOR_1_NAME,
					"source",
					"object"
				)
			);
		}
	}

	@Test
	void testProcessJmxSource() {
		final JmxSource source = JmxSource.builder()
			.type("jmx")
			.objectName("org.metricshub.extension.jmx:type=JmxMBean,scope=*")
			.attributes(new LinkedList<>(List.of("Name")))
			.key("${source::beforeAll.jmxSource}")
			.forceSerialization(false)
			.keyProperties(new LinkedList<>(List.of("scope")))
			.build();

		final SourceTable sourceTable = SourceTable.ofTable(List.of(List.of("12345")));
		doReturn(sourceTable).when(sourceProcessor).process(any(JmxSource.class));
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostConfiguration(HostConfiguration.builder().build())
			.build();
		assertEquals(
			sourceTable,
			new SourceUpdaterProcessor(
				sourceProcessor,
				telemetryManager,
				MY_CONNECTOR_1_NAME,
				Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE),
				extensionManager
			).process(source),
			"Processing JMX source did not return expected result."
		);
	}

	/**
	 * The updater must pass an Awk source's variables through untouched. Every reference they hold, source references
	 * included, is resolved in one pass by AwkVariableHelper just before the script runs, so that a variable holding a
	 * source reference can become an array rather than the CSV text this chain would produce.
	 */
	@Test
	void testProcessJawkSourceVariables() {
		final String otherSourceRef = "${source::monitors.enclosure.discovery.sources.other}";

		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostConfiguration(
				HostConfiguration.builder()
					.hostname(LOCALHOST)
					.hostId(LOCALHOST)
					.hostType(DeviceKind.LINUX)
					.attributes(Map.of("host.name", "my-host", "host.type", "linux"))
					.build()
			)
			.build();

		final Map<String, String> variables = new LinkedHashMap<>();
		variables.put("hostname", "${resource.attribute::host.name}");
		variables.put("hostType", "${resource.attribute::host.type}");
		variables.put("monitorId", "${attribute::id}");
		variables.put("aTable", otherSourceRef);

		final JawkSource jawkSource = JawkSource.builder()
			.type("awk")
			.key("${source::monitors.enclosure.discovery.sources.awk}")
			.script("BEGIN { print hostname }")
			.variables(variables)
			.build();

		doReturn(SourceTable.empty()).when(sourceProcessor).process(any(JawkSource.class));

		new SourceUpdaterProcessor(
			sourceProcessor,
			telemetryManager,
			MY_CONNECTOR_1_NAME,
			Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE),
			extensionManager
		).process(jawkSource);

		final ArgumentCaptor<JawkSource> captor = ArgumentCaptor.forClass(JawkSource.class);
		verify(sourceProcessor).process(captor.capture());

		assertEquals(
			variables,
			captor.getValue().getVariables(),
			"The updater must leave the variables to AwkVariableHelper"
		);

		// The original source must not have been mutated: the updater works on a copy
		assertEquals("${resource.attribute::host.name}", jawkSource.getVariables().get("hostname"));
	}

	/**
	 * An awk source input that is a single source reference is resolved to that source when the script runs: the
	 * updater must not expand it into the source's text. A reference inside a text is expanded (#1559).
	 */
	@Test
	void testProcessJawkSourceReferenceInputKeepsItsReference() {
		final String inputRef = "${source::monitors.enclosure.discovery.sources.raw}";
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostConfiguration(HostConfiguration.builder().hostname(LOCALHOST).hostId(LOCALHOST).build())
			.hostProperties(HostProperties.builder().build())
			.build();
		// A text that looks like a source reference
		telemetryManager.getHostProperties()
			.getConnectorNamespace(MY_CONNECTOR_1_NAME)
			.addSourceTable(inputRef, SourceTable.ofText("${source::monitors.enclosure.discovery.sources.other}"));
		doReturn(SourceTable.empty()).when(sourceProcessor).process(any(JawkSource.class));
		final SourceUpdaterProcessor updater = new SourceUpdaterProcessor(
			sourceProcessor,
			telemetryManager,
			MY_CONNECTOR_1_NAME,
			Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE),
			extensionManager
		);

		updater.process(JawkSource.builder().input(inputRef).script("{ print }").build());
		updater.process(JawkSource.builder().input("x " + inputRef).script("{ print }").build());

		final ArgumentCaptor<JawkSource> captor = ArgumentCaptor.forClass(JawkSource.class);
		verify(sourceProcessor, times(2)).process(captor.capture());
		assertEquals(inputRef, captor.getAllValues().get(0).getInput(), "single reference: kept");
		// Inside a text: the reference is expanded into the source's text (which looks like a reference)
		assertEquals("x ${source::monitors.enclosure.discovery.sources.other}", captor.getAllValues().get(1).getInput());
	}

	@Test
	void testProtocolReferencesRememberTheCredentials() {
		final IConfiguration ipmi = org.mockito.Mockito.mock(IConfiguration.class);
		doReturn(true).when(ipmi).isCorrespondingProtocol("ipmi");
		doReturn("0011").when(ipmi).getProperty("bmckey");
		doReturn("bmc-1").when(ipmi).getProperty("hostname");
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostConfiguration(HostConfiguration.builder().configurations(Map.of(ipmi.getClass(), ipmi)).build())
			.build();

		assertEquals(
			"ipmitool -k 0011 -H bmc-1",
			SourceUpdaterProcessor.replaceProtocolPropertyReferences(
				"ipmitool -k ${protocol::ipmi.bmckey} -H ${protocol::ipmi.hostname}",
				telemetryManager
			)
		);
		// The key, not the hostname: an error quoting the request masks it for a tool's caller
		assertEquals(Set.of("0011"), telemetryManager.getResolvedSecrets());
	}
}
