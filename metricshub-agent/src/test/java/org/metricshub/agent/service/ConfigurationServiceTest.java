package org.metricshub.agent.service;

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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.configuration.YamlConfigurationProvider;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.extension.IConfigurationProvider;
import org.mockito.InOrder;

class ConfigurationServiceTest {

	private static final String UI_CONFIG_FILENAME = "metricshub-ui.yaml";

	@TempDir
	Path tempDir;

	/**
	 * Builds a {@link ConfigurationService} targeting the test's temporary directory.
	 *
	 * @return a new {@link ConfigurationService}
	 */
	private ConfigurationService newConfigurationService() {
		return ConfigurationService.builder().withConfigDirectory(tempDir).build();
	}

	@Test
	void testUiConfigurationOverridesEveryOtherYamlFile() throws IOException {
		Files.writeString(
			tempDir.resolve("metricshub.yaml"),
			"""
			loggerLevel: debug
			resources:
			  server-1:
			    attributes:
			      host.name: server-1
			      host.type: linux
			"""
		);
		// Sorts after metricshub-ui.yaml in a directory listing: without the deferred pass it
		// would be merged after the UI file and would win.
		Files.writeString(
			tempDir.resolve("zzz-extra.yaml"),
			"""
			loggerLevel: info
			extraKey: fromZzz
			"""
		);
		Files.writeString(
			tempDir.resolve(UI_CONFIG_FILENAME),
			"""
			loggerLevel: error
			resources:
			  server-1:
			    attributes:
			      host.type: windows
			  ui-host:
			    attributes:
			      host.name: ui-host
			"""
		);

		final ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withConfigurationProviderExtensions(List.of(new YamlConfigurationProvider()))
			.build();

		final JsonNode config = newConfigurationService().loadConfiguration(extensionManager);

		assertEquals("error", config.get("loggerLevel").asText(), "metricshub-ui.yaml must win over every YAML file");
		assertEquals("fromZzz", config.get("extraKey").asText(), "Non-conflicting keys must be preserved");

		final JsonNode server1Attributes = config.get("resources").get("server-1").get("attributes");
		assertEquals(
			"windows",
			server1Attributes.get("host.type").asText(),
			"metricshub-ui.yaml must override nested resource attributes"
		);
		assertEquals(
			"server-1",
			server1Attributes.get("host.name").asText(),
			"Attributes not redefined by metricshub-ui.yaml must be preserved"
		);
		assertTrue(config.get("resources").has("ui-host"), "Resources added by metricshub-ui.yaml must be present");
	}

	@Test
	void testUiConfigurationAppliedAfterAllProviders() throws IOException {
		Files.writeString(tempDir.resolve(UI_CONFIG_FILENAME), "loggerLevel: error");

		// Regular fragments overriding the same key, one provider discovered before the YAML
		// provider and one after: the UI file must win over both.
		final IConfigurationProvider leadingProvider = staticProvider("loggerLevel: debug\nleadingKey: leading");
		final IConfigurationProvider trailingProvider = staticProvider("loggerLevel: warn\ntrailingKey: trailing");

		final ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withConfigurationProviderExtensions(List.of(leadingProvider, new YamlConfigurationProvider(), trailingProvider))
			.build();

		final JsonNode config = newConfigurationService().loadConfiguration(extensionManager);

		assertEquals(
			"error",
			config.get("loggerLevel").asText(),
			"metricshub-ui.yaml must be applied after the regular fragments of all providers"
		);
		assertEquals("leading", config.get("leadingKey").asText(), "Leading provider fragment must be merged");
		assertEquals("trailing", config.get("trailingKey").asText(), "Trailing provider fragment must be merged");
	}

