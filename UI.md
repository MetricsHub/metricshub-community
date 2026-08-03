# UI — Guided Configuration (Issue #1174)

**Scope:** every component, hook, and class added or updated on branch `web-ui-configuration-forms` since `7bb9a200` (names below reflect the post-review renames — the old `HostWizard*` names are given in parentheses).

**Entry point:** `/configuration/guided-config/...` — e.g. `https://localhost:31888/configuration/guided-config/resource-groups/Elyes/resources/Carnapp` opens the *Carnapp* resource inside the *Elyes* resource group.

## Screenshot

![MetricsHub login](ui-login-screenshot.png)

> The guided-config pages sit behind authentication, so a headless capture can only reach the login screen above. To embed the actual resource form, sign in and take the capture from your browser (or connect the Claude-in-Chrome extension), then replace this image.

---

## 1. Frontend — pages and navigation

| Component | Purpose |
|---|---|
| `App.jsx` (routes) | Declares the `/configuration/guided-config/...` route family: browse, create resource, edit resource, resource groups CRUD, standalone (`no-resource-group`) resources. All render `HostsPage`, which picks the view from the URL. |
| `HostsPage.jsx` | Route dispatcher for the whole guided-config area. Parses the URL (group name, host id, `new`/`edit`) and renders the matching view below. |
| `HostsBrowseView.jsx` | The main browse screen: resource-group overview plus the create-resource form when requested (`renderCreateHostForm`). |
| `ResourceGroupsOverviewView.jsx` | Lists all resource groups as cards/sections with their hosts and per-host protocol health. |
| `StandaloneHostsView.jsx` | Same idea for top-level resources that belong to no resource group. |
| `HostsResourcesTree.jsx` | Left-hand tree of resource groups and their hosts for quick navigation; filtering via `hosts-filter-utils`. |
| `HostsResourceTable.jsx` | DataGrid of hosts inside a group (or standalone): name, type, protocol chips, selection checkboxes, pagination. Rows are draggable (`setHostDragData`) — **note: drop zones are not wired yet, see CODE_REVIEW.md §6.6**. |
| `HostsFilterBar.jsx` (ex `HostsHostFilterBar`) | Search/filter toolbar above host lists: free-text query, protocol filter, sort order. |
| `HostsHostEditPanel.jsx` | Read/edit side panel for a host selected in the browse view; shows live protocol health via `useHostsProtocolHealth`. |
| `ResourceGroupFormPage.jsx` | Create/edit form for a *resource group* (name, attributes, metrics), in the same single-page layout as the resource form. |
| `GlobalSettingsView.jsx` | Form for agent-level settings (logger level, collect period, sequential, …) using the same section layout as the host config form. |

## 2. Frontend — the resource configuration form

This is the page your example URL opens. It is a **single scrollable page** with a section navigator, not a step-by-step wizard (hence the renames).

