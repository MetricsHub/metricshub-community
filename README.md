# MetricsHub

![GitHub release (with filter)](https://img.shields.io/github/v/release/sentrysoftware/metricshub)
![Build](https://img.shields.io/github/actions/workflow/status/sentrysoftware/metricshub/build.yml)
![GitHub top language](https://img.shields.io/github/languages/top/sentrysoftware/metricshub)
![License](https://img.shields.io/github/license/sentrysoftware/metricshub)

## Structure

This is a multi-module project:

* **/**: The root (parent of all submodules)
* **metricshub-engine**: The brain, the heart of this project. It houses the core logic and essential functionalities that power the entire system.
* **hardware**: Hardware Energy and Sustainability module, dedicated to managing and monitoring hardware-related metrics, focusing on energy consumption and sustainability aspects.
* **metricshub-agent**: The MetricsHub Agent module includes a Command-Line Interface (CLI) and is responsible for interacting with the MetricsHub engine. It acts as an entry point, collecting and transmitting data to the OpenTelemetry Collector.
* **metricshub-windows**: Builds the `.zip` package for MetricsHub on Windows platforms.
* **metricshub-linux**: Builds the `.tar.gz` package of MetricsHub on Linux platforms.
* **metricshub-doc**: Houses the documentation for MetricsHub.


## How to build the Project

### Requirements

* Have [Maven 3.x properly installed and configured](http://alpha.internal.sentrysoftware.net/lecloud/x/TwJn), with access to Sentry's repository.
* Latest LTS Release of [JDK 21](https://adoptium.net).

### Build

To build the MetricsHub package, from `./metricshub`:

```sh
$ mvn clean package
```

#### Building Windows Packages (.zip)

* **Host:** Windows
* Execute the `mvn package` command within the MetricsHub root directory (`metricshub`). You can find the `.zip` package in the `metricshub/metricshub-windows/target` directory upon completion (`metricshub-windows-<version>.zip`).

#### Building Linux Packages (.tar.gz)

* **Host:** Linux
* Execute the `mvn package` command within the MetricsHub root directory (`metricshub`). You can find the `.tar.gz` package in the `metricshub/metricshub-linux/target` directory upon completion (`metricshub-linux-<version>.tar.gz`).
  * The `Docker` package is compatible with the `debian:latest` image, it will be generated under the `metricshub/metricshub-linux/target` directory (`metricshub-linux-<version>-docker.tar.gz`).

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

