package org.metricshub.opamp.client.http;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.opamp.client.OpampClientSettings;

class OpampHttpTransportTest {

	@TempDir
	Path tempDir;

	@Test
	void eachPostShouldCarryTheSuppliersCurrentHeaders() throws Exception {
		final List<String> received = Collections.synchronizedList(new ArrayList<>());
		final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/opamp", exchange -> {
			received.add(exchange.getRequestHeaders().getFirst("Authorization"));
			exchange.getRequestBody().readAllBytes();
			exchange.sendResponseHeaders(200, -1);
			exchange.close();
		});
		server.start();
		final AtomicInteger token = new AtomicInteger();
		final OpampClientSettings settings = OpampClientSettings.builder()
			.withEndpoint(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/opamp"))
			.withHeadersSupplier(() -> Map.of("Authorization", "Bearer token-" + token.incrementAndGet()))
			.withInstanceUidFile(tempDir.resolve("instance-uid"))
			.build();
		try (OpampHttpTransport transport = new OpampHttpTransport(settings)) {
			transport.send(new byte[0]);
			transport.send(new byte[0]);
		} finally {
			server.stop(0);
		}

		assertEquals(List.of("Bearer token-1", "Bearer token-2"), received);
	}
}
