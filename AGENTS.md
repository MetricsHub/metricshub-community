# Instructions for AI Agents

## 🧠 Project Overview

This repository hosts the **MetricsHub Community Edition**, an open-source infrastructure metrics collector based on the [OpenTelemetry](https://opentelemetry.io) standard.
MetricsHub enables scalable, extensible monitoring across Windows, Linux, and various enterprise systems (HPE, Cisco, Dell, Hitachi, etc.), exporting metrics in OpenTelemetry format to any compatible observability backend.

It includes:

* The **collection engine** (Java)
* Multiple **protocol extensions** (SNMP, WMI, SSH, IPMI, HTTP REST, etc.)
* A **web UI** (React + Vite)
* Documentation under `metricshub-doc/`

A separate repository contains the **connectors** (YAML-based monitoring definitions).

---

## 🧩 Multi-Module Maven Architecture

MetricsHub is a **large, multi-module Maven project**.
Each module is self-contained and can be built or tested independently.
The full project can be built from the repository root — but that can be slow.

When working on specific modules, **always run build commands from that module’s root** to reduce build time.

---

## ⚙️ Core Workflow Rules for AI Agents

When making or reviewing changes **you must never skip these steps**, even partially.
Each step **depends** on the previous one.

**ALWAYS perform these actions, in this exact order:**

1. 🧹 **Code Formatting**

   ```bash
   mvn prettier:write
   ```

   * Must be run **before any other Maven goal.**
   * Ensures consistent formatting across all modules.
   * The build will fail if formatting is incorrect.

2. 🪪 **License Header Validation**

   ```bash
   mvn license:check-file-header
   ```

   * All source files must include the **AGPL-3.0** license header.
   * To fix a specific file:

     ```bash
     mvn license:update-file-header -Dlicense.includes=<RELATIVE_PATH>
     ```

   ⚠️ **Never run** `mvn license:update-file-header` without specifying files, or it will modify every file in the repo.

3. 🧩 **Checkstyle & Code Quality**

   ```bash
   mvn checkstyle:check
   ```

   * Ensures all code meets MetricsHub’s style and quality standards.
   * Reports are in `target/checkstyle-result.xml` for each module.

4. 🧪 **Unit Tests**

   ```bash
   mvn test
   ```

   * Run tests and confirm **all pass**.
   * Test reports: `target/surefire-reports/`

5. 🧬 **Integration Tests**

   ```bash
   mvn verify
   ```

   * Runs deeper validation (integration and compatibility tests).
   * Reports: `target/failsafe-reports/`

6. 📜 **Documentation Consistency**

   * Update `README.md` and content under `metricshub-doc/` if any end-user behavior changes.
   * Verify examples, CLI options, and environment variables are current.

7. 🧰 **Web App (React Frontend)**

   * Located in `metricshub-web/react`
   * When editing frontend code:

     ```bash
     npm run format
     npm run lint
     npm run build
     ```

   * Do **not** rely on Maven to build the React app for testing; use npm directly in this directory for speed.

8. 🏗️ **Final Build Validation**

   ```bash
   mvn package
   ```

   * Run from the root **only after** all prior steps pass.
   * Confirms the complete repository builds cleanly.

---

## 🧾 Summary: Mandatory Pre-Commit Checklist

Before committing or submitting changes, the agent **must** execute all of the following in order:

```bash
mvn prettier:write
mvn license:check-file-header
mvn checkstyle:check
mvn verify
```

✅ All tests and checks must pass.
✅ Checkstyle and license validation must report **0 issues.**
✅ Documentation and web code (if applicable) must be up to date.
✅ Only then, commit and propose changes.

---

## 🔁 Context Retention Rules for Agents

To avoid “forgetting” steps or commands when the project is large:

* **Never execute Maven goals in isolation.**
  Each step builds on previous checks; always run them in sequence.
* **Do not discard build artifacts** between phases — reuse target directories where possible.
* **Maintain project root awareness:** always re-anchor paths relative to the Maven module being built.
* **Preserve execution context:** before submitting changes, the agent must recall:

  * The last Maven module worked on.
  * Whether previous commands (`prettier`, `license`, `verify`) succeeded or failed.
  * Any pending errors or warnings that require fixing.

If at any time context is lost or unclear, **rebuild from the module root** using:

```bash
mvn clean verify
```

---

## 🧱 Common Failure Scenarios

| Problem                 | Likely Cause                  | Fix                                                            |
| ----------------------- | ----------------------------- | -------------------------------------------------------------- |
| Build fails on `verify` | Code formatting not fixed     | Run `mvn prettier:write`                                       |
| License check fails     | Missing AGPL header           | Run `mvn license:update-file-header -Dlicense.includes=<path>` |
| Checkstyle errors       | Formatting or Javadoc missing | Run `mvn checkstyle:check` and fix reported issues             |
| Tests failing           | Unit/integration issue        | Inspect `target/surefire-reports` or `target/failsafe-reports` |
| Web app build fails     | Node dependencies missing     | Run `npm install` inside `metricshub-web/react`                |

---

## 🧠 Tips for Large Repositories

* Use `mvn -pl <module>` to build/test only one module.
* Use `mvn -T 1C` for parallel builds (1 thread per core).
* Use `mvn dependency:analyze` before committing large refactors.
* Cache dependencies between steps — don’t re-download Maven artifacts unnecessarily.

---

## ✅ Final Rule

When you (the AI agent) **propose a change or submit a pull request**, your output must include:

* Confirmation that:

  * `mvn prettier:write` was executed successfully
  * `mvn license:check-file-header` passed
  * `mvn checkstyle:check` passed
  * `mvn verify` succeeded
* A short summary of affected modules and test results
* Mention of any documentation or frontend updates

Only after all that, commit or submit.