	@Test
	void testUiConfigurationWinsOverOtherDeferredFragments() throws IOException {
		Files.writeString(tempDir.resolve(UI_CONFIG_FILENAME), "loggerLevel: error");

		// Providers also deferring fragments, one discovered before the YAML provider and
		// one after: the UI file must still be merged last within the loadLast pass.
		final IConfigurationProvider leadingDeferred = deferredProvider("loggerLevel: debug\nleadingKey: leading");
		final IConfigurationProvider trailingDeferred = deferredProvider("loggerLevel: warn\ntrailingKey: trailing");

		final ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withConfigurationProviderExtensions(
				List.of(leadingDeferred, new YamlConfigurationProvider(), trailingDeferred)
			)
			.build();

		final JsonNode config = newConfigurationService().loadConfiguration(extensionManager);

		assertEquals(
			"error",
			config.get("loggerLevel").asText(),
			"metricshub-ui.yaml must win even over other deferred fragments"
		);
		assertEquals("leading", config.get("leadingKey").asText(), "Leading deferred fragment must be merged");
		assertEquals("trailing", config.get("trailingKey").asText(), "Trailing deferred fragment must be merged");
	}

	@Test
	void testLoadLastInvokedAfterAllRegularLoads() {
		final IConfigurationProvider firstProvider = mock(IConfigurationProvider.class);
		final IConfigurationProvider secondProvider = mock(IConfigurationProvider.class);

		when(firstProvider.load(tempDir)).thenReturn(List.of(JsonNodeFactory.instance.objectNode().put("key", "one")));
		when(secondProvider.load(tempDir)).thenReturn(List.of(JsonNodeFactory.instance.objectNode().put("key", "two")));
		when(firstProvider.loadLast(tempDir))
			.thenReturn(List.of(JsonNodeFactory.instance.objectNode().put("key", "last")));
		when(secondProvider.loadLast(tempDir)).thenReturn(List.of());

		final ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withConfigurationProviderExtensions(List.of(firstProvider, secondProvider))
			.build();

		final JsonNode config = newConfigurationService().loadConfiguration(extensionManager);

		final InOrder order = inOrder(firstProvider, secondProvider);
		order.verify(firstProvider).load(tempDir);
		order.verify(secondProvider).load(tempDir);
		order.verify(firstProvider).loadLast(tempDir);
		order.verify(secondProvider).loadLast(tempDir);

		assertEquals("last", config.get("key").asText(), "Deferred fragments must be merged after regular fragments");
	}

	@Test
	void testLoadConfigurationToleratesNullFragments() {
		final IConfigurationProvider nullProvider = mock(IConfigurationProvider.class);
		when(nullProvider.load(tempDir)).thenReturn(null);
		when(nullProvider.loadLast(tempDir)).thenReturn(null);

		final ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withConfigurationProviderExtensions(List.of(nullProvider))
			.build();

		final JsonNode config = newConfigurationService().loadConfiguration(extensionManager);

		assertTrue(config.isObject(), "Configuration must still be an object when providers return null");
		assertTrue(config.isEmpty(), "Configuration must be empty when providers return null");
	}

	/**
	 * Creates a provider returning the given YAML content as its single regular fragment.
	 *
	 * @param yaml the YAML content of the fragment
	 * @return a stub {@link IConfigurationProvider}
	 */
	private static IConfigurationProvider staticProvider(final String yaml) {
		return new IConfigurationProvider() {
			@Override
			public Collection<JsonNode> load(final Path path) {
				return List.of(parseYaml(yaml));
			}

			@Override
			public Set<String> getFileExtensions() {
				return Set.of();
			}
		};
	}

	/**
	 * Creates a provider returning the given YAML content as its single deferred fragment,
	 * with the default {@code loadLastOrder}.
	 *
	 * @param yaml the YAML content of the fragment
	 * @return a stub {@link IConfigurationProvider}
	 */
	private static IConfigurationProvider deferredProvider(final String yaml) {
		return new IConfigurationProvider() {
			@Override
			public Collection<JsonNode> load(final Path path) {
				return List.of();
			}

			@Override
			public Collection<JsonNode> loadLast(final Path path) {
				return List.of(parseYaml(yaml));
			}

			@Override
			public Set<String> getFileExtensions() {
				return Set.of();
			}
		};
	}

	/**
	 * Parses the given YAML content into a {@link JsonNode}.
	 *
	 * @param yaml the YAML content
	 * @return the parsed node
	 */
	private static JsonNode parseYaml(final String yaml) {
		try {
			return JsonHelper.buildYamlMapper().readTree(yaml);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
