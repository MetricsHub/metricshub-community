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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.extension.oscommand.OsCommandExtension;
import org.metricshub.extension.oscommand.SshConfiguration;

class ProtocolConfigurationMapsTest {

	private static final ObjectMapper JSON = new ObjectMapper();

	private ExtensionManager extensionManager;

	@BeforeEach
	void setUp() {
		extensionManager = ExtensionManager.builder().withProtocolExtensions(List.of(new OsCommandExtension())).build();
	}

	@Test
	void testFromJsonNodeReturnsEmptyMapForNullOrMissingNode() {
		assertTrue(ProtocolConfigurationMaps.fromJsonNode(null, "ssh", extensionManager).isEmpty());
		assertTrue(
			ProtocolConfigurationMaps.fromJsonNode(JsonNodeFactory.instance.nullNode(), "ssh", extensionManager).isEmpty()
		);
		assertTrue(
			ProtocolConfigurationMaps.fromJsonNode(JsonNodeFactory.instance.missingNode(), "ssh", extensionManager).isEmpty()
		);
	}

	@Test
	void testFromJsonNodeReturnsEmptyMapWhenExtensionManagerIsNull() throws Exception {
		final var configNode = JSON.readTree("{\"username\":\"admin\",\"port\":22}");
		assertTrue(ProtocolConfigurationMaps.fromJsonNode(configNode, "ssh", null).isEmpty());
	}

	@Test
	void testFromJsonNodeParsesFlatInlineFieldsUsingProtocolHint() throws Exception {
		final var configNode = JSON.readTree("{\"username\":\"admin\",\"port\":22}");

		final Map<String, IConfiguration> result = ProtocolConfigurationMaps.fromJsonNode(
			configNode,
			"ssh",
			extensionManager
		);

		assertEquals(1, result.size());
		final IConfiguration configuration = result.get("ssh");
		assertNotNull(configuration);
		assertTrue(configuration instanceof SshConfiguration);
		assertEquals("admin", ((SshConfiguration) configuration).getUsername());
		assertEquals(22, ((SshConfiguration) configuration).getPort());
	}

	@Test
	void testFromJsonNodeParsesProtocolKeyedShape() throws Exception {
		final var configNode = JSON.readTree("{\"ssh\":{\"username\":\"root\",\"port\":2222}}");

		final Map<String, IConfiguration> result = ProtocolConfigurationMaps.fromJsonNode(
			configNode,
			"ssh",
			extensionManager
		);

		assertEquals(1, result.size());
		final SshConfiguration ssh = (SshConfiguration) result.get("ssh");
		assertNotNull(ssh);
		assertEquals("root", ssh.getUsername());
		assertEquals(2222, ssh.getPort());
	}

	@Test
	void testFromJsonNodeIgnoresUnknownProtocolKeys() throws Exception {
		final var configNode = JSON.readTree("{\"unknown\":{\"field\":\"value\"}}");

		final Map<String, IConfiguration> result = ProtocolConfigurationMaps.fromJsonNode(
			configNode,
			null,
			extensionManager
		);

		assertTrue(result.isEmpty());
	}

	@Test
	void testResolveForProtocolMatchesCaseInsensitively() {
		final SshConfiguration ssh = SshConfiguration.sshConfigurationBuilder().username("admin").build();
		final Map<String, IConfiguration> protocolConfig = Map.of("ssh", ssh);

		assertEquals(ssh, ProtocolConfigurationMaps.resolveForProtocol("SSH", protocolConfig));
	}

	@Test
	void testResolveForProtocolReturnsSingleEntryWhenProtocolHintMissing() {
		final SshConfiguration ssh = SshConfiguration.sshConfigurationBuilder().username("admin").build();
		final Map<String, IConfiguration> protocolConfig = Map.of("ssh", ssh);

		assertEquals(ssh, ProtocolConfigurationMaps.resolveForProtocol(null, protocolConfig));
	}

	@Test
	void testResolveForProtocolReturnsNullForAmbiguousMap() {
		final SshConfiguration ssh = SshConfiguration.sshConfigurationBuilder().username("admin").build();
		final Map<String, IConfiguration> protocolConfig = Map.of(
			"ssh",
			ssh,
			"http",
			SshConfiguration.sshConfigurationBuilder().username("other").build()
		);

		assertNull(ProtocolConfigurationMaps.resolveForProtocol(null, protocolConfig));
		assertNull(ProtocolConfigurationMaps.resolveForProtocol("wmi", protocolConfig));
	}

	@Test
	void testResolveForProtocolReturnsNullForEmptyMap() {
		assertNull(ProtocolConfigurationMaps.resolveForProtocol("ssh", Map.of()));
		assertNull(ProtocolConfigurationMaps.resolveForProtocol("ssh", null));
	}
}