| Component | Purpose |
|---|---|
| `HostConfigPage.jsx` (ex `HostWizardPage`) | Top-level create/edit page for one resource. Instantiates `useHostConfig`, restores scroll position, computes dirty state for the leave-guard, and lays out nav + body. |
| `HostConfigFormBody.jsx` | Renders the ordered list of section panels from `form.steps`, tagging each with validated / invalid / edited status for the navigator. |
| `HostConfigSectionNav.jsx` (ex `HostWizardCardStepper`) | The card navigator on the side: one entry per section, highlights the active one from scroll position (scroll-spy), click scrolls to the section. |
| `HostConfigSectionHeader.jsx` (ex `HostWizardSectionHeader`) | Prominent numbered heading for each scroll section. |
| `HostConfigSectionContent.jsx` (ex `HostWizardStepContent`) | Switchboard that renders the right section body for a section descriptor (basics / one per protocol / connectors) and wires it to the form state. |
| `HostConfigBasicsSection.jsx` (ex `HostWizardBasicsStep`) | First section: resource identity and placement — host id, hostname(s), host type, resource group. |
| `HostProtocolConfigStep.jsx` | One section per selected protocol; looks up the protocol's field list in `PROTOCOL_FIELDS` and delegates to `ProtocolConfigForm`. |
| `ProtocolConfigForm.jsx` (ex `GenericProtocolConfigForm`) | Renders a protocol's configuration fields from its declarative definition (username, password, port, timeout, transport, …) including the Advanced accordion (hostname override, namespace, retry intervals, …). |
| `ProtocolSelectionGrid.jsx` | Multi-select chip grid to choose which protocols to configure (SSH, SNMP, WMI, …), filtered by host type compatibility. |
| `HostConfigConnectorsSection.jsx` (ex `HostWizardConnectorsStep`) | The connectors section: automatic detection vs. manual selection, searchable connector DataGrid with category tabs, directive chips (`+force`, `!exclude`, `#tag`), a Code view for editing directives as text, and "additional connector" instances for connectors with variables. |
| `ConnectorConfigurationDialog.jsx` | Dialog to create/edit one *additional connector* instance: instance id, template (`uses`), force/select mode, and connector variable values. |
| `HostConnectorsCatalogDialog.jsx` | Full-catalog browser dialog for picking connectors outside the filtered grid. |
| `ProtocolChip.jsx` / `protocol-chip-sx.js` (ex `protocol-chip-styles`) | Small protocol badge (chip) and its theme-aware MUI `sx` styling, used in host tables and health displays. |
| `ProtocolPasswordField.jsx`, `PasswordFieldWithEncrypt.jsx`, `DeferredPasswordEncryptAlert.jsx` | Password inputs for protocol configs: reveal toggle, on-demand encryption via the backend, and the informational alert shown when encryption is deferred until save. |
| `ProtocolTestButton.jsx` | "Test" button on each protocol section — calls the backend protocol check endpoint with the current form values and reports reachability. |
| `protocol-form-primitives.jsx`, `guided-config-form-primitives.jsx`, `guided-config-ui-tokens.js` | Shared field-row primitives, labels/help-tooltip building blocks, and styling tokens used across all guided-config forms. |
| `ResourceAdvancedOptionsSection.jsx` | "Advanced options" block on the resource form: logger level, collect period, discovery cycle, sequential, self-monitoring (three-state), monitor filters, enrichments, alerting system. |

## 3. Frontend — hooks and state modules

| Module | Purpose |
|---|---|
| `useHostConfig.js` (ex `useHostWizard`) | The brain of the form. Owns the form state (create or edit), loads the connector catalog, computes section descriptors and per-section validity/dirtiness, persists drafts to sessionStorage, applies connector patches, and builds/submits the final payload. |
| `host-config-state.js` (ex `host-wizard-state`) | Pure form-state model: `createEmptyHostFormState`, normalization, and JSON *fingerprints* used for dirty detection (whole form and per section). |
| `host-config-sections.js` (ex `host-wizard-steps`) | Builds the dynamic section list (basics → one per selected protocol, A–Z → connectors) with titles and subtitles. |
| `host-config-session.js` (ex `host-wizard-session`) | sessionStorage draft persistence: save/load/clear the in-progress form and its scroll position, keyed per route (prefix `metricshub-host-config`). |
| `host-config-scroll-spy.js` | Scroll mechanics: which section is active for the navigator, scroll-to-section targeting, DataGrid row scrolling below the sticky header, and `retryOnAnimationFrames` (bounded rAF retry helper). Unit-tested. |
| `host-config-utils.js` | Mapping layer: snapshot host entry → form state (`hostConfigToFormState`) and form state → API payload (`buildHostPayloadFromForm[Async]`, with optional password encryption). Also display-name and protocol helpers used by the browse views. |
| `host-config-form-layout.js` | Layout constants (column widths, insets) shared by the form pages. |
| `connector-utils.js` | Everything about connector directives and the catalog: parse/format `+force` / `!exclude` / `#tag` directives, selection-kind computation, additional-connector instance management, catalog fetch + compatibility annotation, connector descriptions. Unit-tested. |
| `protocol-definitions.js` | **Declarative source of truth for protocols**: field lists (`PROTOCOL_FIELDS`, incl. the shared `PROTOCOL_HOSTNAME_FIELD`), defaults, port/timeout validation, transport-linked port rules, form ↔ API config conversion (`buildProtocolConfigFromForm`), and validation error collection. |
| `resource-config-fields.js` / `resource-config-utils.js` | Field metadata + mapping for the resource-level Advanced options (defaults, host-node ↔ form conversion, payload building). |
| `hosts-filter-utils.js` | Search/sort predicates for the browse views (group and host matching, sort orders). |
| `host-drag.js` | Drag-and-drop payload helpers for moving hosts between resource groups. Currently only the drag *source* is wired (see CODE_REVIEW.md §6.6). |
| `hooks/use-hosts-protocol-health.js` + `utils/host-protocol-health.js` | Polls the backend for per-host protocol health (`host.up` style metrics) and exposes it to the browse views/edit panel as chip statuses. |
| `utils/password-encrypt.js` | Client-side orchestration of password encryption: finds password-type fields per protocol via `PROTOCOL_FIELDS` and calls the backend encrypt endpoint on save. |
| `utils/alphabetic-sort.js` | Locale-aware comparison (`compareLocale`) and connector-summary sorting used everywhere lists are sorted. |

