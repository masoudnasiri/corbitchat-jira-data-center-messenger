# Sprint 04D — Mobile Board Gallery

Status: **DONE** — deployed to test (`http://185.83.181.194:8080`) and production
(`https://jira.7gtech.net`). Plugin version `1.0.0-mobile-s04d`.

Goal: add a mobile **Board Gallery** to CorbitHub that lists the Jira boards a
user can access, as mobile cards that **mirror the existing web plugin Board
Gallery cards** (same data, same avatar colour/initials, same type/lead/project
chips). Tapping a card opens a safe, read-only landing (the full interactive
board stays in Jira). No plugin key / AO schema / existing endpoint changes.

Credentials are never written here; use the operational values from AGENTS.md /
your secure notes.

---

## 1. What shipped

### Backend (Mobile BFF)

- **New endpoint** `GET /rest/corbit-mobile/1.0/boards` — returns the boards the
  caller can see, already permission-filtered, for **both** PAT and mobile-session
  auth (covered by the existing `/rest/corbit-mobile/1.0/*` session filter).
- Response shape:
  ```json
  {
    "agileAvailable": true,
    "total": 5,
    "boards": [{
      "id": 29,
      "name": "CorBit With CEO",
      "type": "kanban",
      "projectKeys": ["COR"],
      "multipleProjects": false,
      "project": { "key": "COR", "name": "CorBit With CEO",
                   "avatarUrl": "/rest/corbit-mobile/1.0/avatar/project/COR",
                   "lead": "Masoud Nasirinezhad",
                   "leadAvatarUrl": "/rest/corbit-mobile/1.0/avatar/user/JIRAUSER10000",
                   "url": "https://jira.7gtech.net/browse/COR" },
      "assignedCount": 2
    }]
  }
  ```
