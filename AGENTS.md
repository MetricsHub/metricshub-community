# Instructions for AI Agents

## About this project

This is the main repository of the MetricsHub project, to build MetricsHub Community Edition. It's an open-source collector of infrastructure metrics based on the [OpenTelemetry](https://opentelemetry.io) standard. It's highly scalable, it's extensible, and it enables AI to manage the IT infrastructure with its MCP server.

Typically, users install MetricsHub to monitor hundreds of remote systems (Windows, Linux servers, HPE, Cisco, Dell, Hitachi, network devices, storage systems, etc.) using various protocols (SNMP, WMI, WBEM, SSH, IPMI, HTTP REST), and export metrics following the OpenTelemetry standard to any observability backend that supports OpenTelemetry.

This repository includes the main collection engine written in Java and all its extensions to support various standard protocols (each protocol is implemented as an extension to the engine).

A separate repository includes the connectors, a library of YAML files that describe how to monitor a given platform.

## Multi-module

This is a large multi-module Maven projet. Everything can be built from the root of the project, but it may take time to complete. To avoid waiting too long, run the build commands from the root of the module you're working on.

## Code Format

You never need to worry about code formatting manually.
Simply run from the project root, or at the root of a specific Maven module:

```bash
mvn prettier:write
```

before committing changes to ensure the code follows MetricsHub’s formatting rules.

If you just want to validate the formatting (without modifying files), use:

```bash
mvn prettier:check
```

⚠️ **Note:** The build will fail if the formatting check fails. Always run `prettier:write` before pushing changes.

All source files must include the proper **AGPL-3.0 license header**.

To verify that all files are compliant, run from the root of the repository:

```bash
mvn license:check-file-header
```

If a source file is reported as missing the proper header (or header is outdate), use the below command to update it:

```bash
mvn license:update-file-header -Dlicense.includes=\<PATH_TO_THE_FILE_TO_UPDATE\>
```

where \<PATH_TO_THE_FILE_TO_UPDATE\> is either the absolute path of the file which needs to be updated, or the path relative to the root of the module you're working on.

The build will fail if any file is missing or has an incorrect license header.

⚠️ **Warning:** NEVER run `mvn license:update-file-header` without specifying which files to update! Otherwise, it will udpate ALL source files which will end up in your commit. You'll waste a lot of time removing all these unneeded tracked changes!

## Build

The MetricsHub project uses **Maven** as its build system. It is a **multi-module project** composed of core components (`metricshub-engine`, `metricshub-agent`, etc.), platform packages (`metricshub-community-windows`, `metricshub-community-linux`), and several extensions. The platform packages are not to be built automatically. Don't bother, they will be automatically excluded from the build.

You can build the entire project from the repository root using:

```bash
mvn package
```

## Test

All tests must pass before you commit or submit a PR.

To run the full test suite:

```bash
mvn test
```

Do **not** use the `-q` (quiet) option — you should see the detailed test output.
Test results are stored in the `./target/surefire-reports` directory.

Integration tests and compatibility tests (in modules like `metricshub-it-common`) are executed with:

```bash
mvn verify
```

Their reports are generated under `./target/failsafe-reports`.

## Code Quality Reports

Code quality checks are part of the build process and include **Checkstyle**, **PMD**, and **SpotBugs**.

Run:

```bash
mvn verify
```

and fix any reported issues before committing.

For each Maven module, the reports are located in:

* `./target/checkstyle-result.xml`
* `./target/pmd.xml`
* `./target/spotbugsXml.xml`

### Checkstyle

To manually run Checkstyle and generate a report in the overall repository, or just in a module:

```bash
mvn checkstyle:checkstyle
```

Report: `target/site/checkstyle.html`

To print violations to the console:

```bash
mvn checkstyle:check
```

The Checkstyle rules are defined in `checkstyle.xml` at the project root.
The build will fail if rules are violated.

## Documentation

Any change that affects end-user behavior must be reflected in:

* `README.md`
* Documentation under `metricshub-doc/`

Make sure any new or modified feature is properly documented before submitting.

## Submitting changes

Before committing and submitting your changes:

1. Run:

   ```bash
   mvn prettier:write
   mvn license:check-file-header
   mvn verify
   ```

2. Ensure **all unit tests** and **integration tests** pass.
3. Check that the **Checkstyle** and **SpotBugs** analyses report no issues.
4. Update relevant documentation if necessary.

Only then submit your changes.
