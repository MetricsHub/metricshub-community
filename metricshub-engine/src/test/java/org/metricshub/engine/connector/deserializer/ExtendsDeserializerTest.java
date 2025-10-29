package org.metricshub.engine.connector.deserializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.Connector;

class ExtendsDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/extends/";
	}

	@Test
	void testDeserializeExtends() throws IOException {
		final Connector connector = getConnector("extends");
		assertNotNull(connector);

		var extendsConnectors = connector.getExtendsConnectors();

		assertTrue(extendsConnectors instanceof LinkedHashSet, "extends are expected to be a LinkedHashSet.");

		// We want to keep the order declared in the YAML file
		// Later in the post parser code, we must keep the same order to perform merge operations
		assertEquals(new LinkedHashSet<>(List.of("Connector1", "Connector2")), extendsConnectors);
	}

	@Test
	void testDeserializeExtendsEmptyConnectorRefNotAccepted() {
		try {
			getConnector("extendsEmptyConnectorRef");
			Assertions.fail(IO_EXCEPTION_MSG);
		} catch (Exception e) {
			String message = "The connector referenced by 'extends' cannot be empty.";
			checkMessage(e, message);
		}
	}

	@Test
	void testDeserializeExtendsNullConnectorRefNotAccepted() {
		try {
			getConnector("extendsNullConnectorRef");
			Assertions.fail(IO_EXCEPTION_MSG);
		} catch (Exception e) {
			String message = "The connector referenced by 'extends' cannot be empty.";
			checkMessage(e, message);
		}
	}

	@Test
	void testDeserializeExtendsSingleEmptyValueNotAccepted() throws IOException {
		try {
			getConnector("extendsSingleEmptyValue");
			Assertions.fail(IO_EXCEPTION_MSG);
		} catch (Exception e) {
			String message = "The connector referenced by 'extends' cannot be empty.";
			checkMessage(e, message);
		}
	}

	@Test
	void testDeserializeExtendsSingleValueAcceptedAsLinkedHashSet() throws IOException {
		final Connector connector = getConnector("extendsSingleValue");
		var extendsConnectors = connector.getExtendsConnectors();

		assertEquals(new LinkedHashSet<>(List.of("Connector1")), extendsConnectors);
	}

	@Test
	void testDeserializeExtendsNullValueAcceptedAsEmptySet() throws IOException {
		final Connector connector = getConnector("extendsNullValue");
		var extendsConnectors = connector.getExtendsConnectors();

		assertEquals(Collections.emptySet(), extendsConnectors);
	}

	@Test
	void testDeserializeExtendsNonExistantAcceptedAsEmptySet() throws IOException {
		final Connector connector = getConnector("extendsNonExistant");
		var extendsConnectors = connector.getExtendsConnectors();

		assertEquals(Collections.emptySet(), extendsConnectors);
	}
}
