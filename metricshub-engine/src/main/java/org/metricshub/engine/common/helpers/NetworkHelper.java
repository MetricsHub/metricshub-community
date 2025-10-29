package org.metricshub.engine.common.helpers;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2025 MetricsHub
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

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Helper class to retrieve network information.
 */
@Slf4j
public class NetworkHelper {

	/**
	 * Pattern for IPv6 address enclosed in square brackets ('[' and ']') as specified by
	 * <a href="https://www.ietf.org/rfc/rfc2732.txt">RFC 2732</a>
	 */
	private static final Pattern IPV6_ENCLOSED_IN_SQUARE_BRACKETS_PATTERN = Pattern.compile("^\\[.*\\]$");

	/**
	 * Typical localhost names, which we should figure out immediately
	 */
	private static final Set<String> TYPICAL_LOCALHOST_HOSTNAMES = Set.of(
		"localhost",
		"127.0.0.1",
		"::1",
		"0:0:0:0:0:0:0:1",
		"0000:0000:0000:0000:0000:0000:0000:0001"
	);

	private NetworkHelper() {}

	/**
	 * Check whether the given hostname is a localhost.
	 *
	 * @param hostname The hostname to check.
	 * @return {@code true} if the hostname is a localhost, {@code false} otherwise.
	 */
	public static boolean isLocalhost(final String hostname) {
		// Empty or null hostname is assumed local
		if (hostname == null || hostname.isBlank()) {
			return true;
		}

		// Obvious case
		if (TYPICAL_LOCALHOST_HOSTNAMES.contains(hostname.toLowerCase())) {
			return true;
		}

		// Try to resolve the provided hostname
		InetAddress inetAddress = null;
		try {
			inetAddress = InetAddress.getByName(hostname);
		} catch (UnknownHostException e) {
			log.warn(
				"Hostname {} - Could not resolve the hostname to a valid IP address. The host is considered remote.",
				hostname
			);
			return false;
		}

		if (inetAddress == null) {
			log.warn(
				"Hostname {} - Could not resolve the hostname to a valid IP address. The host is considered remote.",
				hostname
			);
			return false;
		}

		// Check if the address is a valid local or loop back
		if (inetAddress.isAnyLocalAddress() || inetAddress.isLoopbackAddress()) {
			return true;
		}

		// Check if the address is defined on any interface
		try {
			return NetworkInterface.getByInetAddress(inetAddress) != null;
		} catch (SocketException e) {
			log.warn(
				"Hostname {} - Could not find a network interface associated to this IP address. The network interface is considered remote.",
				hostname
			);
			log.debug("Hostname {} - Exception while checking network interfaces: ", hostname, e);
			return false;
		}
	}

	/**
	 * Resolve the IP address of the given hostname.
	 *
	 * @param hostname The hostname.
	 * @return The IP address of the hostname, or {@code null} if not resolved.
	 */
	public static String resolveDns(final String hostname) {
		String ipAddress = null;
		InetAddress inetAddress = null;

		if (hostname != null && !hostname.isBlank()) {
			try {
				inetAddress = InetAddress.getByName(hostname);

				if (inetAddress != null) {
					ipAddress = inetAddress.getHostAddress();
				}
			} catch (UnknownHostException e) {
				log.warn("Could not resolve {} into an IP address.", hostname);
				log.debug("UnknownHostException: ", e);
			}
		}

		return ipAddress;
	}

	/**
	 * Get the fully qualified domain name (FQDN) of the given hostname.
	 *
	 * @param hostname The hostname.
	 * @return The FQDN of the hostname, or the original hostname if not resolved.
	 */
	public static String getFqdn(final String hostname) {
		if (hostname != null && !hostname.isBlank()) {
			try {
				final InetAddress inetAddress = InetAddress.getByName(hostname);
				if (inetAddress != null) {
					return inetAddress.getCanonicalHostName();
				}
			} catch (UnknownHostException e) {
				log.error(
					"Hostname {} - Could not resolve the hostname to a valid IP address. Cannot retrieve FQDN. Using hostname as FQDN.",
					hostname
				);
				return hostname;
			}
		}

		return hostname;
	}

	/**
	 * Constructs a URL from the specified protocol, hostname or IP address, and port.
	 * It properly formats the URL for both IPv4 and IPv6 addresses.
	 *
	 * @param protocol     The communication protocol (e.g., "http", "https")
	 * @param hostnameOrIp The host name or IP address, IPv4 or IPv6
	 * @param portNumber   The port number
	 * @return a URL object constructed from the provided parameters
	 * @throws MalformedURLException if no protocol is specified, or an unknown protocol is found
	 * @throws UnknownHostException  if the IP address or hostname cannot be resolved
	 * @throws URISyntaxException    if the URI string constructed from the input parameters is syntactically incorrect
	 */
	public static URL createUrl(@NonNull String protocol, @NonNull String hostnameOrIp, @NonNull Integer portNumber)
		throws MalformedURLException, UnknownHostException, URISyntaxException {
		// Detect if the host is an IPv6 address
		final InetAddress address = InetAddress.getByName(hostnameOrIp);

		final String uriHost;
		if (
			// CHECKSTYLE:OFF
			address instanceof java.net.Inet6Address &&
			!IPV6_ENCLOSED_IN_SQUARE_BRACKETS_PATTERN.matcher(hostnameOrIp).matches()
			// CHECKSTYLE:ON
		) {
			uriHost = "[" + hostnameOrIp + "]";
		} else {
			uriHost = hostnameOrIp;
		}

		// Format the URI and convert to URL
		return new URI(String.format("%s://%s:%d", protocol, uriHost, portNumber)).toURL();
	}
}
