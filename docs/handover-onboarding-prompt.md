# Onboarding Prompt — new agent for CorbitChat + Hub

> Updated 2026-07-05. Paste the block below verbatim as the FIRST message of
> a new agent session in this workspace. It stays short on purpose: the
> referenced documents carry the full detail and are kept current.

----- PROMPT BEGIN -----

You are taking over two live, related products in this workspace:

- **CorbitChat** — a Jira Data Center 9.17.x messenger plugin
  (`/root/jira-dev/jira-issue-chat-panel`), plugin `1.0.0-mobile-s11-fix3`,
  deployed on a dev/test server and a REAL production server.
- **Hub** — its Flutter mobile client
  (`/root/jira-dev/corbitchat-mobile`), currently `1.0.0+26` / `s11.4-uipolish`.
  It ships in **two brand flavors from one codebase**: standard **Hub** and a
  customer-branded **7G TECH** build.

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
  `sprint-07-attachments-voice.md`, `sprint-08-group-project-assistant.md`,
  `sprint-09-comments-replies.md` (+Fix-1), `sprint-10-transitions-worklog-fieldrules.md`,
  `sprint-11-boards.md` (+Fix-1/Fix-3 — real board lanes with empty lanes,
  drag-to-transition, project workspace tabs, inline project chat).
  Two efforts have **no dedicated sprint guide** — see
  `docs/handover-agent-current-state.md` §9/§11 for the record:
  the **7G TECH branded build variant** (Android product flavors +
  `--dart-define=BRAND`) and **Sprint 11.4 mobile UX polish** (semantic
  design tokens, shared `StatusBadge`/`PriorityBadge`/`KeyChip`, title-first
  cards, operational issue-detail header, prioritized action sheet).
  Reality vs plan differs in places — trust
  `docs/handover-agent-current-state.md` §9 for what actually shipped.
- **Not built yet** (the upcoming work, in order):
  - `sprint-12-productivity-deeplinks-biometrics.md` — deep links,
    biometric unlock, productivity polish.
  - `sprint-13-hardening-security.md` — security/hardening pass.
  - `sprint-14-release-qa.md` — release QA.
  - Also flagged for later: PWA work, and a recommended (not yet done)
    bottom-nav reorder (Dashboard · Tasks · Boards · Chat · More).

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
  `dist/`, `releases/`, or secrets. GitHub pushes now work when the owner
  supplies a Personal Access Token (classic); use it inline via a transient
  credential helper, never persist it to disk, and remind the owner to
  rotate it afterwards. The plugin remote was renamed on GitHub to
  `corbitchat-jira-data-center-messenger` (old `corbitchatforjira` URL still
  redirects). Both repos are currently pushed and in sync with origin.
- Keep web and mobile behavior consistent; the plugin's web surface and
  shared services are the specification.

# Your first actions now

1. Read documents 1–4 above fully; skim the delivered sprint guides and
   read `sprint-12-productivity-deeplinks-biometrics.md` (the next planned
   sprint).
2. Run the smoke check (current-state doc §7) against dev and prod and
   confirm both report plugin `1.0.0-mobile-s11-fix3`.
3. Report preflight status (git state of both repos, app/plugin versions)
   and ask the owner for the current priority. Note: a temporary APK download
   endpoint (`/apk/` on `jira.corbitlogic.com`, plus staged files) may still
   be live from mobile build hand-offs — offer to tear it down.

----- PROMPT END -----
