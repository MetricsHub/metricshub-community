package org.metricshub.agent.opentelemetry;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.agent.opentelemetry.client.GrpcClient;
import org.metricshub.agent.opentelemetry.client.HttpProtobufClient;
import org.metricshub.agent.opentelemetry.client.IOtelClient;
import org.metricshub.agent.opentelemetry.client.NoopClient;
import org.metricshub.agent.service.TestHelper;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetricsExporterTest {

	@Mock
	private IOtelClient mockClient;

	private MetricsExporter exporter;

	@BeforeEach
	void setUp() {
		TestHelper.configureGlobalLogger();
		exporter = MetricsExporter.builder().withClient(mockClient).build();
	}

	@Test
	void export_shouldCallClientSend() {
		final List<ResourceMetrics> metrics = List.of(ResourceMetrics.getDefaultInstance());

		exporter.export(metrics, () -> {});

		verify(mockClient, times(1)).send(any(ExportMetricsServiceRequest.class), any());
	}

	@Test
	void export_shouldHandleExceptionGracefully() {
		doThrow(new RuntimeException("Test exception")).when(mockClient).send(any(), any());

		assertDoesNotThrow(
			() -> exporter.export(List.of(ResourceMetrics.getDefaultInstance()), () -> {}),
			"Exporter should handle exceptions gracefully"
		);
	}

	@Test
	void withConfiguration_shouldCreateGrpcClient() throws InterruptedException {
		final Map<String, String> config = Map.of(
			OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_PROTOCOL,
			OtelConfigConstants.GRPC,
			OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_ENDPOINT,
			"http://localhost:4317"
		);

		final MetricsExporter exporter = MetricsExporter.builder().withConfiguration(config).build();
		assertTrue(exporter.getClient() instanceof GrpcClient, "Should use GrpcClient");
	}

	@Test
	void withConfiguration_shouldCreateHttpProtobufClient() {
		final Map<String, String> config = Map.of(
			OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_PROTOCOL,
			OtelConfigConstants.HTTP_PROTOBUF,
			OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_ENDPOINT,
			"http://localhost:4318"
		);

		final MetricsExporter exporter = MetricsExporter.builder().withConfiguration(config).build();
		assertTrue(exporter.getClient() instanceof HttpProtobufClient, "Should use HttpProtobufClient");
	}

	@Test
	void withConfiguration_shouldCreateNoopClientForInvalidProtocol() {
		final Map<String, String> config = Map.of(
			OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_PROTOCOL,
			"invalid_protocol"
		);

		final MetricsExporter exporter = MetricsExporter.builder().withConfiguration(config).build();
		assertTrue(exporter.getClient() instanceof NoopClient, "Should fallback to NoopClient");
	}
}
