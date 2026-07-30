# MetricsHub Extension Class Loading

How the engine (`org.metricshub.engine.extension`) isolates each extension in its own class loader.

## 1. Loader topology

One `ExtensionClassLoader` per jar in `extensions/`. All share the **application class loader** as parent.
`MetricsHub-Extension-Requires` (manifest) wires *delegate* edges between extension loaders.

```mermaid
graph TD
    BOOT["Bootstrap / Platform loader<br/><i>java.*, javax.*, JAXP defaults, java.sql</i>"]
    APP["Application class loader<br/><b>metricshub-agent fat jar</b><br/><i>engine SPI &amp; types, Jackson, SLF4J/Log4j2,<br/>OpenTelemetry, Spring</i>"]

    SNMP["ExtensionClassLoader<br/><b>metricshub-snmp-extension</b>"]
    JDBC["ExtensionClassLoader<br/><b>metricshub-jdbc-extension</b>"]
    HTTP["ExtensionClassLoader<br/><b>metricshub-http-extension</b>"]
    PROG["ExtensionClassLoader<br/><b>programmable-configuration</b>"]
    EMU["ExtensionClassLoader<br/><b>emulation-extension</b>"]

    D1["IsolatedDriverClassLoader<br/><i>external JDBC driver jar</i>"]

    BOOT --> APP
    APP --> SNMP
    APP --> JDBC
    APP --> HTTP
    APP --> PROG
    APP --> EMU

    PROG -. "Requires: jdbc" .-> JDBC
    EMU -. "Requires: http, jdbc, ..." .-> HTTP
    EMU -. " " .-> JDBC

    JDBC --> D1

    classDef parent fill:#e8f0fe,stroke:#4285f4,color:#1a3c6e;
    classDef ext fill:#e6f4ea,stroke:#34a853,color:#1e4620;
    classDef drv fill:#fef7e0,stroke:#f9ab00,color:#5f4b00;
    class BOOT,APP parent;
    class SNMP,JDBC,HTTP,PROG,EMU ext;
    class D1 drv;
```

**Isolation property:** an extension's `META-INF/services` files (JAXP factories, `java.sql.Driver`,
StAX, ...) are visible only to that extension and its declared dependents — never to siblings, never
JVM-wide. This is what fixes the Oracle-`xmlparserv2`-breaks-WinRM class of bug.

ASCII equivalent:

```
                    Bootstrap / Platform (java.*, JAXP, java.sql)
                                     |
                    Application CL (agent fat jar: engine SPI,
                     Jackson, SLF4J/Log4j2, OTel, Spring)
        _____________________|_________________________________
       |          |          |               |                 |
   [snmp]      [jdbc]     [http]   [programmable-config]  [emulation]
                  |                       :                  :   :
          [isolated driver CL]           Requires ---> jdbc  '---'--> http, jdbc, ...
```

## 2. Class lookup — `ExtensionClassLoader.loadClass`

Default is **parent-first** (single `Class` identity for every type crossing the engine <-> extension
boundary). `MetricsHub-Extension-Child-First` prefixes (opt-in, package-boundary-normalized) flip the
order for the declared packages — unless they overlap a *forced-parent* prefix
(`org.metricshub.engine.`, `com.fasterxml.jackson.`, `org.slf4j.`, `org.apache.logging.log4j.`,
`io.opentelemetry.`), which is rejected at load time.

```mermaid
flowchart TD
    A["loadClass(name)"] --> B{"already loaded?"}
    B -- yes --> Z["return class"]
    B -- no --> C{"name under a<br/>child-first prefix?"}
    C -- yes --> D["findClass in OWN jar"]
    D -- found --> Z
    D -- miss --> E["parent.loadClass"]
    C -- no --> E
    E -- found --> Z
    E -- miss --> F["delegates (declared order):<br/>delegate.loadLocal(name)<br/><i>recursive: dependency subtree,<br/>never the parent again,<br/>honoring the delegate's own<br/>child-first prefixes</i>"]
    F -- found --> Z
    F -- miss --> G["findClass in OWN jar"]
    G -- found --> Z
    G -- miss --> X["ClassNotFoundException"]
```

Resource lookup (`getResource` / `getResources`) follows the **same order** (child-first: own ->
parent -> dependencies; default: parent -> dependencies -> own), with `getResources` deduplicated by
URL external form.

## 3. Discovery pipeline — `ExtensionLoader` -> `ExtensionRuntime`

```mermaid
flowchart TD
    S["scan extensions/*.jar"] --> M["read manifest per jar ->
    ExtensionDescriptor
    (Id, Requires, Child-First)"]
    M --> DUP["drop duplicate Ids"]
    DUP --> TOPO["topological sort:
    dependencies before dependents;
    disable unresolved / cyclic (log ERROR)"]
    TOPO --> BUILD["build one ExtensionClassLoader per jar,
    wiring Requires as delegate loaders"]
    BUILD --> SPI["per loader x 7 SPIs: lazy ServiceLoader
    - skip providers owned by ANOTHER extension loader
    - dedupe by SPI + provider class (parent-resolved kept once)
    - per-element error handling (malformed entry != abort)"]
    SPI --> INST["instantiate under TCCL = extension loader"]
    INST --> WRAP["wrap in TcclClassLoaderDecorator proxy"]
    WRAP --> EM["ExtensionManager
    (holds providers + loaders; close() =
    onShutdown reverse order, then close loaders)"]
    SPI -. "any unexpected failure" .-> CQ["close every constructed
    loader, rethrow"]
```

## 4. Runtime invocation — why TCCL-based lookups keep working

```mermaid
sequenceDiagram
    participant Engine as Engine / Agent
    participant Proxy as TCCL proxy
    participant Ext as Extension code
    participant CL as ExtensionClassLoader

    Engine->>Proxy: any SPI method (processSource, load, ...)
    Proxy->>Proxy: save TCCL; TCCL = extension loader
    Proxy->>Ext: delegate call
    Ext->>CL: TCCL lookups resolve HERE:<br/>JAXP factories, DriverManager,<br/>Spring ClassPathResource, ServiceLoader
    Ext-->>Proxy: result
    Proxy->>Proxy: restore previous TCCL (re-entrant)
    Proxy-->>Engine: result
```

The save/restore is stack-disciplined, so nested engine -> extension -> engine -> extension calls
(e.g. composite source scripts) each see their own loader.
