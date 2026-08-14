# Phase 6 — Hardening plan

Phase 6 is **not** a feature phase. It is hardening, regression
automation, Figma parity QA, and release safety.

Owner opened this phase on 2026-08-14 after Phase 5 close-out.

## Baseline versions (audit 2026-08-14)

| Surface | Version |
|---|---|
| Production plugin | `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat5a-shared-open-hotfix` |
| Production PWA | `1.0.0+88 / figma-chat-shared-open-hotfix` (7G TECH) |
| DEV plugin | same figmachat5a jar |
| DEV PWA | `1.0.0+88 / figma-chat-shared-open-hotfix` (**Hub** brand) |
| Gateway | unchanged `gateway-2.7-prod15` — MCP search **DEV-only** |

## Source commits (at Phase 6 start)

| Repo | Branch | Commit |
|---|---|---|
| `/root/jira-dev/jira-issue-chat-panel-mcp-control-plane` | `feature/mcp-control-plane` | `34ead9869ef34247d15cb4064daeddcaabac8f62` |
| `/root/jira-dev/corbitchat-mobile` | `main` | `392a286b7ed9e41a32250191bfac6d2a14323007` |

Do **not** build from `/root/jira-dev/jira-issue-chat-panel`.

Plugin freeze of the product code: `eaface7875fdc90af903501b6741372033287709`.  
Mobile freeze of the product code: `38d2991b209634bfd5e2c72344998fa2c2c6dcbb`.

## Build / package flow

```bash
# Plugin (offline Maven only)
cd /root/jira-dev/jira-issue-chat-panel-mcp-control-plane
mvn -s /root/.m2/settings.xml \
    -Dmaven.repo.local=/root/maven-repos/atlassian-jira-offline \
    -o clean package

# PWA
cd /root/jira-dev/corbitchat-mobile
scripts/build_web.sh hub          # DEV
scripts/build_web.sh sevengtech   # Production
```

Before package: `docs/deployment-production.md` §2.1 and
`scripts/check-release-assets.sh`.

## Rollback paths (`corbit-prod`)

| Target | Path |
|---|---|
| Plugin → figmachat5-search-mentions | `/root/jira-backups/rollback-figmachat5a-20260814-125535/ROLLBACK-plugin.sh` |
| PWA → +87 / figma-chat-design-reconcile1 | `/root/jira-backups/rollback-figmachat5a-20260814-125535/ROLLBACK-pwa.sh` |

## Scope

- Regression checklist (human + machine-readable)
- DEV-safe smoke/check scripts
- Manual QA script
- Figma remaining-delta report
- Safe polish / clear regression fixes on DEV only
- Read-only Production observation

## Out of scope

- Production deploy (unless later explicitly approved)
- New product capabilities / Phase 7 features
- Gateway production rollout / MCP search on Production
- Report/calendar behavior changes
- Workspace Agent behavior changes
- Schema / AO migrations
- Rewriting Phase 1–5 features

## Pending items (non-blocking)

1. Owner hard-refresh and confirm final UI click/highlight.
2. Push plugin/mobile commits and tags when GitHub credentials/network exist.
3. Decide separately whether to ship Gateway MCP search to Production.

## Risks

- WRM can serve stale `*-min.js` if those files ever re-enter `src/main/resources`.
- Production web basic auth is captcha-blocked from this host — browser QA is owner-side.
- AMPS still writes min files **into the JAR** at package time; that is expected. Source must stay min-free.
- DEV PWA is Hub; Production PWA is 7G TECH. Do not treat DEV Hub as a leak.

## Exact test matrix

See `docs/regression-checklist.yaml` (machine) and
`docs/regression-checklist.md` (human). Groups: A source safety, B WRM,
C Assistant filters, D search, E shared galleries, F message actions,
G mobile/PWA, H admin/health.

## Automation plan

Run on DEV only (`scripts/run-phase6-dev.sh`):

| Script | What |
|---|---|
| `scripts/check-no-stale-repo.sh` | official tree, no stale lineage, no staged secrets |
| `scripts/check-release-assets.sh` | version, no source min, required JS |
| `scripts/smoke-chat-web.sh` | chat/admin HTML 200 + shell IDs |
| `scripts/smoke-chat-api.sh` | health, search DTO, shared, around |
| `scripts/check-wrm-regression.sh` | live WRM version + handlers |
| mobile `scripts/check-mobile-label.sh` | +88 / label lockstep |
| mobile `scripts/check-branding.sh` | 7G TECH tarball + source brands |
| mobile `scripts/smoke-mobile-build.sh` | repo + analyze |

Scripts refuse Production hostnames. They do not write chat data.

## Manual QA plan

`docs/manual-qa-phase6-chat.md` — owner/tester hard-refresh path for
web, PWA, and admin. Automation cannot replace click/highlight UX.

## Versioning

No product-code change ⇒ **do not bump** plugin or PWA versions.
If a DEV-only hotfix is later required:

- Plugin: `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat6-hardening1`
- Mobile: `1.0.0+89 / figma-chat-hardening1`
