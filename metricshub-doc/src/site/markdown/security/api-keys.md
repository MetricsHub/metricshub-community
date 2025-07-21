keywords: api, key, security
description: MetricsHub lets you generate API keys to securely access its services. This document explains how to create, list, and revoke API keys.

# API Keys

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

## Generating an API Key

To generate an API key, run the `apikey create` command:

```shell-session
/$ cd /opt/metricshub/bin
/opt/metricshub/bin$ ./apikey create --alias mcp-openai
API key created for alias 'mcp-openai': eb47c2be-927f-495f-aa97-0ba0f60b8986
Please store this key securely, as it will not be shown again.
```

This command creates a new API key with the alias `mcp-openai`. The key is displayed once in the terminal. Be sure to store it securely, as it will not be shown again.

> User must have Administrator privileges.

## Listing API Keys

To display the list of stored API key aliases, use the `apikey list` command:

```shell-session
/opt/metricshub/bin$ ./apikey list
mcp-openai ********************8986
mcp-claude ********************15f3
```

This command shows the alias and a masked portion of the API key ID.

> Only users with Administrator privileges can list API keys.

## Revoking an API Key

To revoke (delete) an existing API key, use the `apikey revoke` command with the corresponding alias:

```shell-session
/opt/metricshub/bin$ ./apikey revoke --alias mcp-openai
API key 'mcp-openai' has been revoked.
```

Once revoked, the API key can no longer be used to authenticate against the MetricsHub Web server. You may create a new key with the same alias afterward, if needed.

> API key revocation also requires Administrator privileges.
