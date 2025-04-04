package org.metricshub.agent.opentelemetry.client;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2025 Sentry Software
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceResponse;
import io.opentelemetry.proto.collector.metrics.v1.MetricsServiceGrpc;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.opentelemetry.LogContextSetter;

/**
 * OpenTelemetry client for sending metrics to a gRPC endpoint.
 */
@Slf4j
public class GrpcClient extends AbstractOtelClient {

	private ManagedChannel channel;
	private MetricsServiceGrpc.MetricsServiceStub asyncStub;

	/**
	 * Constructor for a gRPC client.
	 * @param endpoint    The URL of the OpenTelemetry receiver.
	 * @param headers     The headers to be sent on OpenTelemetry requests.
	 * @param certificate The path to the file containing trusted certificates to use when verifying a server's TLS credentials.
	 * @param timeout     The timeout for the OpenTelemetry requests.
	 * @param poolSize    The size of the thread pool.
	 * @throws SSLException if an error occurs during the creation of the gRPC client.
	 */
	@Builder(setterPrefix = "with")
	public GrpcClient(
		final String endpoint,
		final Map<String, String> headers,
		final String certificate,
		final long timeout,
		final int poolSize
	) throws SSLException {
		super(endpoint, headers, certificate, timeout, poolSize);
		// Resolve the endpoint
		resolveEndpoint();

		// Create gRPC asyncStub
		createGrpcStub();
	}

	/**
	 * Creates a gRPC asynchronous stub that will be used to send metrics.
	 *
	 * @throws SSLException if an error occurs during the creation of the gRPC client.
	 */
	private void createGrpcStub() throws SSLException {
		// Extract the port from the endpoint
		final NettyChannelBuilder builder = NettyChannelBuilder.forAddress(url.getHost(), resolvePort());
		if (isSecure()) {
			// Create a gRPC channel with TLS
			if (certificate != null && !certificate.isBlank()) {
				// Use the provided certificate
				builder.sslContext(GrpcSslContexts.forClient().trustManager(new File(certificate)).build());
			} else {
				// No custom certificate: Use default system CA trust store
				builder.sslContext(GrpcSslContexts.forClient().build());
			}
		} else {
			// If not secure, disable TLS
			builder.usePlaintext();
		}

		builder.keepAliveTimeout(timeout, TimeUnit.SECONDS).keepAliveTime(30, TimeUnit.SECONDS);

		if (headers != null && !headers.isEmpty()) {
			// Attach the interceptor for headers
			builder.intercept(List.of(new HeaderClientInterceptor(headers)));
		}

		// Create a gRPC asyncStub
		channel = builder.executor(executorService).build();
		asyncStub = MetricsServiceGrpc.newStub(channel);
	}

	/**
	 * Sends the metrics to the gRPC endpoint.
	 *
	 * @param request          The request containing the metrics to be sent.
	 * @param logContextSetter The {@link LogContextSetter} to use for asynchronous logging.
	 */
	@Override
	public void send(final ExportMetricsServiceRequest request, final LogContextSetter logContextSetter) {
		final long startTime = System.currentTimeMillis();
		asyncStub.export(
			request,
			new StreamObserver<>() {
				@Override
				public void onNext(ExportMetricsServiceResponse response) {
					logContextSetter.setContext();
					if (log.isTraceEnabled()) {
						log.trace("Received gRPC Response: {}", response);
					}
				}

				@Override
				public void onError(Throwable t) {
					logContextSetter.setContext();
					log.error("Failed to send metrics. Error message: {}", t.getMessage());
					log.debug("Failed to send metrics:", t);
				}

				@Override
				public void onCompleted() {
					logContextSetter.setContext();
					log.debug("Metrics sent successfully. Duration: {} ms", System.currentTimeMillis() - startTime);
				}
			}
		);
	}

	/**
	 * Get the default port for gRPC.
	 * @return the default port number. (4317)
	 */
	@Override
	protected int defaultPort() {
		return 4317;
	}

	/**
	 * Shutdown the gRPC client.
	 */
	@Override
	public void shutdown() {
		try {
			log.info("Shutting down gRPC client...");
			channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);

			super.shutdownExecutor();
		} catch (Exception e) {
			log.error("Failed to shutdown the gRPC client. Error message: {}", e.getMessage());
			log.debug("Failed to shutdown the gRPC client:", e);
		}
	}
}

/**
 * Custom gRPC Client interceptor to inject headers
 */
class HeaderClientInterceptor implements ClientInterceptor {

	private final Metadata metadata;

	/**
	 * Constructor for a custom gRPC client interceptor.
	 * @param headers The headers to be sent on OpenTelemetry requests.
	 */
	HeaderClientInterceptor(final Map<String, String> headers) {
		metadata = new Metadata();
		headers.forEach((k, v) -> metadata.put(Metadata.Key.of(k, Metadata.ASCII_STRING_MARSHALLER), v));
	}

	@Override
	public <Q, R> ClientCall<Q, R> interceptCall(MethodDescriptor<Q, R> method, CallOptions callOptions, Channel next) {
		return new ForwardingClientCall.SimpleForwardingClientCall<Q, R>(next.newCall(method, callOptions)) {
			@Override
			public void start(final Listener<R> responseListener, final Metadata headers) {
				// Re-use cached headers and inject them into the gRPC request
				headers.merge(metadata);

				super.start(responseListener, headers);
			}
		};
	}
}
