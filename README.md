# MetricsHub

![GitHub release (with filter)](https://img.shields.io/github/v/release/metricshub/metricshub-community)
![Build](https://img.shields.io/github/actions/workflow/status/metricshub/metricshub-community/maven-deploy.yml)
![GitHub top language](https://img.shields.io/github/languages/top/metricshub/metricshub-community)
![License](https://img.shields.io/github/license/metricshub/metricshub-community)

## Structure

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
* **metricshub-it-common**: Contains common code and utilities used by integration tests across various modules.
* **metricshub-community-windows**: Builds the `.zip` package for MetricsHub on Windows platforms.
* **metricshub-community-linux**: Builds the `.tar.gz` package of MetricsHub on Linux platforms.
* **metricshub-doc**: Houses the documentation for MetricsHub.

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

#### Building Windows Packages (.zip)

* **Host:** Windows
* Execute the `mvn package` command within the MetricsHub root directory (`metricshub`). You can find the `.zip` package in the `metricshub/metricshub-community-windows/target` directory upon completion (`metricshub-community-windows-<version>.zip`).

#### Building Linux Packages (.tar.gz)

* **Host:** Linux
* Execute the `mvn package` command within the MetricsHub root directory (`metricshub`). You can find the `.tar.gz` package in the `metricshub/metricshub-community-linux/target` directory upon completion (`metricshub-community-linux-<version>.tar.gz`).
  * The `Docker` package is compatible with the `debian:latest` image, it will be generated under the `metricshub/metricshub-community-linux/target` directory (`metricshub-community-linux-<version>-docker.tar.gz`).

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

