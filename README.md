
---

<div align=center>

[![Website](https://img.shields.io/website?up_message=available&down_message=down&url=https%3A%2F%2Fmetricshub.com&style=for-the-badge)](https://metricshub.com)
[![GitHub release (with filter)](https://img.shields.io/github/v/release/metricshub/metricshub-community?style=for-the-badge)](https://github.com/metricshub/metricshub-community/releases)
[![Build](https://img.shields.io/github/actions/workflow/status/metricshub/metricshub-community/build-main.yml?style=for-the-badge)](https://github.com/MetricsHub/metricshub-community/actions/workflows/build-main.yml)
![GitHub top language](https://img.shields.io/github/languages/top/metricshub/metricshub-community?style=for-the-badge)
[![License](https://img.shields.io/badge/license-AGPL%203.0-7b3e7b?style=for-the-badge)](https://github.com/metricshub/metricshub-community/blob/main/LICENSE)

<a href="https://metricshub.com" target="_blank">
	<picture>
	<source media="(prefers-color-scheme: dark)" srcset=".github/images/logo-dark.svg">
	<source media="(prefers-color-scheme: light)" srcset=".github/images/logo-light.svg">
	<img alt="MetricsHub" src=".github/images/logo-light.svg" width="250">
	</picture>
</a>
<h4>MetricsHub®, is an open-source metrics collection tool that leverages OpenTelemetry for vendor-neutral observability.</h4>
</div>

---

## How to Install (Red Hat, Debian Linux)

```shell-session
curl -fsSL https://get.metricshub.com | bash
```

## Project Structure

This is a multi-module project:

* **/**: The root (parent of all submodules)
* **metricshub-engine**: The brain, the heart of this project. It houses the core logic and essential functionalities that power the entire system.
* **metricshub-agent**: The MetricsHub Agent module includes a Command-Line Interface (CLI) and is responsible for interacting with the MetricsHub engine. It acts as an entry point, collecting and transmitting data to the OpenTelemetry Collector.
* **metricshub-classloader-agent**: Manages class loading for extensions, ensuring that they are loaded correctly within the JVM.
* **metricshub-ipmi-extension**: Provides support for the Intelligent Platform Management Interface (IPMI) to monitor and manage hardware at the firmware level.
* **metricshub-oscommand-extension**: Allows execution of OS-level commands and scripts to gather metrics and other data from the operating system.
* **metricshub-snmp-extension-common**: Contains common functionalities and utilities used by SNMP-based extensions.
* **metricshub-snmp-extension**: Enables Simple Network Management Protocol (SNMP) for monitoring and managing network devices.
* **metricshub-snmpv3-extension**: Adds support for SNMPv3, which includes enhanced security features like authentication and encryption.
* **metricshub-internaldb-extension**: Executes internal database queries using MetricsHub's internal database engine.
* **metricshub-win-extension-common**: Contains common functionalities and utilities used by Windows-specific extensions.
* **metricshub-wmi-extension**: Provides support for Windows Management Instrumentation (WMI) to gather detailed information about Windows systems.
* **metricshub-winrm-extension**: Enables the use of Windows Remote Management (WinRM) for remote management and monitoring of Windows-based systems.
* **metricshub-wbem-extension**: Supports the Web-Based Enterprise Management (WBEM) standard for accessing management information.
* **metricshub-ping-extension**: Enables testing the reachability of hosts using ICMP-based ping commands.
* **metricshub-jawk-extension**: Allows execution of Jawk scripts.
* **metricshub-jdbc-extension**: Provides support for monitoring SQL databases.
* **metricshub-jmx-extension**: Enables monitoring of Java applications through JMX (Java Management Extensions).
* **metricshub-hardware**: Hardware Energy and Sustainability module, dedicated to managing and monitoring hardware-related metrics, focusing on energy consumption and sustainability aspects.
* **metricshub-yaml-configuration-extension**: Extension that loads configuration fragments from YAML files located in a configuration directory.
* **metricshub-programmable-configuration-extension**: Provides a programmable configuration mechanism, allowing users to define custom configurations through [Apache Velocity](https://velocity.apache.org/) scripts.
* **metricshub-web**: Provides a user interface for interacting with MetricsHub features and functionalities.
* **metricshub-it-common**: Contains common code and utilities used by integration tests across various modules.
* **metricshub-assets**: Generates the assets required to package MetricsHub Community for Windows, Debian, RedHat, and Docker platforms.

> [!TIP]
> Looking for connectors? Check the [MetricsHub Community Connectors](https://github.com/metricshub/community-connectors) repository.

## How to build the Project

### Requirements

* Have [Maven 3.x properly installed and configured](https://maven.apache.org/download.cgi).
* Latest LTS Release of [JDK 21](https://adoptium.net).

### JRE Builder dependency

MetricsHub Community uses a custom JRE builder hosted in a separate repository: [metricshub-jre-builder](https://github.com/metricshub/metricshub-jre-builder).

The custom JRE artifact is deployed to **GitHub Packages**. To consume this dependency, ensure your Maven `settings.xml` is properly configured with authentication to GitHub's package registry. Here are the required steps:

##### 1. Update your `~/.m2/settings.xml`

Add the following `<server>` block inside the `<servers>` section of your `settings.xml`:

```xml
<servers>
	...
	<server>
		<id>github</id>
		<username>YOUR_GITHUB_USERNAME</username>
		<password>YOUR_PERSONAL_ACCESS_TOKEN</password>
	</server>
	...
</servers>

```

> 💡 The personal access token (PAT) must have at least the `read:packages` and `repo` scopes.

> 🔐 **Want to encrypt your GitHub token?**
> Maven supports secure encryption of your credentials. Follow the official [Maven Password Encryption](https://maven.apache.org/guides/mini/guide-encryption.html) guide to encrypt your token.

##### 2. Add GitHub packages to `<profiles>`

Add the following configuration inside a new `<profile>` section in your `settings.xml`, and activate it:

```xml
<profiles>
	...
	<profile>
		<id>github</id>
		<repositories>
			<repository>
			<id>github</id>
			<name>GitHub JRE Builder Package</name>
			<url>https://maven.pkg.github.com/metricshub/metricshub-jre-builder</url>
			<releases>
				<enabled>true</enabled>
			</releases>
			<snapshots>
				<enabled>true</enabled>
			</snapshots>
			</repository>
		</repositories>
	</profile>
	...
</profiles>

<activeProfiles>
	...
	<activeProfile>github</activeProfile>
</activeProfiles>
```

> 📦 Artifacts published here are used to build a custom runtime tailored to MetricsHub’s needs.

### Accessing Snapshots from Maven Central

To access **snapshot artifacts** from [Maven Central Snapshots](https://central.sonatype.com/repository/maven-snapshots/), you need to explicitly configure the repository in your Maven `~/.m2/settings.xml`.

Add the following `<repository>` entry:

```xml
<repositories>
    ...
    <repository>
        <id>central-snapshots</id>
        <name>Maven Repository Switchboard</name>
        <url>https://central.sonatype.com/repository/maven-snapshots</url>
        <releases>
            <enabled>false</enabled>
        </releases>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
    ...
</repositories>
```

> 💡 This is required if your build depends on snapshot versions of dependencies hosted on Maven Central’s snapshots repository.

### Build

To build the MetricsHub package, from `./metricshub`:

```sh
$ mvn clean package
```

#### Building Windows Packages (.msi)

* **Host:** Windows
* **Prerequisites:** [WiX Toolset 3.11](https://github.com/wixtoolset/wix3/releases/tag/wix3112rtm) configured in the `PATH`
* Execute the `mvn package` command within the MetricsHub root directory (`metricshub`).
* Execute the `metricshub-assets\target\assets-local\build-windows.cmd` command. The command will create a `packages` sub-directory containing the unsigned `.msi` file and the `MetricsHub` application folder.

#### Building Linux Packages (.deb, .rpm)

##### Building all Linux packages with Docker (recommended)

* **Host:** Windows
* **Prerequisites:** Docker Desktop with QEMU enabled for multi-arch builds
* Execute the `mvn package` command within the MetricsHub root directory (`metricshub`).
* Execute the `metricshub-assets\target\assets-local\build-docker-linux.cmd` command. The command will create a `packages` sub-directory containing the _x86_ and _arm64_ `.deb` and `.rpm` files and the `metricshub` application folder.

##### Building Linux package for your platform

* **Host:** Linux
* **Debian Prerequisites:** The following packages must be installed `fakeroot` and `gcc-multilib` (for _x86_)
* **RedHat Prerequisites:** The following package must be installed `rpm-build`
* Execute the `mvn package` command within the MetricsHub root directory (`metricshub`).
* Execute the `metricshub-assets/target/assets-local/build-linux.sh` command. The command will create a `packages` sub-directory containing the `.deb` **or** `.rpm` file and the `metricshub` application folder for your Linux distribution and CPU architecture.

## Checkstyle

In this project, we use Checkstyle to ensure consistent and clean Java code across our codebase. 

Maven Checkstyle Plugin is configured globally in the main `pom.xml` file, and it verifies the Java code during the build process:

```xml
	<plugin>
		<artifactId>maven-checkstyle-plugin</artifactId>
		<version>3.3.0</version>
		<configuration>
			<sourceEncoding>${project.build.sourceEncoding}</sourceEncoding>
			<configLocation>checkstyle.xml</configLocation>
		</configuration>
		<executions>
			<execution>
				<id>validate</id>
				<phase>validate</phase>
				<goals>
					<goal>checkstyle</goal>
					<goal>check</goal>
				</goals>
			</execution>
		</executions>
	</plugin>
```

The Checkstyle rules that govern our code quality and style are defined in the `./checkstyle.xml` file. It's important to adhere to these rules to maintain code consistency and quality throughout the project.

The build will fail if one or more Checkstyle rules are violated.

To perform Checkstyle analysis and generate a report on violations, navigate to the directory of the Maven project you wish check and run the following `mvn` command:

```bash
mvn checkstyle:checkstyle
```

All the encountered Checkstyle issues are reported under the `target/site` directory.

To perform Checkstyle analysis and output violations to the console, navigate to the directory of the Maven project you wish check and run the following `mvn` command:

```bash
mvn checkstyle:check
```

## Code Formatting

In this project, we maintain code formatting using [prettier-java](https://github.com/jhipster/prettier-java), a tool that helps ensure clean and consistent Java code. It automatically formats your code according to a predefined set of rules.

### Prettier Maven Plugin

To automatically format the Java code in a specific Maven module, navigate to the directory of the Maven project you wish to format and run the following `mvn` command:

```bash
mvn prettier:write
```

To validate the formatted code, navigate to the directory of the Maven project you wish to check and run the following `mvn` command:

```bash
mvn prettier:check
```

The build will fail if you forgot to run Prettier.

## Submitting a PR

Before you submit a PR, make sure to use the available tools for code formatting, and ensure that the style checks and unit tests pass.

## License

License is GNU Affero General Public License v3.0. Each source file must include the AGPL-3.0 header (build will fail otherwise).
To update source files with the proper header, simply execute the below command:

```bash
mvn license:update-file-header
```

