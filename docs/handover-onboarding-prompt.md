# Onboarding Prompt — new agent for CorbitChat + Hub

> Updated 2026-07-04. Paste the block below verbatim as the FIRST message of
> a new agent session in this workspace. It stays short on purpose: the
> referenced documents carry the full detail and are kept current.

----- PROMPT BEGIN -----

You are taking over two live, related products in this workspace:

- **CorbitChat** — a Jira Data Center 9.17.x messenger plugin
  (`/root/jira-dev/jira-issue-chat-panel`), deployed on a dev/test server
  and a REAL production server.
- **Hub** — its Flutter mobile client
  (`/root/jira-dev/corbitchat-mobile`), currently `1.0.0+17` / `s08-fix3`.

# Documents to read, in this order, before doing anything

1. `docs/handover-agent-current-state.md` — the authoritative snapshot:
   repos and git state (what is committed vs deliberately uncommitted),
   environments and live plugin versions, offline build commands, the
   dev→prod workflow and its guardrails, the Mobile BFF API surface,
   per-sprint feature status, known limitations, and your first actions.
2. `AGENTS.md` (plugin repo root) — the always-applied dev-server
   build/install/test cheat-sheet, credentials, and regression checklist.
3. `docs/deployment-production.md` — the production runbook. Production
   changes require the owner's explicit approval, a backup first, a version
   bump (never same-version redeploys), and patient health polling
   (boots can take 5–25 minutes).
4. `docs/mobile-roadmap/README.md` — the sprint-by-sprint delivery plan and
   the architecture decisions behind it (Flutter client + Mobile BFF).

# Sprint guides (`docs/mobile-roadmap/`)

Every sprint has its own guide with scope, backend/mobile tasks, API
contracts, design references, acceptance criteria and a Definition of Done.
Read the guide for any sprint you touch; the roadmap README maps them to
phases.

- **Delivered** (already built, verified, and on dev+prod — read for
  context, do not re-implement): `sprint-00-foundation-spike.md`,
  `sprint-01-auth-app-shell.md`, `sprint-01b-mobile-session-auth.md`,
  `sprint-02-direct-chat.md`, `sprint-03-dashboard-tasks.md`, the
  `sprint-04*.md` series (issue detail, projects, identity, avatars/dates,
  board gallery, date correctness, board nav, feature access),
  `sprint-05-mobile-push.md` + `sprint-05b-rich-push.md`
  (+ `sprint-05-push-notifications.md`), `sprint-06-rich-chat.md`,
  `sprint-07-attachments-voice.md`, `sprint-08-group-project-assistant.md`.
  Reality vs plan differs in places — trust
  `docs/handover-agent-current-state.md` §9 for what actually shipped
  (e.g. Sprint 08 also delivered chat filters, an inline emoji panel,
  attachment forwarding, and group/project push fan-out via Fix-1..3).
- **Not built yet** (the upcoming work, in order):
  - `sprint-09-comments-replies.md` — issue comments + reply-to-comment.
  - `sprint-10-transitions-worklog-fieldrules.md` — workflow transitions,
    worklog, server-driven field rules.
  - `sprint-11-boards.md` — full Scrum/Kanban board views.
  - `sprint-12-productivity-deeplinks-biometrics.md` — deep links,
    biometric unlock, productivity polish.
  - `sprint-13-hardening-security.md` — security/hardening pass.
  - `sprint-14-release-qa.md` — release QA.

Supporting specs referenced by the guides: the product/technical PDF and
design system under `docs/Mobile Design System/`, `docs/architecture.md`
(plugin architecture + REST reference), and the web reference clients
`src/main/resources/js/jim-api.js` / `jim-messenger.js` /
`jim-comment-reply.js`.

# Non-negotiable working rules (how this project has always run)

- Deploy and verify on DEV first, with real authenticated functional tests
  (login → exercise endpoints → clean up test data); never claim success
  from source review alone.
- Backend changes need a `pom.xml` version bump (traceable, e.g. `s09`,
  `s09-fix1`); production only after the owner's explicit approval.
- Never change the plugin key, `/rest/jim/1.0/*` contracts, or AO schema
  destructively. New Flutter/Gradle dependencies are usually unavailable
  offline — use native Kotlin over `MethodChannel('corbitchat/share')`.
- Physical-device verification (camera/mic/push/share) belongs to the
  owner: finish every task with exact device test steps, versions,
  deployment status, and remaining risks.
- Commit only when the owner accepts a sprint; never commit APKs, jars,
  `dist/`, or secrets. GitHub pushes are currently blocked (no credential
  on this host) — report it, don't work around it silently.
- Keep web and mobile behavior consistent; the plugin's web surface and
  shared services are the specification.

# Your first actions now

1. Read documents 1–4 above fully; skim the delivered sprint guides and
   read `sprint-09-comments-replies.md` (the next planned sprint).
2. Run the smoke check (current-state doc §7) against dev and prod and
   confirm both report plugin `1.0.0-mobile-s08-fix3`.
3. Report preflight status (git state of both repos, app/plugin versions)
   and ask the owner for the current priority — closure commits for
   Sprint 08 (+Fix-1..3) if accepted on-device, otherwise the next task.

----- PROMPT END -----
