package org.metricshub.agent.fleet;

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

import java.io.IOException;
import java.nio.file.Path;
import org.metricshub.agent.security.PasswordEncrypt;
import org.metricshub.opamp.client.state.InstanceUidStore;
import org.metricshub.opamp.client.state.UuidV7;

/**
 * The persistent identity of this agent toward the fleet, shared by every fleet channel (OpAMP, M8B
 * tunnel) so the fleet sees one agent whichever channel reports.
 * <p>
 * The uid is a UUIDv7 persisted in the MetricsHub {@code security} directory, which survives upgrades.
 * </p>
 */
public final class AgentInstanceUid {

	/**
	 * Name of the file holding the uid. Kept for compatibility with agents that created it through
	 * OpAMP before the M8B tunnel existed.
	 */
	public static final String FILENAME = "opamp-instance-uid";

	private AgentInstanceUid() {}

	/**
	 * @return the file holding the uid, next to the MetricsHub keystore
	 */
	public static Path file() {
		return PasswordEncrypt.getKeyStoreFile(true).toPath().toAbsolutePath().getParent().resolve(FILENAME);
	}

	/**
	 * Loads the uid from {@link #file()}, creating it on first use.
	 *
	 * @return the canonical UUID string
	 * @throws IOException when the file cannot be read or created
	 */
	public static String loadOrCreate() throws IOException {
		return loadOrCreate(file());
	}

	/**
	 * Loads the uid from the given file, creating it on first use.
	 *
	 * @param file the uid file
	 * @return the canonical UUID string
	 * @throws IOException when the file cannot be read or created
	 */
	public static String loadOrCreate(final Path file) throws IOException {
		return UuidV7.toCanonicalString(new InstanceUidStore(file).loadOrCreate());
	}
}
