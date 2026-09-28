`opamp:`, `m8b:` and `upgrade:` were three root sections describing one relationship. The first two reach the **same server** — same host, same secret, same trusted certificate, same instance uid — so the endpoint and the credential were written twice, and nothing stopped them drifting apart. A dev file pointing OpAMP at `https` and the tunnel at `ws://` is a configuration the agent accepted without a word.

## What an operator writes now

```yaml
central:
  enabled: true
  url: http://127.0.0.1:4320
  headers:
    Authorization: Bearer dev-agent-secret
  attributes:
    env: dev
  opamp:
    pollInterval: 10s
  upgrade:
    hostAllowlist: [ repo.metricshub.com ]
    downloadHeaders:
      repo.metricshub.com:
        Authorization: Basic ...
```

The tunnel needs no line at all: `ws://127.0.0.1:4320/ws/agent` is derived from `url`.

## The shape

| Was | Is | Note |
|---|---|---|
| `opamp.enabled` | `central.enabled` + `central.opamp.enabled` | the switch moves up one |
| `opamp.endpoint` / `m8b.endpoint` | `central.url` + each channel's `path` | a full `endpoint:` still overrides, per channel |
| `opamp.headers` / `m8b.headers` | `central.headers` | written once |
| `opamp.certificateFile` / `m8b.certificateFile` | `central.certificateFile` | written once |
| `opamp.attributes` | `central.attributes` | both channels already reported it |
| `m8b:` | `central.tunnel:` | |
| `upgrade:` | `central.upgrade:` | no key renamed, no meaning changed |

`CentralConfig.opamp()` and `.tunnel()` hand out the **resolved** combination — endpoint built from `url` and the channel's path, `ws`/`wss` derived from the scheme, headers merged key by key, certificate inherited. Every reader below is unchanged but for where it asks, and change detection keeps working because equality is over the resolved value.

## Two decisions worth reviewing

**`central.enabled: true` turns on both channels.** Either is refused on its own — `tunnel.enabled: false` is fleet management without exposing this agent's tools to the Governor — but a server that polls an agent it cannot invoke tools on is half a link, so that is not the default. This is the one security-relevant default in the change.

**`upgrade:` sits beside the channels, not under `opamp:`.** An offer arrives over OpAMP, but the download speaks to a **repository**: its own hosts, its own credentials, its own certificate authority (`upgrade.trustedCertificateFile` is not `central.certificateFile`, and they are rarely the same). Nothing here happens without a Central server, which is why it is under this roof; it is not an OpAMP transport setting, which is why it is not under that key.

## Docs

`m8b-tunnel-protocol.md` → `central-tunnel-protocol.md` and `fleet-management.md` → `central-fleet-management.md` (`git mv`, history follows), with §9 rewritten as `central:` / `central.opamp:` / `central.upgrade:`, §13.4 as `central.tunnel:`, and both README blocks showing the new roof.

Class, package and thread names still say `m8b`. Renaming them is mechanical and touches ~640 occurrences; it is a follow-up PR so this diff stays about the configuration. Note for that one: `AgentConstants.USER_INFO_SEPARATOR` contains `m8b` and is **written into the keystore** — renaming it would make every stored user unreadable.

## Checks

Run in the mandated order on `metricshub-agent`:

- `mvn prettier:write` — clean
- `mvn license:check-file-header` — 0 issues
- `mvn checkstyle:check` — 0 issues
- `mvn verify` — **BUILD SUCCESS, 1002 tests, 0 failures, 0 errors** (4 skipped, as before)

New coverage in `CentralConfigTest` (7 cases): what a channel inherits, `ws`/`wss` derivation, a trailing slash, the per-channel override, a moved path, and a channel that cannot let itself in.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

