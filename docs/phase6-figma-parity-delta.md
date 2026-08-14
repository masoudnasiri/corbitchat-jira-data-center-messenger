# Phase 6 — Figma parity remaining delta

Source of truth (do not reinvent):

- `/root/jira-dev/jira-issue-chat-panel/figma/FIGMA-INVENTORY.md`
- `/root/jira-dev/jira-issue-chat-panel/figma/DESIGN-SYSTEM.md`
- `/root/jira-dev/jira-issue-chat-panel/figma/IMPLEMENTATION-MATRIX.md`
- `/root/jira-dev/jira-issue-chat-panel/figma/figma-manifest.json`

File: [Hub](https://www.figma.com/design/fyCTz4B2izGsUFSsFXV13K/Hub) (`fyCTz4B2izGsUFSsFXV13K`).
Figma MCP screenshots were only captured for five frames; most frames are inventory-only.

Phase 6 does **not** implement these gaps unless they are regressions.
Suggested future work is Phase 7+ backlog, not this phase.

## Already shipped (no delta / keep)

| Figma | Current | Platform | Notes |
|---|---|---|---|
| Chat list All / Personal / Groups | Implemented | Both | Compact sidebar on web |
| Chat thread + composer | Implemented | Both | Attach, send, voice |
| Long-press More (Reply/Copy/Share/Forward/Delete) | Implemented + Save | Both | Extra Save action is product, not a miss |
| Profile Files / Media / Links | Implemented | Both | Open message hotfix in 5a / +88 |
| Bottom nav 5 tabs | Implemented | Mobile/PWA | Web uses Jira chrome |
| Search | Server message search | Both | Figma frame is chat-row filter; Phase 5 added full-text |
| New chat / members | Implemented | Mobile/PWA | Web has new chat / group |
| Dashboard / Boards / Tasks / More | Implemented | Mobile/PWA | Visual fidelity varies |
| RTL + Jalali | Implemented | Both | |

## Remaining gaps

| # | Figma requirement | Current implementation | Platform | Severity | Future phase | API needed | Safe to defer |
|---|---|---|---|---|---|---|---|
| 1 | Chat search frame `33:3687` is a **chat-list** filter with FAB | Message search screen (Phase 5) | Both | P2 | 7 — optional list-filter chrome | No | Yes |
| 2 | Long-press overlay includes **reaction tray** (`83:9983`) | Actions exist; reaction tray not Figma-identical | Both | P2 | 7 polish | Existing reaction API | Yes |
| 3 | Profile **selection mode** + bulk toolbar (attachments/media/links selected frames) | List/grid + per-item open/download/copy | Mobile/PWA | P2 | 7 | Maybe | Yes |
| 4 | Link **preview cards** (`102:14179`) | URL rows / open link | Both | P2 | 7 | No | Yes |
| 5 | Voice bubble play/pause chrome (`102:12031`) | Voice works; visual not pixel-matched | Both | P2 | 7 | No | Yes |
| 6 | Group profile camera / header (`131:16987`) | Group info + shared chips | Mobile/PWA | P2 | 7 | Existing avatar API | Yes |
| 7 | Dashboard doughnut, due-soon carousel, manager vs employee columns | Dashboard exists; not pixel-matched | Mobile/PWA | P2 | 7 | Existing dashboard BFF | Yes |
| 8 | Approval queue frames `163:20350` / reminder modal | Schedule approval exists | Mobile/PWA | P2 | 7 | Existing | Yes |
| 9 | Kanban 412 + 834 wide (`8:2497`, `39:3322`) | Board gallery / issue; not full Figma Kanban | Mobile/PWA | P2 | 7 | Jira boards API | Yes |
| 10 | Issue detail tabs + Report tab (`262:6738`) | Issue detail + report tab in Jira/plugin | Both | P2 | 7 | Existing report | Yes |
| 11 | Tasks filter-chip strip + approve/reject card treatments | Tasks tab exists | Mobile/PWA | P2 | 7 | Existing | Yes |
| 12 | More: quiet hours / channel detail (`64:4198`, `66:3467`) | Notifications/settings exist | Mobile/PWA | P2 | 7 | Maybe | Yes |
| 13 | Design tokens from Figma variables | Local tokens; MCP `get_variable_defs` blocked | Both | P2 | 7 | No | Yes |
| 14 | Web chat is not the 412 mobile frame | Jira-embedded messenger (correct product) | Web | — | n/a | No | Yes — not a miss |
| 15 | Remaining Figma screenshots never captured | Inventory only | Docs | P2 | When MCP quota allows | No | Yes |

## Phase 6 conclusion

No **P0** or **P1** Figma gap is a current Production regression.
Shared-open and search jump are **behavior** requirements and are
covered by the regression checklist, not by new visual work.

Do not implement the P2 table in Phase 6.
