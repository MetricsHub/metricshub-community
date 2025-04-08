package org.metricshub.engine.connector.deserializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.InvalidNullException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.identity.ConnectionType;
import org.metricshub.engine.connector.model.identity.Detection;

class DetectionDeserializerTest extends DeserializerTest {

	private static final String SUPERSEDES_ERROR_MSG = "The connector referenced by 'supersedes' cannot be empty.";

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/connector/detection/";
	}

	@Test
	/**
	 * Checks detection input properties
	 *
	 * @throws IOException
	 */
	void testDeserializeDetectionValues() throws IOException {
		final Connector detection = getConnector("detection");

		// Initialization tests
		assertNotNull(detection);

		assertNotNull(detection.getConnectorIdentity().getDetection());

		// appliesTo
		final Detection expected = Detection
			.builder()
			.appliesTo(Set.of(DeviceKind.values()))
			.connectionTypes(Set.of(ConnectionType.values()))
			.build();
		assertEquals(expected.getAppliesTo(), detection.getConnectorIdentity().getDetection().getAppliesTo());

		// connectionTypes
		assertEquals(expected.getConnectionTypes(), detection.getConnectorIdentity().getDetection().getConnectionTypes());

		// disableAutoDetection
		assertEquals(true, detection.getConnectorIdentity().getDetection().isDisableAutoDetection());

		// onLastResort
		assertEquals("enclosure", detection.getConnectorIdentity().getDetection().getOnLastResort());

		// supersedes
		var expectedSupersedes = detection.getConnectorIdentity().getDetection().getSupersedes();
		assertTrue(expectedSupersedes instanceof HashSet, "supersedes are expected to be a HashSet.");
		assertEquals(new HashSet<>(List.of("Connector1", "Connector2")), expectedSupersedes);
	}

	/*
	 * appliesTo
	 */

	@Test
	/**
	 * appliesTo: checks that an empty value is rejected
	 *
	 * @throws Exception
	 */
	void testDeserializeAppliesToEmpty() throws Exception {
		try {
			getConnector("appliesToEmpty");
			fail(IO_EXCEPTION_MSG);
		} catch (Exception e) {
			final String message = "'' is not a supported device kind.";
			checkMessage(e, message);
		}
	}

	@Test
	/**
	 * appliesTo: check a null value is rejected
	 *
	 * @throws Exception
	 */
	void testDeserializeAppliesToNull() throws Exception {
		try {
			getConnector("appliesToNull");
			fail(INVALID_NULL_EXCEPTION_MSG);
		} catch (InvalidNullException e) {
			final String message = "Invalid `null` value encountered for property \"appliesTo\"";
			checkMessage(e, message);
		}
	}

	@Test
	/**
	 * appliesTo: check an invalid value is rejected
	 *
	 * @throws Exception
	 */
	void testDeserializeAppliesToInvalid() throws Exception {
		try {
			getConnector("appliesToInvalid");
			fail(IO_EXCEPTION_MSG);
		} catch (Exception e) {
			checkMessage(e, "'unknownValue' is not a supported device kind.");
		}
	}

	@Test
	/**
	 * appliesTo: check a commented-out line is rejected
	 *
	 * @throws Exception
	 */
	void testDeserializeAppliesToCommentedOut() throws IOException {
		try {
			getConnector("appliesToCommentedOut");
			fail(MISMATCHED_EXCEPTION_MSG);
		} catch (MismatchedInputException e) {
			final String message = "Missing required creator property 'appliesTo' (index 3)";
			checkMessage(e, message);
		}
	}

	/*
	 * connectionTypes
	 */

	@Test
	/**
	 * connectionTypes: check that an empty value is defaulted to local
	 *
	 * @throws Exception
	 */
	void testDeserializeConnectionTypeEmpty() throws IOException {
		try {
			getConnector("connectionTypesEmpty");
			fail(IO_EXCEPTION_MSG);
		} catch (Exception e) {
			final String message = "ConnectionType must be a known connection type (local, remote)";
			checkMessage(e, message);
		}
	}

	/**
	 * Check that the parsing of the given connector is defaulted to local
	 * connection type
	 *
	 * @param connectorId
	 * @throws IOException
	 */
	private void testDefaultConnectionType(String connectorId) throws IOException {
		final Connector detection = getConnector(connectorId);

		assertEquals(
			Set.of(ConnectionType.LOCAL, ConnectionType.REMOTE),
			detection.getConnectorIdentity().getDetection().getConnectionTypes()
		);
	}

	@Test
	/**
	 * connectionTypes: check that a null value is defaulted to local
	 *
	 * @throws Exception
	 */
	void testDeserializeConnectionTypeNull() throws IOException {
		testDefaultConnectionType("connectionTypesNull");
	}

	@Test
	/**
	 * connectionTypes: check that an commented-out value is defaulted to local
	 * @throws IOException
	 *
	 */
	void testDeserializeConnectionTypeCommentedOut() throws IOException {
		testDefaultConnectionType("connectionTypesCommentedOut");
	}

	@Test
	/**
	 * connectionTypes: check an invalid value is rejected
	 *
	 * @throws Exception
	 */
	void testDeserializeConnectionTypeInvalid() throws Exception {
		try {
			getConnector("connectionTypesInvalid");
			fail(IO_EXCEPTION_MSG);
		} catch (Exception e) {
			final String message = "ConnectionType must be a known connection type (local, remote)";
			checkMessage(e, message);
		}
	}

	//
	// disableAutoDetection
	//

	@Test
	/**
	 * disableAutoDetection: check that an empty value is defaulted to false
	 *
	 * @throws IOException
	 *
	 */
	void testDeserializeDisableAutoDetectionEmpty() throws IOException {
		testDefaultDisableAutoDetection("disableAutoDetectionEmpty");
	}

	/**
	 * Check that the parsing of the given connector is defaulted to disableAutoDetection (false)
	 *
	 * @param connectorId
	 * @throws IOException
	 */
	private void testDefaultDisableAutoDetection(String connectorId) throws IOException {
		final Connector detection = getConnector(connectorId);

		assertEquals(false, detection.getConnectorIdentity().getDetection().isDisableAutoDetection());
	}

	@Test
	/**
	 * disableAutoDetection: check that a null value is defaulted to false
	 *
	 * @throws IOException
	 *
	 */
	void testDeserializeDisableAutoDetectionNull() throws IOException {
		testDefaultDisableAutoDetection("disableAutoDetectionNull");
	}

	@Test
	/**
	 * disableAutoDetection: check that an commented-out value is defaulted to false
	 *
	 * @throws IOException
	 *
	 */
	void testDeserializeDisableAutoDetectionCommentedOut() throws IOException {
		testDefaultDisableAutoDetection("disableAutoDetectionCommentedOut");
	}

	@Test
	/**
	 * disableAutoDetection: check an invalid value is rejected
	 *
	 * @throws Exception
	 */
	void testDeserializeDisableAutodetectionInvalid() throws Exception {
		try {
			getConnector("disableAutoDetectionInvalid");
			fail(INVALID_FORMAT_EXCEPTION_MSG);
		} catch (InvalidFormatException e) {
			final String message = "Cannot deserialize value of type `boolean` from String \"maybe\"";
			assertTrue(
				e.getMessage().contains(message),
				() ->
					"Expected exception should contain \"" + message + "\" but got the following error instead: " + e.getMessage()
			);
		}
	}

	//
	// supersedes
	//

	@Test
	/**
	 * supersedes: check an empty value is rejected
	 *
	 * @throws Exception
	 */
	void testDeserializeSupersedesEmpty() {
		try {
			getConnector("supersedesEmpty");
			fail(IO_EXCEPTION_MSG);
		} catch (Exception e) {
			String message = SUPERSEDES_ERROR_MSG;
			assertTrue(
				e.getMessage().contains(message),
				() ->
					"Expected exception should contain: " + message + " but got the following error instead: " + e.getMessage()
			);
		}
	}

	@Test
	/**
	 * supersedes: check a null value is just ignored and returns an empty value
	 *
	 * @throws Exception
	 */
	void testDeserializeSupersedesNull() throws IOException {
		final Connector detection = getConnector("supersedesNull");

		var supersedes = detection.getConnectorIdentity().getDetection().getSupersedes();

		assertTrue(supersedes instanceof HashSet, "supersedes are expected to be a HashSet.");
		assertEquals(Collections.emptySet(), supersedes);
	}

	@Test
	/**
	 * supersedes: check a commented-out line is just ignored and returns an empty value
	 *
	 * @throws Exception
	 */
	void testDeserializeSupersedesCommentedOut() throws IOException {
		final Connector detection = getConnector("supersedesCommentedOut");

		var supersedes = detection.getConnectorIdentity().getDetection().getSupersedes();

		assertTrue(supersedes instanceof HashSet, "supersedes are expected to be a HashSet.");
		assertEquals(Collections.emptySet(), supersedes);
	}

	@Test
	/**
	 * supersedes: check a null value in a list is rejected
	 *
	 * @throws Exception
	 */
	void testDeserializeSupersedesNullList() {
		try {
			getConnector("supersedesNullList");
			fail(IO_EXCEPTION_MSG);
		} catch (Exception e) {
			checkMessage(e, SUPERSEDES_ERROR_MSG);
		}
	}

	@Test
	/**
	 * supersedes: check a empty value in a list is rejected
	 *
	 * @throws Exception
	 */
	void testDeserializeSupersedesEmptyList() {
		try {
			getConnector("supersedesEmptyList");
			fail(IO_EXCEPTION_MSG);
		} catch (Exception e) {
			checkMessage(e, SUPERSEDES_ERROR_MSG);
		}
	}

	@Test
	//
	// onLastResort
	//
	/**
	 * onLastResort: check a commented-out line is ignored and returns a null value
	 *
	 * @throws IOException
	 *
	 */
	void testDeserializeonLastResortCommentedOut() throws IOException {
		final Connector detection = getConnector("onLastResortCommentedOut");

		assertNull(detection.getConnectorIdentity().getDetection().getOnLastResort());
	}

	@Test
	/**
	 * onLastResort: check an empty value triggers an error
	 *
	 * @throws IOException
	 *
	 */
	void testDeserializeonLastResortEmpty() throws IOException {
		try {
			getConnector("onLastResortEmpty");

			fail(INVALID_FORMAT_EXCEPTION_MSG);
		} catch (InvalidFormatException e) {
			String message = "Invalid blank value encountered for property 'onLastResort'.";
			checkMessage(e, message);
		}
	}

	@Test
	/**
	 * onLastResort: check a null value returns a null value
	 *
	 * @throws IOException
	 *
	 */
	void testDeserializeonLastResortNull() throws IOException {
		final Connector detection = getConnector("onLastResortNull");

		assertNull(detection.getConnectorIdentity().getDetection().getOnLastResort());
	}
}