## 4. Backend — `org.metricshub.web` (metricshub-agent)

| Class | Purpose |
|---|---|
| `UiConfigController` | REST endpoints for the guided config UI: configuration snapshot, add host, create/update/delete resource group, connector catalog/listing. |
| `UiConfigService` | The write path. Reads `metricshub-ui.yaml` as a tree, applies typed changes (add host with attributes/protocols/connectors/advanced fields via `applyResourceConfigFields`, resource-group CRUD), validates the result against `AgentConfig`, and writes it back **atomically**. |
| `UiConnectorCompatibilityService` | Builds the connector catalog for the UI: summary per connector (display name, categories, required protocols, variables) computed once at startup and cached, plus per-request compatibility flags for a given host type + protocols. |
| `ProtocolHealthCheckService` | Executes a real protocol check (SSH/SNMP/HTTP/…) against a host using the matching extension, with timeout resolution — backs the "Test" button. |
| `UiProtocolCheckService` | Thin service that adapts UI protocol-check requests (form-shaped JSON) to `ProtocolHealthCheckService`. |
| `ProtocolConfigurationMaps` | Central registry mapping protocol keys to their extension configuration types for deserializing UI JSON into engine `IConfiguration` objects. |
| `WebConnectorStoreConfiguration` | Spring configuration exposing the web-facing `ConnectorStore` bean (the parsed connector catalog the compatibility service reads). |
| `UiConfig` | Spring MVC configuration serving the built React app (static assets + SPA fallback). |
| `ConfigHelper` (touched) | Agent config helper; branch changes align UI-written YAML with what the agent loader accepts. |

### DTOs (`org.metricshub.web.dto.uiconfig`)

| DTO | Purpose |
|---|---|
| `AddHostRequestDto` | Payload for creating a resource: host id, attributes, protocol configs, connector directives, `additionalConnectors`, plus resource-level options (loggerLevel, collectPeriod, discoveryCycle, jobTimeout, stateSetCompression, sequential, enableSelfMonitoring, logFileSourceDetails, resolveHostnameToFqdn, monitorFilters, enrichments, metrics, alertingSystem). |
| `UiAlertingSystemConfigDto` | Nested alerting-system options (disable, problemTemplate). |
| `UiAdditionalConnectorDto` | One additional-connector instance: `uses` template, `force` flag, variable values. |
| `UiConnectorSummaryDto` | Catalog entry sent to the UI: id, display name, information, categories/tags, required protocols, applicable host types, variables, compatibility flags. |
| `UiConnectorVariableDto` | One connector variable definition (name, description, default) for the configuration dialog. |
| `UiConfigSnapshotDto` | The whole UI configuration snapshot: top-level `resources` and `resourceGroups` maps from `metricshub-ui.yaml`. |
| `CreateResourceGroupRequestDto` / `UpdateResourceGroupRequestDto` | Resource-group create/update payloads (name, attributes, metrics). |
