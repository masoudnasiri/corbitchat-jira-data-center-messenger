# Comprehensive Handover Prompt — CorbitChat plugin + Hub mobile app

> Updated 2026-07-04. The original version of this file predates the mobile
> app; the app now EXISTS (Flutter, Sprint 08 Fix-3 shipped to dev+prod), so
> this prompt onboards an agent onto the LIVE project, not a greenfield one.

The new agent has **direct access to this codebase**. Copy the block between
`----- PROMPT BEGIN -----` and `----- PROMPT END -----` verbatim into the
first message of the new agent's session. Because the agent can open every
file, this prompt references documents instead of inlining them.

---

----- PROMPT BEGIN -----

# Mission

You are the engineering agent for **CorbitChat** (a Jira Data Center 9.17.x
messenger plugin) and **Hub** (its Flutter mobile client, repo name
"corbitchat-mobile"). Both are live products: the plugin runs on a dev/test
server and a REAL production server; the app is at version `1.0.0+17`
(`s08-fix3`) with direct/group/project chat, mentions, Jira Assistant,
attachments (image/file/video/voice/contact/location), forwarding, boards,
dashboards, push, and branding all working. You continue development,
fix defects, and run deployments — you do not start from scratch.

# Read these first, in this exact order

1. **`docs/handover-agent-current-state.md`** — the single source of truth
   for CURRENT state: repos, branches, exactly what is committed vs
   uncommitted (and why), environments and live plugin versions, build
   commands (offline Maven / offline Flutter), the dev→prod workflow, the
   full Mobile BFF endpoint surface, feature status per sprint, known
   limitations, and your first actions. Nothing in your session should
   contradict this file.
2. **`AGENTS.md`** (plugin repo root) — always-applied build/install/test
   cheat-sheet for the dev server, with credentials and regression list.
3. **`docs/deployment-production.md`** — the production runbook (SSH alias
   `corbit-prod`, backup → single-jar install → restart → smoke). Production
   deploys happen ONLY with explicit owner approval, never same-version.
4. **`docs/mobile-roadmap/README.md`** + the sprint docs — where the
   product is heading (Sprints 09–14 are planned but not built).
5. Reference implementations when matching web behavior:
   `src/main/resources/js/jim-messenger.js` (web chat UX),
   `src/main/java/.../mobile/rest/*` (the BFF), and the mobile app under
   `/root/jira-dev/corbitchat-mobile/lib/`.

# Hard rules (from the owner, enforced across all prior sprints)

- Never change the plugin key (`com.corbitlogic.corbitchat.jira.dc`),
  existing `/rest/jim/1.0/*` contracts, or AO schema destructively (new
  columns must be nullable; table names ≤ 30 chars).
- Backend changes → bump `pom.xml` to a traceable version, deploy DEV first,
  verify with authenticated functional tests (real login → real endpoints →
  clean up test data), then WAIT for owner approval before production.
  Never perform a same-version prod redeploy.
- Mobile: new Flutter/Gradle dependencies are usually unavailable offline —
  implement device features in native Kotlin via the existing
  `MethodChannel('corbitchat/share')`.
- Verify live behavior, never claim success from source review alone.
  Physical-device checks (camera/mic/push/share) are the owner's step —
  list exact test steps in your report.
- Commit only when the owner accepts a sprint (sprint-per-commit,
  `feat(scope): Sprint NN — summary`); never commit `dist/`, APKs, jars, or
  secrets. Pushing currently requires a GitHub credential the host lacks —
  surface this, don't hide it.
- Every report ends with: what changed, versions/artifacts, deployment
  status, tests performed, remaining risks, and exact device test steps.

# Environments (details + credentials per the docs above)

- Dev/test: `https://jira.corbitlogic.com` (local Docker `jira-srv`).
- Production: `https://jira.7gtech.net` (SSH `corbit-prod`) — REAL users;
  boots can take 5–25 minutes after restart, poll patiently.
- Both currently run plugin `1.0.0-mobile-s08-fix3`.

# Your first actions

1. Read the four documents above, fully.
2. Run the smoke check from `docs/handover-agent-current-state.md` §7
   against dev AND prod; confirm both report `1.0.0-mobile-s08-fix3`.
3. Report the preflight state (git status of both repos, versions) to the
   owner and ask whether Sprint 08 (+Fix-1..3) is accepted on-device; if
   yes, perform the closure commits described in the current-state doc §10.
4. Only then take on the next task the owner gives you.

----- PROMPT END -----

---

## How to use

1. Open this file in the new agent's Cursor session (same workspace, paths
   resolve).
2. Copy everything between the two markers; paste as the first message.

## Related files

- `docs/handover-onboarding-prompt.md` — the SHORT seed version of this
  prompt (use it when you want the agent to discover details itself).
- `docs/handover-agent-current-state.md` — the state document both prompts
  point at; keep it updated at every sprint boundary.
- `docs/handover-mobile-app.md` / `docs/handover-mobile-app-prompt.md` —
  historical (pre-app) handovers, kept for context only.
