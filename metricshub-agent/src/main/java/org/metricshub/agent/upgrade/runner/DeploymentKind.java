package org.metricshub.agent.upgrade.runner;

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

/**
 * How MetricsHub was deployed on this host. Only package-manager based deployments
 * ({@code DEB}, {@code RPM}, {@code MSI}) support automatic in-place upgrades.
 */
public enum DeploymentKind {
	/**
	 * Installed through a Debian package.
	 */
	DEB(".deb"),
	/**
	 * Installed through an RPM package.
	 */
	RPM(".rpm"),
	/**
	 * Installed through a Windows MSI package.
	 */
	MSI(".msi"),
	/**
	 * Extracted from a tar.gz/zip application image: no package manager, no automatic upgrade.
	 */
	ARCHIVE(null),
	/**
	 * Running inside a container image: upgrades are image redeployments, not package installs.
	 */
	DOCKER(null);

	private final String packageExtension;

	DeploymentKind(final String packageExtension) {
		this.packageExtension = packageExtension;
	}

	/**
	 * Returns the package file extension expected for this deployment kind.
	 *
	 * @return the extension including the leading dot, or {@code null} when in-place upgrades are
	 *         not supported
	 */
	public String getPackageExtension() {
		return packageExtension;
	}

	/**
	 * Indicates whether this deployment kind supports automatic in-place upgrades.
	 *
	 * @return {@code true} for package-manager based deployments
	 */
	public boolean isUpgradable() {
		return packageExtension != null;
	}
}
