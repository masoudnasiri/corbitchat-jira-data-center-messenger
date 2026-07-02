# Sprint 04G — Mobile Feature Access Control

Admin-configurable, server-enforced access control for CorbitHub mobile app
sections (Dashboard, Chat, Boards, Projects, Tasks, Issue Detail). Layered on
top of Jira permissions — it never replaces them. Default is **full access**:
with no rules configured, every user keeps every mobile feature.

## Feature keys

Canonical keys (backend `JimMobileFeatures`, mobile `MobileFeatures`), in
navigation-priority order:

`boards`, `dashboard`, `chat`, `tasks`, `projects`, `issueDetail`

`profile` / `settings` / `about` / logout are **never** gated — a restricted
user can always change settings or sign out (the More tab stays reachable).

## Rule storage

New Active Objects entity `JimMobileFeatureRule`
(`@Table("JimMobileFeatRule")` → `AO_xxxxxx_JIMMOBILEFEATRULE`, 27 ≤ 30 chars):

| Column | Meaning |
|---|---|
| `SUBJECT_TYPE` | `USER` or `GROUP` |
| `SUBJECT_VALUE` | username / user key, or group name |
| `FEATURES` | CSV of allowed feature keys, e.g. `chat,boards` |
| `ENABLED` | rule on/off |
| `PRIORITY` | integer, higher wins |
| `CREATED_BY` / `CREATED_AT` / `UPDATED_AT` | audit metadata |

The chat access policy (`JimAccessPolicy`) is a different, chat-pair model and
was left untouched; mobile feature access is a separate, simpler entity that
reuses the same USER/GROUP + priority conventions.

## Precedence (deterministic)

1. Only **enabled** rules are considered.
2. A `USER` rule is more specific than a `GROUP` rule and **always wins**: if any
   enabled USER rule matches, the single highest-priority USER rule applies.
3. Otherwise, if any enabled GROUP rule matches (user is a member), the single
   highest-priority GROUP rule applies.
4. Ties (same type + same priority) are broken by the **lowest row id** (oldest
   rule wins) — fully deterministic.
5. If **no** rule matches → the user gets **all** features (backward-compatible
   default).

Exactly one rule ever applies to a user; feature sets are **never merged**, so
an admin can always reason about a single applied rule.

## Backend enforcement

- `JimMobileFeatureService.isAllowed(user, key)` / `allowedFeatures(user)` is the
  single source of truth, reused by bootstrap and every gate.
- Each mobile BFF endpoint checks its feature immediately after auth and before
  loading data, returning a consistent **403** contract:
  `{"error":"feature_disabled","feature":"<key>","message":"…"}`.
- Endpoint → feature map:
  - `GET /dashboard` → `dashboard`
  - `/chat/*` (all methods) → `chat`
  - `GET /boards` → `boards`
  - `GET /projects`, `GET /projects/{key}` → `projects`
  - `GET /issues/search` → `tasks`
  - `GET /issues/{key}` → `issueDetail`
- Jira permission checks (Browse Project, issue security, chat access policy,
  license) remain in place and run **after** the feature check.
- `bootstrap` is intentionally **not** gated (so the app can always learn its
  allowed features and reach logout) and now returns `mobileFeatures`
  (per-key booleans) + `mobileFeatureOrder`.

## Admin UI

New **Mobile Access** tab in the CorbitChat admin console
(`/plugins/servlet/jim/admin`): a rules table (subject, allowed features,
enabled, priority) + an add/edit form with a USER/GROUP typeahead picker (reused
from the chat policy UI) and a feature checkbox row. Backed by
`/rest/jim/1.0/admin/mobile-feature-rules` (GET/POST/PUT/DELETE, SYSTEM_ADMIN
only) and `/rest/jim/1.0/admin/mobile-features`. Every change is written to the
existing admin audit log (`mobileFeatureRule.create|update|delete`).

## Mobile app

- `Bootstrap.mobileFeatures` parsed from bootstrap; missing key (old server) →
  all features (backward compatible).
- `AppShell` builds bottom navigation from allowed features only. Display order
  Dashboard, Chat, Boards, Tasks; Projects is reached from the Boards top-bar
  action (shown only when `projects` allowed). If Boards is **not** allowed but
  Projects **is**, Projects becomes its own tab.
- Default landing = first allowed of `[boards, dashboard, chat, tasks,
  projects]` → Boards for full-access users; **Chat for a chat-only user**.
- Deep-link/drill-in protection: `IssueDetailScreen` shows a
  `FeatureDisabledView` when `issueDetail` is disabled (no request issued).
- No allowed features → a clear "No mobile features available" state, with the
  More tab still reachable (settings + logout).
- More/Profile/Settings/About/Logout always available.

## Verification

Test server (`185.83.181.194`):
- Default (no rules): bootstrap all `true`; all endpoints 200.
- USER rule `m.nasiri → chat`: bootstrap chat-only; `chat` 200; dashboard,
  boards, projects, issues/search, issues/{key} all **403 feature_disabled**.
- Precedence: USER (pri 10) + GROUP `chat-internal → dashboard,boards` (pri 5)
  → chat only (USER wins). Disable USER rule → group applies (dashboard,boards;
  chat 403, dashboard 200).
- Rules deleted → full access restored; admin JS/CSS batch confirmed live.
- `flutter analyze` clean, `flutter test` green, release APK built.

Production (`193.162.129.56` / `jira.7gtech.net`):
- DB + jar backup taken before deploy (`/root/jira-backups/…-185446`).
- Deployed `1.0.0-mobile-s04g`; all smoke endpoints 200; bootstrap all `true`
  (no rules → backward compatible); AO health OK (new table created cleanly).

## Rollback

Reinstall `/root/jira-dev/releases/corbitchat-jira-dc-1.0.0-mobile-s04f.jar`
and restart (see `docs/deployment-production.md`). Deleting all rules also
restores full access without a redeploy.

## Artifacts

- Plugin: `target/corbitchat-jira-dc-1.0.0-mobile-s04g.jar`
- APK: `corbitchat-mobile/build/corbithub-s04g-feature-access.apk`