- **Data source:** Jira Software / GreenHopper `RapidViewService.getRapidViews(user)`
  (in-process, permission-safe). Board → project mapping is resolved from the
  board's **saved filter JQL** (same source the web gallery uses); `assignedCount`
  is a bounded `SearchService.searchCount` of the board's JQL `AND assignee =
  currentUser()`.
- **Card fields mirror the web gallery** (`jim-board-gallery.js`): board name,
  type (Scrum/Kanban/Simple), primary project chip (key + name), "Multi" for
  multi-project boards, project lead, and a "my tasks" count. Avatars reuse the
  Sprint 04C proxy paths (`/avatar/project/*`, `/avatar/user/*`) — no tokens or
  internal hosts ever leave the server.

### Frontend (Flutter — CorbitHub)

- **Board Gallery screen** reachable from the **Projects tab** app-bar action
  (`view_kanban` icon), per the design-system placement guidance — **not** a 6th
  bottom tab (Material caps at 5, and it avoids shell churn).
- The per-project **Board tab** in the project overview now shows that project's
  boards (previously "coming soon"), reusing the same provider/cards.
- **Card widget** reproduces the web card exactly: 44px rounded avatar with the
  **same 10-colour palette + hash** (`name + '#' + id`) and initials rules, the
  project image overlaid on top (initials remain if the image fails), type chip
  with matching Scrum/Kanban/Simple tones, project key badge, lead line, and
  "All tasks" / "My tasks: N" pills.
- **Search** (name / project) + **type filter** (All / Scrum / Kanban), and full
  **loading / empty / no-match / error+retry / boards-unavailable** states.
- **Safe tap landing** (`BoardDetailScreen`): read-only board metadata + an
  "Open in Jira" action that copies the RapidBoard URL (no browser dependency
  added). The full board view intentionally stays in Jira.
- Fully **RTL/LTR** and **FA/EN** localized (new keys in `lib/core/strings.dart`),
  using the Sprint 04C avatar infra and readable `onSurfaceVariant` text tokens.

---

## 2. Safety / risk containment

The only structurally risky part is depending on a **bundled** Jira Software
plugin. It is contained so it can never break the rest of CorbitChat:

1. **Dependency is `provided`** (`com.atlassian.jira.plugins:jira-greenhopper-plugin`)
   — never packaged into our jar.
2. **OSGi import is optional.** The pom's `Import-Package` ends with
   `*;resolution:=optional`, so the three `com.atlassian.greenhopper.*` packages
   are imported `resolution:=optional` (verified in the built `MANIFEST.MF`). If
   Jira Software is absent/mismatched, **the plugin still enables** — only the
   board endpoint degrades.
3. **All GreenHopper references live in one class** (`JimMobileBoards`) and the
   endpoint calls it inside `try/catch(Throwable)`. On any linkage/API error the
   endpoint returns `{"agileAvailable": false, "boards": []}` (HTTP 200) and the
   app shows a friendly "Boards unavailable" state. Every other endpoint is
   unaffected.
4. **Per-board failures are isolated** — one bad board never drops the gallery.
5. **Permissions:** boards come pre-filtered by `RapidViewService`; each project
   chip is re-checked with `BROWSE_PROJECTS`; counts run through `SearchService`
   (issue-level security enforced). No client-supplied JQL is accepted.

---

## 3. Key files

| File | Role |
|------|------|
| `src/main/java/.../mobile/JimMobileBoards.java` | **New.** Isolated GreenHopper access; builds board cards (name/type/project/lead/count). |
| `src/main/java/.../mobile/rest/CorbitMobileBoardResource.java` | **New.** `GET /boards`; guards GreenHopper behind `try/catch(Throwable)`. |
| `pom.xml` | Added `jira-greenhopper-plugin` (`provided`); version → `1.0.0-mobile-s04d`. |
| `lib/models/board.dart` | **New.** `BoardSummary`, `BoardGallery`. |
| `lib/api/corbit_api.dart` | Added `boards()`. |
| `lib/state/issue_providers.dart` | Added `boardsProvider`. |
| `lib/features/boards/board_visuals.dart` | **New.** Palette/hash/initials/type styles mirroring the web gallery. |
| `lib/features/boards/widgets/board_card.dart` | **New.** Gallery card. |
| `lib/features/boards/board_gallery_screen.dart` | **New.** Screen + search + type filter + states. |
| `lib/features/boards/board_detail_screen.dart` | **New.** Safe read-only landing. |
| `lib/features/boards/project_boards_view.dart` | **New.** Embeddable project-scoped board list. |
| `lib/features/shell/app_shell.dart` | Projects-tab app-bar action → gallery. |
| `lib/features/projects/project_overview_screen.dart` | Board tab now shows project boards. |
| `lib/core/strings.dart` | New FA/EN Boards copy. |

---

## 4. Build / deploy / verify

- Plugin: offline Maven build green → `corbitchat-jira-dc-1.0.0-mobile-s04d.jar`
  (kept in `/root/jira-dev/releases/`). Manifest confirms greenhopper imports are
  `resolution:=optional`.
- Flutter: `flutter analyze` clean (no issues); `flutter build apk --release`
  green (`build/app/outputs/flutter-apk/app-release.apk`, ~54.5 MB).
- **Test server:** all endpoints 200; `/boards` returns real Scrum boards with
  project chips + avatar proxy paths + counts.
- **Production** (`https://jira.7gtech.net`, backup taken first — DB dump + prior
  `s04c` jar preserved for rollback): all endpoints 200; `/boards` returns 5
  boards (Kanban + Scrum, incl. Persian project names) with proxy avatars.
- **Regression (both servers):** `chat/conversations`, `dashboard`, `projects`,
  `bootstrap`, and the web `/plugins/servlet/jim/chat` all 200.
- **Leak scan:** board JSON exposes only proxy avatar paths and the public browse
  URL — no tokens, no internal IPs.

---

## 5. Out of scope (future)

- Full interactive board (columns, sprints, drag-and-drop) — intentionally kept
  in Jira; mobile shows a safe landing.
- Board card "Project chat" pill from the web gallery (mobile chat is 1:1 direct;
  project chat isn't in mobile scope yet).
- Server-side pagination / caching of boards (current bound: 120 boards).
