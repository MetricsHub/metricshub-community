package org.metricshub.opamp.client.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.opamp.proto.AgentDescription;
import org.metricshub.opamp.proto.AgentToServer;
import org.metricshub.opamp.proto.AnyValue;
import org.metricshub.opamp.proto.ComponentHealth;
import org.metricshub.opamp.proto.KeyValue;
import org.metricshub.opamp.proto.PackageStatuses;

class AgentToServerAssemblerTest {

	private static final ByteString INSTANCE_UID = ByteString.copyFromUtf8("0123456789abcdef");
	private static final long CAPABILITIES = 0x811L;

	private AgentToServerAssembler assembler;

	@BeforeEach
	void setUp() {
		assembler = new AgentToServerAssembler(INSTANCE_UID, CAPABILITIES);
	}

	private static AgentDescription description(final String version) {
		return AgentDescription.newBuilder()
			.addIdentifyingAttributes(
				KeyValue.newBuilder()
					.setKey("service.version")
					.setValue(AnyValue.newBuilder().setStringValue(version).build())
					.build()
			)
			.build();
	}

	private static ComponentHealth health(final boolean healthy) {
		return ComponentHealth.newBuilder().setHealthy(healthy).build();
	}

	@Test
	void firstMessageShouldCarryFullState() {
		assembler.setAgentDescription(description("1.0.0"));
		assembler.setHealth(health(true));
		assembler.setPackageStatusesSupplier(PackageStatuses::getDefaultInstance);

		final AgentToServer message = assembler.assemble();

		assertEquals(INSTANCE_UID, message.getInstanceUid());
		assertEquals(1, message.getSequenceNum());
		assertEquals(CAPABILITIES, message.getCapabilities());
		assertTrue(message.hasAgentDescription());
		assertTrue(message.hasHealth());
		assertTrue(message.hasPackageStatuses());
	}

	@Test
	void unchangedFieldsShouldBeOmittedAfterCommit() {
		assembler.setAgentDescription(description("1.0.0"));
		assembler.setHealth(health(true));

		assembler.assemble();
		assembler.commit();
		final AgentToServer second = assembler.assemble();

		assertFalse(second.hasAgentDescription());
		assertFalse(second.hasHealth());
		assertEquals(2, second.getSequenceNum());
	}

	@Test
	void changedFieldsShouldBeIncluded() {
		assembler.setAgentDescription(description("1.0.0"));
		assembler.setHealth(health(true));
		assembler.assemble();
		assembler.commit();

		assembler.setHealth(health(false));
		final AgentToServer message = assembler.assemble();

		assertFalse(message.hasAgentDescription());
		assertTrue(message.hasHealth());
		assertFalse(message.getHealth().getHealthy());
	}

	@Test
	void uncommittedMessageShouldBeResentInFull() {
		assembler.setAgentDescription(description("1.0.0"));
		assembler.assemble();
		// No commit: the send failed, and requestFullState() is expected from the client
		assembler.requestFullState();

		final AgentToServer retry = assembler.assemble();

		assertTrue(retry.hasAgentDescription());
		assertEquals(2, retry.getSequenceNum());
	}

	@Test
	void requestFullStateShouldResendEverything() {
		assembler.setAgentDescription(description("1.0.0"));
		assembler.setHealth(health(true));
		assembler.assemble();
		assembler.commit();

		assembler.requestFullState();
		final AgentToServer message = assembler.assemble();

		assertTrue(message.hasAgentDescription());
		assertTrue(message.hasHealth());
	}

	@Test
	void commitShouldClearTheFullStateRequestOnlyWhenHonored() {
		assembler.setAgentDescription(description("1.0.0"));
		assembler.assemble();
		assembler.commit();
		assembler.requestFullState();
		assembler.assemble();
		assembler.commit();

		final AgentToServer afterFullState = assembler.assemble();

		assertFalse(afterFullState.hasAgentDescription());
	}

	@Test
	void disconnectMessageShouldCarryIdentityAndDisconnect() {
		assembler.assemble();

		final AgentToServer disconnect = assembler.assembleDisconnect();

		assertEquals(INSTANCE_UID, disconnect.getInstanceUid());
		assertEquals(2, disconnect.getSequenceNum());
		assertTrue(disconnect.hasAgentDisconnect());
	}

	@Test
	void setInstanceUidShouldApplyToSubsequentMessages() {
		final ByteString newUid = ByteString.copyFromUtf8("fedcba9876543210");
		assembler.setInstanceUid(newUid);

		assertEquals(newUid, assembler.assemble().getInstanceUid());
		assertEquals(newUid, assembler.getInstanceUid());
	}
}
