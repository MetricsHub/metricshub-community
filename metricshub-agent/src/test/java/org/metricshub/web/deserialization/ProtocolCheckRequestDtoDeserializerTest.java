package org.metricshub.web.deserialization;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2026 MetricsHub
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.extension.oscommand.OsCommandExtension;
import org.metricshub.extension.oscommand.SshConfiguration;
import org.metricshub.web.dto.uiconfig.ProtocolCheckRequestDto;

class ProtocolCheckRequestDtoDeserializerTest {

	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		final ExtensionManager extensionManager = ExtensionManager.builder()
			.withProtocolExtensions(List.of(new OsCommandExtension()))
			.build();
		objectMapper = new ObjectMapper();
		final InjectableValues.Std injectableValues = new InjectableValues.Std();
		injectableValues.addValue(ExtensionManager.class, extensionManager);
		injectableValues.addValue(ExtensionManager.class.getName(), extensionManager);
		objectMapper.setInjectableValues(injectableValues);
	}

	@Test
	void testDeserializeNullBodyReturnsEmptyDto() throws Exception {
		final ProtocolCheckRequestDto request = objectMapper.readValue("null", ProtocolCheckRequestDto.class);

		assertNotNull(request);
		assertTrue(request.getProtocolConfig().isEmpty());
	}

	@Test
	void testDeserializeFlatProtocolConfig() throws Exception {
		final String json = """
			{
			  "hostname": "server1",
			  "protocol": "ssh",
			  "protocolConfig": {
			    "username": "admin",
			    "port": 22
			  }
			}
			""";

		final ProtocolCheckRequestDto request = objectMapper.readValue(json, ProtocolCheckRequestDto.class);

		assertEquals("server1", request.getHostname());
		assertEquals("ssh", request.getProtocol());
		assertEquals(1, request.getProtocolConfig().size());
		final SshConfiguration ssh = (SshConfiguration) request.getProtocolConfig().get("ssh");
		assertNotNull(ssh);
		assertEquals("admin", ssh.getUsername());
		assertEquals(22, ssh.getPort());
	}

	@Test
	void testDeserializeProtocolKeyedConfig() throws Exception {
		final String json = """
			{
			  "hostname": "server2",
			  "protocol": "ssh",
			  "protocolConfig": {
			    "ssh": {
			      "username": "root",
			      "port": 2222
			    }
			  }
			}
			""";

		final ProtocolCheckRequestDto request = objectMapper.readValue(json, ProtocolCheckRequestDto.class);

		assertEquals("server2", request.getHostname());
		assertEquals("ssh", request.getProtocol());
		final SshConfiguration ssh = (SshConfiguration) request.getProtocolConfig().get("ssh");
		assertNotNull(ssh);
		assertEquals("root", ssh.getUsername());
		assertEquals(2222, ssh.getPort());
	}
}
