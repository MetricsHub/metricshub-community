package org.metricshub.engine.common.helpers;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2024 Sentry Software
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

import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Helper class for handling the local operating system.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LocalOsHandler {

	/**
	 * Represents the Windows operating system.
	 */
	public static final ILocalOs WINDOWS = new Windows();
	/**
	 * Represents the Linux operating system.
	 */
	public static final ILocalOs LINUX = new Linux();
	/**
	 * Represents the Sun operating system.
	 */
	public static final ILocalOs SUN = new Sun();
	/**
	 * Represents the HP-UX operating system.
	 */
	public static final ILocalOs HP = new Hp();
	/**
	 * Represents the Solaris operating system.
	 */
	public static final ILocalOs SOLARIS = new Solaris();
	/**
	 * Represents the AIX operating system.
	 */
	public static final ILocalOs AIX = new Aix();
	/**
	 * Represents the FreeBSD operating system.
	 */
	public static final ILocalOs FREE_BSD = new FreeBsd();
	/**
	 * Represents the OpenBSD operating system.
	 */
	public static final ILocalOs OPEN_BSD = new OpenBsd();
	/**
	 * Represents the NetBSD operating system.
	 */
	public static final ILocalOs NET_BSD = new NetBsd();
	/**
	 * Represents the macOS operating system.
	 */
	public static final ILocalOs MAC_OS_X = new MacOsx();

	private static final List<ILocalOs> OS_LIST = List.of(
		WINDOWS,
		LINUX,
		AIX,
		SUN,
		HP,
		MAC_OS_X,
		SOLARIS,
		FREE_BSD,
		OPEN_BSD,
		NET_BSD
	);

	@Getter
	private static final Optional<ILocalOs> OS = detectOs();

	private static final boolean IS_WINDOWS = OS.isPresent() && OS.get().equals(WINDOWS);

	/**
	 * Detect the current Local OS.
	 * @return An optional with the current local OS. Empty if not determined.
	 */
	static Optional<ILocalOs> detectOs() {
		return getSystemOsName()
			.map(String::toLowerCase)
			.map(name -> OS_LIST.stream().filter(os -> name.startsWith(os.getOsTag())).findFirst().orElse(null));
	}

	/**
	 * Check if the Local OS is a Windows.
	 *
	 * @return true if Windows false otherwise.
	 */
	public static boolean isWindows() {
		return IS_WINDOWS;
	}

	/**
	 * Get the OS Name from the System.
	 * @return An Optional of the OS name. Empty if not defined.
	 */
	public static Optional<String> getSystemOsName() {
		return Optional.ofNullable(System.getProperty("os.name"));
	}

	/**
	 * Get the OS Version from the System.
	 * @return An Optional of the OS version. Empty if not defined.
	 */
	public static Optional<String> getSystemOsVersion() {
		return Optional.ofNullable(System.getProperty("os.version"));
	}

	/**
	 * Visitor interface for local operating system types.
	 */
	public interface ILocalOsVisitor {
		/**
		 * Visit method for Windows operating system.
		 *
		 * @param os Windows operating system instance
		 */
		void visit(Windows os);
		/**
		 * Visit method for Linux operating system.
		 *
		 * @param os Linux operating system instance
		 */
		void visit(Linux os);
		/**
		 * Visit method for Sun operating system.
		 *
		 * @param os Sun operating system instance
		 */
		void visit(Sun os);
		/**
		 * Visit method for HP-UX operating system.
		 *
		 * @param os HP-UX operating system instance
		 */
		void visit(Hp os);
		/**
		 * Visit method for Solaris operating system.
		 *
		 * @param os Solaris operating system instance
		 */
		void visit(Solaris os);
		/**
		 * Visit method for AIX operating system.
		 *
		 * @param os AIX operating system instance
		 */
		void visit(Aix os);
		/**
		 * Visit method for FreeBSD operating system.
		 *
		 * @param os FreeBSD operating system instance
		 */
		void visit(FreeBsd os);
		/**
		 * Visit method for OpenBSD operating system.
		 *
		 * @param os OpenBSD operating system instance
		 */
		void visit(OpenBsd os);
		/**
		 * Visit method for NetBSD operating system.
		 *
		 * @param os NetBSD operating system instance
		 */
		void visit(NetBsd os);
		/**
		 * Visit method for macOS operating system.
		 *
		 * @param os macOS operating system instance
		 */
		void visit(MacOsx os);
	}

	/**
	 * Abstract base class representing a local operating system.
	 */
	@Getter
	public abstract static class ILocalOs {

		protected String osTag;
		protected boolean unix;

		/**
		 * Accepts a visitor for handling different types of local operating systems.
		 *
		 * @param visitor The visitor to accept.
		 */
		public abstract void accept(ILocalOsVisitor visitor);
	}

	/**
	 * Represents the Windows operating system.
	 */
	public static class Windows extends ILocalOs {

		Windows() {
			osTag = "windows";
			unix = false;
		}

		@Override
		public void accept(final ILocalOsVisitor visitor) {
			visitor.visit(this);
		}
	}

	/**
	 * Represents the Linux operating system.
	 */
	public static class Linux extends ILocalOs {

		Linux() {
			osTag = "linux";
			unix = true;
		}

		@Override
		public void accept(final ILocalOsVisitor visitor) {
			visitor.visit(this);
		}
	}

	/**
	 * Represents the Sun operating system.
	 */
	public static class Sun extends ILocalOs {

		Sun() {
			osTag = "sunos";
			unix = true;
		}

		@Override
		public void accept(final ILocalOsVisitor visitor) {
			visitor.visit(this);
		}
	}

	/**
	 * Represents the HP-UX operating system.
	 */
	public static class Hp extends ILocalOs {

		Hp() {
			osTag = "hp-ux";
			unix = true;
		}

		@Override
		public void accept(final ILocalOsVisitor visitor) {
			visitor.visit(this);
		}
	}

	/**
	 * Represents the Solaris operating system.
	 */
	public static class Solaris extends ILocalOs {

		Solaris() {
			osTag = "solaris";
			unix = true;
		}

		@Override
		public void accept(final ILocalOsVisitor visitor) {
			visitor.visit(this);
		}
	}

	/**
	 * Represents the Aix operating system.
	 */
	public static class Aix extends ILocalOs {

		Aix() {
			osTag = "aix";
			unix = true;
		}

		@Override
		public void accept(final ILocalOsVisitor visitor) {
			visitor.visit(this);
		}
	}

	@Getter
	private abstract static class BsdOs extends ILocalOs {

		private final boolean bsd;

		BsdOs() {
			bsd = true;
			unix = true;
		}
	}

	/**
	 * Represents the FreeBsd operating system.
	 */
	public static class FreeBsd extends BsdOs {

		FreeBsd() {
			super();
			osTag = "freebsd";
		}

		@Override
		public void accept(final ILocalOsVisitor visitor) {
			visitor.visit(this);
		}
	}

	/**
	 * Represents the OpenBSD operating system.
	 */
	public static class OpenBsd extends BsdOs {

		OpenBsd() {
			super();
			osTag = "openbsd";
		}

		@Override
		public void accept(final ILocalOsVisitor visitor) {
			visitor.visit(this);
		}
	}

	/**
	 * Represents the NetBSD operating system.
	 */
	public static class NetBsd extends BsdOs {

		NetBsd() {
			super();
			osTag = "netbsd";
		}

		@Override
		public void accept(final ILocalOsVisitor visitor) {
			visitor.visit(this);
		}
	}

	/**
	 * Represents the macOS operating system.
	 */
	public static class MacOsx extends ILocalOs {

		MacOsx() {
			osTag = "mac os x";
			unix = true;
		}

		@Override
		public void accept(final ILocalOsVisitor visitor) {
			visitor.visit(this);
		}
	}
}
