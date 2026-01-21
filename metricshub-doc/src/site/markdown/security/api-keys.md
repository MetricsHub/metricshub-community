keywords: api, key, security
description: MetricsHub lets you generate API keys to securely access its services. This document explains how to create, list, and revoke API keys.

# API Keys

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

## Overview

**MetricsHub** provides a secure way to access its services through API keys. These keys are used to authenticate requests made to the MetricsHub Web server, allowing users to interact with the system programmatically. API Keys are ideal for scenarios where long-lived interactions are needed, such as:

- Connecting **MCP Clients** or other agents to the MetricsHub Web server
- Automating secure interactions with the **MetricsHub REST API**

Each API key is associated with a human-readable alias and stored securely within the encrypted keystore **metricshub-keystore.p12** on the local system. Keys can be easily created, listed, and revoked via the `apikey` CLI tool.

> API keys should be treated as secrets. Anyone with access to an API key can perform the actions to the MetricsHub Web server that the key is authorized for.

> On **Windows**, the **metricshub-keystore.p12** file is located under `C:\ProgramData\MetricsHub\security`, which is typically accessible **only to Administrator users**. As a result, non-admin users may encounter **access denied** errors when attempting to create, list, or revoke API keys.

> On **Linux**, the **metricshub-keystore.p12** file resides under the MetricsHub installation path (e.g., `/opt/metricshub/security`), and access depends on the file system permissions.

## Generating an API Key

To generate an API key, run the `apikey create` command:

```shell-session
/$ cd /opt/metricshub/bin
/opt/metricshub/bin$ ./apikey create mcp-openai
API key created for alias 'mcp-openai': eb47c2be-927f-495f-aa97-0ba0f60b8986 (No expiration)
Please store this key securely, as it will not be shown again.
```

This command creates a new API key with the alias `mcp-openai`. The key is displayed once in the terminal. Be sure to store it securely, as it will not be shown again.

To specify an expiration date for the API key, you can use the `--expires-on` option:

```shell-session
/opt/metricshub/bin$ ./apikey create mcp-claude --expires-on 2025-12-31T23:59:59
API key created for alias 'mcp-claude': 3f2b1c4d-8e5f-4a2b-bc15-0ba0f60b15f3 (Expires on 2025-12-31T23:59:59)
Please store this key securely, as it will not be shown again.
```

## Listing API Keys

To display the list of stored API key aliases, use the `apikey list` command:

```shell-session
/opt/metricshub/bin$ ./apikey list
mcp-openai ********************8986 (No expiration)
mcp-claude ********************15f3 (Expires on 2025-12-31T23:59:59)
```

This command shows the alias and a masked portion of the API key ID.

## Revoking an API Key

To revoke (delete) an existing API key, use the `apikey revoke` command with the corresponding alias:

```shell-session
/opt/metricshub/bin$ ./apikey revoke mcp-openai
API key 'mcp-openai' has been revoked.
```

Once revoked, the API key can no longer be used to authenticate against the MetricsHub Web server. You may create a new key with the same alias afterward, if needed.
