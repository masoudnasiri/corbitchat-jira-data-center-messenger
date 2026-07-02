# CorbitChat Mobile — Development Roadmap

Sprint-by-sprint delivery plan for the **CorbitChat Mobile Application
for Jira Data Center** (Android + iOS). Each sprint below has its own
document with scope, backend/mobile tasks, API contracts, design-system
references, acceptance criteria and a Definition of Done. Ship at any
sprint boundary.

## Source of truth

This roadmap is derived from and must stay consistent with:

- **Product & Technical Spec (PDF):**
  `docs/Mobile Design System/CorbitChat_Jira_DC_Mobile_App_Product_and_Technical_Spec_EN.pdf`
- **Design System:** `docs/Mobile Design System/` (tokens, CSS,
  `flutter/corbitchat_theme.dart`, and the `docs/*.md` specs).
- **Engineering handover:** `docs/handover-mobile-app-prompt-full.md`
  and `docs/handover-mobile-app.md`.
- **Plugin architecture reference:** `docs/architecture.md` (feature
  list §2, data model §4, REST reference §8, data flows §9, security
  §11, external integrations §12).
- **Existing REST client (payload shapes):**
  `src/main/resources/js/jim-api.js`,
  `src/main/resources/js/jim-messenger.js`,
  `src/main/resources/js/jim-comment-reply.js`.

## Architecture decision (reconciling the two handovers)

Two visions exist in the docs. This roadmap follows the **PDF spec**,
which is the newer, definitive product/technical document, and which
the design system was built for:

| Topic | Handover prompt (older) | PDF spec (adopted) |
|-------|-------------------------|--------------------|
| Client framework | React Native + TS | **Flutter** (stable RTL, matches `flutter/corbitchat_theme.dart`) |
| Server integration | Mobile talks direct to `/rest/jim/1.0/*` + `/rest/api/2/*` | **Mobile BFF inside the plugin** at `/rest/corbit-mobile/1.0/*` |
| Backend work in phase 1 | none | new AO tables + mobile endpoints |

**Why the BFF:** permission-aware, paginated, UI-ready payloads;
server-driven field rules; a single push owner; and one place to add
`actioned`/receipt/mention semantics so web and mobile stay identical.
Where a mobile endpoint is not yet built, the client may fall back to
the existing `/rest/jim/1.0/*` and native Jira REST — noted per sprint.

> The mobile client lives in a **new sibling repo**
> `/root/jira-dev/corbitchat-mobile/`. The Mobile BFF is **new code in
> the plugin repo** under `com.corbitlogic.jira.internalmessenger` with
> base path `/rest/corbit-mobile/1.0`. Do not change the plugin key, the
> existing AO schema, or existing `/rest/jim/1.0/*` contracts.

## Conventions used by every sprint doc

- **Duration:** 2 weeks unless noted. **Team:** ~1 backend (plugin
  Java) + ~2 mobile (Flutter) + shared QA.
- **Tracks:** each sprint separates **Backend (plugin BFF)** and
  **Mobile (Flutter client)** tasks so the two teams can parallelize.
- **Every screen** must implement the five states (loading / empty /
  error / no-permission / retry) per `docs/Mobile Design System/docs/states.md`.
- **RTL-first:** Persian `fa-IR` default; verify LTR too — see
  `docs/Mobile Design System/docs/rtl-i18n.md`.
- **Security:** no endpoint returns data by id alone; always resolve the
  current user and enforce Jira permissions / `JimAccessPolicyService`.

## Phase → Sprint map

| Phase (PDF §9) | Sprint | Document |
|----------------|--------|----------|
| Phase 0 — Spike | 00 | [Foundation & Technical Spike](./sprint-00-foundation-spike.md) |
| Phase 1 — MVP Core | 01 | [Auth, Bootstrap & App Shell](./sprint-01-auth-app-shell.md) |
| Phase 1 — MVP Core | 02 | [Direct Chat](./sprint-02-direct-chat.md) |
| Phase 1 — MVP Core | 03 | [Dashboard & Tasks](./sprint-03-dashboard-tasks.md) |
| Phase 1 — MVP Core | 04 | [Read-only Issue Detail & Projects](./sprint-04-issue-detail-projects.md) |
| Phase 1 — MVP Core | 05 | [Push Notifications (FCM/APNs)](./sprint-05-push-notifications.md) |
| Phase 2 — Jira Actions | 06 | [Rich Chat](./sprint-06-rich-chat.md) |
| Phase 2 — Jira Actions | 07 | [Attachments & Voice](./sprint-07-attachments-voice.md) |
| Phase 2 — Jira Actions | 08 | [Group & Project Chat + Assistant](./sprint-08-group-project-assistant.md) |
| Phase 2 — Jira Actions | 09 | [Issue Comments & Replies](./sprint-09-comments-replies.md) |
| Phase 2 — Jira Actions | 10 | [Transitions, Assign, Worklog & Field Rules](./sprint-10-transitions-worklog-fieldrules.md) |
| Phase 3 — Board & Productivity | 11 | [Boards (Gallery, Board/List, Sprints)](./sprint-11-boards.md) |
| Phase 3 — Board & Productivity | 12 | [Productivity, Deep Links & Biometrics](./sprint-12-productivity-deeplinks-biometrics.md) |
| Phase 4 — Hardening | 13 | [Hardening & Security](./sprint-13-hardening-security.md) |
| Phase 4 — Hardening | 14 | [Release & QA](./sprint-14-release-qa.md) |

## Indicative timeline

| Phase | Sprints | Weeks |
|-------|---------|-------|
| Phase 0 | 00 | 2 |
| Phase 1 | 01–05 | 10 |
| Phase 2 | 06–10 | 10 |
| Phase 3 | 11–12 | 4 |
| Phase 4 | 13–14 | 4 |
| **Total** | **15 sprints** | **~30 weeks** |

Timeline assumes the two tracks run in parallel; a smaller team should
serialize backend-before-mobile within each vertical slice and expect a
longer calendar.

## Cross-cutting workstreams (spread across sprints)

- **Server-driven field rules** — introduced in Sprint 10, consumed by
  dashboard/board/issue cards thereafter.
- **Offline read cache + write drafts** — cache added incrementally
  from Sprint 02; consolidated/hardened in Sprint 14 (v1.0 is read-cache
  + draft-preservation, not full offline write per PDF §1.3).
- **Audit + observability** — every write endpoint gets a minimal audit
  record as it lands; formalized in Sprint 13.
- **Localization (fa/en) + Jalali dates** — enforced from Sprint 01 on
  every new screen, never retrofitted.

## New plugin data model (added incrementally, PDF §6.3)

| Entity | Introduced in | Purpose |
|--------|---------------|---------|
| `JimMobileDevice` | Sprint 05 | device + push token registration |
| `JimMobilePreference` | Sprint 01 | language, notification level, quiet hours |
| `JimFieldDisplayRule` | Sprint 10 | field color/format/badge rules |
| `JimMobileSyncCursor` | Sprint 03 | lightweight dashboard/chat sync |
| `JimMobileAudit` | Sprint 13 | audit of sensitive mobile writes |
| `JimMobileSession` | Sprint 13 (optional) | plugin-issued session/refresh tokens |
